/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import bibliothek.gui.DockStation;
import bibliothek.gui.Dockable;
import bibliothek.gui.dock.SplitDockStation;
import bibliothek.gui.dock.StackDockStation;
import bibliothek.gui.dock.action.DefaultDockActionSource;
import bibliothek.gui.dock.action.DockActionSource;
import bibliothek.gui.dock.action.view.ActionViewConverter;
import bibliothek.gui.dock.action.view.ViewTarget;
import bibliothek.gui.dock.common.DefaultSingleCDockable;
import bibliothek.gui.dock.common.action.CAction;
import bibliothek.gui.dock.common.action.CButton;
import bibliothek.gui.dock.common.action.core.CommonSimpleMenuAction;
import bibliothek.gui.dock.common.intern.CDockable;
import bibliothek.gui.dock.common.intern.action.CDecorateableAction;
import bibliothek.gui.dock.layout.DockableProperty;
import bibliothek.gui.dock.station.split.SplitDockProperty;
import bibliothek.gui.dock.util.DockUtilities;
import java.awt.Component;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.Icon;
import javax.swing.SwingUtilities;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.ui.editor.SplitPosition;

/**
 * Actions of the contextual menu of a tab which change where that tab sits in the working area:
 * splitting its tab group in two, as when the 3D viewer is opened from an MPR container, or moving
 * it into another tab group.
 *
 * <p>Every placement is a drop of the tab in a region of the working area, either half of a tab
 * group to split it, or the whole region of a tab group to stack the tab into it.
 */
final class TabPlacement {

  private static final int MAX_MENU_LABEL_LENGTH = 60;

  private TabPlacement() {}

  /** Returns the placement actions to add to the contextual menu of the tab of {@code plugin}. */
  static List<CAction> actions(ViewerPlugin<?> plugin) {
    return List.of(
        new SplitAction(plugin, SplitPosition.RIGHT),
        new SplitAction(plugin, SplitPosition.BOTTOM),
        new MoveToGroupMenu(plugin));
  }

  /**
   * Returns the element identifying the tab group of {@code dockable}: the tab station when the
   * group holds several viewers, the dockable itself when it is alone in its area.
   */
  private static Dockable tabGroupOf(Dockable dockable) {
    return dockable.getDockParent() instanceof StackDockStation stack ? stack : dockable;
  }

  private static SplitDockStation workingArea() {
    return GuiUtils.getUICore().getMainArea().getStation();
  }

  /**
   * Detaches a tab from its parent, which is required before dropping it somewhere else: going
   * through {@link CDockable#setLocation} instead would leave a tab which is a direct child of the
   * working area, that is a tab alone in its area, where it is, because the docking framework then
   * relocates it with {@link SplitDockStation#move}, which does nothing.
   */
  private static boolean detachTab(SplitDockStation area, Dockable dockable) {
    DockStation parent = dockable.getDockParent();
    if (parent == null || !DockUtilities.acceptable(area, dockable)) {
      return false;
    }
    parent.drag(dockable);
    return true;
  }

  /** Drops a detached tab back into the working area, at its root when the location is lost. */
  private static void dropTab(SplitDockStation area, Dockable dockable, DockableProperty location) {
    if (location == null || !area.drop(dockable, location)) {
      area.drop(dockable);
    }
  }

  /** Returns the bounds of {@code component} in the coordinate system of the working area. */
  private static Rectangle areaBounds(Component component) {
    SplitDockStation area = workingArea();
    if (component == null
        || !component.isShowing()
        || area.getWidth() <= 0
        || area.getHeight() <= 0) {
      return null;
    }
    return SwingUtilities.convertRectangle(component, new Rectangle(component.getSize()), area);
  }

  private static SplitDockProperty splitProperty(SplitDockStation area, Rectangle bounds) {
    double width = area.getWidth();
    double height = area.getHeight();
    return new SplitDockProperty(
        bounds.x / width, bounds.y / height, bounds.width / width, bounds.height / height);
  }

  private static String menuLabel(String text) {
    return text.length() > MAX_MENU_LABEL_LENGTH
        ? text.substring(0, MAX_MENU_LABEL_LENGTH) + "…" // NON-NLS
        : text;
  }

  /** Returns the title of a tab group: the title its viewers share, or all their titles. */
  private static String groupLabel(List<ViewerPlugin<?>> members) {
    return members.stream()
        .map(ViewerPlugin::getPluginName)
        .distinct()
        .collect(Collectors.joining(", "));
  }

  /** Returns a miniature of the working area locating {@code component}, or {@code null}. */
  private static Icon areaIcon(Component component) {
    Rectangle bounds = areaBounds(component);
    if (bounds == null) {
      return null;
    }
    SplitDockProperty place = splitProperty(workingArea(), bounds);
    return new AreaIcon(place.getX(), place.getY(), place.getWidth(), place.getHeight());
  }

  /**
   * Splits the tab group holding this tab in two.
   *
   * <p>The placement is expressed as a rectangle of the tab group rather than as a location
   * relative to the root of the working area, so that a group which is itself the result of a split
   * can be split again.
   */
  private static class SplitAction extends CButton {
    private final ViewerPlugin<?> plugin;
    private final boolean down;

