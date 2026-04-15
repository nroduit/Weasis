/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.util;

import static org.weasis.core.api.gui.util.ActionW.CINESTART;

import com.formdev.flatlaf.util.SystemInfo;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.swing.KeyStroke;
import org.osgi.service.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.Messages;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.util.EscapeChars;
import org.weasis.core.util.StringUtil;

/**
 * Manages all keyboard shortcuts in the application.
 *
 * <p>Provides a central registry for shortcut bindings, user customization, conflict detection, and
 * persistence via Preferences.
 */
public final class ShortcutManager {

  /**
   * Represents the activation context of a keyboard shortcut. Contexts form a hierarchy: a child
   * context inherits all shortcuts from its parent. Two shortcuts can only conflict when their
   * contexts overlap, i.e. one is an ancestor (or descendant) of the other in the hierarchy.
   *
   * <p>The current hierarchy is:
   *
   * <pre>
   * VIEW_CANVAS          (root – active in every viewer type)
   *   ├── DICOM_VIEWER   (active only in the DICOM 2D viewer)
   *   │     └── MPR      (active only in MPR mode within the DICOM viewer)
   *   └── VIEWER_3D      (active only in the 3D volume viewer)
   *
   * DICOM_EXPLORER       (separate root – active in the DICOM explorer thumbnail panel)
   * </pre>
   *
   * <p>Because the overlap check walks the parent chain, sibling contexts (e.g. {@code
   * DICOM_VIEWER} and {@code VIEWER_3D}) do <em>not</em> conflict with each other since they are
   * never active simultaneously. {@code DICOM_EXPLORER} is a separate root that does not overlap
   * with any viewer context, since the explorer panel and the viewer canvas cannot be focused at
   * the same time.
   */
  public enum ShortcutContext {
    /** Common viewer shortcuts – active whenever a ViewCanvas has focus (root context). */
    VIEW_CANVAS("ShortcutManager.ctx.view_canvas", null),

    /** DICOM 2D viewer shortcuts – inherits from {@link #VIEW_CANVAS}. */
    DICOM_VIEWER("ShortcutManager.ctx.dicom_viewer", VIEW_CANVAS),

    /** MPR-specific shortcuts – inherits from {@link #DICOM_VIEWER}. */
    MPR("ShortcutManager.ctx.mpr", DICOM_VIEWER),

    /**
     * 3D volume viewer shortcuts – inherits from {@link #VIEW_CANVAS}. Sibling of {@link
     * #DICOM_VIEWER}, so shortcuts here do <em>not</em> conflict with DICOM or MPR shortcuts.
     */
    VIEWER_3D("ShortcutManager.ctx.viewer_3d", VIEW_CANVAS),

    /**
     * DICOM Explorer shortcuts – active when the thumbnail panel has focus. Separate root context
     * that does not overlap with any viewer context.
     */
    DICOM_EXPLORER("ShortcutManager.ctx.dicom_explorer", null);

    private final String displayKey;
    private final ShortcutContext parent;

    ShortcutContext(String displayKey, ShortcutContext parent) {
      this.displayKey = displayKey;
      this.parent = parent;
    }

    /** Returns the human-readable name of this context, suitable for display in the preferences. */
    public String getDisplayName() {
      return Messages.getString(displayKey);
    }

    /** Returns the parent context, or {@code null} if this is the root context. */
    public ShortcutContext getParent() {
      return parent;
    }

    /**
     * Checks whether this context overlaps with another, meaning shortcuts in both contexts can be
     * active at the same time and therefore may conflict.
     *
     * <p>Two contexts overlap when one is an ancestor of the other (or they are the same). Sibling
     * contexts (e.g. {@code DICOM_VIEWER} and a hypothetical {@code VIEWER_3D}) do <em>not</em>
     * overlap because they are never active simultaneously.
     *
     * @param other the other context to test
     * @return {@code true} if both contexts can be simultaneously active
     */
    public boolean overlapsWith(ShortcutContext other) {
      if (other == null) {
        return false;
      }
      if (this == other) {
        return true;
      }
      for (ShortcutContext ancestor = this.parent; ancestor != null; ancestor = ancestor.parent) {
        if (ancestor == other) {
          return true;
        }
      }
      for (ShortcutContext ancestor = other.parent; ancestor != null; ancestor = ancestor.parent) {
        if (ancestor == this) {
          return true;
        }
      }
      return false;
    }
  }

  private static final Logger LOGGER = LoggerFactory.getLogger(ShortcutManager.class);

  public static final String PREFERENCE_NODE = "keyboard.shortcuts";
  public static final String PROPERTY_SHORTCUTS_CHANGED = "shortcuts.changed";
  public static final String KEY_CODE_SUFFIX = ".keyCode";
  public static final String MODIFIER_SUFFIX = ".modifier";

  private static final ShortcutManager INSTANCE = new ShortcutManager();

  private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
  private final Map<String, ShortcutEntry> shortcuts = new LinkedHashMap<>();

  private ShortcutManager() {}

  public static ShortcutManager getInstance() {
    return INSTANCE;
  }

  /**
   * Returns the OS-specific preference node name for storing keyboard shortcuts. Since preferences
   * may be synchronized to a central server shared across systems, this ensures that each operating
   * system (Windows, macOS, Linux) maintains its own shortcut configuration.
   *
   * <p>For example, macOS users typically use Cmd (Meta) as the primary modifier while Windows and
   * Linux users use Ctrl. System-level shortcut conflicts also differ by OS.
   *
   * @return the preference node name, e.g. "keyboard.shortcuts.macos"
   */
  public static String getPreferenceNodeName() {
    String os;
    if (SystemInfo.isMacOS) {
      os = "macos"; // NON-NLS
    } else if (SystemInfo.isWindows) {
      os = "windows"; // NON-NLS
    } else {
      os = "linux"; // NON-NLS
    }
    return PREFERENCE_NODE + "." + os;
  }

  public static final class ShortcutEntry {
    private final String id;
    private final String description;
    private final String category;
    private final ShortcutContext context;
    private final int defaultKeyCode;
    private final int defaultModifier;
    private int keyCode;
    private int modifier;

    public ShortcutEntry(
        String id,
        String description,
        String category,
        ShortcutContext context,
        int defaultKeyCode,
        int defaultModifier) {
      this.id = Objects.requireNonNull(id);
      this.description = description != null ? description : id;
      this.category = category != null ? category : "";
      this.context = context != null ? context : ShortcutContext.VIEW_CANVAS;
      this.defaultKeyCode = defaultKeyCode;
      this.defaultModifier = defaultModifier;
      this.keyCode = defaultKeyCode;
      this.modifier = defaultModifier;
    }

    public String getId() {
      return id;
    }

    public String getDescription() {
      return description;
    }

    public String getCategory() {
      return category;
    }

    /** Returns the activation context of this shortcut (e.g., ViewCanvas, DICOM Viewer, MPR). */
    public ShortcutContext getContext() {
      return context;
    }

    public int getDefaultKeyCode() {
      return defaultKeyCode;
    }

    public int getDefaultModifier() {
      return defaultModifier;
    }

    public int getKeyCode() {
      return keyCode;
    }

    public void setKeyCode(int keyCode) {
      this.keyCode = keyCode;
    }

    public int getModifier() {
      return modifier;
    }

    public void setModifier(int modifier) {
      this.modifier = modifier;
    }

    public boolean isModified() {
      return keyCode != defaultKeyCode || modifier != defaultModifier;
    }

    public void resetToDefault() {
      this.keyCode = defaultKeyCode;
      this.modifier = defaultModifier;
    }

    /**
     * Returns a human-readable representation of the current shortcut key combination.
     *
     * @return the shortcut text (e.g. "Ctrl+Z"), or an empty string if no key is assigned.
     */
    public String getShortcutText() {
      return getKeyStrokeText(keyCode, modifier);
    }

    public String getDefaultShortcutText() {
      return getKeyStrokeText(defaultKeyCode, defaultModifier);
    }

    private static String getKeyStrokeText(int kc, int mod) {
      if (kc == 0) {
        return "";
      }
      KeyStroke ks = KeyStroke.getKeyStroke(kc, mod);
      return ks == null ? "" : formatKeyStroke(ks);
    }

    @Override
    public String toString() {
      return description + " [" + getShortcutText() + "]";
    }
  }

