/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.util;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/**
 * A {@link FlowLayout} that wraps its rows to fit the available width and reports a correct
 * preferred size, so it re-flows reliably on every layout pass (including inside a vertically
 * scrolling {@link JScrollPane}). This avoids having to recompute a wrap count from resize events.
 */
public class WrapLayout extends FlowLayout {

  public WrapLayout() {
    super();
  }

  public WrapLayout(int align) {
    super(align);
  }

  public WrapLayout(int align, int hgap, int vgap) {
    super(align, hgap, vgap);
  }

  @Override
  public Dimension preferredLayoutSize(Container target) {
    return layoutSize(target, true);
  }

  @Override
  public Dimension minimumLayoutSize(Container target) {
    Dimension minimum = layoutSize(target, false);
    minimum.width -= (getHgap() + 1);
    return minimum;
  }

  private Dimension layoutSize(Container target, boolean preferred) {
    synchronized (target.getTreeLock()) {
      // use the width of the target, or the enclosing scroll viewport, to wrap against
      int targetWidth = target.getSize().width;
      Container scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane.class, target);
      if (targetWidth == 0) {
        targetWidth = Integer.MAX_VALUE;
      }

      int hgap = getHgap();
      int vgap = getVgap();
      Insets insets = target.getInsets();
      int horizontalInsetsAndGap = insets.left + insets.right + (hgap * 2);
      int maxWidth = targetWidth - horizontalInsetsAndGap;

      Dimension dim = new Dimension(0, 0);
      int rowWidth = 0;
      int rowHeight = 0;

      int count = target.getComponentCount();
      for (int i = 0; i < count; i++) {
        Component m = target.getComponent(i);
        if (m.isVisible()) {
          Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
          if (rowWidth + d.width > maxWidth) {
            addRow(dim, rowWidth, rowHeight);
            rowWidth = 0;
            rowHeight = 0;
          }
          if (rowWidth != 0) {
            rowWidth += hgap;
          }
          rowWidth += d.width;
          rowHeight = Math.max(rowHeight, d.height);
        }
      }
      addRow(dim, rowWidth, rowHeight);

      dim.width += horizontalInsetsAndGap;
      dim.height += insets.top + insets.bottom + (vgap * 2);

      // when in a scroll pane, the parent has already validated against the viewport width, so trim
      // a pixel to avoid a spurious horizontal scroll bar
      if (scrollPane != null && target.isValid()) {
        dim.width -= (hgap + 1);
      }
      return dim;
    }
  }

  private void addRow(Dimension dim, int rowWidth, int rowHeight) {
    dim.width = Math.max(dim.width, rowWidth);
    if (dim.height > 0) {
      dim.height += getVgap();
    }
    dim.height += rowHeight;
  }
}
