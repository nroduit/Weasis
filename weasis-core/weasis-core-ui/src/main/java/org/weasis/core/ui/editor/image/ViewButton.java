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

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.geom.Rectangle2D;
import java.util.Objects;
import javax.swing.Icon;

public class ViewButton extends Rectangle2D.Double implements ShowPopup {

  private final ShowPopup popup;
  private final Icon icon;
  private final String name;
  private boolean visible;
  private boolean enable;
  private int position;

  public ViewButton(ShowPopup popup, Icon icon, String name) {
    this.popup = Objects.requireNonNull(popup);
    this.icon = Objects.requireNonNull(icon);
    this.name = Objects.requireNonNull(name);
    this.position = GridBagConstraints.EAST;
    this.setFrame(0, 0, icon.getIconWidth(), icon.getIconHeight());
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
}
