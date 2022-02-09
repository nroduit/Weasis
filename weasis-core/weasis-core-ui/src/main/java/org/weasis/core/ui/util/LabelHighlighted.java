/*
 * Copyright (c) 2022 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.util;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.JLabel;

public class LabelHighlighted extends JLabel {
  private final List<Rectangle2D> rectangles = new ArrayList<>();
  private final Color highlightColor;

  public LabelHighlighted(Color highlightColor) {
    this.highlightColor = Objects.requireNonNull(highlightColor);
  }

  public void reset() {
    rectangles.clear();
    repaint();
  }

  public void highlightText(String textToHighlight) {
    if (textToHighlight == null) {
      return;
    }
    reset();

    final String textToMatch = textToHighlight.toLowerCase().trim();
    if (textToMatch.length() == 0) {
      return;
    }

    final String labelText = getText().toLowerCase();
    if (labelText.contains(textToMatch)) {
      String highlight = textToHighlight.trim();
      FontMetrics fm = getFontMetrics(getFont());
      float w = -1;
      final float h = fm.getHeight() - 1f;
      int i = 0;
      while (true) {
        i = labelText.indexOf(textToMatch, i);
        if (i == -1) {
          break;
        }
        if (w == -1) {
          String matchingText = getText().substring(i, i + highlight.length());
          w = fm.stringWidth(matchingText);
        }
        String preText = getText().substring(0, i);
        float x = fm.stringWidth(preText);
        rectangles.add(new Rectangle2D.Float(x, 1, w, h));
        i = i + textToMatch.length();
      }
      repaint();
    }
  }

  public List<Rectangle2D> getRectangles() {
    return rectangles;
  }

  @Override
  protected void paintComponent(Graphics g) {
    g.setColor(getBackground());
    g.fillRect(0, 0, getWidth(), getHeight());
    if (!rectangles.isEmpty()) {
      Graphics2D g2d = (Graphics2D) g;
      Color c = g2d.getColor();
      for (Rectangle2D rectangle : rectangles) {
        g2d.setColor(highlightColor);
        g2d.fill(rectangle);
      }
      g2d.setColor(c);
    }
    super.paintComponent(g);
  }
}