  // -- Categories --
  public static final String CATEGORY_MOUSE_ACTIONS =
      Messages.getString("ShortcutManager.cat.mouse_actions");
  public static final String CATEGORY_VIEWER = Messages.getString("ShortcutManager.cat.viewer");
  public static final String CATEGORY_NAVIGATION =
      Messages.getString("ShortcutManager.cat.navigation");
  public static final String CATEGORY_MEASURES = Messages.getString("ShortcutManager.cat.measures");
  public static final String CATEGORY_DRAWINGS = Messages.getString("ShortcutManager.cat.drawings");
  public static final String CATEGORY_DISPLAY = Messages.getString("ShortcutManager.cat.display");
  public static final String CATEGORY_DICOM_NAV =
      Messages.getString("ShortcutManager.cat.dicom_nav");
  public static final String CATEGORY_MPR = Messages.getString("ShortcutManager.cat.mpr");
  public static final String CATEGORY_PAN = Messages.getString("ShortcutManager.cat.pan");
  public static final String CATEGORY_DOCKING = Messages.getString("ShortcutManager.cat.docking");
  public static final String CATEGORY_DICOM_EXPLORER =
      Messages.getString("ShortcutManager.cat.dicom_explorer");
  public static final String CATEGORY_OTHER = Messages.getString("ShortcutManager.cat.other");

  // -- Shortcut IDs: Viewer --
  public static final String ID_VIEWER_PRINT = "viewer.print";
  public static final String ID_VIEWER_ESCAPE = "viewer.escape";
  public static final String ID_VIEWER_ZOOM_OUT = "viewer.zoomOut";
  public static final String ID_VIEWER_ZOOM_IN = "viewer.zoomIn";
  public static final String ID_VIEWER_BEST_FIT = "viewer.bestFit";
  public static final String ID_VIEWER_ROTATE_LEFT = "viewer.rotateLeft";
  public static final String ID_VIEWER_ROTATE_RIGHT = "viewer.rotateRight";
  public static final String ID_VIEWER_FLIP_HORIZONTAL = "viewer.flipHorizontal";

  // -- Shortcut IDs: Navigation --
  public static final String ID_VIEWER_SCROLL_UP = "viewer.scrollUp";
  public static final String ID_VIEWER_SCROLL_DOWN = "viewer.scrollDown";
  public static final String ID_VIEWER_SCROLL_UP_FAST = "viewer.scrollUpFast";
  public static final String ID_VIEWER_SCROLL_DOWN_FAST = "viewer.scrollDownFast";
  public static final String ID_VIEWER_SCROLL_FIRST = "viewer.scrollFirst";
  public static final String ID_VIEWER_SCROLL_LAST = "viewer.scrollLast";

  // -- Shortcut IDs: Mouse Actions --
  public static final String ID_VIEWER_NEXT_MOUSE_ACTION = "viewer.nextMouseAction";

  // -- Shortcut IDs: View Navigation --
  public static final String ID_VIEWER_NEXT_VIEW = "viewer.nextView";
  public static final String ID_VIEWER_PREV_VIEW = "viewer.prevView";

  // -- Shortcut IDs: Display --
  public static final String ID_VIEWER_TOGGLE_INFO = "viewer.toggleInfo";
  public static final String ID_VIEWER_TOGGLE_INFO_ALT = "viewer.toggleInfoAlt";
  public static final String ID_VIEWER_FULLSCREEN = "viewer.fullscreen";

  // -- Shortcut IDs: Graphic tools --
  public static final String ID_GRAPHIC_LINE = "graphic.line";
  public static final String ID_GRAPHIC_ANGLE = "graphic.angle";
  public static final String ID_GRAPHIC_POLYGON = "graphic.polygon";
  public static final String ID_GRAPHIC_ANNOTATION = "graphic.annotation";

  // -- Shortcut IDs: Drawings --

  public static final String ID_DRAW_DELETE = "draw.delete";
  public static final String ID_DRAW_DESELECT_ALL = "draw.deselectAll";
  public static final String ID_DRAW_SELECT_ALL = "draw.selectAll";

  // -- Shortcut IDs: Pan --
  public static final String ID_PAN_LEFT = "pan.left";
  public static final String ID_PAN_RIGHT = "pan.right";
  public static final String ID_PAN_UP = "pan.up";
  public static final String ID_PAN_DOWN = "pan.down";
  public static final String ID_PAN_LEFT_FAST = "pan.leftFast";
  public static final String ID_PAN_RIGHT_FAST = "pan.rightFast";
  public static final String ID_PAN_UP_FAST = "pan.upFast";
  public static final String ID_PAN_DOWN_FAST = "pan.downFast";

  // -- Shortcut IDs: DICOM Navigation --
  public static final String ID_DICOM_PREV_SERIES = "dicom.prevSeries";
  public static final String ID_DICOM_NEXT_SERIES = "dicom.nextSeries";
  public static final String ID_DICOM_PREV_STUDY = "dicom.prevStudy";
  public static final String ID_DICOM_NEXT_STUDY = "dicom.nextStudy";
  public static final String ID_DICOM_PREV_PATIENT = "dicom.prevPatient";
  public static final String ID_DICOM_NEXT_PATIENT = "dicom.nextPatient";
  public static final String ID_DICOM_FIRST_SERIES = "dicom.firstSeries";
  public static final String ID_DICOM_LAST_SERIES = "dicom.lastSeries";
  public static final String ID_DICOM_FIRST_STUDY = "dicom.firstStudy";
  public static final String ID_DICOM_LAST_STUDY = "dicom.lastStudy";
  public static final String ID_DICOM_FIRST_PATIENT = "dicom.firstPatient";
  public static final String ID_DICOM_LAST_PATIENT = "dicom.lastPatient";

  // -- Shortcut IDs: Docking --
  public static final String ID_DOCKING_MAXIMIZE = "docking.maximize";
  public static final String ID_DOCKING_EXTERNALIZE = "docking.externalize";
  public static final String ID_DOCKING_NORMALIZE = "docking.normalize";
  public static final String ID_DOCKING_CLOSE = "docking.close";
  public static final String ID_DOCKING_PANEL_LIST = "docking.panelList";
  public static final String ID_DOCKING_NEXT_TAB = "docking.nextTab";
  public static final String ID_DOCKING_PREV_TAB = "docking.prevTab";

  // -- Shortcut IDs: MPR --
  public static final String ID_MPR_RECENTER = "mpr.recenter";
  public static final String ID_MPR_RECENTER_ALL = "mpr.recenterAll";
  public static final String ID_MPR_TOGGLE_CENTER = "mpr.toggleCenter";
  public static final String ID_MPR_TOGGLE_CENTER_ALL = "mpr.toggleCenterAll";
  public static final String ID_MPR_TOGGLE_CROSS_LINES = "mpr.toggleCrossLines";
  public static final String ID_MPR_TOGGLE_CROSS_LINES_ALL = "mpr.toggleCrossLinesAll";
  public static final String ID_MPR_CYCLE_MIP = "mpr.cycleMip";

  // -- Shortcut IDs: DICOM Explorer --
  public static final String ID_EXPLORER_OPEN = "explorer.open";
  public static final String ID_EXPLORER_SELECT_NEXT = "explorer.selectNext";
  public static final String ID_EXPLORER_SELECT_PREVIOUS = "explorer.selectPrevious";
  public static final String ID_EXPLORER_SELECT_FIRST = "explorer.selectFirst";
  public static final String ID_EXPLORER_SELECT_FIRST_ALT = "explorer.selectFirstAlt";
  public static final String ID_EXPLORER_SELECT_LAST = "explorer.selectLast";
  public static final String ID_EXPLORER_SELECT_LAST_ALT = "explorer.selectLastAlt";
  public static final String ID_EXPLORER_SELECT_ALL = "explorer.selectAll";

  // -- Registration --
  /**
   * Registers all default shortcuts from {@link ActionW} feature fields and hardcoded viewer
   * shortcuts.
   *
   * <p>This should be called once at application startup, before loading user preferences.
   */
  public void registerDefaults() {
    shortcuts.clear();
    registerActionWFeatures();
    // Register hardcoded viewer shortcuts
    registerViewerShortcuts();
  }

