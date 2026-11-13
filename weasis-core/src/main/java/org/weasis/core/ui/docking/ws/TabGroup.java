/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.docking.ws;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.weasis.core.Messages;
import org.weasis.core.ui.docking.RoundedTopBorder;
import org.weasis.core.ui.docking.ws.drag.TabDragController;

/**
 * Leaf of the workspace split tree: a tabbed group of {@link WorkspaceView}s. Structure is only
 * changed through the {@link WorkspaceManager}.
 */
public final class TabGroup extends JTabbedPane {

  private final WorkspaceManager manager;
  private final Workspace workspace;
  private final List<WorkspaceView> views = new ArrayList<>();

  /** The gap between the frame of a group and the edge of its area (unscaled pixels). */
  static final int GROUP_MARGIN = 2;

  /** The widest a tab gets before its title is cut with an ellipsis (unscaled pixels). */
  static final int MAX_TAB_WIDTH = 240;

  private static final String BASE_STYLE =
      "cardTabArc: 14; tabArc: 10; cardTabSelectionHeight: 2;" // NON-NLS
          + " tabInsets: 4,12,4,8; tabAreaInsets: 3,4,0,4; selectedInsets: 0,0,0,0;"; // NON-NLS

  /** Whether this group holds the selected view of the workspace: only it shows the accent. */
  private boolean active = true;

  /** False while the super constructor runs updateUI, before the fields are set. */
  private boolean styleReady;

