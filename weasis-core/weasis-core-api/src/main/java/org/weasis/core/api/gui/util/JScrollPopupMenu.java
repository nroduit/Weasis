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

import java.awt.Adjustable;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.event.MouseWheelEvent;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;

public class JScrollPopupMenu extends JPopupMenu {

  protected int maximumVisibleRows = 10;
  private JScrollBar popupScrollBar;

  public JScrollPopupMenu() {
    this(null);
  }

  public JScrollPopupMenu(String label) {
    super(label);
    setLayout(new ScrollPopupMenuLayout());

    super.add(getScrollBar());
    addMouseWheelListener(
        event -> {
          JScrollBar scrollBar = getScrollBar();
          int amount =
              (event.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL)
                  ? event.getUnitsToScroll() * scrollBar.getUnitIncrement()
                  : (event.getWheelRotation() < 0 ? -1 : 1) * scrollBar.getBlockIncrement();

          scrollBar.setValue(scrollBar.getValue() + amount);
          event.consume();
        });
  }

  protected JScrollBar getScrollBar() {
    if (popupScrollBar == null) {
      popupScrollBar = new JScrollBar(Adjustable.VERTICAL);
      popupScrollBar.addAdjustmentListener(
          e -> {
            doLayout();
            repaint();
          });

      popupScrollBar.setVisible(false);
    }

    return popupScrollBar;
  }

  public int getMaximumVisibleRows() {
    return maximumVisibleRows;
  }

  public void setMaximumVisibleRows(int maximumVisibleRows) {
    this.maximumVisibleRows = maximumVisibleRows;
  }

  @Override
  public void paintChildren(Graphics g) {
    Insets insets = getInsets();
    g.clipRect(insets.left, insets.top, getWidth(), getHeight() - insets.top - insets.bottom);
    super.paintChildren(g);
  }

  @Override
  protected void addImpl(Component comp, Object constraints, int index) {
    super.addImpl(comp, constraints, index);

    if (maximumVisibleRows < getComponentCount() - 1) {
      getScrollBar().setVisible(true);
    }
  }

  @Override
  public void remove(int index) {
    // can't remove the scrollbar
    ++index;

    super.remove(index);

    if (maximumVisibleRows >= getComponentCount() - 1) {
      getScrollBar().setVisible(false);
    }
  }

  @Override
  public void show(Component invoker, int x, int y) {
    JScrollBar scrollBar = getScrollBar();
    if (scrollBar.isVisible()) {
      int extent = 0;
      int max = 0;
      int i = 0;
      int unit = -1;
      int width = 0;
      for (Component comp : getComponents()) {
        if (!(comp instanceof JScrollBar)) {
          Dimension preferredSize = comp.getPreferredSize();
          width = Math.max(width, preferredSize.width);
          if (unit < 0) {
            unit = preferredSize.height;
          }
          if (i++ < maximumVisibleRows) {
            extent += preferredSize.height;
          }
          max += preferredSize.height;
        }
      }

      Insets insets = getInsets();
      int widthMargin = insets.left + insets.right;
      int heightMargin = insets.top + insets.bottom;
      scrollBar.setUnitIncrement(unit);
      scrollBar.setBlockIncrement(extent);
      scrollBar.setValues(0, heightMargin + extent, 0, heightMargin + max);

      width += scrollBar.getPreferredSize().width + widthMargin;
      int height = heightMargin + extent;

      setPopupSize(new Dimension(width, height));
    }

    super.show(invoker, x, y);
  }

  protected static class ScrollPopupMenuLayout implements LayoutManager {
    @Override
    public void addLayoutComponent(String name, Component comp) {
      // Not implemented
    }

    @Override
    public void removeLayoutComponent(Component comp) {
      // Not implemented
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
      int visibleAmount = Integer.MAX_VALUE;
      Dimension dim = new Dimension();
      for (Component comp : parent.getComponents()) {
        if (comp.isVisible()) {
          if (comp instanceof JScrollBar scrollBar) {
            visibleAmount = scrollBar.getVisibleAmount();
          } else {
            Dimension pref = comp.getPreferredSize();
            dim.width = Math.max(dim.width, pref.width);
            dim.height += pref.height;
          }
        }
      }

      Insets insets = parent.getInsets();
      dim.height = Math.min(dim.height + insets.top + insets.bottom, visibleAmount);

      return dim;
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
      int visibleAmount = Integer.MAX_VALUE;
      Dimension dim = new Dimension();
      for (Component comp : parent.getComponents()) {
        if (comp.isVisible()) {
          if (comp instanceof JScrollBar scrollBar) {
            visibleAmount = scrollBar.getVisibleAmount();
          } else {
            Dimension min = comp.getMinimumSize();
            dim.width = Math.max(dim.width, min.width);
            dim.height += min.height;
          }
        }
      }

      Insets insets = parent.getInsets();
      dim.height = Math.min(dim.height + insets.top + insets.bottom, visibleAmount);

      return dim;
    }

    @Override
    public void layoutContainer(Container parent) {
      Insets insets = parent.getInsets();

      int width = parent.getWidth() - insets.left - insets.right;
      int height = parent.getHeight() - insets.top - insets.bottom;

      int x = insets.left;
      int y = insets.top;
      int position = 0;

      for (Component comp : parent.getComponents()) {
        if ((comp instanceof JScrollBar scrollBar) && comp.isVisible()) {
          Dimension dim = scrollBar.getPreferredSize();
          scrollBar.setBounds(x + width - dim.width, y, dim.width, height);
          width -= dim.width;
          position = scrollBar.getValue();
        }
      }

      y -= position;
      for (Component comp : parent.getComponents()) {
        if (!(comp instanceof JScrollBar) && comp.isVisible()) {
          Dimension pref = comp.getPreferredSize();
          comp.setBounds(x, y, width, pref.height);
          y += pref.height;
        }
      }
    }
  }
}