  private void registerActionWFeatures() {
    for (Field field : ActionW.class.getDeclaredFields()) {
      if (Modifier.isStatic(field.getModifiers())
          && Modifier.isFinal(field.getModifiers())
          && Feature.class.isAssignableFrom(field.getType())) {
        try {
          Feature<?> feature = (Feature<?>) field.get(null);
          if (feature != null && feature.getKeyCode() != 0) {
            String category = determineFeatureCategory(feature);
            register(
                feature.cmd(),
                feature.getTitle().isEmpty() ? field.getName() : feature.getTitle(),
                category,
                feature.getKeyCode(),
                feature.getModifier());
          }
        } catch (IllegalAccessException e) {
          LOGGER.warn("Cannot access ActionW field: {}", field.getName(), e);
        }
      }
    }
  }

  private String determineFeatureCategory(Feature<?> feature) {
    if (feature.isDrawingAction()) {
      return CATEGORY_MEASURES;
    } else if (feature.isGraphicListAction()) {
      return CATEGORY_DRAWINGS;
    }
    return CATEGORY_MOUSE_ACTIONS;
  }

  private void registerViewerShortcuts() {
    register(
        CINESTART.cmd(),
        Messages.getString("ShortcutManager.cine_start_stop"),
        CATEGORY_MOUSE_ACTIONS,
        KeyEvent.VK_C,
        0);

    // ---- Common display shortcuts (ImageViewerEventManager.commonDisplayShortcuts()) ----
    register(
        ID_VIEWER_PRINT,
        Messages.getString("ShortcutManager.print_views"),
        CATEGORY_VIEWER,
        KeyEvent.VK_P,
        0);
    register(
        ID_VIEWER_ESCAPE,
        Messages.getString("ShortcutManager.reset_view"),
        CATEGORY_VIEWER,
        KeyEvent.VK_ESCAPE,
        0);
    register(
        ID_VIEWER_ZOOM_OUT,
        Messages.getString("ShortcutManager.zoom_out"),
        CATEGORY_VIEWER,
        KeyEvent.VK_SUBTRACT,
        KeyEvent.CTRL_MASK);
    register(
        ID_VIEWER_ZOOM_IN,
        Messages.getString("ShortcutManager.zoom_in"),
        CATEGORY_VIEWER,
        KeyEvent.VK_ADD,
        KeyEvent.CTRL_MASK);
    register(
        ID_VIEWER_BEST_FIT,
        Messages.getString("ShortcutManager.best_fit"),
        CATEGORY_VIEWER,
        KeyEvent.VK_ENTER,
        KeyEvent.CTRL_MASK);

    // ---- Slice navigation (ImageViewerEventManager.commonDisplayShortcuts()) ----
    register(
        ID_VIEWER_SCROLL_UP,
        Messages.getString("ShortcutManager.prev_image"),
        CATEGORY_NAVIGATION,
        KeyEvent.VK_UP,
        0);
    register(
        ID_VIEWER_SCROLL_DOWN,
        Messages.getString("ShortcutManager.next_image"),
        CATEGORY_NAVIGATION,
        KeyEvent.VK_DOWN,
        0);
    register(
        ID_VIEWER_SCROLL_UP_FAST,
        Messages.getString("ShortcutManager.back_10"),
        CATEGORY_NAVIGATION,
        KeyEvent.VK_UP,
        KeyEvent.SHIFT_MASK);
    register(
        ID_VIEWER_SCROLL_DOWN_FAST,
        Messages.getString("ShortcutManager.fwd_10"),
        CATEGORY_NAVIGATION,
        KeyEvent.VK_DOWN,
        KeyEvent.SHIFT_MASK);
    register(
        ID_VIEWER_SCROLL_FIRST,
        Messages.getString("ShortcutManager.first_image"),
        CATEGORY_NAVIGATION,
        KeyEvent.VK_HOME,
        0);
    register(
        ID_VIEWER_SCROLL_LAST,
        Messages.getString("ShortcutManager.last_image"),
        CATEGORY_NAVIGATION,
        KeyEvent.VK_END,
        0);

    // ---- ViewCanvas.defaultKeyPressed() shortcuts ----
    register(
        ID_VIEWER_NEXT_VIEW,
        Messages.getString("ShortcutManager.next_view"),
        CATEGORY_NAVIGATION,
        KeyEvent.VK_TAB,
        0);
    register(
        ID_VIEWER_PREV_VIEW,
        Messages.getString("ShortcutManager.prev_view"),
        CATEGORY_NAVIGATION,
        KeyEvent.VK_TAB,
        KeyEvent.SHIFT_MASK);
    register(
        ID_VIEWER_NEXT_MOUSE_ACTION,
        Messages.getString("ShortcutManager.next_mouse_action"),
        CATEGORY_MOUSE_ACTIONS,
        KeyEvent.VK_SPACE,
        KeyEvent.CTRL_MASK);
    register(
        ID_VIEWER_TOGGLE_INFO,
        Messages.getString("ShortcutManager.toggle_info"),
        CATEGORY_DISPLAY,
        KeyEvent.VK_SPACE,
        0);
    register(
        ID_VIEWER_TOGGLE_INFO_ALT,
        Messages.getString("ShortcutManager.toggle_info"),
        CATEGORY_DISPLAY,
        KeyEvent.VK_I,
        0);
    register(
        ID_VIEWER_FULLSCREEN,
        Messages.getString("ShortcutManager.fullscreen"),
        CATEGORY_DISPLAY,
        KeyEvent.VK_F11,
        0);
    register(
        ID_VIEWER_ROTATE_LEFT,
        Messages.getString("ShortcutManager.rotate_left"),
        CATEGORY_VIEWER,
        KeyEvent.VK_L,
        KeyEvent.ALT_MASK);
    register(
        ID_VIEWER_ROTATE_RIGHT,
        Messages.getString("ShortcutManager.rotate_right"),
        CATEGORY_VIEWER,
        KeyEvent.VK_R,
        KeyEvent.ALT_MASK);
    register(
        ID_VIEWER_FLIP_HORIZONTAL,
        Messages.getString("ShortcutManager.flip_horiz"),
        CATEGORY_VIEWER,
        KeyEvent.VK_F,
        KeyEvent.ALT_MASK);

    // ---- Graphic tool shortcuts (Graphic.getKeyCode() in various subclasses) ----
    register(
        ID_GRAPHIC_LINE,
        Messages.getString("ShortcutManager.dist_measure"),
        CATEGORY_MEASURES,
        KeyEvent.VK_D,
        0);
    register(
        ID_GRAPHIC_ANGLE,
        Messages.getString("ShortcutManager.angle_measure"),
        CATEGORY_MEASURES,
        KeyEvent.VK_A,
        0);
    register(
        ID_GRAPHIC_POLYGON,
        Messages.getString("ShortcutManager.polyline_measure"),
        CATEGORY_MEASURES,
        KeyEvent.VK_Y,
        0);
    register(
        ID_GRAPHIC_ANNOTATION,
        Messages.getString("ShortcutManager.textbox"),
        CATEGORY_DRAWINGS,
        KeyEvent.VK_B,
        0);

    // ---- Drawing management shortcuts (DrawingsKeyListeners) ----
    register(
        ID_DRAW_DELETE,
        Messages.getString("ShortcutManager.delete_graphics"),
        CATEGORY_DRAWINGS,
        KeyEvent.VK_DELETE,
        0);
    register(
        ID_DRAW_DESELECT_ALL,
        Messages.getString("ShortcutManager.deselect_all"),
        CATEGORY_DRAWINGS,
        KeyEvent.VK_D,
        KeyEvent.CTRL_MASK);
    register(
        ID_DRAW_SELECT_ALL,
        Messages.getString("ShortcutManager.select_all_graphics"),
        CATEGORY_DRAWINGS,
        KeyEvent.VK_A,
        KeyEvent.CTRL_MASK);

    // ---- Pan with keyboard (PannerListener.keyPressed()) ----
    register(
        ID_PAN_LEFT,
        Messages.getString("ShortcutManager.pan_left_5"),
        CATEGORY_PAN,
        KeyEvent.VK_LEFT,
        KeyEvent.ALT_MASK);
    register(
        ID_PAN_RIGHT,
        Messages.getString("ShortcutManager.pan_right_5"),
        CATEGORY_PAN,
        KeyEvent.VK_RIGHT,
        KeyEvent.ALT_MASK);
    register(
        ID_PAN_UP,
        Messages.getString("ShortcutManager.pan_up_5"),
        CATEGORY_PAN,
        KeyEvent.VK_UP,
        KeyEvent.ALT_MASK);
    register(
        ID_PAN_DOWN,
        Messages.getString("ShortcutManager.pan_down_5"),
        CATEGORY_PAN,
        KeyEvent.VK_DOWN,
        KeyEvent.ALT_MASK);
    register(
        ID_PAN_LEFT_FAST,
        Messages.getString("ShortcutManager.pan_left_10"),
        CATEGORY_PAN,
        KeyEvent.VK_LEFT,
        KeyEvent.ALT_MASK | KeyEvent.SHIFT_MASK);
    register(
        ID_PAN_RIGHT_FAST,
        Messages.getString("ShortcutManager.pan_right_10"),
        CATEGORY_PAN,
        KeyEvent.VK_RIGHT,
        KeyEvent.ALT_MASK | KeyEvent.SHIFT_MASK);
    register(
        ID_PAN_UP_FAST,
        Messages.getString("ShortcutManager.pan_up_10"),
        CATEGORY_PAN,
        KeyEvent.VK_UP,
        KeyEvent.ALT_MASK | KeyEvent.SHIFT_MASK);
    register(
        ID_PAN_DOWN_FAST,
        Messages.getString("ShortcutManager.pan_down_10"),
        CATEGORY_PAN,
        KeyEvent.VK_DOWN,
        KeyEvent.ALT_MASK | KeyEvent.SHIFT_MASK);

    // ---- DICOM series/study/patient navigation (dicom EventManager.keyPressed()) ----
    register(
        ID_DICOM_PREV_SERIES,
        Messages.getString("ShortcutManager.prev_series"),
        CATEGORY_DICOM_NAV,
        ShortcutContext.DICOM_VIEWER,
        KeyEvent.VK_LEFT,
        0);
    register(
        ID_DICOM_NEXT_SERIES,
        Messages.getString("ShortcutManager.next_series"),
        CATEGORY_DICOM_NAV,
        ShortcutContext.DICOM_VIEWER,
        KeyEvent.VK_RIGHT,
        0);
    register(
        ID_DICOM_PREV_STUDY,
        Messages.getString("ShortcutManager.prev_study"),
        CATEGORY_DICOM_NAV,
        ShortcutContext.DICOM_VIEWER,
        KeyEvent.VK_LEFT,
        KeyEvent.CTRL_MASK);
    register(
        ID_DICOM_NEXT_STUDY,
        Messages.getString("ShortcutManager.next_study"),
        CATEGORY_DICOM_NAV,
        ShortcutContext.DICOM_VIEWER,
        KeyEvent.VK_RIGHT,
        KeyEvent.CTRL_MASK);
    register(
        ID_DICOM_PREV_PATIENT,
        Messages.getString("ShortcutManager.prev_patient"),
        CATEGORY_DICOM_NAV,
        ShortcutContext.DICOM_VIEWER,
        KeyEvent.VK_UP,
        KeyEvent.CTRL_MASK);
    register(
        ID_DICOM_NEXT_PATIENT,
        Messages.getString("ShortcutManager.next_patient"),
        CATEGORY_DICOM_NAV,
        ShortcutContext.DICOM_VIEWER,
        KeyEvent.VK_DOWN,
        KeyEvent.CTRL_MASK);
    register(
        ID_DICOM_FIRST_SERIES,
        Messages.getString("ShortcutManager.first_series"),
        CATEGORY_DICOM_NAV,
        ShortcutContext.DICOM_VIEWER,
        KeyEvent.VK_PAGE_UP,
        0);
    register(
        ID_DICOM_LAST_SERIES,
        Messages.getString("ShortcutManager.last_series"),
        CATEGORY_DICOM_NAV,
        ShortcutContext.DICOM_VIEWER,
        KeyEvent.VK_PAGE_DOWN,
        0);
    register(
        ID_DICOM_FIRST_STUDY,
        Messages.getString("ShortcutManager.first_study"),
        CATEGORY_DICOM_NAV,
        ShortcutContext.DICOM_VIEWER,
        KeyEvent.VK_PAGE_UP,
        KeyEvent.CTRL_MASK);
    register(
        ID_DICOM_LAST_STUDY,
        Messages.getString("ShortcutManager.last_study"),
        CATEGORY_DICOM_NAV,
        ShortcutContext.DICOM_VIEWER,
        KeyEvent.VK_PAGE_DOWN,
        KeyEvent.CTRL_MASK);
    register(
        ID_DICOM_FIRST_PATIENT,
        Messages.getString("ShortcutManager.first_patient"),
        CATEGORY_DICOM_NAV,
        ShortcutContext.DICOM_VIEWER,
        KeyEvent.VK_HOME,
        KeyEvent.CTRL_MASK);
    register(
        ID_DICOM_LAST_PATIENT,
        Messages.getString("ShortcutManager.last_patient"),
        CATEGORY_DICOM_NAV,
        ShortcutContext.DICOM_VIEWER,
        KeyEvent.VK_END,
        KeyEvent.CTRL_MASK);

    // ---- MPR-specific shortcuts (dicom viewer2d EventManager.keyPressed() for MPR) ----
    register(
        ID_MPR_RECENTER,
        Messages.getString("ShortcutManager.mpr_recenter"),
        CATEGORY_MPR,
        ShortcutContext.MPR,
        KeyEvent.VK_X,
        KeyEvent.ALT_MASK);
    register(
        ID_MPR_RECENTER_ALL,
        Messages.getString("ShortcutManager.mpr_recenter_all"),
        CATEGORY_MPR,
        ShortcutContext.MPR,
        KeyEvent.VK_X,
        KeyEvent.CTRL_MASK | KeyEvent.ALT_MASK);
    register(
        ID_MPR_TOGGLE_CENTER,
        Messages.getString("ShortcutManager.mpr_toggle_center"),
        CATEGORY_MPR,
        ShortcutContext.MPR,
        KeyEvent.VK_C,
        KeyEvent.ALT_MASK);
    register(
        ID_MPR_TOGGLE_CENTER_ALL,
        Messages.getString("ShortcutManager.mpr_toggle_center_all"),
        CATEGORY_MPR,
        ShortcutContext.MPR,
        KeyEvent.VK_C,
        KeyEvent.CTRL_MASK | KeyEvent.ALT_MASK);
    register(
        ID_MPR_TOGGLE_CROSS_LINES,
        Messages.getString("ShortcutManager.mpr_toggle_cross"),
        CATEGORY_MPR,
        ShortcutContext.MPR,
        KeyEvent.VK_V,
        KeyEvent.ALT_MASK);
    register(
        ID_MPR_TOGGLE_CROSS_LINES_ALL,
        Messages.getString("ShortcutManager.mpr_toggle_cross_all"),
        CATEGORY_MPR,
        ShortcutContext.MPR,
        KeyEvent.VK_V,
        KeyEvent.CTRL_MASK | KeyEvent.ALT_MASK);
    register(
        ID_MPR_CYCLE_MIP,
        Messages.getString("ShortcutManager.mpr_cycle_mip"),
        CATEGORY_MPR,
        ShortcutContext.MPR,
        KeyEvent.VK_B,
        KeyEvent.CTRL_MASK | KeyEvent.ALT_MASK);

    // ---- Docking framework tab shortcuts (WeasisWin.createMainPanel()) ----
    register(
        ID_DOCKING_MAXIMIZE,
        Messages.getString("ShortcutManager.dock_maximize"),
        CATEGORY_DOCKING,
        KeyEvent.VK_M,
        KeyEvent.CTRL_MASK);
    register(
        ID_DOCKING_EXTERNALIZE,
        Messages.getString("ShortcutManager.dock_externalize"),
        CATEGORY_DOCKING,
        KeyEvent.VK_E,
        KeyEvent.CTRL_MASK);
    register(
        ID_DOCKING_NORMALIZE,
        Messages.getString("ShortcutManager.dock_normalize"),
        CATEGORY_DOCKING,
        KeyEvent.VK_N,
        KeyEvent.CTRL_MASK);
    register(
        ID_DOCKING_CLOSE,
        Messages.getString("ShortcutManager.dock_close"),
        CATEGORY_DOCKING,
        KeyEvent.VK_W,
        KeyEvent.CTRL_MASK);
    register(
        ID_DOCKING_PANEL_LIST,
        Messages.getString("ShortcutManager.dock_panel_list"),
        CATEGORY_DOCKING,
        KeyEvent.VK_E,
        KeyEvent.CTRL_MASK | KeyEvent.SHIFT_MASK);
    register(
        ID_DOCKING_NEXT_TAB,
        Messages.getString("ShortcutManager.dock_next_tab"),
        CATEGORY_DOCKING,
        KeyEvent.VK_TAB,
        KeyEvent.CTRL_MASK);
    register(
        ID_DOCKING_PREV_TAB,
        Messages.getString("ShortcutManager.dock_prev_tab"),
        CATEGORY_DOCKING,
        KeyEvent.VK_TAB,
        KeyEvent.CTRL_MASK | KeyEvent.SHIFT_MASK);

    // ---- DICOM Explorer shortcuts (ThumbnailMouseAndKeyAdapter.keyPressed()) ----
    register(
        ID_EXPLORER_OPEN,
        Messages.getString("ShortcutManager.explorer_open"),
        CATEGORY_DICOM_EXPLORER,
        ShortcutContext.DICOM_EXPLORER,
        KeyEvent.VK_ENTER,
        0);
    register(
        ID_EXPLORER_SELECT_NEXT,
        Messages.getString("ShortcutManager.explorer_next"),
        CATEGORY_DICOM_EXPLORER,
        ShortcutContext.DICOM_EXPLORER,
        KeyEvent.VK_DOWN,
        0);
    register(
        ID_EXPLORER_SELECT_PREVIOUS,
        Messages.getString("ShortcutManager.explorer_prev"),
        CATEGORY_DICOM_EXPLORER,
        ShortcutContext.DICOM_EXPLORER,
        KeyEvent.VK_UP,
        0);
    register(
        ID_EXPLORER_SELECT_FIRST,
        Messages.getString("ShortcutManager.explorer_first"),
        CATEGORY_DICOM_EXPLORER,
        ShortcutContext.DICOM_EXPLORER,
        KeyEvent.VK_HOME,
        0);
    register(
        ID_EXPLORER_SELECT_FIRST_ALT,
        Messages.getString("ShortcutManager.explorer_first"),
        CATEGORY_DICOM_EXPLORER,
        ShortcutContext.DICOM_EXPLORER,
        KeyEvent.VK_PAGE_UP,
        0);
    register(
        ID_EXPLORER_SELECT_LAST,
        Messages.getString("ShortcutManager.explorer_last"),
        CATEGORY_DICOM_EXPLORER,
        ShortcutContext.DICOM_EXPLORER,
        KeyEvent.VK_END,
        0);
    register(
        ID_EXPLORER_SELECT_LAST_ALT,
        Messages.getString("ShortcutManager.explorer_last"),
        CATEGORY_DICOM_EXPLORER,
        ShortcutContext.DICOM_EXPLORER,
        KeyEvent.VK_PAGE_DOWN,
        0);
    register(
        ID_EXPLORER_SELECT_ALL,
        Messages.getString("ShortcutManager.explorer_select_all"),
        CATEGORY_DICOM_EXPLORER,
        ShortcutContext.DICOM_EXPLORER,
        KeyEvent.VK_A,
        KeyEvent.CTRL_MASK);
  }