  TabGroup(WorkspaceManager manager, Workspace workspace) {
    this.manager = manager;
    this.workspace = workspace;
    setTabLayoutPolicy(SCROLL_TAB_LAYOUT);
    setFocusable(false);
    // card tabs: the selected tab is a card with rounded top corners joined to its content, as the
    // title bars of the side slots; the others show a rounded background when hovered
    putClientProperty(
        FlatClientProperties.TABBED_PANE_TAB_TYPE, FlatClientProperties.TABBED_PANE_TAB_TYPE_CARD);
    putClientProperty(FlatClientProperties.TABBED_PANE_MAXIMUM_TAB_WIDTH, MAX_TAB_WIDTH);
    // tabs that do not fit: a chevron at the end lists them, no scroll arrows
    putClientProperty(
        FlatClientProperties.TABBED_PANE_SCROLL_BUTTONS_POLICY,
        FlatClientProperties.TABBED_PANE_POLICY_NEVER);
    putClientProperty(
        FlatClientProperties.TABBED_PANE_TABS_POPUP_POLICY,
        FlatClientProperties.TABBED_PANE_POLICY_AS_NEEDED);
    styleReady = true;
    applyStyle();
    putClientProperty(FlatClientProperties.TABBED_PANE_TAB_CLOSABLE, true);
    putClientProperty(
        FlatClientProperties.TABBED_PANE_TAB_CLOSE_CALLBACK,
        (java.util.function.BiConsumer<JTabbedPane, Integer>)
            (pane, index) -> {
              WorkspaceView view = viewAt(index);
              if (view != null && view.isClosable()) {
                view.requestClose();
              }
            });
    addChangeListener(e -> manager.tabSelectionChanged(this));
    TabDragController.install(manager, this);
    addTabAreaListener(
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent e) {
            maybeShowMenu(e);
          }

          @Override
          public void mouseReleased(MouseEvent e) {
            maybeShowMenu(e);
            if (SwingUtilities.isMiddleMouseButton(e)) {
              // middle click closes the tab, as in a browser
              WorkspaceView view = viewAt(indexAtLocation(e.getX(), e.getY()));
              if (view != null && view.isClosable()) {
                view.requestClose();
              }
            }
          }

          @Override
          public void mouseClicked(MouseEvent e) {
            if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
              // double click maximizes the view in its window, again restores it
              WorkspaceView view = viewAt(indexAtLocation(e.getX(), e.getY()));
              if (view != null) {
                if (manager.isMaximized(view)) {
                  manager.restore(view);
                } else {
                  manager.maximize(view);
                }
              }
            }
          }
        });
  }

  /**
   * Registers a listener for mouse events on the tab strip. Listeners go on the tabbed pane itself:
   * the scroll-layout viewport and its tab panel have no mouse listeners, so AWT routes events to
   * this pane, where the look-and-feel handles selection, close buttons and rollover. Adding
   * listeners to those children would make them the dispatch target and starve the L&F.
   */
  public void addTabAreaListener(MouseAdapter listener) {
    addMouseListener(listener);
    addMouseMotionListener(listener);
  }

  private void maybeShowMenu(MouseEvent e) {
    if (!e.isPopupTrigger()) {
      return;
    }
    int index = indexAtLocation(e.getX(), e.getY());
    WorkspaceView view = viewAt(index);
    if (view != null) {
      setSelectedIndex(index);
      JPopupMenu menu = new JPopupMenu();
      view.populateTabMenu(menu);
      if (workspace.getWindow() instanceof DetachedFrame) {
        if (menu.getComponentCount() > 0) {
          menu.addSeparator();
        }
        JMenuItem moveToMain = new JMenuItem(Messages.getString("Workspace.move_main"));
        moveToMain.addActionListener(evt -> manager.open(view));
        menu.add(moveToMain);
      }
      if (menu.getComponentCount() > 0) {
        menu.show(this, e.getX(), e.getY());
      }
    }
  }

  public Workspace getWorkspace() {
    return workspace;
  }

  void addView(WorkspaceView view, int index) {
    int i = index < 0 || index > views.size() ? views.size() : index;
    views.add(i, view);
    view.component()
        .putClientProperty(FlatClientProperties.TABBED_PANE_TAB_CLOSABLE, view.isClosable());
    insertTab(view.tabTitle(), view.tabIcon(), view.component(), view.tabTooltip(), i);
  }

  void removeView(WorkspaceView view) {
    int index = views.indexOf(view);
    if (index >= 0) {
      views.remove(index);
      removeTabAt(index);
    }
  }

  void moveView(WorkspaceView view, int index) {
    int from = views.indexOf(view);
    if (from < 0) {
      return;
    }
    int to = index < 0 || index > views.size() ? views.size() : index;
    if (to > from) {
      to--; // removing the tab shifts the following indices
    }
    if (to == from) {
      return;
    }
    views.remove(from);
    removeTabAt(from);
    views.add(to, view);
    insertTab(view.tabTitle(), view.tabIcon(), view.component(), view.tabTooltip(), to);
    setSelectedIndex(to);
  }

  private WorkspaceView viewAt(int index) {
    return index >= 0 && index < views.size() ? views.get(index) : null;
  }

  public List<WorkspaceView> getViews() {
    return List.copyOf(views);
  }

  public int getViewCount() {
    return views.size();
  }

  public boolean containsView(WorkspaceView view) {
    return views.contains(view);
  }

  public WorkspaceView getSelectedView() {
    return viewAt(getSelectedIndexSafe());
  }

  private int getSelectedIndexSafe() {
    int index = getSelectedIndex();
    return index < getTabCount() ? index : -1;
  }

  void selectView(WorkspaceView view) {
    int index = views.indexOf(view);
    if (index >= 0 && index != getSelectedIndex()) {
      setSelectedIndex(index);
    }
  }

  /**
   * Marks this group as the one holding the selected view, or not. The selected tab of the active
   * group carries the accent (top line and title color); the selected tab of another group is drawn
   * in neutral colors, so the view that the next action targets is clear across splits and windows.
   */
  public void setActive(boolean active) {
    if (this.active != active) {
      this.active = active;
      applyStyle();
    }
  }

  public boolean isActive() {
    return active;
  }

  @Override
  public void updateUI() {
    super.updateUI();
    if (styleReady) {
      applyStyle(); // the colors come from the look and feel
    }
  }

  private void applyStyle() {
    Color line;
    Color title;
    if (active) {
      line = UIManager.getColor("TabbedPane.underlineColor"); // NON-NLS
      title = UIManager.getColor("TabbedPane.selectedForeground"); // NON-NLS
    } else {
      line = UIManager.getColor("Component.borderColor"); // NON-NLS
      title = UIManager.getColor("TabbedPane.foreground"); // NON-NLS
    }
    StringBuilder style = new StringBuilder(BASE_STYLE);
    if (line != null) {
      // the card line is painted with the inactive color while the group has no focus
      style.append(" inactiveUnderlineColor: ").append(hex(line)).append(';'); // NON-NLS
      style.append(" underlineColor: ").append(hex(line)).append(';'); // NON-NLS
    }
    if (title != null) {
      style.append(" selectedForeground: ").append(hex(title)).append(';'); // NON-NLS
    }
    putClientProperty(FlatClientProperties.STYLE, style.toString());
    // framed as the slots of the sides, a small margin apart from the neighbor groups
    setBorder(new RoundedTopBorder(GROUP_MARGIN));
  }

  private static String hex(Color c) {
    return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue()); // NON-NLS
  }

  /** The full title of a tab cut at {@link #MAX_TAB_WIDTH}, when the view gives no tooltip. */
  @Override
  public String getToolTipText(MouseEvent event) {
    String tip = super.getToolTipText(event);
    if (tip != null) {
      return tip;
    }
    int index = indexAtLocation(event.getX(), event.getY());
    if (index < 0) {
      return null;
    }
    Rectangle bounds = getBoundsAt(index);
    boolean cut = bounds != null && bounds.width >= UIScale.scale(MAX_TAB_WIDTH) - 1;
    return cut ? getTitleAt(index) : null;
  }

  /** Refreshes the tab title, icon and tooltip of the given view. */
  public void updateTabInfo(WorkspaceView view) {
    int index = views.indexOf(view);
    if (index >= 0) {
      setTitleAt(index, view.tabTitle());
      setIconAt(index, view.tabIcon());
      setToolTipTextAt(index, view.tabTooltip());
    }
  }

  /** Bounds of this group in the coordinate space of its workspace root. */
  public Rectangle getBoundsInRoot() {
    return SwingUtilities.convertRectangle(this, new Rectangle(getSize()), workspace);
  }
}
