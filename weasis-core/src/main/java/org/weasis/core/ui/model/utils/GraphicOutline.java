/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.utils;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.Stroke;

/**
 * Dark halo painted under the lines of a graphic. No line color stands out on every image: a thin
 * bright line vanishes on bone, on air or on a bright radiograph. The halo gives the line its own
 * dark surround, so the chosen color only has to be bright.
 */
public final class GraphicOutline {

  /** How much wider than the line the halo is, in screen pixels. */
  public static final float EXTRA_WIDTH = 2.0f;

  /** Opacity of the halo: dark enough under a bright line, light enough to see the image. */
  public static final float OPACITY = 0.55f;

  public static final Color COLOR = new Color(0f, 0f, 0f, OPACITY);

  /** Below this relative luminance a line is hard to see on the dark areas of an image. */
  public static final double MIN_LINE_LUMINANCE = 0.4;

  private GraphicOutline() {}

  /** Relative luminance of an sRGB color, from 0 (black) to 1 (white). */
  public static double relativeLuminance(Color color) {
    return 0.2126 * linear(color.getRed())
        + 0.7152 * linear(color.getGreen())
        + 0.0722 * linear(color.getBlue());
  }

  private static double linear(int channel) {
    double c = channel / 255.0;
    return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  }

  /** Draws the halo of a shape already in screen coordinates, for the stroke of its line. */
  public static void draw(Graphics2D g2d, Shape screenShape, Stroke lineStroke) {
    g2d.setPaint(COLOR);
    g2d.setStroke(widen(lineStroke));
    g2d.draw(screenShape);
  }

  /** Same stroke, wider, with round joins so the halo has no spikes at sharp corners. */
  static Stroke widen(Stroke stroke) {
    if (stroke instanceof BasicStroke basic) {
      return new BasicStroke(
          basic.getLineWidth() + EXTRA_WIDTH,
          basic.getEndCap(),
          BasicStroke.JOIN_ROUND,
          basic.getMiterLimit(),
          basic.getDashArray(),
          basic.getDashPhase());
    }
    return new BasicStroke(1.0f + EXTRA_WIDTH, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);
  }
}
