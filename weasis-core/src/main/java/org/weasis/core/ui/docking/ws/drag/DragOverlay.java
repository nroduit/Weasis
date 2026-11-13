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
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import javax.swing.UIManager;

/**
 * Lightweight overlay painted on the layered pane during a tab drag: a pre-rendered ghost of the
 * dragged tab following the cursor and a translucent highlight of the current drop zone. Repaints
 * are limited to the union of the previous and current dirty rectangles.
 */
final class DragOverlay extends JComponent {

  private static final int GHOST_OFFSET = 14;

  private final BufferedImage ghost;
  private Point cursor;
  private Rectangle highlight;

  DragOverlay(BufferedImage ghost) {
    this.ghost = ghost;
    setOpaque(false);
  }

  static Color accentColor() {
    Color accent = UIManager.getColor("Component.accentColor");
    if (accent == null) {
      accent = UIManager.getColor("List.selectionBackground");
    }
    return accent != null ? accent : new Color(0, 120, 215);
  }

  void update(Point newCursor, Rectangle newHighlight) {
    Rectangle dirty = dirtyRect(cursor, highlight);
    this.cursor = newCursor;
    this.highlight = newHighlight;
    Rectangle next = dirtyRect(newCursor, newHighlight);
    if (dirty != null) {
      dirty = next != null ? dirty.union(next) : dirty;
    } else {
      dirty = next;
    }
    if (dirty != null) {
      dirty.grow(2, 2);
      repaint(dirty);
    }
  }

  private Rectangle dirtyRect(Point cursorPos, Rectangle highlightRect) {
    Rectangle rect = highlightRect != null ? new Rectangle(highlightRect) : null;
    if (cursorPos != null) {
      Rectangle ghostRect =
          new Rectangle(
              cursorPos.x + GHOST_OFFSET,
              cursorPos.y + GHOST_OFFSET,
              ghost.getWidth(),
              ghost.getHeight());
      rect = rect != null ? rect.union(ghostRect) : ghostRect;
    }
    return rect;
  }

  /** The narrowest highlight drawn as an area; a narrower one is the insertion mark of a tab. */
  private static final int CARET_MAX_WIDTH = 8;

  /**
   * Paints the drop target: an area (a group, or the half of it a split takes) as a translucent
   * rounded card inset from the edges of the group, as the cards of the tabs and the frames of the
   * side slots; the insertion mark between two tabs as a solid pill.
   */
  private static void paintHighlight(Graphics2D g2, Rectangle r) {
    Color accent = accentColor();
    if (r.width <= CARET_MAX_WIDTH) {
      float w = Math.max(3f, UIScale.scale(3f));
      g2.setColor(accent);
      g2.fill(
          new RoundRectangle2D.Float(
              r.x + (r.width - w) / 2f, r.y + 2f, w, Math.max(0, r.height - 4f), w, w));
      return;
    }
    float inset = UIScale.scale(4f);
    float arc = UIScale.scale(14f);
    float stroke = Math.max(1.5f, UIScale.scale(1.5f));
    RoundRectangle2D.Float card =
        new RoundRectangle2D.Float(
            r.x + inset,
            r.y + inset,
            Math.max(0, r.width - 2 * inset),
            Math.max(0, r.height - 2 * inset),
            arc,
            arc);
    g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 64));
    g2.fill(card);
    g2.setStroke(new BasicStroke(stroke));
    g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 220));
    g2.draw(card);
  }

  @Override
  protected void paintComponent(Graphics g) {
    Graphics2D g2 = (Graphics2D) g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      if (highlight != null) {
        paintHighlight(g2, highlight);
      }
      if (cursor != null) {
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f));
        g2.drawImage(ghost, cursor.x + GHOST_OFFSET, cursor.y + GHOST_OFFSET, null);
      }
    } finally {
      g2.dispose();
    }
  }
}
