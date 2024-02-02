/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.util;

import java.awt.Color;
import java.util.Objects;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.ui.model.graphic.imp.seg.SegRegion;
import org.weasis.opencv.seg.RegionAttributes;

public abstract class StructToolTipTreeNode extends DefaultMutableTreeNode {

  public StructToolTipTreeNode(SegRegion<?> userObject, boolean allowsChildren) {
    super(Objects.requireNonNull(userObject), allowsChildren);
  }

  public abstract String getToolTipText();

  @Override
  public String toString() {
    RegionAttributes seg = (RegionAttributes) getUserObject();
    return getColorBullet(seg.getColor(), seg.getLabel());
  }

  protected static String getColorBullet(Color c, String label) {
    StringBuilder buf = new StringBuilder("<html><font color='rgb("); // NON-NLS
    buf.append(c.getRed());
    buf.append(",");
    buf.append(c.getGreen());
    buf.append(",");
    buf.append(c.getBlue());
    // Other square: u2B1B (unicode)
    buf.append(")'> █ </font>"); // NON-NLS
    buf.append(label);
    buf.append(GuiUtils.HTML_END);
    return buf.toString();
  }

  public static String getSegItemToolTipText(TreePath curPath) {
    if (curPath != null) {
      Object object = curPath.getLastPathComponent();
      if (object instanceof StructToolTipTreeNode treeNode) {
        return treeNode.getToolTipText();
      }
    }
    return null;
  }
}
