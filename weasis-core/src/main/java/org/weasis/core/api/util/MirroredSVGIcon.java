/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.util;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.Icon;

/**
 * An SVG icon painted with a horizontal flip, from the same file: the form of a direction icon
 * (back, next, previous) when the interface is right to left. Its derived and disabled forms are
 * flipped too.
 */
public class MirroredSVGIcon extends FlatSVGIcon {

  public MirroredSVGIcon(FlatSVGIcon icon) {
    super(icon);
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    Graphics2D g2 = (Graphics2D) g.create();
    try {
      g2.translate(x + getIconWidth(), y);
      g2.scale(-1.0, 1.0);
      super.paintIcon(c, g2, 0, 0);
    } finally {
      g2.dispose();
    }
  }

  @Override
  public FlatSVGIcon derive(int width, int height) {
    return new MirroredSVGIcon(super.derive(width, height));
  }

  @Override
  public FlatSVGIcon derive(float scale) {
    return new MirroredSVGIcon(super.derive(scale));
  }

  @Override
  public Icon getDisabledIcon() {
    Icon disabled = super.getDisabledIcon();
    return disabled instanceof FlatSVGIcon svg ? new MirroredSVGIcon(svg) : disabled;
  }
}
