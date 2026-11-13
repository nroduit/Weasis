/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.docking;

import com.formdev.flatlaf.ui.FlatUIUtils;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Path2D;
import javax.swing.border.AbstractBorder;
import org.weasis.core.api.gui.util.GuiUtils;

/**
 * The frame of a docked area: a one-pixel outline with rounded top corners and square bottom ones,
 * in the border color of the look and feel. The slots of the side panels and the tab groups of the
 * workspace share it, so that every docked area reads as a window of the same kind.
 */
public final class RoundedTopBorder extends AbstractBorder {

  private final int margin;

  /** A frame drawn on the edge of the component. */
  public RoundedTopBorder() {
    this(0);
  }

  /** A frame drawn {@code margin} unscaled pixels inside the edge of the component. */
  public RoundedTopBorder(int margin) {
    this.margin = Math.max(0, margin);
  }

  /** The color of the frame, from the look and feel. */
  public static Color color() {
    return FlatUIUtils.getUIColor("Component.borderColor", Color.GRAY); // NON-NLS
  }

  /** The diameter of the rounded corners, from the arc of the look and feel. */
  public static int arc() {
    return GuiUtils.getScaleLength(Math.max(8, FlatUIUtils.getUIInt("Component.arc", 5) + 9));
  }

  /** A rectangle whose top corners are rounded with the given diameter and bottom ones square. */
  public static Shape topRounded(double x, double y, double w, double h, double arc) {
    double r = Math.min(arc / 2.0, Math.min(w, h) / 2.0);
    Path2D path = new Path2D.Double();
    path.moveTo(x, y + h);
    path.lineTo(x, y + r);
    path.quadTo(x, y, x + r, y);
    path.lineTo(x + w - r, y);
    path.quadTo(x + w, y, x + w, y + r);
    path.lineTo(x + w, y + h);
    path.closePath();
    return path;
  }

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    int m = GuiUtils.getScaleLength(margin);
    Graphics2D g2 = (Graphics2D) g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
      g2.setColor(color());
      g2.draw(
          topRounded(x + m + 0.5, y + m + 0.5, width - 2.0 * m - 1, height - 2.0 * m - 1, arc()));
    } finally {
      g2.dispose();
    }
  }

  @Override
  public Insets getBorderInsets(Component c, Insets insets) {
    int m = GuiUtils.getScaleLength(margin) + 1;
    insets.set(m, m, m, m);
    return insets;
  }
}