  /**
   * Registers a shortcut entry. If a shortcut with the same id already exists, it is replaced.
   *
   * @param id unique identifier
   * @param description human-readable description
   * @param category the category for grouping in the UI
   * @param context the activation context (determines conflict scope)
   * @param defaultKeyCode the default key code
   * @param defaultModifier the default modifier mask
   */
  public void register(
      String id,
      String description,
      String category,
      ShortcutContext context,
      int defaultKeyCode,
      int defaultModifier) {
    shortcuts.put(
        id, new ShortcutEntry(id, description, category, context, defaultKeyCode, defaultModifier));
  }

  /**
   * Registers a shortcut entry with the default {@link ShortcutContext#VIEW_CANVAS} context.
   *
   * @see #register(String, String, String, ShortcutContext, int, int)
   */
  public void register(
      String id, String description, String category, int defaultKeyCode, int defaultModifier) {
    register(
        id, description, category, ShortcutContext.VIEW_CANVAS, defaultKeyCode, defaultModifier);
  }

  // -- Lookups --

  /**
   * Checks whether the given key code and modifier mask match the current shortcut for the
   * specified id.
   *
   * @param id the shortcut id
   * @param keyCode the key code to test
   * @param modifier the modifier mask to test
   * @return true if both key code and modifier match
   */
  public boolean matches(String id, int keyCode, int modifier) {
    ShortcutEntry entry = shortcuts.get(id);
    if (entry == null || entry.getKeyCode() == 0) {
      return false;
    }
    return entry.getKeyCode() == keyCode && entry.getModifier() == modifier;
  }

