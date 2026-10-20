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

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import org.weasis.core.ui.docking.ws.snapshot.ViewRestorer;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot;

/**
 * The workspace engine: tab groups and splits of viewer panels in the central area of each window.
 * Views are direct references, never registered; all operations must run on the EDT.
 */
public final class WorkspaceManager {

  private static final double DEFAULT_RATIO = 0.5;

  private final Window mainWindow;
  private final Workspace mainWorkspace;
  private final Map<Window, Workspace> workspaces = new LinkedHashMap<>();
  private final Map<WorkspaceView, TabGroup> viewGroups = new IdentityHashMap<>();
  private final List<WorkspaceListener> listeners = new CopyOnWriteArrayList<>();
  private boolean mutating;
  private boolean dragEnabled = true;
  private WorkspaceView selectedView;

  public WorkspaceManager(Window mainWindow) {
    this.mainWindow = mainWindow;
    this.mainWorkspace = new Workspace(mainWindow);
    addWorkspace(mainWindow, mainWorkspace);
  }

  public Window mainWindow() {
    return mainWindow;
  }

  public Workspace mainWorkspace() {
    return mainWorkspace;
  }

  /** The view the application has selected, while it is showing. */
  public Optional<WorkspaceView> selectedView() {
    return selectedView != null && viewGroups.containsKey(selectedView)
        ? Optional.of(selectedView)
        : Optional.empty();
  }

  /**
   * The workspace of the selected view, which is where a new view opens: the active window; the
   * main workspace when nothing is selected.
   */
  public Workspace activeWorkspace() {
    return selectedView().map(view -> viewGroups.get(view).getWorkspace()).orElse(mainWorkspace);
  }

  public boolean isDragEnabled() {
    return dragEnabled;
  }

  /** Enables or disables interactive tab dragging (menu-based placement stays available). */
  public void setDragEnabled(boolean dragEnabled) {
    this.dragEnabled = dragEnabled;
  }

  public boolean isShowing(WorkspaceView view) {
    return viewGroups.containsKey(view);
  }

  public Optional<TabGroup> groupOf(WorkspaceView view) {
    return Optional.ofNullable(viewGroups.get(view));
  }

  public List<TabGroup> getTabGroups(Window window) {
    Workspace workspace = workspaces.get(window);
    return workspace == null ? List.of() : List.copyOf(workspace.groups());
  }

  /** All windows hosting a workspace: the main window first, then the floating ones. */
  public List<Window> getWindows() {
    return List.copyOf(workspaces.keySet());
  }

  /** All docked views, in workspace and tab order. */
  /** The open view with this {@link WorkspaceView#viewId()}. */
  public Optional<WorkspaceView> findView(String viewId) {
    return getViews().stream().filter(v -> v.viewId().equals(viewId)).findFirst();
  }

  public List<WorkspaceView> getViews() {
    List<WorkspaceView> all = new ArrayList<>();
    for (Workspace workspace : workspaces.values()) {
      for (TabGroup group : workspace.groups()) {
        all.addAll(group.getViews());
      }
    }
    return all;
  }

  /**
   * Docks the view into the main workspace: fills an empty area, else stacks in its first group.
   */
  public void open(WorkspaceView view) {
    openIn(mainWorkspace, view);
  }

  /** Docks the view into the {@link #activeWorkspace() active workspace} the same way. */
  public void openInActive(WorkspaceView view) {
    openIn(activeWorkspace(), view);
  }

  private void openIn(Workspace workspace, WorkspaceView view) {
    mutate(
        () -> {
          undockInternal(view);
          workspace.restoreMaximized();
          TabGroup target;
          if (workspace.isEmpty()) {
            target = newGroup(workspace);
            workspace.setRoot(target);
          } else {
            target = workspace.groups().getFirst();
          }
          dockInto(view, target, -1);
        });
    fireIfSelected(view);
  }

