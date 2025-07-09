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

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.Objects;
import javax.swing.*;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.util.ResourceUtil;

public class ViewButton extends Rectangle2D.Double implements ShowPopup {

  private final ShowPopup popup;
  private final Icon icon;
  private final String name;
  private boolean visible;
  private boolean enable;
  private boolean hover;
  private int position;

  public ViewButton(ShowPopup popup, Icon icon, String name) {
    this.popup = Objects.requireNonNull(popup);
    this.icon = Objects.requireNonNull(icon);
    this.name = Objects.requireNonNull(name);
    this.position = GridBagConstraints.EAST;
    this.setFrame(
        0,
        0,
        GuiUtils.getScaleLength(icon.getIconWidth()),
        GuiUtils.getScaleLength(icon.getIconHeight()));
  }

  public boolean isVisible() {
    return visible;
  }

  public void setVisible(boolean visible) {
    this.visible = visible;
  }

  public boolean isEnable() {
    return enable;
  }

  public void setEnable(boolean enable) {
    this.enable = enable;
  }

  public boolean isHover() {
    return hover;
  }

  public void setHover(boolean hover) {
    this.hover = hover;
  }

  public Icon getIcon() {
    return icon;
  }

  public String getName() {
    return name;
  }

  public int getPosition() {
    return position;
  }

  public void setPosition(int position) {
    this.position = position;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    ViewButton that = (ViewButton) o;
    return popup.equals(that.popup) && name.equals(that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), popup, name);
  }

  @Override
  public void showPopup(Component invoker, int x, int y) {
    popup.showPopup(invoker, x, y);
  }

  public static Icon getResizedIcon(ResourceUtil.ResourceIconPath path, int size) {
    return ResourceUtil.getIcon(path, size, size);
  }

  public static void drawButtonBackground(Graphics2D g2d, Component c, ViewButton b, Icon icon) {
    int x = (int) b.x - 3;
    int y = (int) b.y - 3;
    int width = icon.getIconWidth() + 7;
    int height = icon.getIconHeight() + 7;

    Color oldColor = g2d.getColor();
    Color bck = UIManager.getColor("Button.background");
    g2d.setColor(bck);
    g2d.fillRoundRect(x, y, width, height, 7, 7);

    if (b.isHover()) {
      Color borderColor = UIManager.getColor("Button.hoverBorderColor");
      Color highlight = borderColor.brighter().brighter();
      Color shadow = borderColor.darker().darker();

      g2d.setStroke(new BasicStroke(2.0f));
      g2d.setColor(highlight);
      g2d.drawRoundRect(x, y, width, height, 7, 7);
      g2d.setColor(shadow);
      g2d.drawRoundRect(x, y, width, height, 7, 7);
    }

    icon.paintIcon(c, g2d, (int) b.x, (int) b.y);
    g2d.setColor(oldColor);
  }
}
