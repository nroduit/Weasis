/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.Feature;
import org.weasis.core.api.setting.Scope;
import org.weasis.core.api.util.DocumentStore;
import org.weasis.core.api.util.LayeredDocument;
import org.weasis.core.api.util.LayeredDocument.Entry;
import org.weasis.core.api.util.ListDocument;
import org.weasis.core.api.util.SiteDocuments;
import org.weasis.core.ui.docking.Shell;
import org.weasis.core.ui.docking.ShellState;
import org.weasis.core.ui.docking.ShellState.ShellSide;
import org.weasis.core.ui.docking.SidePanel;
import org.weasis.core.ui.docking.SidePanel.Role;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MouseActionType;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.editor.image.MouseButton;
import org.weasis.core.ui.editor.image.ViewerToolBar;
import org.weasis.core.ui.model.graphic.profile.MeasurementProfileRegistry;

/**
 * The UI profiles: a layered document (built-in, site, user) and the one laid over the workspace
 * for the selected view. {@link Relevance#apply} activates the profile the view context asks for
 * through {@link ContextFacts#UI_PROFILE}: its shell template fills the slots, its toolbars and
 * menus narrow the rows, its mouse bindings and measurement profile replace the user's until it is
 * released, when the user's own come back. A change the user makes in a slot or in the toolbar row
 * while a profile is active is written to the user layer of that profile.
 */
public final class UiProfiles {

  private static final Logger LOGGER = LoggerFactory.getLogger(UiProfiles.class);

  public static final String DOCUMENT = "uiProfiles.json"; // NON-NLS
  public static final String ENTRIES = "profiles"; // NON-NLS
  public static final String MENU_PREFIX = "weasis.contextmenu."; // NON-NLS

  private static final LayeredDocument PROFILES = new LayeredDocument(DOCUMENT, ENTRIES);
  private static volatile Supplier<Shell> shell = Shell::current;
  private static volatile Supplier<MeasurementProfileRegistry> measurement =
      MeasurementProfileRegistry::getInstance;

  private static UiProfile active;

  /** The bindings each event manager had before a profile changed them, by button key. */
  private static final Map<ImageViewerEventManager<?>, Map<String, String>> USER_MOUSE =
      new LinkedHashMap<>();

  private UiProfiles() {}

  /** Reads the site and user documents; called once the document store is configured. */
  public static void configure() {
    PROFILES.configure(SiteDocuments.read(DOCUMENT), DocumentStore.locate(Scope.USER, DOCUMENT));
    SiteDocuments.onReload(DOCUMENT, () -> PROFILES.reload(SiteDocuments.read(DOCUMENT)));
    DocumentStore.registerIds(ListDocument.documentId(Path.of(DOCUMENT)), PROFILES::ids);
  }

  public static LayeredDocument document() {
    return PROFILES;
  }

  /** Replaces the shell lookup, for tests. */
  public static void setShell(Supplier<Shell> supplier) {
    shell = Objects.requireNonNull(supplier);
  }

  /** Replaces the measurement profile registry lookup, for tests. */
  public static void setMeasurementRegistry(Supplier<MeasurementProfileRegistry> supplier) {
    measurement = Objects.requireNonNull(supplier);
  }

  // ---- entries

  /** The profiles in force, every layer merged. */
  public static List<UiProfile> profiles() {
    List<UiProfile> list = new ArrayList<>();
    for (Entry entry : PROFILES.entries()) {
      read(entry).ifPresent(list::add);
    }
    return list;
  }

  public static Optional<UiProfile> find(String id) {
    return id == null ? Optional.empty() : PROFILES.find(id).flatMap(UiProfiles::read);
  }

  private static Optional<UiProfile> read(Entry entry) {
    try {
      return Optional.of(UiProfile.fromJson(entry.json()));
    } catch (RuntimeException e) {
      LOGGER.warn("UI profile {} of the {} layer is invalid: {}", entry.id(), entry.origin(), e);
      return Optional.empty();
    }
  }

  // ---- the active profile

  public static synchronized Optional<UiProfile> active() {
    return Optional.ofNullable(active);
  }

  public static String activeId() {
    return active().map(UiProfile::id).orElse(null);
  }

  /**
   * Activates the profile the context asks for, or releases the active one when the context names
   * none or the filtering is off ({@link Relevance#CONTEXTUAL}). Runs on the event dispatch thread.
   *
   * @return the profile in force afterwards, null when none
   */
  public static UiProfile apply(ViewContext context) {
    String id = Relevance.contextual() ? ContextFacts.text(context, ContextFacts.UI_PROFILE) : null;
    UiProfile profile = find(id).orElse(null);
    if (profile == null) {
      if (id != null) {
        LOGGER.debug("No UI profile {}", id);
      }
      release();
      return null;
    }
    activate(profile, context == null ? null : context.viewer());
    return profile;
  }

  /**
   * Lays a profile over the workspace of a viewer: slots, mouse bindings of its event manager and
   * measurement profile; the toolbars and menus follow through {@link #active()}.
   */
  public static void activate(UiProfile profile, SeriesViewer<?> viewer) {
    Objects.requireNonNull(profile);
    boolean changed;
    synchronized (UiProfiles.class) {
      changed = !profile.equals(active);
      active = profile;
    }
    Shell current = currentShell();
    if (current != null) {
      current.explorer().onUserChange(UiProfiles::userChangedSlots);
      current.tools().onUserChange(UiProfiles::userChangedSlots);
      if (changed && profile.shell() != null) {
        applyShell(profile.shell(), current);
      }
    }
    if (viewer instanceof ImageViewerPlugin<?> plugin) {
      applyMouse(profile, plugin);
    }
    if (changed) {
      measurement.get().override(profile.measurementProfile());
    }
  }