  /** Stacks the view as a tab in the group of the anchor view. */
  public void stack(WorkspaceView view, WorkspaceView anchor) {
    TabGroup group = viewGroups.get(anchor);
    if (view == anchor || group == null) {
      if (group == null) {
        open(view);
      }
      return;
    }
    if (group.containsView(view)) {
      select(view);
      return;
    }
    mutate(
        () -> {
          undockInternal(view);
          group.getWorkspace().restoreMaximized();
          dockInto(view, group, -1);
        });
    fireIfSelected(view);
  }

  /** Stacks the view as a tab of the given group at {@code index} (-1 appends). */
  public void stack(WorkspaceView view, TabGroup group, int index) {
    if (!group.getWorkspace().groups().contains(group)) {
      return;
    }
    if (viewGroups.get(view) == group) {
      moveTab(view, index);
      return;
    }
    mutate(
        () -> {
          undockInternal(view);
          group.getWorkspace().restoreMaximized();
          dockInto(view, group, index);
        });
    fireIfSelected(view);
  }

  /** Moves the view's tab to {@code index} within its own group (-1 moves to the end). */
  public void moveTab(WorkspaceView view, int index) {
    TabGroup group = viewGroups.get(view);
    if (group != null) {
      mutate(() -> group.moveView(view, index));
      fireIfSelected(view);
    }
  }

  /** Splits the anchor's group, the view taking the {@code side} half with the given ratio. */
  public void split(WorkspaceView view, WorkspaceView anchor, DockSide side, double ratio) {
    TabGroup anchorGroup = viewGroups.get(anchor);
    if (view == anchor) {
      return;
    }
    if (anchorGroup == null) {
      open(view);
      return;
    }
    double prop = ratio > 0 && ratio < 1 ? ratio : DEFAULT_RATIO;
    mutate(
        () -> {
          undockInternal(view);
          Workspace workspace = anchorGroup.getWorkspace();
          workspace.restoreMaximized();

          Container parent = anchorGroup.getParent();
          boolean wasLeft = parent instanceof JSplitPane sp && sp.getLeftComponent() == anchorGroup;

          TabGroup newGroup = newGroup(workspace);
          dockInto(view, newGroup, -1);

          JSplitPane splitPane = new JSplitPane(side.splitOrientation());
          splitPane.setContinuousLayout(true);
          splitPane.setResizeWeight(0.5);
          splitPane.setBorder(null);
          // assigning the children removes the anchor group from its previous parent slot
          splitPane.setLeftComponent(side.newGroupFirst() ? newGroup : anchorGroup);
          splitPane.setRightComponent(side.newGroupFirst() ? anchorGroup : newGroup);

          if (parent instanceof JSplitPane sp) {
            if (wasLeft) {
              sp.setLeftComponent(splitPane);
            } else {
              sp.setRightComponent(splitPane);
            }
            sp.revalidate();
          } else {
            workspace.setRoot(splitPane);
          }
          double dividerProportion = side.newGroupFirst() ? prop : 1 - prop;
          SwingUtilities.invokeLater(() -> splitPane.setDividerLocation(dividerProportion));
        });
    fireIfSelected(view);
  }

  /** Removes the view from its tab group, collapsing empty groups and windows. */
  public void close(WorkspaceView view) {
    mutate(() -> undockInternal(view));
  }

  /** Brings the view's tab to the front, raising its window when needed. */
  public void select(WorkspaceView view) {
    TabGroup group = viewGroups.get(view);
    if (group == null) {
      return;
    }
    Workspace workspace = group.getWorkspace();
    if (workspace.getMaximizedView() != null && workspace.getMaximizedView() != view) {
      workspace.restoreMaximized();
    }
    Window window = workspace.getWindow();
    // the activation that toFront triggers must find this view, not the previous one
    workspace.setLastSelected(view);
    if (window != null && !window.isFocused()) {
      window.toFront();
    }
    boolean alreadySelected = group.getSelectedView() == view;
    group.selectView(view); // fires viewSelected through the tab change listener
    if (alreadySelected) {
      fireViewSelected(view, window);
    }
  }