  /**
   * Checks whether the given KeyEvent matches the current shortcut for the specified id.
   *
   * @param id the shortcut id
   * @param e the KeyEvent to test
   * @return true if both key code and modifier match
   */
  public boolean matches(String id, KeyEvent e) {
    return matches(id, e.getKeyCode(), e.getModifiers());
  }

  public ShortcutEntry getEntry(String id) {
    return shortcuts.get(id);
  }

  /**
   * Returns the current key code for the given shortcut id.
   *
   * @return the key code, or 0 if not found
   */
  public int getKeyCode(String id) {
    ShortcutEntry entry = shortcuts.get(id);
    return entry != null ? entry.getKeyCode() : 0;
  }

  /**
   * Returns the current modifier mask for the given shortcut id.
   *
   * @return the modifier, or 0 if not found
   */
  public int getModifier(String id) {
    ShortcutEntry entry = shortcuts.get(id);
    return entry != null ? entry.getModifier() : 0;
  }

  /** Returns an unmodifiable view of all registered shortcut entries. */
  public Map<String, ShortcutEntry> getShortcuts() {
    return Collections.unmodifiableMap(shortcuts);
  }

  /** Returns all shortcut entries as a list, sorted by category, context, then description. */
  public List<ShortcutEntry> getShortcutList() {
    List<ShortcutEntry> list = new ArrayList<>(shortcuts.values());
    list.sort(
        Comparator.comparing(ShortcutEntry::getCategory)
            .thenComparing(e -> e.getContext().getDisplayName())
            .thenComparing(ShortcutEntry::getDescription));
    return list;
  }

  // -- Modification --

  /**
   * Updates the shortcut for the given id.
   *
   * @param id the shortcut id
   * @param keyCode the new key code
   * @param modifier the new modifier mask
   * @return true if the shortcut was found and updated
   */
  public boolean setShortcut(String id, int keyCode, int modifier) {
    ShortcutEntry entry = shortcuts.get(id);
    if (entry == null) {
      return false;
    }
    int oldKeyCode = entry.getKeyCode();
    int oldModifier = entry.getModifier();
    entry.setKeyCode(keyCode);
    entry.setModifier(modifier);
    if (oldKeyCode != keyCode || oldModifier != modifier) {
      pcs.firePropertyChange(PROPERTY_SHORTCUTS_CHANGED, null, entry);
    }
    return true;
  }

  /** Resets all shortcuts to their default values. */
  public void resetAllToDefaults() {
    for (ShortcutEntry entry : shortcuts.values()) {
      entry.resetToDefault();
    }
    pcs.firePropertyChange(PROPERTY_SHORTCUTS_CHANGED, null, null);
  }

  // -- Conflict detection --

  /**
   * Finds all shortcuts that conflict with the given key/modifier, excluding the specified id. Only
   * entries whose context overlaps with the context of the excluded entry are considered, so that
   * shortcuts in non-overlapping contexts (e.g., different viewer types) are not flagged as
   * conflicts.
   *
   * @param excludeId the id of the entry being edited (excluded from results)
   * @param keyCode the key code to test
   * @param modifier the modifier mask to test
   * @return list of conflicting entries, empty if none
   */
  public List<ShortcutEntry> findConflicts(String excludeId, int keyCode, int modifier) {
    if (keyCode == 0) {
      return Collections.emptyList();
    }
    ShortcutEntry source = shortcuts.get(excludeId);
    ShortcutContext sourceContext =
        source != null ? source.getContext() : ShortcutContext.VIEW_CANVAS;
    return findConflicts(excludeId, keyCode, modifier, sourceContext);
  }

