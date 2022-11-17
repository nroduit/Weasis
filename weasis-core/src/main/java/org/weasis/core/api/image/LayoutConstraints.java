/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import org.weasis.core.api.util.Copyable;

public class LayoutConstraints extends GridBagConstraints
    implements Comparable<LayoutConstraints>, Copyable<LayoutConstraints> {

  public static final int SPACE = 3;
  private final String type;
  private final int layoutID;
  private Color color;

  public LayoutConstraints(
      String type,
      int layoutID,
      int gridX,
      int gridY,
      int gridWidth,
      int gridHeight,
      double weightX,
      double weightY,
      int anchor,
      int fill) {
    super(
        gridX,
        gridY,
        gridWidth,
        gridHeight,
        weightX,
        weightY,
        anchor,
        fill,
        new Insets(gridY == 0 ? 0 : SPACE, gridX == 0 ? 0 : SPACE, 0, 0),
        0,
        0);
    this.type = type;
    this.layoutID = layoutID;
  }

  public LayoutConstraints(LayoutConstraints lc) {
    super(
        lc.gridx,
        lc.gridy,
        lc.gridwidth,
        lc.gridheight,
        lc.weightx,
        lc.weighty,
        lc.anchor,
        lc.fill,
        new Insets(lc.insets.top, lc.insets.left, lc.insets.bottom, lc.insets.right),
        lc.ipadx,
        lc.ipady);
    this.type = lc.type;
    this.layoutID = lc.layoutID;
  }

  public String getType() {
    return type;
  }

  public int getLayoutID() {
    return layoutID;
  }

  @Override
  public int compareTo(LayoutConstraints o) {
    return Integer.compare(layoutID, o.layoutID);
  }

  @Override
  public LayoutConstraints copy() {
    return new LayoutConstraints(this);
  }

  public Color getColor() {
    return color;
  }

  public void setColor(Color color) {
    this.color = color;
  }
}
