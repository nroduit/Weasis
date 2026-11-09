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

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import java.awt.Dialog;
import java.awt.Window;
import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.command.CommandOrigin;
import org.weasis.core.api.command.StateBoundary;
import org.weasis.core.api.event.Event;
import org.weasis.core.api.event.EventBus;
import org.weasis.core.api.event.EventType;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.setting.Settings;
import org.weasis.core.api.setting.SettingsPackage;
import org.weasis.core.api.util.DocumentVersions;
import org.weasis.core.api.util.SiteDocuments;
import org.weasis.core.ui.docking.Shell;
import org.weasis.core.ui.docking.ShellState;
import org.weasis.core.ui.docking.ws.WorkspaceManager;
import org.weasis.core.ui.docking.ws.WorkspaceView;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot.ViewDescriptor;
import org.weasis.core.ui.editor.SeriesResolver;
import org.weasis.core.ui.editor.ViewerRestorer;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.event.UiActivityBridge;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.serialize.XmlSerializer;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.hp.HangingPlan.CellPlan;
import org.weasis.dicom.explorer.DicomModel;

/**
 * The checkpoints of this instance: a bounded stack of {@link Checkpoint}s taken at the boundaries
 * of the display's transactions (a script, a remote batch, a hanging, a restore), the diff between
 * two of them, and the rollback that puts the display back. In memory and instance-local; the
 * persistent form a user sees is the session of {@code state:keep}.
 *
 * <p>A rollback runs in phases under the restoring guard of the workspace manager: the derived
 * series are built again off the event thread, then on it the layout and the views (one task, the
 * existing restorer), the graphics of each displayed image, the selection, and the hanging is
 * reattached. Studies closed since the checkpoint are reported, not reloaded; studies loaded since
 * are closed only on request.
 */
public final class CheckpointService implements StateBoundary {

  private static final Logger LOGGER = LoggerFactory.getLogger(CheckpointService.class);

  public static final String SINCE = "5.0"; // NON-NLS
  public static final String CHECKPOINT_TAKEN = "state:checkpoint.taken"; // NON-NLS
  public static final String ROLLBACK_DONE = "state:rollback.done"; // NON-NLS
  private static final String CHECKPOINT = "checkpoint"; // NON-NLS

  /** The payload key of the boundary's sequence number ("seq" is reserved for the event's own). */
  private static final String CHECKPOINT_SEQ = "checkpointSeq"; // NON-NLS

  private static final CheckpointService GLOBAL = new CheckpointService();

  /** How long a checkpoint or a rollback waits for a held mouse button before refusing. */
  static final long MOUSE_DEFERRAL_MS = 2_000;

  private final List<Consumer<Checkpoint>> remoteBoundaryListeners = new CopyOnWriteArrayList<>();

  /** The checkpoints of this instance. */
  public static CheckpointService global() {
    return GLOBAL;
  }

  private final Deque<Checkpoint> stack = new ArrayDeque<>();
  private IntSupplier limit = () -> Settings.get(HpSettings.CHECKPOINTS_MAX);
  private Supplier<EventBus> bus = EventBus::global;
  private Supplier<DicomModel> model = HpCommands::findModel;
  private Supplier<WorkspaceManager> manager = () -> GuiUtils.getUICore().getWorkspaceManager();

  public CheckpointService() {}

  /** A service with its own bound and bus, for tests. */
  CheckpointService(IntSupplier limit, Supplier<EventBus> bus) {
    this.limit = Objects.requireNonNull(limit);
    this.bus = Objects.requireNonNull(bus);
  }

  /** Replaces the lookups, for headless tests. */
  CheckpointService with(Supplier<DicomModel> model, Supplier<WorkspaceManager> manager) {
    this.model = Objects.requireNonNull(model);
    this.manager = Objects.requireNonNull(manager);
    return this;
  }

  // ---------------------------------------------------------------------------- the stack