  /**
   * Finds all shortcuts that conflict with the given key/modifier within the specified context,
   * excluding the specified id.
   *
   * @param excludeId the id of the entry being edited (excluded from results)
   * @param keyCode the key code to test
   * @param modifier the modifier mask to test
   * @param context the activation context to check overlaps against
   * @return list of conflicting entries whose context overlaps with the given one
   */
  public List<ShortcutEntry> findConflicts(
      String excludeId, int keyCode, int modifier, ShortcutContext context) {
    if (keyCode == 0) {
      return Collections.emptyList();
    }
    List<ShortcutEntry> conflicts = new ArrayList<>();
    for (ShortcutEntry entry : shortcuts.values()) {
      if (!entry.getId().equals(excludeId)
          && entry.getKeyCode() == keyCode
          && entry.getModifier() == modifier
          && context.overlapsWith(entry.getContext())) {
        conflicts.add(entry);
      }
    }
    return conflicts;
  }

  // -- Apply to Feature objects --

  /**
   * Applies the current shortcut settings back to the {@link Feature} fields in {@link ActionW}.
   * This ensures that all code reading {@code feature.getKeyCode()} / {@code feature.getModifier()}
   * gets the user's customized values.
   */
  public void applyToFeatures() {
    for (Field field : ActionW.class.getDeclaredFields()) {
      if (Modifier.isStatic(field.getModifiers())
          && Modifier.isFinal(field.getModifiers())
          && Feature.class.isAssignableFrom(field.getType())) {
        try {
          Feature<?> feature = (Feature<?>) field.get(null);
          if (feature != null) {
            ShortcutEntry entry = shortcuts.get(feature.cmd());
            if (entry != null) {
              feature.setKeyCode(entry.getKeyCode());
              feature.setModifier(entry.getModifier());
            }
          }
        } catch (IllegalAccessException e) {
          LOGGER.warn("Cannot access ActionW field: {}", field.getName(), e);
        }
      }
    }
    pcs.firePropertyChange(PROPERTY_SHORTCUTS_CHANGED, null, null);
  }

  // -- Persistence --

  /**
   * Loads user-customized shortcuts from OSGi preferences. Only overrides entries that exist in the
   * preferences node; missing entries keep their defaults.
   */
  public void loadPreferences(Preferences prefs) {
    if (prefs == null) {
      return;
    }
    Preferences node = prefs.node(getPreferenceNodeName());
    for (ShortcutEntry entry : shortcuts.values()) {
      int kc = node.getInt(entry.getId() + KEY_CODE_SUFFIX, -1);
      int mod = node.getInt(entry.getId() + MODIFIER_SUFFIX, -1);
      if (kc >= 0) {
        entry.setKeyCode(kc);
      }
      if (mod >= 0) {
        entry.setModifier(mod);
      }
    }
  }

  /**
   * Saves user-customized shortcuts to OSGi preferences. Only entries that differ from defaults are
   * persisted; default entries are removed from the preferences node.
   */
  public void savePreferences(Preferences prefs) {
    if (prefs == null) {
      return;
    }
    Preferences node = prefs.node(getPreferenceNodeName());
    for (ShortcutEntry entry : shortcuts.values()) {
      if (entry.isModified()) {
        BundlePreferences.putIntPreferences(
            node, entry.getId() + KEY_CODE_SUFFIX, entry.getKeyCode());
        BundlePreferences.putIntPreferences(
            node, entry.getId() + MODIFIER_SUFFIX, entry.getModifier());
      } else {
        // Clean up defaults from prefs to keep it lean
        node.remove(entry.getId() + KEY_CODE_SUFFIX);
        node.remove(entry.getId() + MODIFIER_SUFFIX);
      }
    }
  }

  // -- Listeners --

  public void addPropertyChangeListener(PropertyChangeListener listener) {
    pcs.addPropertyChangeListener(listener);
  }

  public void removePropertyChangeListener(PropertyChangeListener listener) {
    pcs.removePropertyChangeListener(listener);
  }

  // -- Utility --

  /**
   * Generates a self-contained HTML page showing all registered shortcuts, grouped by category.
   * Customized shortcuts are highlighted. The page reflects the current (possibly user-modified)
   * state of the shortcut bindings.
   *
   * @return the HTML content as a String
   */
  public String generateHtmlPage() {
    List<ShortcutEntry> entries = getShortcutList();
    String lang = Locale.getDefault().toLanguageTag();
    String helpBaseUrl =
        GuiUtils.getUICore()
            .getSystemPreferences()
            .getProperty("weasis.help.online", "https://weasis.org/en/tutorials/"); // NON-NLS
    Map<String, String> categoryTopics = buildCategoryDocTopics();

    StringBuilder html = new StringBuilder(4096);
    html.append(
        """
        <!DOCTYPE html>
        <html lang="%s">
        <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>%s</title>
        """
            .formatted(
                escapeHtml(lang), escapeHtml(Messages.getString("ShortcutManager.html.title"))));
    html.append(
        """
        <style>
          :root {
            --bg: #1e1e2e; --surface: #313244; --text: #cdd6f4;
            --subtext: #a6adc8; --accent: #89b4fa; --green: #a6e3a1;
            --border: #45475a; --modified: #f9e2af; --header-bg: #181825;
          }
          @media (prefers-color-scheme: light) {
            :root {
              --bg: #eff1f5; --surface: #ffffff; --text: #4c4f69;
              --subtext: #6c6f85; --accent: #1e66f5; --green: #40a02b;
              --border: #ccd0da; --modified: #df8e1d; --header-bg: #e6e9ef;
            }
          }
          * { box-sizing: border-box; margin: 0; padding: 0; }
          body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: var(--bg); color: var(--text); padding: 2rem; line-height: 1.6;
          }
          h1 {
            text-align: center; color: var(--accent); margin-bottom: .3rem;
            font-size: 1.8rem; font-weight: 700;
          }
          .subtitle {
            text-align: center; color: var(--subtext); margin-bottom: 2rem;
            font-size: .9rem;
          }
          .category { margin-bottom: 2rem; }
          .category h2 {
            color: var(--green); font-size: 1.15rem; font-weight: 600;
            border-bottom: 2px solid var(--border); padding-bottom: .4rem;
            margin-bottom: .6rem;
          }
          table {
            width: 100%%; border-collapse: collapse;
            background: var(--surface); border-radius: 8px; overflow: hidden;
          }
          th {
            background: var(--header-bg); text-align: left; padding: .55rem .8rem;
            font-size: .8rem; text-transform: uppercase; letter-spacing: .04em;
            color: var(--subtext); border-bottom: 1px solid var(--border);
          }
          td {
            padding: .5rem .8rem; border-bottom: 1px solid var(--border);
            font-size: .88rem;
          }
          tr:last-child td { border-bottom: none; }
          tr:hover td { background: var(--header-bg); }
          .kbd {
            display: inline-block; background: var(--header-bg); color: var(--accent);
            border: 1px solid var(--border); border-radius: 4px;
            padding: .1rem .45rem; font-family: 'SF Mono', Consolas, monospace;
            font-size: .82rem; white-space: nowrap;
          }
          .modified .kbd { color: var(--modified); border-color: var(--modified); }
          .modified td:first-child::after {
            content: ' ✎'; color: var(--modified); font-size: .75rem;
          }
          .info-note {
            background: var(--surface); border-left: 4px solid var(--accent);
            border-radius: 6px; padding: .8rem 1rem; margin-bottom: 1.5rem;
            color: var(--subtext); font-size: .85rem; line-height: 1.5;
          }
          .info-note strong { color: var(--text); }
          .fixed-section { margin-bottom: 2rem; }
          .fixed-section h2 {
            color: var(--accent); font-size: 1.15rem; font-weight: 600;
            border-bottom: 2px solid var(--border); padding-bottom: .4rem;
            margin-bottom: .6rem;
          }
          .fixed-section h3 {
            color: var(--green); font-size: 1rem; font-weight: 600;
            margin: 1rem 0 .4rem;
          }
          footer {
            text-align: center; color: var(--subtext); margin-top: 2rem;
            font-size: .78rem;
          }
          a.doc-link {
            color: var(--accent); text-decoration: none; font-size: .82rem;
            margin-left: .4rem; opacity: .7; transition: opacity .15s;
          }
          a.doc-link:hover { opacity: 1; text-decoration: underline; }
          .doc-bar {
            text-align: center; margin-bottom: 1.5rem;
          }
          .doc-bar a {
            color: var(--accent); text-decoration: none; font-size: .92rem;
          }
          .doc-bar a:hover { text-decoration: underline; }
        </style>
        </head>
        <body>
        """);
    html.append("<h1>⌨ ")
        .append(escapeHtml(Messages.getString("ShortcutManager.html.title")))
        .append("</h1>\n");
    html.append("<div class=\"subtitle\">")
        .append(escapeHtml(Messages.getString("ShortcutManager.html.subtitle")))
        .append("</div>\n");
    html.append("<div class=\"doc-bar\"><a href=\"")
        .append(escapeHtml(helpBaseUrl))
        .append("index.html\" target=\"_blank\">↗ ")
        .append(escapeHtml(Messages.getString("ShortcutManager.html.online_doc")))
        .append("</a></div>\n");

    // Group entries by category
    Map<String, List<ShortcutEntry>> byCategory = new LinkedHashMap<>();
    for (ShortcutEntry entry : entries) {
      byCategory.computeIfAbsent(entry.getCategory(), k -> new ArrayList<>()).add(entry);
    }

    for (Map.Entry<String, List<ShortcutEntry>> group : byCategory.entrySet()) {
      html.append("<div class=\"category\">\n");
      html.append("<h2>").append(escapeHtml(group.getKey()));
      appendDocLink(html, helpBaseUrl, categoryTopics.get(group.getKey()));
      html.append("</h2>\n");
      html.append("<table>\n");
      html.append("<tr><th>")
          .append(escapeHtml(Messages.getString("ShortcutManager.html.col.action")))
          .append("</th><th>")
          .append(escapeHtml(Messages.getString("ShortcutManager.html.col.context")))
          .append("</th><th>")
          .append(escapeHtml(Messages.getString("ShortcutManager.html.col.shortcut")))
          .append("</th><th>")
          .append(escapeHtml(Messages.getString("ShortcutManager.html.col.default")))
          .append("</th></tr>\n");

      for (ShortcutEntry entry : group.getValue()) {
        String rowClass = entry.isModified() ? " class=\"modified\"" : "";
        String shortcutText = entry.getShortcutText();
        String defaultText = entry.getDefaultShortcutText();
        String noneText = Messages.getString("ShortcutManager.html.none");
        String shortcutHtml =
            shortcutText.isEmpty()
                ? "<em style=\"color:var(--subtext)\">" + escapeHtml(noneText) + "</em>"
                : "<span class=\"kbd\">" + escapeHtml(shortcutText) + "</span>";
        String defaultHtml =
            defaultText.isEmpty()
                ? "—"
                : "<span class=\"kbd\">" + escapeHtml(defaultText) + "</span>";

        html.append("<tr").append(rowClass).append(">");
        html.append("<td>").append(escapeHtml(entry.getDescription())).append("</td>");
        html.append("<td>").append(escapeHtml(entry.getContext().getDisplayName())).append("</td>");
        html.append("<td>").append(shortcutHtml).append("</td>");
        html.append("<td>").append(entry.isModified() ? defaultHtml : "—").append("</td>");
        html.append("</tr>\n");
      }

      html.append("</table>\n</div>\n");
    }

    // ---- Non-modifiable shortcuts section ----
    appendFixedShortcutsSection(html, helpBaseUrl);

    html.append("<footer>")
        .append(escapeHtml(Messages.getString("ShortcutManager.html.footer")))
        .append(" · ")
        .append(AppProperties.WEASIS_NAME)
        .append(StringUtil.SPACE)
        .append(AppProperties.WEASIS_VERSION)
        .append("</footer>\n");
    html.append("</body>\n</html>\n");
    return html.toString();
  }

