/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.docking.ws.drag;

import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.util.TooManyListenersException;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.ui.docking.ws.DockSide;
import org.weasis.core.ui.docking.ws.TabGroup;
import org.weasis.core.ui.docking.ws.Workspace;
import org.weasis.core.ui.docking.ws.WorkspaceManager;
import org.weasis.core.ui.docking.ws.WorkspaceView;

/**
 * The drop target of the workspace of one window, the main one or a viewer window: a tab dragged
 * from any window of Weasis lands here (stacked in a group, at an insertion point of a tab strip,
 * or splitting a group), and every other drop goes to the series handler of the explorer.
 *
 * <p>The drag is a system drag and drop, so the windowing system tells which window is under the
 * pointer, including on Wayland where a client knows neither the pointer position on the screen nor
 * where its windows are. The zones are computed from the position relative to this workspace, and
 * the rounded preview is drawn on the layered pane of its window.
 */
public final class WorkspaceDropHandler extends TransferHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(WorkspaceDropHandler.class);

  private final WorkspaceManager manager;
  private final Workspace workspace;
  private TransferHandler delegate;
  private DragOverlay overlay;
  private JLayeredPane layeredPane;

  private WorkspaceDropHandler(WorkspaceManager manager, Workspace workspace) {
    this.manager = manager;
    this.workspace = workspace;
  }

  /**
   * Makes the workspace a drop target for tabs, the other drops going to {@code delegate} (the
   * series handler, null when there is none).
   */
  public static void install(
      WorkspaceManager manager, Workspace workspace, TransferHandler delegate) {
    WorkspaceDropHandler handler =
        workspace.getTransferHandler() instanceof WorkspaceDropHandler current
            ? current
            : new WorkspaceDropHandler(manager, workspace);
    handler.delegate = delegate;
    if (workspace.getTransferHandler() != handler) {
      workspace.setTransferHandler(handler);
      if (workspace.getDropTarget() == null) {
        return; // headless: no drop target, no preview
      }
      try {
        workspace.getDropTarget().addDropTargetListener(handler.new Preview());
      } catch (TooManyListenersException e) {
        LOGGER.warn("No preview for the tab drop of a workspace", e);
      }
    }
  }

  // ---- import
  // ------------------------------------------------------------------------------------

  @Override
  public boolean canImport(TransferSupport support) {
    if (support.isDataFlavorSupported(TabTransfer.FLAVOR)) {
      return support.isDrop() && zoneAt(support.getDropLocation().getDropPoint()) != null;
    }
    return delegate != null && delegate.canImport(support);
  }

  @Override
  public boolean importData(TransferSupport support) {
    if (!support.isDataFlavorSupported(TabTransfer.FLAVOR)) {
      return delegate != null && delegate.importData(support);
    }
    if (!support.isDrop()) {
      return false;
    }
    WorkspaceView view = draggedView(support.getTransferable());
    DropZone zone = zoneAt(support.getDropLocation().getDropPoint());
    if (view == null || zone == null) {
      return false;
    }
    execute(view, zone);
    return true;
  }

  @Override
  public int getSourceActions(JComponent c) {
    return delegate != null ? delegate.getSourceActions(c) : NONE;
  }

  private static WorkspaceView draggedView(Transferable transferable) {
    try {
      return (WorkspaceView) transferable.getTransferData(TabTransfer.FLAVOR);
    } catch (Exception e) {
      LOGGER.debug("No dragged tab", e);
      return null;
    }
  }

  private void execute(WorkspaceView view, DropZone zone) {
    switch (zone) {
      case DropZone.Stack(TabGroup group, int index) -> {
        if (manager.groupOf(view).orElse(null) == group) {
          if (index >= 0) {
            manager.moveTab(view, index);
          }
        } else {
          manager.stack(view, group, index);
        }
        manager.select(view);
      }
      case DropZone.Split(TabGroup group, DockSide side) ->
          group.getViews().stream()
              .filter(v -> v != view)
              .findFirst()
              .ifPresent(
                  anchor -> {
                    manager.split(view, anchor, side, 0.5);
                    manager.select(view);
                  });
      case DropZone.Detach() -> manager.detach(view, null);
    }
  }

  // ---- zones
  // -------------------------------------------------------------------------------------

  /** The zone under a point of the workspace, null outside every group. */
  DropZone zoneAt(Point p) {
    for (TabGroup group : manager.getTabGroups(workspace.getWindow())) {
      if (!inLayout(group)) {
        continue;
      }
      Rectangle strip = tabStrip(group);
      if (strip != null && strip.contains(p)) {
        return new DropZone.Stack(group, DropGeometry.tabInsertionIndex(tabRects(group), p.x));
      }
    }
    for (TabGroup group : manager.getTabGroups(workspace.getWindow())) {
      if (!inLayout(group)) {
        continue;
      }
      Rectangle bounds = inWorkspace(group, new Rectangle(group.getSize()));
      if (bounds.contains(p)) {
        DockSide side = DropGeometry.sideAt(bounds, p);
        return side == null ? new DropZone.Stack(group, -1) : new DropZone.Split(group, side);
      }
    }
    return null;
  }

  /** A group of this workspace that is laid out (not out of the tree while a view is maximized). */
  private boolean inLayout(TabGroup group) {
    return group.getWorkspace() == workspace
        && group.isVisible()
        && SwingUtilities.isDescendingFrom(group, workspace);
  }

  private Rectangle[] tabRects(TabGroup group) {
    Rectangle[] rects = new Rectangle[group.getTabCount()];
    for (int i = 0; i < rects.length; i++) {
      rects[i] = inWorkspace(group, group.getBoundsAt(i));
    }
    return rects;
  }

  /** The tab strip row of a group, beyond its last tab included; null without a tab. */
  private Rectangle tabStrip(TabGroup group) {
    Rectangle strip = null;
    for (Rectangle r : tabRects(group)) {
      if (r != null) {
        strip = strip == null ? new Rectangle(r) : strip.union(r);
      }
    }
    if (strip != null) {
      Rectangle bounds = inWorkspace(group, new Rectangle(group.getSize()));
      strip.width = bounds.width - (strip.x - bounds.x);
    }
    return strip;
  }

  private Rectangle inWorkspace(Component c, Rectangle r) {
    return r == null ? null : SwingUtilities.convertRectangle(c, r, workspace);
  }

  /** The rectangle the preview highlights for a zone, in workspace coordinates. */
  private Rectangle highlight(DropZone zone) {
    return switch (zone) {
      case DropZone.Stack(TabGroup group, int index) -> {
        Rectangle bounds = inWorkspace(group, new Rectangle(group.getSize()));
        Rectangle strip = tabStrip(group);
        if (index < 0 || strip == null) {
          yield DropGeometry.highlightRect(bounds, null);
        }
        Rectangle[] tabs = tabRects(group);
        int x =
            index < tabs.length
                ? tabs[index].x
                : tabs.length > 0 ? tabs[tabs.length - 1].x + tabs[tabs.length - 1].width : 0;
        yield new Rectangle(x - 2, strip.y, 4, strip.height);
      }
      case DropZone.Split(TabGroup group, DockSide side) ->
          DropGeometry.highlightRect(inWorkspace(group, new Rectangle(group.getSize())), side);
      case DropZone.Detach() -> null;
    };
  }

  // ---- preview
  // -----------------------------------------------------------------------------------

  /** Shows and moves the rounded preview while a tab is dragged over this workspace. */
  private final class Preview extends DropTargetAdapter {
    @Override
    public void dragEnter(DropTargetDragEvent e) {
      if (isTab(e.getCurrentDataFlavors())) {
        TabDragController.enteredWorkspace(true);
        show(e.getLocation());
      }
    }

    @Override
    public void dragOver(DropTargetDragEvent e) {
      if (isTab(e.getCurrentDataFlavors())) {
        show(e.getLocation());
      }
    }

    @Override
    public void dragExit(DropTargetEvent e) {
      TabDragController.enteredWorkspace(false);
      hide();
    }

    @Override
    public void drop(DropTargetDropEvent e) {
      hide();
    }

    private boolean isTab(DataFlavor[] flavors) {
      for (DataFlavor flavor : flavors) {
        if (TabTransfer.FLAVOR.equals(flavor)) {
          return true;
        }
      }
      return false;
    }
  }

  private void show(Point inWorkspace) {
    JRootPane root = SwingUtilities.getRootPane(workspace);
    if (root == null) {
      return;
    }
    if (overlay == null) {
      layeredPane = root.getLayeredPane();
      overlay = new DragOverlay(TabDragController.ghostForTargets());
      overlay.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());
      layeredPane.add(overlay, JLayeredPane.DRAG_LAYER);
    }
    DropZone zone = zoneAt(inWorkspace);
    Rectangle rect = zone == null ? null : highlight(zone);
    Rectangle inLayer =
        rect == null ? null : SwingUtilities.convertRectangle(workspace, rect, layeredPane);
    Point cursor =
        TabDragController.ghostForTargets() == null
            ? null
            : SwingUtilities.convertPoint(workspace, inWorkspace, layeredPane);
    overlay.update(cursor, inLayer);
  }

  private void hide() {
    if (overlay != null) {
      Rectangle bounds = overlay.getBounds();
      layeredPane.remove(overlay);
      layeredPane.repaint(bounds);
      overlay = null;
      layeredPane = null;
    }
  }
}
