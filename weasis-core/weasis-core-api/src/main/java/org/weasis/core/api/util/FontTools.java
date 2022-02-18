/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.util;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;

public class FontTools {

  private FontTools() {}

  public static void paintColorFontOutline(
      Graphics2D g2, String str, float x, float y, Color color) {
    g2.setPaint(Color.BLACK);

    if (RenderingHints.VALUE_TEXT_ANTIALIAS_ON.equals(
        g2.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING))) {
      TextLayout layout = new TextLayout(str, g2.getFont(), g2.getFontRenderContext());
      AffineTransform textAt = new AffineTransform();
      textAt.translate(x, y);
      Shape outline = layout.getOutline(textAt);
      g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
      g2.draw(outline);
      g2.setPaint(color);
      g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
      g2.fill(outline);
    } else {
      g2.drawString(str, x - 1f, y - 1f);
      g2.drawString(str, x - 1f, y);
      g2.drawString(str, x - 1f, y + 1f);
      g2.drawString(str, x, y - 1f);
      g2.drawString(str, x, y + 1f);
      g2.drawString(str, x + 1f, y - 1f);
      g2.drawString(str, x + 1f, y);
      g2.drawString(str, x + 1f, y + 1f);
      g2.setPaint(color);
      g2.drawString(str, x, y);
    }
  }

  public static void paintFontOutline(Graphics2D g2, String str, float x, float y) {
    paintColorFontOutline(g2, str, x, y, Color.WHITE);
  }
}