  /** The checkpoints, newest first. */
  public synchronized List<Checkpoint> list() {
    return List.copyOf(stack);
  }

  public synchronized Optional<Checkpoint> find(String id) {
    return stack.stream().filter(c -> c.id().equals(id)).findFirst();
  }

  public synchronized Optional<Checkpoint> latest() {
    return Optional.ofNullable(stack.peekFirst());
  }

  /**
   * Keeps a checkpoint: a display already on top of the stack is kept once, under the new label.
   */
  synchronized Checkpoint push(Checkpoint checkpoint) {
    // an automatic boundary on the display already on top replaces it, so a burst of remote calls
    // does not fill the stack with copies; a checkpoint asked for by name is always kept
    Checkpoint top = stack.peekFirst();
    if (top != null && top.automatic() && checkpoint.automatic() && top.sameDisplayAs(checkpoint)) {
      DocumentVersions.close(stack.pollFirst().id());
    }
    stack.addFirst(checkpoint);
    DocumentVersions.open(checkpoint.id());
    int max = Math.max(1, limit.getAsInt());
    while (stack.size() > max) {
      DocumentVersions.close(stack.pollLast().id());
    }
    return checkpoint;
  }

  synchronized void clear() {
    stack.forEach(c -> DocumentVersions.close(c.id()));
    stack.clear();
  }

  /**
   * Called after a checkpoint the application took at the boundary of a remote batch, so the
   * explorer can tell the user that an agent changed the display and offer to restore it.
   */
  public void onRemoteBoundary(Consumer<Checkpoint> listener) {
    remoteBoundaryListeners.add(Objects.requireNonNull(listener));
  }

  // ---------------------------------------------------------------------------- capture

  /**
   * Takes a checkpoint of the display now, from any thread.
   *
   * @throws IllegalStateException without a workspace, or while a modal dialog is open
   */
  public Checkpoint take(String label, CommandOrigin origin) {
    return take(label, origin, false);
  }

  /**
   * Takes a checkpoint of the display now, from any thread.
   *
   * @param automatic whether the application takes it at a boundary of its own
   * @throws IllegalStateException without a workspace, or while a modal dialog is open
   */
  public Checkpoint take(String label, CommandOrigin origin, boolean automatic) {
    WorkspaceManager mgr = manager.get();
    if (mgr == null) {
      throw new IllegalStateException("No workspace available (headless)");
    }
    DicomModel dicomModel = model.get();
    awaitMouseReleased("A checkpoint");
    Checkpoint checkpoint =
        GuiExecutor.invokeAndRethrow(
            () -> {
              refuseWhileModal("A checkpoint");
              return capture(label, origin, dicomModel, mgr, bus.get().lastSequence(), automatic);
            });
    push(checkpoint);
    bus.get()
        .publish(
            Event.of(CHECKPOINT_TAKEN)
                .origin(origin)
                .put(CHECKPOINT, checkpoint.id())
                .put(CHECKPOINT_SEQ, checkpoint.seq())
                .put("label", label == null ? "" : label)); // NON-NLS
    if (automatic && origin == CommandOrigin.REMOTE) {
      remoteBoundaryListeners.forEach(listener -> listener.accept(checkpoint));
    }
    return checkpoint;
  }

