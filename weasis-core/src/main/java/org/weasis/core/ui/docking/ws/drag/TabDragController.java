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

import com.formdev.flatlaf.util.UIScale;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DropTarget;
import java.awt.dnd.InvalidDnDOperationException;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Icon;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.ui.docking.ws.TabGroup;
import org.weasis.core.ui.docking.ws.WorkspaceManager;
import org.weasis.core.ui.docking.ws.WorkspaceView;

/**
 * Starts the drag of a tab: a system drag and drop, so that the tab can be dropped on the workspace
 * of any window of Weasis (main window or viewer window); the windowing system routes the drag to
 * the window under the pointer, on Wayland as on X11, Windows and macOS. The targets are the {@link
 * WorkspaceDropHandler}s of the workspaces. A tab released outside every workspace (on a side
 * panel, on the desktop) goes to a window of its own, at the drop position when the system gives
 * one. Escape cancels the drag over a workspace; outside every workspace a cancel cannot be told
 * from a drop, so the tab is detached as well.
 */
public final class TabDragController extends DragSourceAdapter implements DragGestureListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(TabDragController.class);

  /** The ghost the targets draw when the system cannot show a drag image, during a drag. */
  private static BufferedImage targetGhost;

  /** Whether the pointer is over a workspace of Weasis, from the targets. */
  private static boolean overWorkspace;

  private final WorkspaceManager manager;
  private final TabGroup source;
  private WorkspaceView draggedView;

  /**
   * The drop targets inside the workspaces (a view takes series drops), switched off while a tab is
   * dragged so that the workspace itself gets the drag wherever the pointer is.
   */
  private final List<DropTarget> paused = new ArrayList<>();

  TabDragController(WorkspaceManager manager, TabGroup source) {
    this.manager = manager;
    this.source = source;
  }

  /** Installs the drag of the tabs of the given group. */
  public static void install(WorkspaceManager manager, TabGroup group) {
    TabDragController controller = new TabDragController(manager, group);
    DragSource.getDefaultDragSource()
        .createDefaultDragGestureRecognizer(group, DnDConstants.ACTION_MOVE, controller);
  }

  /** The ghost the targets draw near the pointer, null when the system shows the drag image. */
  static BufferedImage ghostForTargets() {
    return targetGhost;
  }

  /** Told by a target when the pointer enters or leaves its workspace. */
  static void enteredWorkspace(boolean entered) {
    overWorkspace = entered;
  }

  @Override
  public void dragGestureRecognized(DragGestureEvent dge) {
    if (!manager.isDragEnabled()
        || !(dge.getTriggerEvent() instanceof InputEvent trigger)
        || !SwingUtilities.isLeftMouseButton((java.awt.event.MouseEvent) trigger)) {
      return;
    }
    Point origin = dge.getDragOrigin();
    int index = source.indexAtLocation(origin.x, origin.y);
    List<WorkspaceView> views = source.getViews();
    if (index < 0 || index >= views.size()) {
      return;
    }
    draggedView = views.get(index);
    BufferedImage ghost = renderGhost(draggedView);
    boolean imageShown = DragSource.isDragImageSupported();
    targetGhost = imageShown ? null : ghost;
    overWorkspace = true; // the drag starts on a tab strip
    pauseInnerTargets();
    try {
      dge.startDrag(
          Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR),
          imageShown ? ghost : null,
          new Point(-ghost.getWidth() / 2, -ghost.getHeight() / 2),
          new TabTransfer(draggedView),
          this);
    } catch (InvalidDnDOperationException e) {
      LOGGER.debug("Tab drag not started", e);
      reset();
    }
  }

  @Override
  public void dragDropEnd(DragSourceDropEvent dsde) {
    WorkspaceView view = draggedView;
    boolean outside = !overWorkspace;
    reset();
    if (view == null || dsde.getDropSuccess() || !outside || !manager.isShowing(view)) {
      return;
    }
    // released outside every workspace: the tab gets a window of its own
    Rectangle bounds = null;
    Point at = dsde.getLocation(); // null or meaningless where the screen position is not known
    if (at != null && (at.x != 0 || at.y != 0)) {
      Dimension size = view.component().getSize();
      if (size.width < 100 || size.height < 100) {
        size = new Dimension(800, 600);
      }
      bounds = new Rectangle(at.x, at.y, size.width, size.height);
    }
    manager.detach(view, bounds);
  }

  /** Switches off the drop targets inside every workspace, for the time of the drag. */
  void pauseInnerTargets() {
    for (java.awt.Window window : manager.getWindows()) {
      for (TabGroup group : manager.getTabGroups(window)) {
        pauseIn(group.getWorkspace());
      }
    }
  }

  private void pauseIn(Container container) {
    for (Component child : container.getComponents()) {
      DropTarget target = child.getDropTarget();
      if (target != null && target.isActive() && !paused.contains(target)) {
        target.setActive(false);
        paused.add(target);
      }
      if (child instanceof Container inner) {
        pauseIn(inner);
      }
    }
  }

  void reset() {
    paused.forEach(target -> target.setActive(true));
    paused.clear();
    draggedView = null;
    targetGhost = null;
    overWorkspace = false;
  }

  /** Renders the drag ghost once: a small rounded chip with the tab icon and title. */
  private static BufferedImage renderGhost(WorkspaceView view) {
    Font font = UIManager.getFont("Label.font");
    Icon icon = view.tabIcon();
    String title = view.tabTitle() == null ? "" : view.tabTitle();
    BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    FontMetrics fm = probe.createGraphics().getFontMetrics(font);
    int iconWidth = icon != null ? icon.getIconWidth() + 6 : 0;
    int width = Math.max(40, fm.stringWidth(title) + iconWidth + 20);
    int height = Math.max(24, fm.getHeight() + 10);

    // the chip of the dragged tab, as round as the cards of the tabs
    int arc = UIScale.scale(14);
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2 = image.createGraphics();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g2.setColor(UIManager.getColor("Panel.background"));
      g2.fillRoundRect(0, 0, width - 1, height - 1, arc, arc);
      g2.setColor(DragOverlay.accentColor());
      g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc);
      int x = 10;
      if (icon != null) {
        icon.paintIcon(null, g2, x, (height - icon.getIconHeight()) / 2);
        x += iconWidth;
      }
      g2.setFont(font);
      g2.setColor(UIManager.getColor("Label.foreground"));
      g2.drawString(title, x, (height + fm.getAscent() - fm.getDescent()) / 2);
    } finally {
      g2.dispose();
    }
    return image;
  }
}
