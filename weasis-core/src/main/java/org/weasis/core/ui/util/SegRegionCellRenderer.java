/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.util;

import eu.essilab.lablib.checkboxtree.DefaultCheckboxTreeCellRenderer;
import java.awt.*;
import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.UIManager;
import org.weasis.core.api.gui.util.GuiUtils;

/**
 * Renders a segmentation region as a color swatch followed by its label. Unlike the HTML bullet of
 * {@link StructToolTipTreeNode#toString()}, the swatch has known bounds, so a mouse click can be
 * mapped to it and used to change the region color (see {@link SegRegionTree}).
 */
public class SegRegionCellRenderer extends DefaultCheckboxTreeCellRenderer {

  private final SwatchIcon swatch = new SwatchIcon();

  public SegRegionCellRenderer() {
    setOpenIcon(null);
    setClosedIcon(null);
    setLeafIcon(null);
  }

  @Override
  public Component getTreeCellRendererComponent(
      JTree tree,
      Object value,
      boolean selected,
      boolean expanded,
      boolean leaf,
      int row,
      boolean hasFocus) {
    super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
    if (value instanceof StructToolTipTreeNode node && node.getRegion().getColor() != null) {
      // Ignore the alpha channel: it carries the display opacity, not a swatch transparency.
      swatch.color = new Color(node.getRegion().getColor().getRGB());
      swatch.hovered = tree instanceof SegRegionTree segTree && segTree.isSwatchHovered(row);
      this.label.setIcon(swatch);
      this.label.setText(node.getDisplayLabel());
    }
    return this;
  }

  /**
   * Returns the swatch area of the last rendered node, or {@code null} when it has no color. The
   * hit area spans the whole row height to stay forgiving vertically.
   *
   * @param cellBounds the node bounds in tree coordinates
   */
  public Rectangle getSwatchBounds(Rectangle cellBounds) {
    Icon icon = this.label.getIcon();
    if (icon == null || cellBounds == null) {
      return null;
    }
    int x = cellBounds.x + this.checkBox.getPreferredSize().width + this.label.getInsets().left;
    return new Rectangle(x, cellBounds.y, icon.getIconWidth(), cellBounds.height);
  }

  /**
   * A rounded color chip surrounded by a transparent margin, in which the focus ring is drawn when
   * the mouse hovers the chip.
   */
  private static final class SwatchIcon implements Icon {
    private final int chipSize = GuiUtils.getScaleLength(12);
    private final int margin = GuiUtils.getScaleLength(3);
    private final int arc = GuiUtils.getScaleLength(4);
    private Color color = Color.GRAY;
    private boolean hovered;

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      Graphics2D g2 = (Graphics2D) g.create();
      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setColor(color);
        g2.fillRoundRect(x + margin, y + margin, chipSize, chipSize, arc, arc);
        if (hovered) {
          int size = getIconWidth();
          g2.setColor(focusColor());
          g2.setStroke(new BasicStroke(GuiUtils.getScaleLength(2)));
          g2.drawRoundRect(x + 1, y + 1, size - 3, size - 3, arc + margin, arc + margin);
        }
      } finally {
        g2.dispose();
      }
    }

    private static Color focusColor() {
      Color c = UIManager.getColor("Component.focusedBorderColor"); // NON-NLS
      if (c == null) {
        c = UIManager.getColor("Tree.selectionBackground"); // NON-NLS
      }
      return c == null ? Color.GRAY : c;
    }

    @Override
    public int getIconWidth() {
      return chipSize + 2 * margin;
    }

    @Override
    public int getIconHeight() {
      return getIconWidth();
    }
  }
}
