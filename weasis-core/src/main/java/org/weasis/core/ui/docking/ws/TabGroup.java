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
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import org.weasis.core.Messages;
import org.weasis.core.ui.docking.ws.drag.TabDragController;

/**
 * Leaf of the workspace split tree: a tabbed group of {@link WorkspaceView}s. Structure is only
 * changed through the {@link WorkspaceManager}.
 */
public final class TabGroup extends JTabbedPane {

  private final WorkspaceManager manager;
  private final Workspace workspace;
  private final List<WorkspaceView> views = new ArrayList<>();
  private final List<MouseAdapter> tabAreaListeners = new ArrayList<>();
  private final Set<Component> forwardTargets = Collections.newSetFromMap(new WeakHashMap<>());

  TabGroup(WorkspaceManager manager, Workspace workspace) {
    this.manager = manager;
    this.workspace = workspace;
    setTabLayoutPolicy(SCROLL_TAB_LAYOUT);
    setFocusable(false);
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
          }
        });
    installTabAreaForwarders();
  }

  /**
   * Registers a listener for mouse events on the tab strip. With {@code SCROLL_TAB_LAYOUT} the tabs
   * live inside a nested viewport child that swallows every mouse event, so listeners cannot be
   * added to the tabbed pane directly: events are captured on the tab-strip child, converted to
   * this pane's coordinate space and forwarded.
   */
  public void addTabAreaListener(MouseAdapter listener) {
    tabAreaListeners.add(listener);
  }

  private void installTabAreaForwarders() {
    attachForwarder(this);
    for (Component child : getComponents()) {
      if (child instanceof JViewport viewport && viewport.getView() != null) {
        attachForwarder(viewport.getView());
      }
    }
  }

  private void attachForwarder(Component target) {
    if (!forwardTargets.add(target)) {
      return;
    }
    MouseAdapter forwarder =
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent e) {
            forward(e, MouseAdapter::mousePressed);
          }

          @Override
          public void mouseReleased(MouseEvent e) {
            forward(e, MouseAdapter::mouseReleased);
          }

          @Override
          public void mouseClicked(MouseEvent e) {
            forward(e, MouseAdapter::mouseClicked);
          }

          @Override
          public void mouseDragged(MouseEvent e) {
            forward(e, MouseAdapter::mouseDragged);
          }

          @Override
          public void mouseMoved(MouseEvent e) {
            forward(e, MouseAdapter::mouseMoved);
          }
        };
    target.addMouseListener(forwarder);
    target.addMouseMotionListener(forwarder);
  }

  private void forward(MouseEvent e, BiConsumer<MouseAdapter, MouseEvent> method) {
    MouseEvent converted =
        e.getComponent() == this ? e : SwingUtilities.convertMouseEvent(e.getComponent(), e, this);
    for (MouseAdapter listener : tabAreaListeners) {
      method.accept(listener, converted);
    }
  }

  @Override
  public void updateUI() {
    super.updateUI();
    if (forwardTargets != null) {
      // the UI recreates the tab-strip viewport; re-hook the event forwarding
      SwingUtilities.invokeLater(this::installTabAreaForwarders);
    }
  }

  @Override
  public void addNotify() {
    super.addNotify();
    installTabAreaForwarders();
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