  /**
   * A mouse drag on a view keeps state in its mouse handler (a drawing, a window change, a pan):
   * capturing or replacing the display under it would leave the handler on a dead view, so the work
   * waits for the release, up to {@link #MOUSE_DEFERRAL_MS}, then refuses. On the event thread
   * itself the release cannot arrive, so the check is immediate.
   */
  static void awaitMouseReleased(String what) {
    if (!UiActivityBridge.isMouseButtonHeld()) {
      return;
    }
    if (!javax.swing.SwingUtilities.isEventDispatchThread()) {
      long deadline = System.nanoTime() + MOUSE_DEFERRAL_MS * 1_000_000;
      while (UiActivityBridge.isMouseButtonHeld() && System.nanoTime() < deadline) {
        try {
          Thread.sleep(50);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
      if (!UiActivityBridge.isMouseButtonHeld()) {
        return;
      }
    }
    throw new IllegalStateException(
        what + " cannot run while a mouse button is held (waited " + MOUSE_DEFERRAL_MS + " ms)");
  }

  /** The display as a checkpoint; on the event dispatch thread. */
  static Checkpoint capture(
      String label,
      CommandOrigin origin,
      DicomModel model,
      WorkspaceManager manager,
      long seq,
      boolean automatic) {
    WorkspaceSnapshot snapshot = manager.capture();
    Shell shell = Shell.current();
    if (shell != null) {
      snapshot = snapshot.withShell(ShellState.capture(shell));
    }
    String selected = manager.selectedView().map(WorkspaceView::viewId).orElse(null);
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
    return new Checkpoint(
        now.toString(),
        seq,
        now,
        label,
        origin,
        snapshot,
        graphicsOf(manager),
        studiesOf(model),
        selected,
        protocol,
        step,
        bindings,
        model == null ? List.of() : SessionActions.derivedOf(model, snapshot),
        automatic);
  }

  /** The graphic model of every image a pane shows, as the XML the model persists. */
  static Map<String, String> graphicsOf(WorkspaceManager manager) {
    Map<String, String> graphics = new LinkedHashMap<>();
    for (ImageElement image : displayedImages(manager).values()) {
      if (image.getTagValue(TagW.PresentationModel) instanceof GraphicModel model
          && model.hasSerializableGraphics()) {
        try {
          graphics.put(keyOf(image), XmlSerializer.toXml(model));
        } catch (Exception e) {
          LOGGER.warn("Cannot keep the graphics of {}", image, e);
        }
      }
    }
    return graphics;
  }

  /** The images the panes of the 2D viewers show, by key. */
  static Map<String, ImageElement> displayedImages(WorkspaceManager manager) {
    Map<String, ImageElement> images = new LinkedHashMap<>();
    for (WorkspaceView view : manager.getViews()) {
      if (view instanceof ImageViewerPlugin<?> plugin) {
        for (ViewCanvas<?> pane : plugin.getImagePanels()) {
          if (pane instanceof DefaultView2d<?> view2d && view2d.getImage() != null) {
            ImageElement image = view2d.getImage();
            images.putIfAbsent(keyOf(image), image);
          }
        }
      }
    }
    return images;
  }

  static String keyOf(ImageElement image) {
    String sop = TagD.getTagValue(image, Tag.SOPInstanceUID, String.class);
    return Checkpoint.imageKey(
        sop == null ? String.valueOf(image.getMediaURI()) : sop, image.getKey());
  }

  static Set<String> studiesOf(DicomModel model) {
    Set<String> uids = new LinkedHashSet<>();
    if (model == null) {
      return uids;
    }
    for (MediaSeriesGroup patient : model.getChildren(MediaSeriesGroupNode.rootNode)) {
      for (MediaSeriesGroup study : model.getChildren(patient)) {
        String uid = TagD.getTagValue(study, Tag.StudyInstanceUID, String.class);
        if (uid != null) {
          uids.add(uid);
        }
      }
    }
    return uids;
  }

  // ---------------------------------------------------------------------------- diff

  /** What changed between two checkpoints, or between a checkpoint and the display now. */
  public record Diff(
      List<String> viewsAdded,
      List<String> viewsRemoved,
      List<String> viewsChanged,
      List<String> graphicsChanged,
      List<String> studiesLoaded,
      List<String> studiesClosed,
      boolean selectionChanged,
      boolean shellChanged,
      List<String> documentsChanged) {

    /** A diff of the display alone, no document written. */
    public Diff(
        List<String> viewsAdded,
        List<String> viewsRemoved,
        List<String> viewsChanged,
        List<String> graphicsChanged,
        List<String> studiesLoaded,
        List<String> studiesClosed,
        boolean selectionChanged,
        boolean shellChanged) {
      this(
          viewsAdded,
          viewsRemoved,
          viewsChanged,
          graphicsChanged,
          studiesLoaded,
          studiesClosed,
          selectionChanged,
          shellChanged,
          List.of());
    }

    public boolean isEmpty() {
      return viewsAdded.isEmpty()
          && viewsRemoved.isEmpty()
          && viewsChanged.isEmpty()
          && graphicsChanged.isEmpty()
          && studiesLoaded.isEmpty()
          && studiesClosed.isEmpty()
          && !selectionChanged
          && !shellChanged
          && documentsChanged.isEmpty();
    }

    public JsonObjectBuilder toJson() {
      return Json.createObjectBuilder()
          .add("empty", isEmpty()) // NON-NLS
          .add("viewsAdded", array(viewsAdded)) // NON-NLS
          .add("viewsRemoved", array(viewsRemoved)) // NON-NLS
          .add("viewsChanged", array(viewsChanged)) // NON-NLS
          .add("graphicsChanged", array(graphicsChanged)) // NON-NLS
          .add("studiesLoaded", array(studiesLoaded)) // NON-NLS
          .add("studiesClosed", array(studiesClosed)) // NON-NLS
          .add("selectionChanged", selectionChanged) // NON-NLS
          .add("shellChanged", shellChanged) // NON-NLS
          .add("documentsChanged", array(documentsChanged)); // NON-NLS
    }
  }

  public static Diff diff(Checkpoint from, Checkpoint to) {
    Map<String, ViewDescriptor> before = from.views();
    Map<String, ViewDescriptor> after = to.views();
    List<String> added = new ArrayList<>();
    List<String> removed = new ArrayList<>();
    List<String> changed = new ArrayList<>();
    after.forEach(
        (id, view) -> {
          ViewDescriptor old = before.get(id);
          if (old == null) {
            added.add(id);
          } else if (!Objects.equals(old.state(), view.state())
              || !Objects.equals(old.typeId(), view.typeId())) {
            changed.add(id);
          }
        });
    before.keySet().stream().filter(id -> !after.containsKey(id)).forEach(removed::add);
    List<String> graphics = new ArrayList<>();
    Set<String> keys = new LinkedHashSet<>(from.graphics().keySet());
    keys.addAll(to.graphics().keySet());
    for (String key : keys) {
      if (!Objects.equals(from.graphics().get(key), to.graphics().get(key))) {
        graphics.add(key);
      }
    }
    List<String> loaded =
        to.studies().stream().filter(uid -> !from.studies().contains(uid)).toList();
    List<String> closed =
        from.studies().stream().filter(uid -> !to.studies().contains(uid)).toList();
    boolean shell =
        !Objects.equals(from.workspace().shell(), to.workspace().shell())
            || from.workspace().windows().size() != to.workspace().windows().size();
    return new Diff(
        added,
        removed,
        changed,
        graphics,
        loaded,
        closed,
        !Objects.equals(from.selectedView(), to.selectedView()),
        shell,
        DocumentVersions.documentsBefore(from.id(), to.at()));
  }

  /** The diff between a checkpoint and the display now. */
  public Diff diffFromNow(Checkpoint from) {
    WorkspaceManager mgr = manager.get();
    if (mgr == null) {
      throw new IllegalStateException("No workspace available (headless)");
    }
    DicomModel dicomModel = model.get();
    Checkpoint now =
        GuiExecutor.invokeAndRethrow(
            () -> capture(null, null, dicomModel, mgr, bus.get().lastSequence(), true));
    return diff(from, now);
  }

  // ---------------------------------------------------------------------------- rollback

  /** What a rollback did: the views shown after it, what stayed as it was, the studies closed. */
  public record Rollback(
      Checkpoint checkpoint,
      List<String> views,
      List<String> notRestored,
      List<String> closedStudies,
      List<String> documents) {

    public JsonObjectBuilder toJson() {
      return checkpoint
          .summary()
          .add("restored", true) // NON-NLS
          .add("views", array(views)) // NON-NLS
          .add("notRestored", array(notRestored)) // NON-NLS
          .add("closedStudies", array(closedStudies)) // NON-NLS
          .add("documents", array(documents)) // NON-NLS
          .add("notCaptured", array(Checkpoint.NOT_CAPTURED)); // NON-NLS
    }
  }

  /**
   * Puts the display back to a checkpoint, from any thread.
   *
   * @param closeNew whether the studies loaded since the checkpoint are closed
   * @throws IllegalStateException without a workspace, or while a modal dialog is open
   */
  public Rollback rollback(Checkpoint checkpoint, boolean closeNew) {
    WorkspaceManager mgr = manager.get();
    if (mgr == null) {
      throw new IllegalStateException("No workspace available (headless)");
    }
    DicomModel dicomModel = model.get();
    awaitMouseReleased("A rollback");
    List<String> notRestored = new ArrayList<>();
    // (a) data, off the event thread: the documents written since go back to their version, the
    // derived series the checkpoint needs are built again
    List<String> documents = restoreDocuments(checkpoint, notRestored);
    if (dicomModel != null && !checkpoint.derived().isEmpty()) {
      try {
        SessionActions.rebuildDerived(checkpoint.toSession(null, false, List.of()));
      } catch (RuntimeException e) {
        notRestored.add("derived series: " + e.getMessage());
      }
    }
    Set<String> loadedNow = studiesOf(dicomModel);
    checkpoint.studies().stream()
        .filter(uid -> !loadedNow.contains(uid))
        .forEach(uid -> notRestored.add("study " + uid + " was closed and is not reloaded"));
    List<String> closed = new ArrayList<>();
    List<String> views =
        GuiExecutor.invokeAndRethrow(
            () -> {
              refuseWhileModal("A rollback");
              mgr.beginRestore();
              try {
                // (b) + (c) the layout and the views, in one task, by the restorer of the sessions
                List<WorkspaceView> pool = mgr.getViews();
                Set<String> reused =
                    pool.stream()
                        .map(WorkspaceView::viewId)
                        .collect(java.util.stream.Collectors.toSet());
                mgr.restore(checkpoint.workspace(), new ViewerRestorer(pool));
                // a view kept open is reused as it is: its state comes from the checkpoint
                SeriesResolver resolver = SeriesResolver.ofApplication();
                checkpoint
                    .views()
                    .forEach(
                        (id, descriptor) -> {
                          if (reused.contains(id) && descriptor.state() != null) {
                            mgr.findView(id)
                                .filter(ViewerPlugin.class::isInstance)
                                .map(ViewerPlugin.class::cast)
                                .ifPresent(
                                    plugin -> plugin.restoreState(descriptor.state(), resolver));
                          }
                        });
                for (WorkspaceView leftover : pool) {
                  if (!mgr.isShowing(leftover) && leftover instanceof ViewerPlugin<?> plugin) {
                    plugin.close();
                    plugin.handleFocusAfterClosing();
                  }
                }
                Shell shell = Shell.current();
                if (shell != null && checkpoint.workspace().shell() != null) {
                  ShellState.apply(checkpoint.workspace().shell(), shell);
                }
                // (d) the graphics of every image the checkpoint knows
                notRestored.addAll(applyGraphics(checkpoint, dicomModel, mgr));
                // (e) the selection, through the path the toolbars and menus follow
                if (checkpoint.selectedView() != null) {
                  mgr.findView(checkpoint.selectedView()).ifPresent(mgr::select);
                }
                if (dicomModel != null && checkpoint.protocol() != null) {
                  StateCommands.reattach(
                      checkpoint.toSession(null, false, List.of()), dicomModel, mgr);
                }
                if (closeNew && dicomModel != null) {
                  for (String uid : loadedNow) {
                    if (!checkpoint.studies().contains(uid)) {
                      MediaSeriesGroup study = dicomModel.getStudyNode(uid);
                      if (study != null) {
                        dicomModel.removeStudy(study);
                        closed.add(uid);
                      }
                    }
                  }
                }
              } finally {
                mgr.endRestore();
              }
              return mgr.getViews().stream().map(WorkspaceView::viewId).toList();
            });
    Rollback result = new Rollback(checkpoint, views, List.copyOf(notRestored), closed, documents);
    bus.get()
        .publish(
            Event.of(ROLLBACK_DONE)
                .origin(checkpoint.origin())
                .put(CHECKPOINT, checkpoint.id())
                .put(CHECKPOINT_SEQ, checkpoint.seq())
                .put("views", views.size()) // NON-NLS
                .put("notRestored", notRestored.size()) // NON-NLS
                .put("documents", documents.size())); // NON-NLS
    return result;
  }

  /**
   * The documents of the user back to their version at the checkpoint: the backup package of a
   * settings import that followed it is imported again first (it carries the settings that are not
   * documents), then every document written since gets its kept version; the registries reload.
   */
  static List<String> restoreDocuments(Checkpoint checkpoint, List<String> notRestored) {
    List<String> documents = new ArrayList<>();
    Optional<java.nio.file.Path> backup = DocumentVersions.importBackup(checkpoint.id());
    if (backup.isPresent()) {
      try {
        DocumentVersions.suspended(
            () -> {
              try {
                SettingsPackage.importPackage(
                    backup.get(),
                    SettingsPackage.Mode.REPLACE,
                    false,
                    SettingsPackage.currentEnvironment(),
                    null);
              } catch (java.io.IOException e) {
                throw new IllegalStateException(e.getMessage(), e);
              }
            });
        documents.add("settings backup " + backup.get().getFileName());
      } catch (RuntimeException e) {
        notRestored.add("settings backup " + backup.get().getFileName() + ": " + e.getMessage());
      }
    }
    DocumentVersions.Restored restored = DocumentVersions.restore(checkpoint.id());
    documents.addAll(restored.documents());
    notRestored.addAll(restored.problems());
    if (!documents.isEmpty()) {
      try {
        SiteDocuments.reloadAll();
      } catch (RuntimeException e) {
        notRestored.add("registries not reloaded: " + e.getMessage());
      }
    }
    return List.copyOf(documents);
  }

  /** Gives every image of the checkpoint its graphics back; on the event dispatch thread. */
  static List<String> applyGraphics(
      Checkpoint checkpoint, DicomModel model, WorkspaceManager manager) {
    List<String> problems = new ArrayList<>();
    if (checkpoint.graphics().isEmpty() && displayedImages(manager).isEmpty()) {
      return problems;
    }
    Map<String, ImageElement> images = imagesOf(checkpoint, model, manager);
    // the images shown now that had no graphics at the checkpoint lose the ones drawn since
    Set<String> keys = new LinkedHashSet<>(checkpoint.graphics().keySet());
    keys.addAll(images.keySet());
    for (String key : keys) {
      ImageElement image = images.get(key);
      if (image == null) {
        problems.add("graphics of " + key + ": image not loaded");
        continue;
      }
      String xml = checkpoint.graphics().get(key);
      try {
        GraphicModel restored =
            xml == null ? null : XmlSerializer.readPresentationModel(new StringReader(xml));
        if (restored == null) {
          if (image.getTagValue(TagW.PresentationModel) instanceof GraphicModel current) {
            current.setChangeFiringSuspended(true);
            current.clear();
            current.setChangeFiringSuspended(false);
            current.fireChanged();
          }
        } else {
          image.setTag(TagW.PresentationModel, restored);
        }
        refreshPanesShowing(image, manager);
      } catch (Exception e) {
        problems.add("graphics of " + key + ": " + e.getMessage());
      }
    }
    return problems;
  }

  /** The images of the checkpoint's keys: the displayed ones first, then the loaded series. */
  private static Map<String, ImageElement> imagesOf(
      Checkpoint checkpoint, DicomModel model, WorkspaceManager manager) {
    Map<String, ImageElement> images = displayedImages(manager);
    if (model == null) {
      return images;
    }
    Set<String> missing = new LinkedHashSet<>(checkpoint.graphics().keySet());
    missing.removeAll(images.keySet());
    if (missing.isEmpty()) {
      return images;
    }
    for (String seriesId : checkpoint.series()) {
      MediaSeries<? extends MediaElement> series = model.findSeries(seriesId).orElse(null);
      if (series == null) {
        continue;
      }
      for (MediaElement media : series.getMedias(null, null)) {
        if (media instanceof ImageElement image) {
          String key = keyOf(image);
          if (missing.remove(key)) {
            images.put(key, image);
          }
        }
      }
      if (missing.isEmpty()) {
        break;
      }
    }
    return images;
  }

  private static void refreshPanesShowing(ImageElement image, WorkspaceManager manager) {
    for (WorkspaceView view : manager.getViews()) {
      if (view instanceof ImageViewerPlugin<?> plugin) {
        for (ViewCanvas<?> pane : plugin.getImagePanels()) {
          if (pane instanceof DefaultView2d<?> view2d && view2d.getImage() == image) {
            refresh(view2d, image);
          }
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static <E extends ImageElement> void refresh(DefaultView2d<E> view, ImageElement image) {
    view.updateGraphicManager((E) image, true);
    view.getJComponent().repaint();
  }

  // ---------------------------------------------------------------------------- boundary

  @Override
  public Optional<String> checkpoint(String label, CommandOrigin origin) {
    try {
      return Optional.of(take(label, origin, true).id());
    } catch (RuntimeException e) {
      LOGGER.debug("No checkpoint taken ({})", label, e);
      return Optional.empty();
    }
  }

  @Override
  public Optional<Latest> newest() {
    return latest().map(c -> new Latest(c.id(), c.label() == null ? "" : c.label()));
  }

  @Override
  public List<String> rollback(String checkpointId) {
    Checkpoint checkpoint =
        find(checkpointId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown checkpoint " + checkpointId));
    return rollback(checkpoint, false).notRestored();
  }

  // ---------------------------------------------------------------------------- helpers

  /** A modal dialog owns the event thread's attention: a checkpoint or a rollback waits for it. */
  static void refuseWhileModal(String what) {
    for (Window window : Window.getWindows()) {
      if (window instanceof Dialog dialog && dialog.isShowing() && dialog.isModal()) {
        throw new IllegalStateException(
            what + " cannot run while the dialog '" + dialog.getTitle() + "' is open");
      }
    }
  }

  static JsonArrayBuilder array(List<String> values) {
    JsonArrayBuilder array = Json.createArrayBuilder();
    values.forEach(array::add);
    return array;
  }

  public static List<EventType> eventTypes() {
    return List.of(
        EventType.of(CHECKPOINT_TAKEN, SINCE, "A checkpoint of the display was taken.")
            .field(CHECKPOINT, String.class, "checkpoint id")
            .field(CHECKPOINT_SEQ, long.class, "event sequence number at the boundary")
            .field("label", String.class, "what the checkpoint precedes") // NON-NLS
            .build(),
        EventType.of(ROLLBACK_DONE, SINCE, "The display was put back to a checkpoint.")
            .field(CHECKPOINT, String.class, "checkpoint id")
            .field(CHECKPOINT_SEQ, long.class, "event sequence number of the checkpoint")
            .field("views", int.class, "views shown after the rollback") // NON-NLS
            .field("notRestored", int.class, "what could not be restored, count") // NON-NLS
            .field("documents", int.class, "documents put back to their version, count") // NON-NLS
            .build());
  }
}
