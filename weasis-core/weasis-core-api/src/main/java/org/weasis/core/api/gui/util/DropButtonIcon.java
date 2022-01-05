/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.util;

import com.formdev.flatlaf.ui.FlatUIUtils;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.ButtonModel;
import javax.swing.Icon;

public class DropButtonIcon implements Icon {

  private final Icon leftIcon;

  public DropButtonIcon(Icon leftIcon) {
    this.leftIcon = leftIcon;
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    Graphics2D g2d = (Graphics2D) g;
    leftIcon.paintIcon(c, g2d, x, y);
    if (c instanceof DropDownButton button) {
      ButtonModel model = button.getModel();
      Color color;
      if (model.isRollover()) {
        color = FlatUIUtils.getUIColor("ComboBox.buttonHoverArrowColor", Color.GRAY);
      } else if (model.isPressed()) {
        color = FlatUIUtils.getUIColor("ComboBox.buttonPressedArrowColor", Color.LIGHT_GRAY);
      } else if (!model.isEnabled()) {
        color = FlatUIUtils.getUIColor("ComboBox.buttonDisabledArrowColor", Color.DARK_GRAY);
      } else {
        color = FlatUIUtils.getUIColor("ComboBox.buttonArrowColor", Color.DARK_GRAY);
      }
      g2d.setPaint(color);
    }
    int midSize = GuiUtils.getScaleLength(3);
    int shiftx = x + leftIcon.getIconWidth() + GuiUtils.getScaleLength(1);
    int shifty = y + leftIcon.getIconHeight() - GuiUtils.getScaleLength(5);
    int[] xPoints = {shiftx, shiftx + 2 * midSize, shiftx + midSize};
    int[] yPoints = {shifty, shifty, shifty + midSize};
    g2d.fillPolygon(xPoints, yPoints, xPoints.length);
  }

  @Override
  public int getIconWidth() {
    return leftIcon.getIconWidth() + GuiUtils.getScaleLength(7);
  }

  @Override
  public int getIconHeight() {
    return leftIcon.getIconHeight();
  }
}
