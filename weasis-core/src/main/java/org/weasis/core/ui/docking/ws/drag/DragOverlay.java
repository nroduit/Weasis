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

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
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

  @Override
  protected void paintComponent(Graphics g) {
    Graphics2D g2 = (Graphics2D) g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      if (highlight != null) {
        Color accent = accentColor();
        g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 90));
        g2.fillRect(highlight.x, highlight.y, highlight.width, highlight.height);
        g2.setColor(accent);
        g2.drawRect(highlight.x, highlight.y, highlight.width - 1, highlight.height - 1);
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
