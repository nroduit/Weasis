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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.SwingUtilities;

/**
 * Left to right flow that wraps to the width of the container and, unlike {@link
 * java.awt.FlowLayout}, asks for the height its lines need, so what follows is pushed down. A
 * component added with {@link #KEEP_WITH_NEXT} stays on the line of the next one, as a label with
 * its field; such a group is only split when it is wider than the container on its own. A component
 * added with {@link #FILL} takes what is left of its line: it stays beside the previous component
 * while its minimum width fits there, and drops to a line of its own otherwise.
 */
public class WrapFlowLayout implements LayoutManager2 {

  /** Constraint of a component that must not end a line, such as the label of a field. */
  public static final String KEEP_WITH_NEXT = "keepWithNext"; // NON-NLS

  /** Constraint of a component that stretches to the end of its line, such as a combo box. */
  public static final String FILL = "fill"; // NON-NLS

  private final int hgap;
  private final int vgap;
  private final Set<Component> keptWithNext = new HashSet<>();
  private final Set<Component> filling = new HashSet<>();
  private int announcedHeight = -1;

  public WrapFlowLayout(int hgap, int vgap) {
    this.hgap = hgap;
    this.vgap = vgap;
  }

  /** A component with the place the layout gives it, relative to the insets of the container. */
  record Placement(Component component, int x, int y, int width, int height) {}

  /** Groups of components that stay on one line. */
  private List<List<Component>> groups(Container parent) {
    List<List<Component>> groups = new ArrayList<>();
    List<Component> current = new ArrayList<>();
    for (Component c : parent.getComponents()) {
      if (c.isVisible()) {
        current.add(c);
        if (!keptWithNext.contains(c)) {
          groups.add(current);
          current = new ArrayList<>();
        }
      }
    }
    if (!current.isEmpty()) {
      groups.add(current);
    }
    return groups;
  }

  /** Width a component needs to be placed: its minimum when it fills the rest of the line. */
  private int fitWidth(Component c, int available) {
    boolean bounded = available != Integer.MAX_VALUE;
    return filling.contains(c) && bounded ? c.getMinimumSize().width : c.getPreferredSize().width;
  }

  private int widthOf(List<Component> group, int available) {
    int width = 0;
    for (Component c : group) {
      width += fitWidth(c, available);
    }
    return width + hgap * (group.size() - 1);
  }

  /** Places every visible component for an available width; lines are centred vertically. */
  List<Placement> place(Container parent, int available) {
    List<Placement> placements = new ArrayList<>();
    List<Component> line = new ArrayList<>();
    int x = 0;
    int y = 0;
    for (List<Component> group : groups(parent)) {
      // A group wider than the container cannot stay whole: its components wrap one by one
      List<List<Component>> units =
          widthOf(group, available) > available
              ? group.stream().map(List::of).toList()
              : List.of(group);
      for (List<Component> unit : units) {
        int width = widthOf(unit, available);
        if (!line.isEmpty() && x + width > available) {
          y += closeLine(line, placements, y, available) + vgap;
          x = 0;
        }
        for (Component c : unit) {
          int w = Math.min(fitWidth(c, available), available);
          placements.add(new Placement(c, x, y, w, c.getPreferredSize().height));
          line.add(c);
          x += w + hgap;
        }
      }
    }
    closeLine(line, placements, y, available);
    return placements;
  }

  /**
   * Finishes a line: its components are centred on its height, which is returned, and the room left
   * goes to the components that fill, those after them moving right.
   */
  private int closeLine(List<Component> line, List<Placement> placements, int y, int available) {
    int height = 0;
    int used = -hgap;
    int fillers = 0;
    int first = placements.size() - line.size();
    for (int i = first; i < placements.size(); i++) {
      height = Math.max(height, placements.get(i).height());
      used += placements.get(i).width() + hgap;
      fillers += filling.contains(placements.get(i).component()) ? 1 : 0;
    }
    boolean bounded = available != Integer.MAX_VALUE;
    int extra = fillers == 0 || !bounded ? 0 : Math.max(0, available - used) / fillers;
    int shift = 0;
    for (int i = first; i < placements.size(); i++) {
      Placement p = placements.get(i);
      int grow = filling.contains(p.component()) ? extra : 0;
      placements.set(
          i,
          new Placement(
              p.component(),
              p.x() + shift,
              y + (height - p.height()) / 2,
              p.width() + grow,
              p.height()));
      shift += grow;
    }
    line.clear();
    return height;
  }

  private static int heightOf(List<Placement> placements) {
    int height = 0;
    for (Placement p : placements) {
      height = Math.max(height, p.y() + p.height());
    }
    return height;
  }

  /** Width inside the insets, unbounded while the container has no width yet. */
  private static int available(Container parent) {
    Insets insets = parent.getInsets();
    return parent.getWidth() <= 0
        ? Integer.MAX_VALUE
        : Math.max(1, parent.getWidth() - insets.left - insets.right);
  }

  private int widestComponent(Container parent) {
    int width = 0;
    for (Component c : parent.getComponents()) {
      if (c.isVisible()) {
        width = Math.max(width, c.getMinimumSize().width);
      }
    }
    return width;
  }

  @Override
  public Dimension preferredLayoutSize(Container parent) {
    synchronized (parent.getTreeLock()) {
      Insets insets = parent.getInsets();
      List<Placement> placements = place(parent, available(parent));
      announcedHeight = heightOf(placements) + insets.top + insets.bottom;
      int width = 0;
      if (parent.getWidth() <= 0) {
        for (Placement p : placements) {
          width = Math.max(width, p.x() + p.width());
        }
      } else {
        // The width is imposed: only the height matters
        width = widestComponent(parent);
      }
      return new Dimension(width + insets.left + insets.right, announcedHeight);
    }
  }

  @Override
  public Dimension minimumLayoutSize(Container parent) {
    synchronized (parent.getTreeLock()) {
      Insets insets = parent.getInsets();
      return new Dimension(
          widestComponent(parent) + insets.left + insets.right,
          heightOf(place(parent, available(parent))) + insets.top + insets.bottom);
    }
  }

  @Override
  public Dimension maximumLayoutSize(Container target) {
    return new Dimension(Integer.MAX_VALUE, preferredLayoutSize(target).height);
  }

  @Override
  public void layoutContainer(Container parent) {
    synchronized (parent.getTreeLock()) {
      Insets insets = parent.getInsets();
      List<Placement> placements = place(parent, available(parent));
      for (Placement p : placements) {
        p.component().setBounds(insets.left + p.x(), insets.top + p.y(), p.width(), p.height());
      }
      int height = heightOf(placements) + insets.top + insets.bottom;
      if (height != announcedHeight) {
        // The height announced was for another width
        announcedHeight = height;
        SwingUtilities.invokeLater(() -> WrapGridLayout.revalidate(parent));
      }
    }
  }

  @Override
  public void addLayoutComponent(Component comp, Object constraints) {
    if (KEEP_WITH_NEXT.equals(constraints)) {
      keptWithNext.add(comp);
    } else if (FILL.equals(constraints)) {
      filling.add(comp);
    }
  }

  @Override
  public void addLayoutComponent(String name, Component comp) {
    addLayoutComponent(comp, name);
  }

  @Override
  public void removeLayoutComponent(Component comp) {
    keptWithNext.remove(comp);
    filling.remove(comp);
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
    // Nothing cached but the announced height, checked at the next layout
  }
}
