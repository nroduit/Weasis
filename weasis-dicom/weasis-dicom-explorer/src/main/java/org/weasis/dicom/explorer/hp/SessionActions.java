/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.hp;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.setting.Settings;
import org.weasis.core.ui.docking.Shell;
import org.weasis.core.ui.docking.ShellState;
import org.weasis.core.ui.docking.ws.WorkspaceManager;
import org.weasis.core.ui.docking.ws.WorkspaceView;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot;
import org.weasis.core.ui.editor.DerivedSeriesProvider;
import org.weasis.core.ui.editor.ViewerRestorer;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.hp.HangingPlan.CellPlan;
import org.weasis.dicom.explorer.DicomModel;

/**
 * The reading-state actions behind the {@code state:} verbs and the preferences: capture the
 * display as a {@link Session}, restore one, keep the display when Weasis closes ({@code
 * weasis.hp.keepOnClose}) and offer or restore the session of a study opened again ({@code
 * weasis.hp.restore}).
 */
public final class SessionActions {

  private static final Logger LOGGER = LoggerFactory.getLogger(SessionActions.class);
  private static final DateTimeFormatter WHEN =
      DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT);

  private static volatile Supplier<SessionStore> store = SessionStore::new;

  private SessionActions() {}

  /** Replaces the store, for tests. */
  static void setStore(Supplier<SessionStore> supplier) {
    store = Objects.requireNonNull(supplier);
  }

  static SessionStore store() {
    return store.get();
  }

  /**
   * The display as a session: the workspace and the shell, the studies shown by archive, the active
   * hanging of the selected viewer. On the event dispatch thread.
   *
   * @throws IllegalStateException when no loaded study is displayed
   */
  public static Session capture(
      DicomModel model, WorkspaceManager manager, String label, boolean pin) {
    WorkspaceSnapshot snapshot = manager.capture();
    Shell shell = Shell.current();
    if (shell != null) {
      snapshot = snapshot.withShell(ShellState.capture(shell));
    }
    List<Session.Source> sources = StateCommands.sourcesOf(model, snapshot);
    if (sources.isEmpty()) {
      throw new IllegalStateException("No loaded study is displayed");
    }
    String protocol = null;
    int step = 0;
    Map<Integer, String> bindings = new LinkedHashMap<>();
    Optional<HangingPlanApplier.Applied> active =
        GuiUtils.getUICore().getSelectedPlugin().flatMap(ActiveHanging::of);
    if (active.isPresent()) {
      protocol = active.get().plan().protocolId();
      step = active.get().step() + 1;
      for (CellPlan cell : active.get().stepPlan().cells()) {
        if (cell.series() != null) {
          bindings.put(cell.displaySet(), cell.series());
        }
      }
    }
    Instant now = Instant.now();
    return new Session(
        now.toString(),
        label,
        pin,
        now,
        now,
        sources,
        protocol,
        step,
        bindings,
        snapshot,
        derivedOf(model, snapshot));
  }

  /** The specs of the series of the snapshot that a plugin derived, so they can be built again. */
  static List<Session.Derived> derivedOf(DicomModel model, WorkspaceSnapshot snapshot) {
    List<Session.Derived> derived = new ArrayList<>();
    List<DerivedSeriesProvider> providers = providers();
    for (String id : StateCommands.seriesOf(snapshot)) {
      MediaSeries<? extends MediaElement> series = model.findSeries(id).orElse(null);
      if (series == null) {
        continue;
      }
      for (DerivedSeriesProvider provider : providers) {
        provider
            .describe(series)
            .ifPresent(spec -> derived.add(new Session.Derived(provider.kind(), spec)));
      }
    }
    return derived;
  }

  /**
   * Builds the derived series of a session again, from their specs, so the restore finds them; one
   * that cannot be built is logged and skipped. Off the event dispatch thread.
   */
  public static void rebuildDerived(Session session) {
    if (session.derived().isEmpty()) {
      return;
    }
    List<DerivedSeriesProvider> providers = providers();
    for (Session.Derived derived : session.derived()) {
      providers.stream()
          .filter(p -> p.kind().equals(derived.kind()))
          .findFirst()
          .ifPresentOrElse(
              provider -> {
                try {
                  provider.rebuild(derived.spec());
                } catch (IOException | RuntimeException e) {
                  LOGGER.warn("Cannot derive a {} series of the session", derived.kind(), e);
                }
              },
              () -> LOGGER.warn("No provider derives {} series", derived.kind()));
    }
  }

  private static List<DerivedSeriesProvider> providers() {
    return GuiUtils.getUICore().getPluginManager().getExtensions(DerivedSeriesProvider.class);
  }

  /**
   * Rebuilds the workspace of a session, closes the viewers it does not reuse, applies its shell
   * and gives its 2D viewer its hanging back. On the event dispatch thread.
   *
   * @return the view ids shown after the restore
   * @throws IllegalStateException when a study of the session is not loaded
   */
  public static List<String> restore(Session session, DicomModel model, WorkspaceManager manager) {
    List<String> missing =
        session.studies().stream().filter(uid -> model.getStudyNode(uid) == null).toList();
    if (!missing.isEmpty()) {
      throw new IllegalStateException(
          "Not loaded, load them first: " + String.join(", ", missing)); // NON-NLS
    }
    List<WorkspaceView> pool = manager.getViews();
    manager.restore(session.workspace(), new ViewerRestorer(pool));
    for (WorkspaceView leftover : pool) {
      if (!manager.isShowing(leftover) && leftover instanceof ViewerPlugin<?> plugin) {
        plugin.close();
        plugin.handleFocusAfterClosing();
      }
    }
    Shell shell = Shell.current();
    if (shell != null && session.workspace().shell() != null) {
      ShellState.apply(session.workspace().shell(), shell);
    }
    StateCommands.reattach(session, model, manager);
    return manager.getViews().stream().map(WorkspaceView::viewId).toList();
  }

  /** Restores a session by id from any thread; false when it is unknown or cannot be restored. */
  public static boolean restore(String sessionId) {
    Optional<Session> session = store().find(sessionId);
    DicomModel model = HpCommands.findModel();
    WorkspaceManager manager = GuiUtils.getUICore().getWorkspaceManager();
    if (session.isEmpty() || model == null || manager == null) {
      return false;
    }
    try {
      rebuildDerived(session.get());
      GuiExecutor.invokeAndRethrow(() -> restore(session.get(), model, manager));
      store().touch(session.get());
      return true;
    } catch (RuntimeException | IOException e) {
      LOGGER.warn("Cannot restore the session {}", sessionId, e);
      return false;
    }
  }

  /** The latest session showing a study, when the setting lets it be offered or restored. */
  public static Optional<Session> offerFor(MediaSeriesGroup study) {
    String uid = study == null ? null : TagD.getTagValue(study, Tag.StudyInstanceUID, String.class);
    if (uid == null || Settings.get(HpSettings.RESTORE) == HpSettings.Restore.NEVER) {
      return Optional.empty();
    }
    return store().latestOf(uid);
  }

  /**
   * Restores the latest session of a study about to be hung when the setting says so and every
   * study of the session is loaded; what the opener tries before planning a protocol.
   *
   * @return true when the session was restored, so no protocol is hung
   */
  public static boolean restoreAutomatically(DicomModel model, MediaSeriesGroup study) {
    if (Settings.get(HpSettings.RESTORE) != HpSettings.Restore.AUTO) {
      return false;
    }
    Session session = offerFor(study).orElse(null);
    if (session == null
        || session.studies().stream().anyMatch(uid -> model.getStudyNode(uid) == null)) {
      return false;
    }
    return restore(session.id());
  }

  /** Keeps the display as a session when the setting asks for it; nothing stops the close. */
  public static void keepOnClose() {
    if (!Settings.get(HpSettings.KEEP_ON_CLOSE)) {
      return;
    }
    DicomModel model = HpCommands.findModel();
    WorkspaceManager manager = GuiUtils.getUICore().getWorkspaceManager();
    if (model == null || manager == null) {
      return;
    }
    try {
      Session session = GuiExecutor.invokeAndRethrow(() -> capture(model, manager, null, false));
      if (Session.forbiddenKeys(session.toJson()).isEmpty()) {
        store().keep(session);
        LOGGER.info("Display kept as session {} on close", session.id());
      }
    } catch (IllegalStateException e) {
      LOGGER.debug("Nothing to keep on close: {}", e.getMessage());
    } catch (RuntimeException | IOException e) {
      LOGGER.warn("Cannot keep the display on close", e);
    }
  }

  /** The session as a short line: its label, else when it was kept. */
  public static String label(Session session) {
    if (session.label() != null && !session.label().isBlank()) {
      return session.label();
    }
    return WHEN.format(session.updated().atZone(ZoneId.systemDefault()));
  }
}