  /**
   * Writes the shortcut HTML page to a temporary file and returns the file path.
   *
   * @param tempDir the directory for the temp file, or {@code null} to use the system default
   * @return the path to the generated HTML file
   * @throws IOException if writing fails
   */
  public Path writeHtmlToTempFile(Path tempDir) throws IOException {
    String html = generateHtmlPage();
    Path file;
    if (tempDir != null) {
      file = Files.createTempFile(tempDir, "weasis-shortcuts-", ".html");
    } else {
      file = Files.createTempFile("weasis-shortcuts-", ".html");
    }
    Files.writeString(file, html, StandardCharsets.UTF_8);
    return file;
  }

  /**
   * Appends additional shortcut sections to the HTML page:
   *
   * <ul>
   *   <li>Non-modifiable shortcuts (DICOM presets defined in external XML configuration)
   *   <li>Mouse and combined interaction shortcuts (key + mouse, drag &amp; drop, click gestures)
   * </ul>
   */
  private static void appendFixedShortcutsSection(StringBuilder html, String helpBaseUrl) {
    // ---- Non-modifiable keyboard shortcuts ----
    html.append("<div class=\"fixed-section\">\n");
    html.append("<h2>")
        .append("\ud83d\udd12 ")
        .append(escapeHtml(Messages.getString("ShortcutManager.html.fixed_title")))
        .append("</h2>\n");

    html.append("<h3>")
        .append(escapeHtml(Messages.getString("ShortcutManager.html.dicom_presets")))
        .append("</h3>\n");
    html.append("<div class=\"info-note\">")
        .append(escapeHtml(Messages.getString("ShortcutManager.html.dicom_presets_note")))
        .append("</div>\n");
    html.append("<table>\n");
    html.append("<tr><th>")
        .append(escapeHtml(Messages.getString("ShortcutManager.html.col.shortcut")))
        .append("</th><th>")
        .append(escapeHtml(Messages.getString("ShortcutManager.html.col.action")))
        .append("</th><th>")
        .append(escapeHtml(Messages.getString("ShortcutManager.html.col.note")))
        .append("</th></tr>\n");
    html.append("<tr><td><span class=\"kbd\">")
        .append(escapeHtml("0 1 2 3 … 9"))
        .append("</span></td><td>")
        .append(escapeHtml(Messages.getString("ShortcutManager.html.dicom_presets_action")))
        .append("</td><td>")
        .append(escapeHtml(Messages.getString("ShortcutManager.html.dicom_presets_note_col")))
        .append("</td></tr>\n");
    html.append("</table>\n");
    html.append("</div>\n");

    // ---- Mouse & combined interaction shortcuts ----
    html.append("<div class=\"fixed-section\">\n");
    html.append("<h2>")
        .append("\ud83d\uddb1 ")
        .append(escapeHtml(Messages.getString("ShortcutManager.html.mouse_title")))
        .append("</h2>\n");
    html.append("<div class=\"info-note\">")
        .append(escapeHtml(Messages.getString("ShortcutManager.html.mouse_note")))
        .append("</div>\n");

    html.append("<h3>")
        .append(escapeHtml(Messages.getString("ShortcutManager.html.dicom_2d_viewer")));
    appendDocLink(html, helpBaseUrl, "dicom-2d-viewer"); // NON-NLS
    html.append("</h3>\n");
    html.append("<table>\n");
    html.append("<tr><th>")
        .append(escapeHtml(Messages.getString("ShortcutManager.html.col.shortcut")))
        .append("</th><th>")
        .append(escapeHtml(Messages.getString("ShortcutManager.html.col.action")))
        .append("</th></tr>\n");
    appendFixedRow(
        html,
        Messages.getString("ShortcutManager.html.mouse.left_drag"),
        Messages.getString("ShortcutManager.html.mouse.left_drag_action"));
    appendFixedRow(
        html,
        Messages.getString("ShortcutManager.html.mouse.middle_drag"),
        Messages.getString("ShortcutManager.html.mouse.middle_drag_action"));
    appendFixedRow(
        html,
        Messages.getString("ShortcutManager.html.mouse.right_click"),
        Messages.getString("ShortcutManager.html.mouse.right_click_action"));
    appendFixedRow(
        html,
        Messages.getString("ShortcutManager.html.mouse.scroll"),
        Messages.getString("ShortcutManager.html.mouse.scroll_action"));
    appendFixedRow(
        html,
        Messages.getString("ShortcutManager.html.mouse.ctrl_drag"),
        Messages.getString("ShortcutManager.html.mouse.ctrl_drag_action"));
    appendFixedRow(
        html,
        Messages.getString("ShortcutManager.html.mouse.ctrl_shift_drag"),
        Messages.getString("ShortcutManager.html.mouse.ctrl_shift_drag_action"));
    appendFixedRow(
        html,
        Messages.getString("ShortcutManager.html.mouse.dblclick_f11"),
        Messages.getString("ShortcutManager.html.mouse.dblclick_f11_action"));
    html.append("</table>\n");

    html.append("<h3>").append(escapeHtml(Messages.getString("ShortcutManager.html.mpr_viewer")));
    appendDocLink(html, helpBaseUrl, "mpr"); // NON-NLS
    html.append("</h3>\n");
    html.append("<table>\n");
    html.append("<tr><th>")
        .append(escapeHtml(Messages.getString("ShortcutManager.html.col.shortcut")))
        .append("</th><th>")
        .append(escapeHtml(Messages.getString("ShortcutManager.html.col.action")))
        .append("</th></tr>\n");
    appendFixedRow(
        html,
        Messages.getString("ShortcutManager.html.mouse.alt_scroll"),
        Messages.getString("ShortcutManager.html.mouse.alt_scroll_action"));
    html.append("</table>\n");

    html.append("<h3>").append(escapeHtml(Messages.getString("ShortcutManager.html.graphics")));
    appendDocLink(html, helpBaseUrl, "draw-measure"); // NON-NLS
    html.append("</h3>\n");
    html.append("<table>\n");
    html.append("<tr><th>")
        .append(escapeHtml(Messages.getString("ShortcutManager.html.col.shortcut")))
        .append("</th><th>")
        .append(escapeHtml(Messages.getString("ShortcutManager.html.col.action")))
        .append("</th></tr>\n");
    appendFixedRow(
        html,
        Messages.getString("ShortcutManager.html.mouse.click_graphic"),
        Messages.getString("ShortcutManager.html.mouse.click_graphic_action"));
    appendFixedRow(
        html,
        Messages.getString("ShortcutManager.html.mouse.click_drag"),
        Messages.getString("ShortcutManager.html.mouse.click_drag_action"));
    appendFixedRow(
        html,
        Messages.getString("ShortcutManager.html.mouse.shift_click_graphic"),
        Messages.getString("ShortcutManager.html.mouse.shift_click_graphic_action"));
    appendFixedRow(
        html,
        Messages.getString("ShortcutManager.html.mouse.dblclick"),
        Messages.getString("ShortcutManager.html.mouse.dblclick_action"));
    html.append("</table>\n");

    html.append("<h3>")
        .append(escapeHtml(Messages.getString("ShortcutManager.ctx.dicom_explorer")));
    appendDocLink(html, helpBaseUrl, "dicom-explorer"); // NON-NLS
    html.append("</h3>\n");
    html.append("<table>\n");
    html.append("<tr><th>")
        .append(escapeHtml(Messages.getString("ShortcutManager.html.col.shortcut")))
        .append("</th><th>")
        .append(escapeHtml(Messages.getString("ShortcutManager.html.col.action")))
        .append("</th></tr>\n");
    appendFixedRow(
        html,
        Messages.getString("ShortcutManager.html.mouse.ctrl_click_thumb"),
        Messages.getString("ShortcutManager.html.mouse.ctrl_click_thumb_action"));
    appendFixedRow(
        html,
        Messages.getString("ShortcutManager.html.mouse.shift_click_thumb"),
        Messages.getString("ShortcutManager.html.mouse.shift_click_thumb_action"));
    appendFixedRow(
        html,
        Messages.getString("ShortcutManager.html.mouse.right_click"),
        Messages.getString("ShortcutManager.html.mouse.right_click_explorer_action"));
    html.append("</table>\n");

    html.append("<h3>")
        .append(escapeHtml(Messages.getString("ShortcutManager.html.drag_drop")))
        .append("</h3>\n");
    html.append("<table>\n");
    html.append("<tr><th>")
        .append(escapeHtml(Messages.getString("ShortcutManager.html.col.shortcut")))
        .append("</th><th>")
        .append(escapeHtml(Messages.getString("ShortcutManager.html.col.action")))
        .append("</th></tr>\n");
    appendFixedRow(
        html,
        Messages.getString("ShortcutManager.html.mouse.drag_files"),
        Messages.getString("ShortcutManager.html.mouse.drag_files_action"));
    appendFixedRow(
        html,
        Messages.getString("ShortcutManager.html.mouse.drag_thumb"),
        Messages.getString("ShortcutManager.html.mouse.drag_thumb_action"));
    html.append("</table>\n");

    html.append("<h3>")
        .append(escapeHtml(Messages.getString("ShortcutManager.html.tabs")))
        .append("</h3>\n");
    html.append("<table>\n");
    html.append("<tr><th>")
        .append(escapeHtml(Messages.getString("ShortcutManager.html.col.shortcut")))
        .append("</th><th>")
        .append(escapeHtml(Messages.getString("ShortcutManager.html.col.action")))
        .append("</th></tr>\n");
    appendFixedRow(
        html,
        Messages.getString("ShortcutManager.html.mouse.tab_right_click"),
        Messages.getString("ShortcutManager.html.mouse.tab_right_click_action"));
    html.append("</table>\n");

    html.append("</div>\n");
  }