  /** Detaches the view into its own window; {@code screenBounds} may be null for a default size. */
  public DetachedFrame detach(WorkspaceView view, Rectangle screenBounds) {
    DetachedFrame frame = new DetachedFrame(this, view.tabTitle());
    mutate(
        () -> {
          undockInternal(view);
          Workspace workspace = frame.getWorkspace();
          addWorkspace(frame, workspace);
          TabGroup group = newGroup(workspace);
          workspace.setRoot(group);
          dockInto(view, group, -1);
          frame.setBounds(screenBounds != null ? screenBounds : defaultDetachedBounds());
          frame.setVisible(true);
        });
    fireIfSelected(view);
    return frame;
  }

  private Rectangle defaultDetachedBounds() {
    Point location = mainWindow == null ? new Point() : mainWindow.getLocation();
    return new Rectangle(location.x + 60, location.y + 60, 800, 600);
  }

  public boolean isMaximized(WorkspaceView view) {
    TabGroup group = viewGroups.get(view);
    return group != null && group.getWorkspace().getMaximizedView() == view;
  }

  public void maximize(WorkspaceView view) {
    TabGroup group = viewGroups.get(view);
    if (group != null) {
      group.getWorkspace().showMaximized(view);
    }
  }

  public void restore(WorkspaceView view) {
    if (isMaximized(view)) {
      viewGroups.get(view).getWorkspace().restoreMaximized();
    }
  }

  /** Refreshes the tab title, icon and tooltip of the view. */
  public void updateTabInfo(WorkspaceView view) {
    TabGroup group = viewGroups.get(view);
    if (group != null) {
      group.updateTabInfo(view);
    }
  }

  public void addListener(WorkspaceListener listener) {
    listeners.add(listener);
  }

  public void removeListener(WorkspaceListener listener) {
    listeners.remove(listener);
  }

  /** Disposes every detached window; called when the application closes. */
  public void disposeAll() {
    for (Window window : List.copyOf(workspaces.keySet())) {
      if (window instanceof DetachedFrame frame) {
        workspaces.remove(frame);
        frame.dispose();
      }
    }
    viewGroups.clear();
    mainWorkspace.groups().clear();
    mainWorkspace.setRoot(null);
  }

  // ---- snapshots ----------------------------------------------------------

  /** Captures the layout of every window: screens, bounds, split trees and view descriptors. */
  public WorkspaceSnapshot capture() {
    List<WorkspaceSnapshot.WindowSnapshot> windows = new ArrayList<>();
    for (Workspace workspace : workspaces.values()) {
      if (workspace.isEmpty()) {
        continue;
      }
      Window window = workspace.getWindow();
      String screenId = null;
      Rectangle bounds = null;
      if (window != null) {
        bounds = window.getBounds();
        GraphicsConfiguration gc = window.getGraphicsConfiguration();
        if (gc != null) {
          screenId = gc.getDevice().getIDstring();
        }
      }
      WorkspaceView maximized = workspace.getMaximizedView();
      windows.add(
          new WorkspaceSnapshot.WindowSnapshot(
              screenId,
              bounds,
              workspace == mainWorkspace,
              captureNode(workspace.getRoot()),
              maximized != null ? maximized.viewId() : null));
    }
    return new WorkspaceSnapshot(WorkspaceSnapshot.CURRENT_VERSION, windows);
  }

  private WorkspaceSnapshot.Node captureNode(Component component) {
    if (component == null) {
      return null; // a window whose last view was closed holds no tree
    }
    if (component instanceof TabGroup group) {
      List<WorkspaceSnapshot.ViewDescriptor> views =
          group.getViews().stream()
              .map(v -> new WorkspaceSnapshot.ViewDescriptor(v.viewId(), v.typeId(), v.saveState()))
              .toList();
      return new WorkspaceSnapshot.GroupNode(views, Math.max(0, group.getSelectedIndex()));
    }
    JSplitPane split = (JSplitPane) component;
    boolean vertical = split.getOrientation() == JSplitPane.VERTICAL_SPLIT;
    int size = (vertical ? split.getHeight() : split.getWidth()) - split.getDividerSize();
    double ratio =
        size > 0 ? Math.clamp(split.getDividerLocation() / (double) size, 0.1, 0.9) : 0.5;
    return new WorkspaceSnapshot.SplitNode(
        vertical,
        ratio,
        captureNode(split.getLeftComponent()),
        captureNode(split.getRightComponent()));
  }

