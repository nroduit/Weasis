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
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import org.weasis.core.api.command.ScreenRole;
import org.weasis.core.api.command.ViewId;
import org.weasis.core.api.event.Event;
import org.weasis.core.api.event.EventBus;
import org.weasis.core.api.event.EventType;
import org.weasis.core.ui.docking.ws.snapshot.ViewRestorer;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot;
import org.weasis.core.ui.editor.ExternalDisplay;
import org.weasis.core.ui.editor.ScreenRoles;
import org.weasis.core.ui.editor.ScreenRoles.Mapping;
import org.weasis.core.ui.editor.ScreenRoles.Screen;

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

  /** Events of the engine, in the {@code workspace} namespace. */
  public static final String VIEW_OPENED = "workspace:view.opened"; // NON-NLS

  public static final String VIEW_SELECTED = "workspace:view.selected"; // NON-NLS
  public static final String WINDOW_OPENED = "workspace:window.opened"; // NON-NLS
  public static final String WINDOW_CLOSED = "workspace:window.closed"; // NON-NLS
  public static final String WINDOW_ACTIVATED = "workspace:window.activated"; // NON-NLS
  public static final String PANEL_CHANGED = "workspace:panel.changed"; // NON-NLS
  private static final String WINDOW = "window"; // NON-NLS
  private static final String SINCE = "5.0"; // NON-NLS
  private static final int ACTIVATION_SETTLE_MS = 100;

  private Timer activationTimer;
  private Window pendingActivation;
  private boolean mutating;
  private boolean dragEnabled = true;
  private WorkspaceView selectedView;
  private Workspace activeOverride;
  private final Set<Window> immersive = Collections.newSetFromMap(new IdentityHashMap<>());
  private Supplier<TransferHandler> dropHandlers;

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
    if (activeOverride != null && workspaces.containsValue(activeOverride)) {
      return activeOverride;
    }
    return selectedView().map(view -> viewGroups.get(view).getWorkspace()).orElse(mainWorkspace);
  }

  /**
   * Makes a workspace the active one, as a drop on its window does: its selected view becomes the
   * application's selection; an empty workspace stays the target of the next open until a view is
   * selected elsewhere.
   */
  public void activate(Workspace workspace) {
    if (workspace == null || !workspaces.containsValue(workspace)) {
      return;
    }
    WorkspaceView view = viewToSelect(workspace);
    if (view != null) {
      activeOverride = null;
      if (view != selectedView) {
        fireViewSelected(view, workspace.getWindow());
      }
    } else {
      activeOverride = workspace;
    }
  }

  /** The workspace of a window, or null when the window hosts none. */
  public Workspace workspaceOf(Window window) {
    return workspaces.get(window);
  }

  /** The window of a docked view, empty when it is not showing or runs headless. */
  public Optional<Window> windowOf(WorkspaceView view) {
    TabGroup group = viewGroups.get(view);
    return group == null ? Optional.empty() : Optional.ofNullable(group.getWorkspace().getWindow());
  }

  /**
   * The series drop handler every workspace window gets (the main one and every viewer window), so
   * a series dragged from the explorer opens where it is dropped.
   */
  public void setDropHandler(Supplier<TransferHandler> handlers) {
    this.dropHandlers = handlers;
    if (handlers != null) {
      for (Workspace workspace : workspaces.values()) {
        workspace.setTransferHandler(handlers.get());
      }
    }
  }

  /** Moves a window to the screen of a role (its usable area); ends its fullscreen state. */
  public void moveToScreen(Window window, ScreenRole role) {
    if (window == null || role == null) {
      return;
    }
    setFullscreen(window, false);
    Rectangle bounds = ExternalDisplay.onRole(role, mainWindow).screenBounds();
    if (window instanceof Frame frame
        && (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH) {
      // the window manager restores the normal bounds after the state change, so ours come next
      frame.setExtendedState(frame.getExtendedState() & ~Frame.MAXIMIZED_BOTH);
      SwingUtilities.invokeLater(
          () -> {
            window.setBounds(bounds);
            window.toFront();
          });
      return;
    }
    window.setBounds(bounds);
    window.toFront();
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

  /** Whether the window is in the fullscreen state of {@link #setFullscreen}. */
  public boolean isFullscreen(Window window) {
    if (window == null) {
      return false;
    }
    GraphicsConfiguration gc = window.getGraphicsConfiguration();
    return gc != null && gc.getDevice().getFullScreenWindow() == window;
  }

  /**
   * Puts a workspace window on the full bounds of its monitor without decorations, or ends that
   * state; the toolkit restores the previous bounds and state on the way back. A window state, so
   * the snapshot carries it ({@code fullscreen}) and a session restores it.
   */
  public void setFullscreen(Window window, boolean fullscreen) {
    if (window == null) {
      return;
    }
    GraphicsConfiguration gc = window.getGraphicsConfiguration();
    if (gc == null) {
      return;
    }
    GraphicsDevice device = gc.getDevice();
    if (fullscreen) {
      if (device.getFullScreenWindow() != window) {
        device.setFullScreenWindow(window);
      }
    } else if (device.getFullScreenWindow() == window) {
      device.setFullScreenWindow(null);
    }
  }

  /** Whether the window shows its workspace alone (canvas fullscreen). */
  public boolean isImmersive(Window window) {
    return window != null && immersive.contains(window);
  }

  /**
   * Shows the workspace of a window alone, or everything again: the listeners that own the chrome
   * (menu bar, toolbar row, shell) hide or show it. Not a window state of the snapshot: it is what
   * the canvas fullscreen composes with {@link #setFullscreen} and it ends with it.
   */
  public void setImmersive(Window window, boolean on) {
    if (window == null || on == immersive.contains(window)) {
      return;
    }
    if (on) {
      immersive.add(window);
    } else {
      immersive.remove(window);
    }
    for (WorkspaceListener listener : listeners) {
      listener.immersiveChanged(window, on);
    }
  }

  /** Refreshes the tab title, icon and tooltip of the view. */
  public void updateTabInfo(WorkspaceView view) {
    TabGroup group = viewGroups.get(view);
    if (group != null) {
      group.updateTabInfo(view);
      refreshTitle(group.getWorkspace());
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
        forgetWindow(frame);
        frame.dispose();
      }
    }
    viewGroups.clear();
    mainWorkspace.groups().clear();
    mainWorkspace.setRoot(null);
  }

  // ---- snapshots ----------------------------------------------------------

  /**
   * Captures the layout of every window: screen (device id, bounds and role), bounds, state, split
   * trees and view descriptors.
   */
  public WorkspaceSnapshot capture() {
    List<WorkspaceSnapshot.WindowSnapshot> windows = new ArrayList<>();
    Mapping mapping = ScreenRoles.mapping(mainWindow);
    for (Workspace workspace : workspaces.values()) {
      if (workspace.isEmpty()) {
        continue;
      }
      Window window = workspace.getWindow();
      String screenId = null;
      Rectangle bounds = null;
      String role = null;
      Rectangle screenBounds = null;
      String state = null;
      if (window != null) {
        bounds = window.getBounds();
        GraphicsConfiguration gc = window.getGraphicsConfiguration();
        if (gc != null) {
          screenId = gc.getDevice().getIDstring();
          screenBounds = gc.getBounds();
          role = mapping.roleOf(screenId).map(ScreenRole::key).orElse(null);
        }
        if (isFullscreen(window)) {
          state = WorkspaceSnapshot.WindowSnapshot.FULLSCREEN;
        } else if (window instanceof Frame frame) {
          state =
              (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH
                  ? WorkspaceSnapshot.WindowSnapshot.MAXIMIZED
                  : WorkspaceSnapshot.WindowSnapshot.NORMAL;
        }
      }
      WorkspaceView maximized = workspace.getMaximizedView();
      windows.add(
          new WorkspaceSnapshot.WindowSnapshot(
              screenId,
              bounds,
              workspace == mainWorkspace,
              captureNode(workspace.getRoot()),
              maximized != null ? maximized.viewId() : null,
              role,
              screenBounds,
              state));
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

  /** What a restore could not place as saved: the windows folded into the main window. */
  public record RestoreReport(List<String> folded) {}

  /**
   * Rebuilds the layout from a snapshot. Every currently docked view is undocked first (not
   * disposed); the restorer resolves each descriptor to a live or recreated view, and unresolved
   * descriptors are skipped so the layout collapses gracefully. Windows are placed by screen role
   * first, then by device id, then by bounds; a viewer window whose monitor is missing folds into
   * the main window (tabs, or a split by the local setting), never lost.
   */
  public RestoreReport restore(WorkspaceSnapshot snapshot, ViewRestorer restorer) {
    return restore(snapshot, restorer, ScreenRoles.foldMode());
  }

  /** {@link #restore(WorkspaceSnapshot, ViewRestorer)} with the fold mode given. */
  public RestoreReport restore(
      WorkspaceSnapshot snapshot, ViewRestorer restorer, ScreenRoles.FoldMode foldMode) {
    List<String> folded = new ArrayList<>();
    Mapping mapping = ScreenRoles.mapping(mainWindow);
    Map<String, GraphicsDevice> devices = ScreenRoles.devices();
    mutate(
        () -> {
          for (WorkspaceView view : getViews()) {
            undockInternal(view);
          }
          // the main window first: folded windows join it
          List<WorkspaceSnapshot.WindowSnapshot> ordered =
              snapshot.windows().stream().sorted(Comparator.comparing(win -> !win.main())).toList();
          for (WorkspaceSnapshot.WindowSnapshot win : ordered) {
            if (win.main()) {
              Component root = restoreNode(win.root(), mainWorkspace, restorer);
              if (root != null) {
                mainWorkspace.setRoot(root);
              }
              placeMainWindow(win, mapping, devices);
              showMaximized(win, mainWorkspace);
              continue;
            }
            Rectangle bounds = place(win, mapping, devices).orElse(null);
            if (bounds == null) {
              if (fold(win, restorer, foldMode)) {
                folded.add(win.role() != null ? win.role() : String.valueOf(win.screenId()));
              }
              continue;
            }
            DetachedFrame frame =
                new DetachedFrame(this, System.getProperty("weasis.name", "Weasis")); // NON-NLS
            Workspace target = frame.getWorkspace();
            addWorkspace(frame, target);
            Component root = restoreNode(win.root(), target, restorer);
            if (root == null) {
              forgetWindow(frame);
              frame.dispose();
              continue;
            }
            target.setRoot(root);
            refreshTitle(target);
            frame.setBounds(bounds);
            frame.setVisible(true);
            applyState(frame, win.state());
            showMaximized(win, target);
          }
        });
    for (Workspace workspace : workspaces.values()) {
      if (!workspace.isEmpty()) {
        fireIfSelected(workspace.groups().getFirst().getSelectedView());
      }
    }
    return new RestoreReport(List.copyOf(folded));
  }

  private static void showMaximized(WorkspaceSnapshot.WindowSnapshot win, Workspace target) {
    if (win.maximizedViewId() != null) {
      target.groups().stream()
          .flatMap(g -> g.getViews().stream())
          .filter(v -> win.maximizedViewId().equals(v.viewId()))
          .findFirst()
          .ifPresent(target::showMaximized);
    }
  }

  private void applyState(Window window, String state) {
    if (window == null || state == null) {
      return;
    }
    if (WorkspaceSnapshot.WindowSnapshot.FULLSCREEN.equals(state)) {
      setFullscreen(window, true);
      return;
    }
    setFullscreen(window, false);
    if (window instanceof Frame frame) {
      int extended = frame.getExtendedState();
      if (WorkspaceSnapshot.WindowSnapshot.MAXIMIZED.equals(state)) {
        frame.setExtendedState(extended | Frame.MAXIMIZED_BOTH);
      } else if (WorkspaceSnapshot.WindowSnapshot.NORMAL.equals(state)) {
        frame.setExtendedState(extended & ~Frame.MAXIMIZED_BOTH);
      }
    }
  }

  /** The main window goes back where the snapshot had it, when that place still exists. */
  private void placeMainWindow(
      WorkspaceSnapshot.WindowSnapshot win, Mapping mapping, Map<String, GraphicsDevice> devices) {
    if (mainWindow == null || win.bounds() == null) {
      return;
    }
    place(win, mapping, devices).ifPresent(mainWindow::setBounds);
    applyState(mainWindow, win.state());
  }

  /**
   * Where a saved window goes on this workstation: on the monitor of its role when that role has
   * one (at the saved bounds, or the same place on the new monitor when it is another one), else on
   * its device when it still exists, else at its bounds when a monitor shows them; empty when
   * nothing of that is true and the window has to fold.
   */
  private Optional<Rectangle> place(
      WorkspaceSnapshot.WindowSnapshot win, Mapping mapping, Map<String, GraphicsDevice> devices) {
    Rectangle saved = win.bounds();
    if (saved == null) {
      return devices.isEmpty() ? Optional.empty() : Optional.of(defaultDetachedBounds());
    }
    ScreenRole role = null;
    if (win.role() != null) {
      try {
        role = ScreenRole.parse(win.role());
      } catch (IllegalArgumentException e) {
        // a role this version does not know: place by device or bounds
      }
    }
    if (role != null && mapping.isFulfilled(role)) {
      Screen target = mapping.screen(role);
      if (target.id().equals(win.screenId())) {
        return Optional.of(new Rectangle(saved));
      }
      return Optional.of(translate(saved, win.screenBounds(), target.bounds()));
    }
    if (win.screenId() != null && devices.containsKey(win.screenId())) {
      return Optional.of(new Rectangle(saved));
    }
    for (GraphicsDevice device : devices.values()) {
      if (device.getDefaultConfiguration().getBounds().intersects(saved)) {
        return Optional.of(new Rectangle(saved));
      }
    }
    return Optional.empty();
  }

  /**
   * The same place on another monitor: the saved fractions of the old screen applied to the new.
   */
  static Rectangle translate(Rectangle saved, Rectangle from, Rectangle to) {
    if (from == null || from.width <= 0 || from.height <= 0) {
      return new Rectangle(
          to.x, to.y, Math.min(saved.width, to.width), Math.min(saved.height, to.height));
    }
    double sx = (double) to.width / from.width;
    double sy = (double) to.height / from.height;
    return new Rectangle(
        (int) Math.round(to.x + (saved.x - from.x) * sx),
        (int) Math.round(to.y + (saved.y - from.y) * sy),
        (int) Math.round(saved.width * sx),
        (int) Math.round(saved.height * sy));
  }

  /** Folds the views of a saved window into the main window; false when none could be restored. */
  private boolean fold(
      WorkspaceSnapshot.WindowSnapshot win, ViewRestorer restorer, ScreenRoles.FoldMode mode) {
    if (mode == ScreenRoles.FoldMode.SPLIT) {
      Component root = restoreNode(win.root(), mainWorkspace, restorer);
      if (root == null) {
        return false;
      }
      Component current = mainWorkspace.getRoot();
      mainWorkspace.setRoot(current == null ? root : newSplit(false, DEFAULT_RATIO, current, root));
      return true;
    }
    TabGroup target = mainWorkspace.isEmpty() ? null : mainWorkspace.groups().getFirst();
    boolean any = false;
    for (WorkspaceSnapshot.ViewDescriptor descriptor : descriptorsOf(win.root())) {
      WorkspaceView view = restorer.recreate(descriptor).orElse(null);
      if (view == null) {
        continue;
      }
      if (viewGroups.containsKey(view)) {
        undockInternal(view);
      }
      if (target == null) {
        target = newGroup(mainWorkspace);
        mainWorkspace.setRoot(target);
      }
      dockInto(view, target, -1);
      any = true;
    }
    return any;
  }

  private static List<WorkspaceSnapshot.ViewDescriptor> descriptorsOf(WorkspaceSnapshot.Node node) {
    return switch (node) {
      case WorkspaceSnapshot.GroupNode(List<WorkspaceSnapshot.ViewDescriptor> views, int s) ->
          views;
      case WorkspaceSnapshot.SplitNode(boolean v, double r, var first, var second) -> {
        List<WorkspaceSnapshot.ViewDescriptor> all = new ArrayList<>(descriptorsOf(first));
        all.addAll(descriptorsOf(second));
        yield all;
      }
      case null -> List.of();
    };
  }

  private static JSplitPane newSplit(boolean vertical, double ratio, Component a, Component b) {
    JSplitPane split =
        new JSplitPane(vertical ? JSplitPane.VERTICAL_SPLIT : JSplitPane.HORIZONTAL_SPLIT, a, b);
    split.setContinuousLayout(true);
    split.setResizeWeight(0.5);
    split.setBorder(null);
    SwingUtilities.invokeLater(() -> split.setDividerLocation(ratio));
    return split;
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
        yield newSplit(vertical, ratio, a, b);
      }
      case null -> null;
    };
  }

  // ---- internal mechanics -------------------------------------------------

  private TabGroup newGroup(Workspace workspace) {
    TabGroup group = new TabGroup(this, workspace);
    workspace.groups().add(group);
    return group;
  }

  private void dockInto(WorkspaceView view, TabGroup group, int index) {
    group.addView(view, index);
    boolean opened = viewGroups.put(view, group) == null;
    if (opened) {
      EventBus.global()
          .publish(
              viewEvent(VIEW_OPENED, view, group.getWorkspace().getWindow())
                  .put("type", view.typeId())); // NON-NLS
    }
  }

  private void undockInternal(WorkspaceView view) {
    TabGroup group = viewGroups.remove(view);
    if (group == null) {
      return;
    }
    EventBus.global()
        .publish(viewEvent(EventBus.VIEW_CLOSED, view, group.getWorkspace().getWindow()));
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
        forgetWindow(frame);
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
    if (dropHandlers != null) {
      workspace.setTransferHandler(dropHandlers.get());
    }
    if (window != null) {
      window.addWindowFocusListener(
          new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
              activateWindow(workspace);
              activationSettled(window);
            }
          });
      publishWindow(WINDOW_OPENED, window);
    }
  }

  /**
   * Publishes the active window once it stayed so for 100 ms: AWT activation ping-pongs between
   * windows, menus and tooltips, differently per platform, so the state is read from the engine
   * after it settles, never from the raw events.
   */
  private void activationSettled(Window window) {
    pendingActivation = window;
    if (activationTimer == null) {
      activationTimer =
          new Timer(
              ACTIVATION_SETTLE_MS,
              e -> {
                Window active = pendingActivation;
                if (active != null && active.isFocused()) {
                  publishWindow(WINDOW_ACTIVATED, active);
                }
              });
      activationTimer.setRepeats(false);
    }
    activationTimer.restart();
  }

  /**
   * Activating a window selects the view that window has selected (its last selected view, else the
   * selected view of its first group), so that the toolbars and the tools follow the user's eyes;
   * nothing happens when that view is already the application's selection or the window is empty.
   */
  void activateWindow(Workspace workspace) {
    WorkspaceView view = viewToSelect(workspace);
    if (view != null && view != selectedView) {
      fireViewSelected(view, workspace.getWindow());
    }
  }

  /** The view a window stands for: its last selected view, else the selected one of its tree. */
  private WorkspaceView viewToSelect(Workspace workspace) {
    WorkspaceView view = workspace.getLastSelected();
    if (view == null || !isIn(view, workspace)) {
      view =
          workspace.getMaximizedView() != null
              ? workspace.getMaximizedView()
              : workspace.isEmpty() ? null : workspace.groups().getFirst().getSelectedView();
    }
    return view;
  }

  /** A viewer window is titled after the view it shows, under the identity mask in force. */
  private void refreshTitle(Workspace workspace) {
    if (workspace.getWindow() instanceof DetachedFrame frame) {
      WorkspaceView view = viewToSelect(workspace);
      frame.setTitle(
          view != null ? view.tabTitle() : System.getProperty("weasis.name", "Weasis")); // NON-NLS
    }
  }

  private boolean isIn(WorkspaceView view, Workspace workspace) {
    TabGroup group = viewGroups.get(view);
    return group != null && group.getWorkspace() == workspace;
  }

  private void fireViewSelected(WorkspaceView view, Window window) {
    selectedView = view;
    activeOverride = null;
    TabGroup group = viewGroups.get(view);
    if (group != null) {
      group.getWorkspace().setLastSelected(view);
      refreshTitle(group.getWorkspace());
    }
    for (WorkspaceListener listener : listeners) {
      listener.viewSelected(view, window);
    }
    EventBus.global()
        .publish(
            viewEvent(VIEW_SELECTED, view, window)
                .coalesce(VIEW_SELECTED)
                .put("type", view.typeId())); // NON-NLS
  }

  /** The number {@code workspace:list} gives a window: the main window is 1; 0 for a stranger. */
  public int windowNumber(Window window) {
    int index = 0;
    for (Window known : workspaces.keySet()) {
      index++;
      if (known == window) {
        return index;
      }
    }
    return 0;
  }

  private Event.Builder viewEvent(String name, WorkspaceView view, Window window) {
    Event.Builder event = Event.of(name).put(EventBus.VIEW_KEY, view.viewId());
    int number = windowNumber(window);
    if (number > 0) {
      event.put(WINDOW, number);
    }
    return event;
  }

  private void publishWindow(String name, Window window) {
    int number = windowNumber(window);
    Event.Builder event =
        Event.of(name).put("detached", window instanceof DetachedFrame); // NON-NLS
    if (number > 0) {
      event.put(WINDOW, number);
    }
    EventBus.global().publish(event);
  }

  /** Forgets a detached window and says so; its number is read before it is gone. */
  private void forgetWindow(DetachedFrame frame) {
    publishWindow(WINDOW_CLOSED, frame);
    workspaces.remove(frame);
  }

  /** The events of the engine, declared by the {@code workspace} namespace. */
  public static List<EventType> eventTypes() {
    return List.of(
        EventType.of(VIEW_OPENED, SINCE, "A view was docked into a window.")
            .field(EventBus.VIEW_KEY, ViewId.class, "the view")
            .field("type", String.class, "viewer type id")
            .field(WINDOW, int.class, "window number, as workspace:list numbers them")
            .build(),
        EventType.of(EventBus.VIEW_CLOSED, SINCE, "A view left the workspace.")
            .field(EventBus.VIEW_KEY, ViewId.class, "the view")
            .field(WINDOW, int.class, "window number")
            .build(),
        EventType.of(VIEW_SELECTED, SINCE, "A view became the application's selection.")
            .coalesced()
            .field(EventBus.VIEW_KEY, ViewId.class, "the view")
            .field("type", String.class, "viewer type id")
            .field(WINDOW, int.class, "window number")
            .build(),
        EventType.of(WINDOW_OPENED, SINCE, "A window hosting a workspace appeared.")
            .field(WINDOW, int.class, "window number")
            .field("detached", boolean.class, "whether it is a floating viewer window")
            .build(),
        EventType.of(WINDOW_CLOSED, SINCE, "A floating viewer window closed.")
            .field(WINDOW, int.class, "window number it had")
            .field("detached", boolean.class, "always true")
            .build(),
        EventType.of(
                WINDOW_ACTIVATED,
                SINCE,
                "The active window changed, once it stayed so for 100 ms; from the engine, not AWT.")
            .coalesced()
            .field(WINDOW, int.class, "window number")
            .build(),
        EventType.of(
                PANEL_CHANGED,
                SINCE,
                "The toolbars or the tool panels were rebuilt for the selected viewer.")
            .field("panel", String.class, "toolbars or tools")
            .build());
  }

  private void fireWorkspaceEmptied(Window window) {
    for (WorkspaceListener listener : listeners) {
      listener.workspaceEmptied(window);
    }
  }
}