  private static void appendFixedRow(StringBuilder html, String shortcut, String action) {
    html.append("<tr><td><span class=\"kbd\">")
        .append(escapeHtml(shortcut))
        .append("</span></td><td>")
        .append(escapeHtml(action))
        .append("</td></tr>\n");
  }

  /**
   * Appends an HTML documentation link (↗) to the given StringBuilder if the topic is non-null.
   * Uses the same URL pattern as {@link GuiUtils#createHelpActionListener}.
   *
   * @param html the StringBuilder to append to
   * @param helpBaseUrl the base help URL
   * @param topic the tutorial topic path, or {@code null} to skip
   */
  private static void appendDocLink(StringBuilder html, String helpBaseUrl, String topic) {
    if (topic != null && helpBaseUrl != null) {
      html.append(" <a class=\"doc-link\" href=\"")
          .append(escapeHtml(helpBaseUrl))
          .append(escapeHtml(topic))
          .append("\" target=\"_blank\">↗</a>");
    }
  }

  /**
   * Builds a mapping from translated category names to their corresponding online tutorial topic
   * paths. Categories without a specific tutorial page are not included.
   */
  private static Map<String, String> buildCategoryDocTopics() {
    Map<String, String> topics = new LinkedHashMap<>();
    topics.put(CATEGORY_MOUSE_ACTIONS, "dicom-2d-viewer"); // NON-NLS
    topics.put(CATEGORY_VIEWER, "dicom-2d-viewer"); // NON-NLS
    topics.put(CATEGORY_NAVIGATION, "dicom-2d-viewer"); // NON-NLS
    topics.put(CATEGORY_MEASURES, "draw-measure"); // NON-NLS
    topics.put(CATEGORY_DRAWINGS, "draw-measure"); // NON-NLS
    topics.put(CATEGORY_DISPLAY, "dicom-2d-viewer"); // NON-NLS
    topics.put(CATEGORY_DICOM_NAV, "dicom-2d-viewer"); // NON-NLS
    topics.put(CATEGORY_MPR, "mpr"); // NON-NLS
    topics.put(CATEGORY_PAN, "dicom-2d-viewer"); // NON-NLS
    topics.put(CATEGORY_DICOM_EXPLORER, "dicom-explorer"); // NON-NLS
    return topics;
  }

  private static String escapeHtml(String s) {
    return EscapeChars.forHTML(s);
  }

  /**
   * Formats a KeyStroke into a readable string (e.g. "Ctrl+Shift+Z").
   *
   * @param ks the keystroke
   * @return the formatted string
   */
  public static String formatKeyStroke(KeyStroke ks) {
    if (ks == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    int mod = ks.getModifiers();
    if ((mod & KeyEvent.CTRL_DOWN_MASK) != 0 || (mod & KeyEvent.CTRL_MASK) != 0) {
      sb.append("Ctrl+");
    }
    if ((mod & KeyEvent.ALT_DOWN_MASK) != 0 || (mod & KeyEvent.ALT_MASK) != 0) {
      sb.append("Alt+");
    }
    if ((mod & KeyEvent.SHIFT_DOWN_MASK) != 0 || (mod & KeyEvent.SHIFT_MASK) != 0) {
      sb.append("Shift+");
    }
    if ((mod & KeyEvent.META_DOWN_MASK) != 0 || (mod & KeyEvent.META_MASK) != 0) {
      sb.append("Meta+");
    }
    sb.append(KeyEvent.getKeyText(ks.getKeyCode()));
    return sb.toString();
  }
}
