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

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.dnd.DragSource;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Icon;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.weasis.core.ui.docking.ws.DockSide;
import org.weasis.core.ui.docking.ws.TabGroup;
import org.weasis.core.ui.docking.ws.WorkspaceManager;
import org.weasis.core.ui.docking.ws.WorkspaceView;

/**
 * Drag state machine of one tab group: pressing a tab and dragging past the system threshold shows
 * a lightweight ghost on the layered pane with drop-zone highlighting; releasing executes the drop.
 * All hit-testing uses component-relative coordinates converted to the root pane, never the global
 * pointer position (Wayland-safe). Group bounds are snapshotted once at drag start.
 */
public final class TabDragController extends MouseAdapter {

  private record GroupZone(
      TabGroup group, Rectangle bounds, Rectangle tabStrip, Rectangle[] tabRects) {}

  private final WorkspaceManager manager;
  private final TabGroup source;

  private Point pressPoint;
  private int pressedIndex = -1;
  private boolean dragging;
  private WorkspaceView draggedView;
  private DragOverlay overlay;
  private JLayeredPane layeredPane;
  private List<GroupZone> zones;
  private Rectangle workspaceBounds;
  private DropZone currentZone;
  private KeyEventDispatcher escapeDispatcher;

  private TabDragController(WorkspaceManager manager, TabGroup source) {
    this.manager = manager;
    this.source = source;
  }

  /** Installs a drag controller on the tab strip of the given group. */
  public static void install(WorkspaceManager manager, TabGroup group) {
    group.addTabAreaListener(new TabDragController(manager, group));
  }

  @Override
  public void mousePressed(MouseEvent e) {
    if (SwingUtilities.isLeftMouseButton(e) && manager.isDragEnabled()) {
      pressedIndex = source.indexAtLocation(e.getX(), e.getY());
      pressPoint = pressedIndex >= 0 ? e.getPoint() : null;
    }
  }

  @Override
  public void mouseDragged(MouseEvent e) {
    if (!dragging) {
      if (pressPoint == null || e.getPoint().distance(pressPoint) < DragSource.getDragThreshold()) {
        return;
      }
      startDrag();
      if (!dragging) {
        return;
      }
    }
    updateDrag(e.getPoint());
  }

  @Override
  public void mouseReleased(MouseEvent e) {
    if (dragging) {
      try {
        executeDrop(e.getPoint());
      } finally {
        finishDrag();
      }
    }
    pressPoint = null;
    pressedIndex = -1;
  }

  private void startDrag() {
    List<WorkspaceView> views = source.getViews();
    if (pressedIndex >= views.size()) {
      return;
    }
    draggedView = views.get(pressedIndex);
    JRootPane rootPane = SwingUtilities.getRootPane(source);
    if (rootPane == null) {
      draggedView = null;
      return;
    }
    layeredPane = rootPane.getLayeredPane();
    overlay = new DragOverlay(renderGhost(draggedView));
    overlay.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());
    overlay.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
    layeredPane.add(overlay, JLayeredPane.DRAG_LAYER);

    // one snapshot per drag: the layout cannot change while dragging
    var workspace = source.getWorkspace();
    workspaceBounds = boundsInLayer(workspace);
    zones = new ArrayList<>();
    for (TabGroup group : manager.getTabGroups(workspace.getWindow())) {
      Rectangle bounds = boundsInLayer(group);
      Rectangle[] tabRects = new Rectangle[group.getTabCount()];
      Rectangle strip = null;
      for (int i = 0; i < group.getTabCount(); i++) {
        Rectangle r = group.getBoundsAt(i);
        tabRects[i] = convertToLayer(group, r);
        strip = strip == null ? new Rectangle(tabRects[i]) : strip.union(tabRects[i]);
      }
      if (strip != null) {
        // the whole strip row is a drop target, beyond the last tab included
        strip.width = bounds.width - (strip.x - bounds.x);
      }
      zones.add(new GroupZone(group, bounds, strip, tabRects));
    }