  /** Puts the user's own bindings and measurement profile back; nothing when none is active. */
  public static void release() {
    synchronized (UiProfiles.class) {
      if (active == null) {
        return;
      }
      active = null;
    }
    restoreMouse();
    measurement.get().override(null);
  }

  /** Whether the active profile shows a context-menu entry, by its site switch key. */
  public static boolean showsMenu(String property) {
    UiProfile profile = active().orElse(null);
    if (profile == null || property == null) {
      return true;
    }
    String entry =
        property.startsWith(MENU_PREFIX) ? property.substring(MENU_PREFIX.length()) : property;
    return profile.showsMenu(entry);
  }

  private static Shell currentShell() {
    try {
      return shell.get();
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static void applyShell(ShellState template, Shell current) {
    for (Role role : Role.values()) {
      ShellSide side = template.side(role);
      if (side != null) {
        List<String> skipped = current.side(role).applyState(side);
        if (!skipped.isEmpty()) {
          LOGGER.debug("Tools {} are not offered on the {} side", skipped, role);
        }
      }
    }
  }

  // ---- mouse bindings

  private static void applyMouse(UiProfile profile, ImageViewerPlugin<?> plugin) {
    if (profile.mouse() == null || profile.mouse().isEmpty()) {
      return;
    }
    ImageViewerEventManager<?> manager = plugin.getEventManager();
    if (manager == null) {
      return;
    }
    MouseActions actions = manager.getMouseActions();
    Map<String, String> remembered =
        USER_MOUSE.computeIfAbsent(manager, m -> new LinkedHashMap<>());
    boolean changed = false;
    for (Map.Entry<MouseButton, MouseActionType> binding : profile.mouse().entrySet()) {
      String key = binding.getKey().key();
      String command = binding.getValue().command();
      String before = actions.getAction(key);
      if (!command.equals(before)) {
        remembered.putIfAbsent(key, before);
        actions.setAction(key, command);
        changed = true;
      }
    }
    if (changed) {
      push(plugin, actions);
    }
  }

  private static void restoreMouse() {
    for (Map.Entry<ImageViewerEventManager<?>, Map<String, String>> e : USER_MOUSE.entrySet()) {
      ImageViewerEventManager<?> manager = e.getKey();
      MouseActions actions = manager.getMouseActions();
      e.getValue().forEach(actions::setAction);
      ImageViewerPlugin<?> plugin = manager.getSelectedView2dContainer();
      if (plugin != null) {
        push(plugin, actions);
      }
    }
    USER_MOUSE.clear();
  }

  /** Gives the bindings to the viewer and shows them on its toolbar buttons. */
  private static void push(ImageViewerPlugin<?> plugin, MouseActions actions) {
    plugin.setMouseActions(actions);
    ViewerToolBar<?> toolBar = plugin.getViewerToolBar();
    if (toolBar == null) {
      return;
    }
    for (MouseButton button : MouseButton.values()) {
      if (button != MouseButton.WHEEL) {
        String command = actions.getAction(button.key());
        Feature<?> feature = toolBar.getToolBarAction(command);
        toolBar.changeButtonState(
            button.key(), feature == null ? ActionW.NO_ACTION.cmd() : command);
      }
    }
  }

  // ---- the user layer

  /** The user shaped a side while a profile is active: the slots go to its user layer. */
  static void userChangedSlots(SidePanel side) {
    UiProfile profile = active().orElse(null);
    if (profile == null) {
      return;
    }
    ShellState shell = profile.shell();
    ShellSide changed = new ShellSide(side.captureState().slots(), 0, 0, false);
    ShellState edited =
        new ShellState(
            side.getRole() == Role.EXPLORER ? changed : shell == null ? null : shell.explorer(),
            side.getRole() == Role.TOOLS ? changed : shell == null ? null : shell.tools(),
            shell == null ? null : shell.strip());
    store(profile.withShell(edited));
  }

  /**
   * The user showed or hid a toolbar while a profile lists them: the list of its user layer
   * changes; a profile that lists nothing is left alone.
   *
   * @return whether the profile took the change
   */
  public static boolean userToggledToolbar(String toolbarId, boolean shown) {
    UiProfile profile = active().orElse(null);
    if (profile == null || profile.toolbars() == null) {
      return false;
    }
    List<String> ids = new ArrayList<>(profile.toolbars());
    if (shown && !ids.contains(toolbarId)) {
      ids.add(toolbarId);
    } else if (!shown) {
      ids.remove(toolbarId);
    } else {
      return true;
    }
    store(profile.withToolbars(ids));
    return true;
  }

  private static void store(UiProfile edited) {
    synchronized (UiProfiles.class) {
      active = edited;
    }
    try {
      PROFILES.put(edited.toJson());
    } catch (IOException | RuntimeException e) {
      LOGGER.warn("The change of the UI profile {} is not kept: {}", edited.id(), e.getMessage());
    }
  }
}
