/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.Icon;

/**
 * Miniature of the working area with one of its regions highlighted, so that menu entries sharing
 * the same name can be told apart by where they are on screen.
 *
 * <p>The coordinates are relative to the working area, between 0.0 and 1.0. Painting uses the
 * foreground colour of the component so that the icon follows the theme.
 *
 * @param x the left edge of the region
 * @param y the top edge of the region
 * @param width the width of the region
 * @param height the height of the region
 */
record AreaIcon(double x, double y, double width, double height) implements Icon {

  private static final int SIZE = 16;
  private static final int FILL_ALPHA = 150;
  private static final int MIN_FILL = 2;

  @Override
  public void paintIcon(Component c, Graphics g, int px, int py) {
    Graphics2D g2d = (Graphics2D) g.create();
    Color color = c.getForeground();
    int inner = SIZE - 1;
    g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), FILL_ALPHA));
    g2d.fillRect(
        px + (int) Math.round(x * inner),
        py + (int) Math.round(y * inner),
        Math.max(MIN_FILL, (int) Math.round(width * inner)),
        Math.max(MIN_FILL, (int) Math.round(height * inner)));
    g2d.setColor(color);
    g2d.drawRect(px, py, inner, inner);
    g2d.dispose();
  }

  @Override
  public int getIconWidth() {
    return SIZE;
  }

  @Override
  public int getIconHeight() {
    return SIZE;
  }
}