    escapeDispatcher =
        e -> {
          if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            finishDrag();
            return true;
          }
          return false;
        };
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(escapeDispatcher);
    dragging = true;
  }

  private void updateDrag(Point sourcePoint) {
    Point p = SwingUtilities.convertPoint(source, sourcePoint, layeredPane);
    currentZone = computeZone(p);
    overlay.update(p, highlightFor(currentZone, p));
  }

  private DropZone computeZone(Point p) {
    for (GroupZone zone : zones) {
      if (zone.tabStrip() != null && zone.tabStrip().contains(p)) {
        return new DropZone.Stack(
            zone.group(), DropGeometry.tabInsertionIndex(zone.tabRects(), p.x));
      }
    }
    for (GroupZone zone : zones) {
      if (zone.bounds().contains(p)) {
        DockSide side = DropGeometry.sideAt(zone.bounds(), p);
        return side == null
            ? new DropZone.Stack(zone.group(), -1)
            : new DropZone.Split(zone.group(), side);
      }
    }
    if (!workspaceBounds.contains(p) && draggedView.isDetachable()) {
      return new DropZone.Detach();
    }
    return null;
  }

  private Rectangle highlightFor(DropZone zone, Point p) {
    return switch (zone) {
      case DropZone.Stack(TabGroup group, int index) -> stackHighlight(group, index);
      case DropZone.Split(TabGroup group, DockSide side) ->
          DropGeometry.highlightRect(groupBounds(group), side);
      case DropZone.Detach() -> null;
      case null -> null;
    };
  }

  private Rectangle stackHighlight(TabGroup group, int index) {
    GroupZone zone = zoneOf(group);
    if (index < 0 || zone.tabStrip() == null) {
      return DropGeometry.highlightRect(zone.bounds(), null);
    }
    // insertion caret in the tab strip
    Rectangle[] tabs = zone.tabRects();
    int x =
        index < tabs.length
            ? tabs[index].x
            : tabs.length > 0 ? tabs[tabs.length - 1].x + tabs[tabs.length - 1].width : 0;
    return new Rectangle(x - 2, zone.tabStrip().y, 4, zone.tabStrip().height);
  }

  private GroupZone zoneOf(TabGroup group) {
    return zones.stream().filter(z -> z.group() == group).findFirst().orElseThrow();
  }

  private Rectangle groupBounds(TabGroup group) {
    return zoneOf(group).bounds();
  }

  private void executeDrop(Point sourcePoint) {
    Point p = SwingUtilities.convertPoint(source, sourcePoint, layeredPane);
    WorkspaceView view = draggedView;
    switch (currentZone) {
      case DropZone.Stack(TabGroup group, int index) -> {
        if (group == source) {
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
      case DropZone.Detach() -> {
        Point screen = layeredPane.getLocationOnScreen();
        Dimension size = view.component().getSize();
        if (size.width < 100 || size.height < 100) {
          size = new Dimension(800, 600);
        }
        manager.detach(
            view, new Rectangle(screen.x + p.x, screen.y + p.y, size.width, size.height));
      }
      case null -> {
        // no target: cancelled
      }
    }
  }

  private void finishDrag() {
    if (overlay != null) {
      Rectangle bounds = overlay.getBounds();
      layeredPane.remove(overlay);
      layeredPane.repaint(bounds);
    }
    if (escapeDispatcher != null) {
      KeyboardFocusManager.getCurrentKeyboardFocusManager()
          .removeKeyEventDispatcher(escapeDispatcher);
    }
    overlay = null;
    layeredPane = null;
    zones = null;
    workspaceBounds = null;
    currentZone = null;
    draggedView = null;
    dragging = false;
    pressPoint = null;
    pressedIndex = -1;
  }

  private Rectangle boundsInLayer(java.awt.Component component) {
    return convertToLayer(component, new Rectangle(component.getSize()));
  }

  private Rectangle convertToLayer(java.awt.Component component, Rectangle rect) {
    return SwingUtilities.convertRectangle(component, rect, layeredPane);
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

    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2 = image.createGraphics();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g2.setColor(UIManager.getColor("Panel.background"));
      g2.fillRoundRect(0, 0, width - 1, height - 1, 10, 10);
      g2.setColor(DragOverlay.accentColor());
      g2.drawRoundRect(0, 0, width - 1, height - 1, 10, 10);
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
