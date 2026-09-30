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

import java.awt.Frame;
import java.awt.Rectangle;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.Icon;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.ui.docking.ws.TabGroup;
import org.weasis.core.ui.docking.ws.WorkspaceManager;
import org.weasis.core.ui.editor.SplitLayout;
import org.weasis.core.ui.editor.SplitPosition;

/**
 * Entries of the contextual menu of a tab that change where that tab sits in the docking area:
 * splitting its tab group in two, as when the 3D viewer is opened from an MPR container, or moving
 * it into another tab group.
 *
 * <p>Every placement re-docks the viewer relative to a viewer of the target group, either beside it
 * to split that group, or in its center to stack the tab into it.
 */
final class TabPlacement {

  private static final int MAX_MENU_LABEL_LENGTH = 60;

  private TabPlacement() {}

  /** Adds the placement entries to the contextual menu of the tab of {@code plugin}. */
  static void addMenuItems(JPopupMenu menu, ViewerPlugin<?> plugin) {
    JMenuItem splitRight = splitItem(plugin, SplitPosition.RIGHT);
    JMenuItem splitDown = splitItem(plugin, SplitPosition.BOTTOM);
    JMenu moveToGroup = new JMenu(Messages.getString("ViewerPlugin.move_group"));
    moveToGroup.setToolTipText(Messages.getString("ViewerPlugin.move_group_tip"));
    JMenuItem openInNewWindow = new JMenuItem(Messages.getString("ViewerPlugin.open_win"));
    openInNewWindow.setToolTipText(Messages.getString("ViewerPlugin.open_win_tip"));
    openInNewWindow.addActionListener(e -> workspace().detach(plugin, null));
    menu.add(splitRight);
    menu.add(splitDown);
    menu.add(moveToGroup);
    menu.add(openInNewWindow);

    // The menu is built once, so the entries are refreshed each time it is shown: tab groups appear
    // and disappear as the user moves tabs around.
    menu.addPopupMenuListener(
        new PopupMenuListener() {
          @Override
          public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
            boolean splittable = tabGroup(plugin) != null;
            splitRight.setEnabled(splittable);
            splitDown.setEnabled(splittable);
            openInNewWindow.setEnabled(plugin.isDetachable());
            moveToGroup.removeAll();
            for (TabGroup group : otherGroups(plugin)) {
              moveToGroup.add(moveItem(plugin, group));
            }
            moveToGroup.setEnabled(moveToGroup.getItemCount() > 0);
          }

          @Override
          public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            // Nothing to do
          }

          @Override
          public void popupMenuCanceled(PopupMenuEvent e) {
            // Nothing to do
          }
        });
  }

  private static WorkspaceManager workspace() {
    return GuiUtils.getUICore().getWorkspaceManager();
  }

  private static JMenuItem splitItem(ViewerPlugin<?> plugin, SplitPosition position) {
    boolean down = position == SplitPosition.BOTTOM;
    JMenuItem item =
        new JMenuItem(
            Messages.getString(down ? "ViewerPlugin.split_down" : "ViewerPlugin.split_right"));
    item.setToolTipText(
        Messages.getString(down ? "ViewerPlugin.split_down_tip" : "ViewerPlugin.split_right_tip"));
    item.addActionListener(e -> split(plugin, position));
    return item;
  }

  private static JMenuItem moveItem(ViewerPlugin<?> plugin, TabGroup group) {
    List<ViewerPlugin<?>> members = viewers(group);
    String label = groupLabel(members);
    if (SwingUtilities.getWindowAncestor(plugin) != group.getWorkspace().getWindow()
        && group.getWorkspace().getWindow() instanceof Frame frame) {
      label += " — " + frame.getTitle();
    }
    // Where the group is on screen identifies it, which its name alone cannot do
    JMenuItem item = new JMenuItem(menuLabel(label), areaIcon(group));
    item.addActionListener(
        e -> {
          workspace().stack(plugin, members.getFirst());
          plugin.setSelectedAndGetFocus();
        });
    return item;
  }

  /** Splits the tab group holding this tab in two, this tab taking the new half. */
  private static void split(ViewerPlugin<?> plugin, SplitPosition position) {
    TabGroup group = tabGroup(plugin);
    if (group == null) {
      return;
    }
    // The group keeps at least one tab, so the split can be anchored on a viewer staying there
    viewers(group).stream()
        .filter(viewer -> viewer != plugin)
        .findFirst()
        .ifPresent(
            sibling -> {
              workspace().split(plugin, sibling, position.toDockSide(), SplitLayout.DEFAULT_RATIO);
              plugin.setSelectedAndGetFocus();
            });
  }

  /** Returns the tab group of {@code plugin} when it holds more than one viewer. */
  private static TabGroup tabGroup(ViewerPlugin<?> plugin) {
    return workspace().groupOf(plugin).filter(group -> group.getViewCount() > 1).orElse(null);
  }

  /** Returns every other tab group holding viewers, in this window and in floating ones. */
  private static List<TabGroup> otherGroups(ViewerPlugin<?> plugin) {
    WorkspaceManager manager = workspace();
    TabGroup current = manager.groupOf(plugin).orElse(null);
    return manager.getWindows().stream()
        .flatMap(window -> manager.getTabGroups(window).stream())
        .filter(group -> group != current && !viewers(group).isEmpty())
        .toList();
  }

  private static List<ViewerPlugin<?>> viewers(TabGroup group) {
    return group.getViews().stream()
        .filter(ViewerPlugin.class::isInstance)
        .<ViewerPlugin<?>>map(view -> (ViewerPlugin<?>) view)
        .toList();
  }

  /** Returns a miniature of the docking area locating {@code group}, or {@code null}. */
  private static Icon areaIcon(TabGroup group) {
    var area = group.getWorkspace();
    if (!group.isShowing() || area.getWidth() <= 0 || area.getHeight() <= 0) {
      return null;
    }
    double width = area.getWidth();
    double height = area.getHeight();
    Rectangle bounds = group.getBoundsInRoot();
    return new AreaIcon(
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
}
