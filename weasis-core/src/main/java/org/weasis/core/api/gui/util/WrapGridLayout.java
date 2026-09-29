/*
 * Copyright (c) 2026 Weasis Team and other contributors.
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
import java.awt.Insets;
import java.awt.LayoutManager2;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * Grid of equal cells, each as large as the largest component, whose number of columns follows the
 * width of the container: as many as fit, at least one. The height therefore depends on the width;
 * the layout asks for a new validation when the number of columns it had announced changes.
 */
public class WrapGridLayout implements LayoutManager2 {

  private final int preferredColumns;
  private int announcedColumns = -1;

  /**
   * @param preferredColumns columns asked for while the container has no width yet
   */
  public WrapGridLayout(int preferredColumns) {
    this.preferredColumns = Math.max(1, preferredColumns);
  }

  /** Size of a cell: the largest preferred size among the visible components. */
  static Dimension cellSize(Container parent) {
    Dimension cell = new Dimension();
    for (Component c : parent.getComponents()) {
      if (c.isVisible()) {
        Dimension d = c.getPreferredSize();
        cell.width = Math.max(cell.width, d.width);
        cell.height = Math.max(cell.height, d.height);
      }
    }
    return cell;
  }

  private static int visibleCount(Container parent) {
    int count = 0;
    for (Component c : parent.getComponents()) {
      if (c.isVisible()) {
        count++;
      }
    }
    return count;
  }

  /** Columns for the width the container has, or the preferred number before it has one. */
  int columns(Container parent) {
    int count = Math.max(1, visibleCount(parent));
    Insets insets = parent.getInsets();
    int available = parent.getWidth() - insets.left - insets.right;
    int cellWidth = cellSize(parent).width;
    if (parent.getWidth() <= 0 || cellWidth <= 0) {
      return Math.min(preferredColumns, count);
    }
    return Math.clamp(available / cellWidth, 1, count);
  }

  private Dimension sizeFor(Container parent, int columns) {
    Dimension cell = cellSize(parent);
    int count = visibleCount(parent);
    int rows = count == 0 ? 0 : (count + columns - 1) / columns;
    Insets insets = parent.getInsets();
    return new Dimension(
        columns * cell.width + insets.left + insets.right,
        rows * cell.height + insets.top + insets.bottom);
  }

  @Override
  public Dimension preferredLayoutSize(Container parent) {
    synchronized (parent.getTreeLock()) {
      announcedColumns = columns(parent);
      Dimension size = sizeFor(parent, announcedColumns);
      if (parent.getWidth() <= 0) {
        return size;
      }
      // The width is imposed: only the height matters, and one cell is enough to exist
      return new Dimension(sizeFor(parent, 1).width, size.height);
    }
  }

  @Override
  public Dimension minimumLayoutSize(Container parent) {
    synchronized (parent.getTreeLock()) {
      return new Dimension(sizeFor(parent, 1).width, sizeFor(parent, columns(parent)).height);
    }
  }

  @Override
  public Dimension maximumLayoutSize(Container target) {
    return new Dimension(Integer.MAX_VALUE, preferredLayoutSize(target).height);
  }

  @Override
  public void layoutContainer(Container parent) {
    synchronized (parent.getTreeLock()) {
      int columns = columns(parent);
      Dimension cell = cellSize(parent);
      Insets insets = parent.getInsets();
      int index = 0;
      for (Component c : parent.getComponents()) {
        if (c.isVisible()) {
          c.setBounds(
              insets.left + (index % columns) * cell.width,
              insets.top + (index / columns) * cell.height,
              cell.width,
              cell.height);
          index++;
        }
      }
      if (columns != announcedColumns) {
        // The height announced was for another number of columns
        announcedColumns = columns;
        SwingUtilities.invokeLater(() -> revalidate(parent));
      }
    }
  }

  /** Validates again from the validation root, so that every ancestor takes the new height. */
  static void revalidate(Container parent) {
    if (parent instanceof JComponent component) {
      component.revalidate();
    } else {
      parent.invalidate();
      parent.validate();
    }
  }

  @Override
  public void addLayoutComponent(String name, Component comp) {
    // No constraint
  }

  @Override
  public void addLayoutComponent(Component comp, Object constraints) {
    // No constraint
  }

  @Override
  public void removeLayoutComponent(Component comp) {
    // No constraint
  }

  @Override
  public float getLayoutAlignmentX(Container target) {
    return 0.5f;
  }

  @Override
  public float getLayoutAlignmentY(Container target) {
    return 0.5f;
  }

  @Override
  public void invalidateLayout(Container target) {
    // Nothing cached but the announced columns, checked at the next layout
  }
}