  /**
   * Rebuilds the layout from a snapshot. Every currently docked view is undocked first (not
   * disposed); the restorer resolves each descriptor to a live or recreated view, and unresolved
   * descriptors are skipped so the layout collapses gracefully.
   */
  public void restore(WorkspaceSnapshot snapshot, ViewRestorer restorer) {
    mutate(
        () -> {
          for (WorkspaceView view : getViews()) {
            undockInternal(view);
          }
          for (WorkspaceSnapshot.WindowSnapshot win : snapshot.windows()) {
            Workspace target;
            DetachedFrame frame = null;
            if (win.main()) {
              target = mainWorkspace;
            } else {
              frame =
                  new DetachedFrame(this, System.getProperty("weasis.name", "Weasis")); // NON-NLS
              target = frame.getWorkspace();
              addWorkspace(frame, target);
            }
            Component root = restoreNode(win.root(), target, restorer);
            if (root == null) {
              if (frame != null) {
                workspaces.remove(frame);
                frame.dispose();
              }
              continue;
            }
            target.setRoot(root);
            if (frame != null) {
              frame.setBounds(mapToAvailableScreen(win));
              frame.setVisible(true);
            }
            if (win.maximizedViewId() != null) {
              target.groups().stream()
                  .flatMap(g -> g.getViews().stream())
                  .filter(v -> win.maximizedViewId().equals(v.viewId()))
                  .findFirst()
                  .ifPresent(target::showMaximized);
            }
          }
        });
    for (Workspace workspace : workspaces.values()) {
      if (!workspace.isEmpty()) {
        fireIfSelected(workspace.groups().getFirst().getSelectedView());
      }
    }
  }

  private Component restoreNode(
      WorkspaceSnapshot.Node node, Workspace workspace, ViewRestorer restorer) {
    return switch (node) {
      case WorkspaceSnapshot.GroupNode(
              List<WorkspaceSnapshot.ViewDescriptor> views,
              int selected) -> {
        TabGroup group = null;
        for (WorkspaceSnapshot.ViewDescriptor descriptor : views) {
          WorkspaceView view = restorer.recreate(descriptor).orElse(null);
          if (view != null) {
            if (viewGroups.containsKey(view)) {
              undockInternal(view); // a view can appear only once
            }
            if (group == null) {
              group = newGroup(workspace);
            }
            dockInto(view, group, -1);
          }
        }
        if (group != null && selected >= 0 && selected < group.getViewCount()) {
          group.setSelectedIndex(selected);
        }
        yield group;
      }
      case WorkspaceSnapshot.SplitNode(boolean vertical, double ratio, var first, var second) -> {
        Component a = restoreNode(first, workspace, restorer);
        Component b = restoreNode(second, workspace, restorer);
        if (a == null || b == null) {
          yield a != null ? a : b;
        }
        JSplitPane split =
            new JSplitPane(
                vertical ? JSplitPane.VERTICAL_SPLIT : JSplitPane.HORIZONTAL_SPLIT, a, b);
        split.setContinuousLayout(true);
        split.setResizeWeight(0.5);
        split.setBorder(null);
        SwingUtilities.invokeLater(() -> split.setDividerLocation(ratio));
        yield split;
      }
      case null -> null;
    };
  }

  /** Maps a saved window onto an available screen, falling back near the main window. */
  private Rectangle mapToAvailableScreen(WorkspaceSnapshot.WindowSnapshot win) {
    Rectangle bounds = win.bounds() != null ? new Rectangle(win.bounds()) : null;
    if (bounds == null) {
      return defaultDetachedBounds();
    }
    try {
      for (GraphicsDevice device :
          GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
        if (device.getIDstring().equals(win.screenId())
            || device.getDefaultConfiguration().getBounds().intersects(bounds)) {
          return bounds;
        }
      }
    } catch (HeadlessException e) {
      return bounds;
    }
    Rectangle fallback = defaultDetachedBounds();
    bounds.setLocation(fallback.x, fallback.y);
    return bounds;
  }