    public SplitAction(ViewerPlugin<?> plugin, SplitPosition position) {
      // prevent standard initialization of the action by calling the protected constructor
      super(null);
      this.plugin = plugin;
      this.down = position == SplitPosition.BOTTOM;
      // initialize with a modified action refreshing the enabled state each time the menu is built
      init(new ViewerPlugin.MenuOnlySimpleAction(this, this::refreshEnabled));
      setText(Messages.getString(down ? "ViewerPlugin.split_down" : "ViewerPlugin.split_right"));
      setTooltip(
          Messages.getString(
              down ? "ViewerPlugin.split_down_tip" : "ViewerPlugin.split_right_tip"));
    }

    private void refreshEnabled() {
      setEnabled(currentTabGroup() != null);
    }

    /** Returns the tab group of this tab when it holds more than one viewer. */
    private StackDockStation currentTabGroup() {
      return plugin.getDockable().intern().getDockParent() instanceof StackDockStation stack
              && stack.getDockableCount() > 1
          ? stack
          : null;
    }

    @Override
    protected void action() {
      super.action();
      StackDockStation group = currentTabGroup();
      Rectangle bounds = group == null ? null : areaBounds(group.getComponent());
      if (bounds == null) {
        return;
      }
      if (down) {
        bounds.height /= 2;
        bounds.y += bounds.height;
      } else {
        bounds.width /= 2;
        bounds.x += bounds.width;
      }
      // The group keeps at least one tab, so its bounds survive the move
      SplitDockStation area = workingArea();
      Dockable dockable = plugin.getDockable().intern();
      if (detachTab(area, dockable)) {
        dropTab(area, dockable, splitProperty(area, bounds));
        plugin.setSelectedAndGetFocus();
      }
    }
  }

  /** Lists the other tab groups of the working area so that this tab can be moved into one. */
  private static class MoveToGroupMenu extends CDecorateableAction<CommonSimpleMenuAction> {
    private final ViewerPlugin<?> plugin;
    private final DefaultDockActionSource entries = new DefaultDockActionSource();

    public MoveToGroupMenu(ViewerPlugin<?> plugin) {
      // prevent standard initialization of the action by calling the protected constructor
      super(null);
      this.plugin = plugin;
      // initialize with a modified action rebuilding the entries each time the menu is built
      init(new MenuOnlySimpleMenuAction(this, entries, this::rebuild));
      setText(Messages.getString("ViewerPlugin.move_group"));
      setTooltip(Messages.getString("ViewerPlugin.move_group_tip"));
    }

    /** Rebuilds the entries when the menu is shown, as tab groups appear and disappear. */
    private void rebuild() {
      entries.removeAll();
      buildEntries().forEach(entry -> entries.add(entry.intern()));
      setEnabled(entries.getDockActionCount() > 0);
    }

    /** Returns one entry per tab group of the working area, the group of this tab excluded. */
    private List<CAction> buildEntries() {
      DefaultSingleCDockable dock = plugin.getDockable();
      Dockable current = tabGroupOf(dock.intern());
      Map<Dockable, List<ViewerPlugin<?>>> groups = new LinkedHashMap<>();
      for (ViewerPlugin<?> viewer : GuiUtils.getUICore().getViewerPlugins()) {
        DefaultSingleCDockable other = viewer.getDockable();
        if (other.isVisible() && other.getWorkingArea() == dock.getWorkingArea()) {
          Dockable group = tabGroupOf(other.intern());
          if (group != current) {
            groups.computeIfAbsent(group, g -> new ArrayList<>()).add(viewer);
          }
        }
      }
      return groups.entrySet().stream()
          .map(group -> (CAction) new MoveToGroupAction(plugin, group.getKey(), group.getValue()))
          .toList();
    }
  }

  /** Moves this tab into one specific tab group, named after the viewers it holds. */
  private static class MoveToGroupAction extends CButton {
    private final ViewerPlugin<?> plugin;
    private final Dockable group;

    public MoveToGroupAction(
        ViewerPlugin<?> plugin, Dockable group, List<ViewerPlugin<?>> members) {
      // Where the group is on screen identifies it, which its name alone cannot do
      super(menuLabel(groupLabel(members)), areaIcon(group.getComponent()));
      this.plugin = plugin;
      this.group = group;
    }

    @Override
    protected void action() {
      super.action();
      SplitDockStation area = workingArea();
      Dockable dockable = plugin.getDockable().intern();
      StackDockStation stack = group instanceof StackDockStation s ? s : null;
      // A tab alone in its area is reached through the tree of the working area rather than
      // through the bounds of its component, which are not laid out again before the drop
      DockableProperty location =
          stack == null ? DockUtilities.getPropertyChain(area, group) : null;
      if (!detachTab(area, dockable)) {
        return;
      }
      if (stack == null) {
        dropTab(area, dockable, location);
      } else {
        stack.drop(dockable);
      }
      plugin.setSelectedAndGetFocus();
    }
  }

  /** Menu action which only shows up in menus and rebuilds its entries before being displayed. */
  private static class MenuOnlySimpleMenuAction extends CommonSimpleMenuAction {
    private final Runnable beforeCreateView;

    public MenuOnlySimpleMenuAction(
        CAction action, DockActionSource menu, Runnable beforeCreateView) {
      super(action, menu);
      this.beforeCreateView = beforeCreateView;
    }

    @Override
    public <V> V createView(
        ViewTarget<V> target, ActionViewConverter converter, Dockable dockable) {
      if (ViewTarget.TITLE == target) {
        return null;
      }
      beforeCreateView.run();
      return super.createView(target, converter, dockable);
    }
  }
}