  // ---- internal mechanics -------------------------------------------------

  private TabGroup newGroup(Workspace workspace) {
    TabGroup group = new TabGroup(this, workspace);
    workspace.groups().add(group);
    return group;
  }

  private void dockInto(WorkspaceView view, TabGroup group, int index) {
    group.addView(view, index);
    viewGroups.put(view, group);
  }

  private void undockInternal(WorkspaceView view) {
    TabGroup group = viewGroups.remove(view);
    if (group == null) {
      return;
    }
    Workspace workspace = group.getWorkspace();
    if (workspace.getMaximizedView() == view) {
      workspace.restoreMaximized();
    }
    group.removeView(view);
    if (group.getViewCount() == 0) {
      collapse(group);
    }
  }

  private void collapse(TabGroup group) {
    Workspace workspace = group.getWorkspace();
    workspace.groups().remove(group);
    Container parent = group.getParent();
    if (parent instanceof JSplitPane split) {
      Component sibling =
          split.getLeftComponent() == group ? split.getRightComponent() : split.getLeftComponent();
      split.remove(sibling);
      replaceChild(split, sibling, workspace);
    } else {
      workspace.setRoot(null);
    }
    if (workspace.isEmpty()) {
      fireWorkspaceEmptied(workspace.getWindow());
      if (workspace.getWindow() instanceof DetachedFrame frame) {
        workspaces.remove(frame);
        frame.dispose();
      }
    }
  }

  private static void replaceChild(Component old, Component replacement, Workspace workspace) {
    Container parent = old.getParent();
    if (parent instanceof JSplitPane split) {
      if (split.getLeftComponent() == old) {
        split.setLeftComponent(replacement);
      } else {
        split.setRightComponent(replacement);
      }
      split.revalidate();
    } else {
      workspace.setRoot(replacement);
    }
  }

  private void mutate(Runnable operation) {
    mutating = true;
    try {
      operation.run();
    } finally {
      mutating = false;
    }
  }

  void tabSelectionChanged(TabGroup group) {
    if (mutating) {
      return;
    }
    WorkspaceView selected = group.getSelectedView();
    if (selected != null) {
      fireViewSelected(selected, group.getWorkspace().getWindow());
    }
  }

  private void fireIfSelected(WorkspaceView view) {
    TabGroup group = viewGroups.get(view);
    if (group != null && group.getSelectedView() == view) {
      fireViewSelected(view, group.getWorkspace().getWindow());
    }
  }

  private void addWorkspace(Window window, Workspace workspace) {
    workspaces.put(window, workspace);
    if (window != null) {
      window.addWindowFocusListener(
          new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
              activateWindow(workspace);
            }
          });
    }
  }

  /**
   * Activating a window selects the view that window has selected (its last selected view, else the
   * selected view of its first group), so that the toolbars and the tools follow the user's eyes;
   * nothing happens when that view is already the application's selection or the window is empty.
   */
  void activateWindow(Workspace workspace) {
    WorkspaceView view = workspace.getLastSelected();
    if (view == null || !isIn(view, workspace)) {
      view =
          workspace.getMaximizedView() != null
              ? workspace.getMaximizedView()
              : workspace.isEmpty() ? null : workspace.groups().getFirst().getSelectedView();
    }
    if (view != null && view != selectedView) {
      fireViewSelected(view, workspace.getWindow());
    }
  }

  private boolean isIn(WorkspaceView view, Workspace workspace) {
    TabGroup group = viewGroups.get(view);
    return group != null && group.getWorkspace() == workspace;
  }

  private void fireViewSelected(WorkspaceView view, Window window) {
    selectedView = view;
    TabGroup group = viewGroups.get(view);
    if (group != null) {
      group.getWorkspace().setLastSelected(view);
    }
    for (WorkspaceListener listener : listeners) {
      listener.viewSelected(view, window);
    }
  }

  private void fireWorkspaceEmptied(Window window) {
    for (WorkspaceListener listener : listeners) {
      listener.workspaceEmptied(window);
    }
  }
}
