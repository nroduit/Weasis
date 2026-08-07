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

import eu.essilab.lablib.checkboxtree.CheckboxTree;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.JSliderW;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.ui.dialog.PropertiesDialog;
import org.weasis.core.ui.editor.image.HistogramView;
import org.weasis.core.ui.editor.image.ImageRegionStatistics;
import org.weasis.core.ui.model.graphic.imp.seg.GroupTreeNode;
import org.weasis.core.ui.model.graphic.imp.seg.SegContour;
import org.weasis.core.ui.model.graphic.imp.seg.SegRegion;
import org.weasis.core.ui.model.utils.bean.MeasureItem;

public class SegRegionTree extends CheckboxTree {

  protected final JPopupMenu popupMenu = new JPopupMenu();

  protected final SegRegionTool segRegionTool;

  /** Row whose color swatch is currently hovered, or {@code -1} when none. */
  private int hoveredSwatchRow = -1;

  public SegRegionTree(SegRegionTool segRegionTool) {
    this.segRegionTool = segRegionTool;
  }

  public JPopupMenu getPopupMenu() {
    return popupMenu;
  }

  public SegRegionTool getSegRegionTool() {
    return segRegionTool;
  }

  protected void mousePressed(MouseEvent e) {
    popupMenu.removeAll();
    if (SwingUtilities.isRightMouseButton(e)) {
      showPopupMenu(e);
    } else if (SwingUtilities.isLeftMouseButton(e)) {
      pickColorOnSwatch(e.getPoint());
    }
  }

  protected void showPopupMenu(MouseEvent e) {
    DefaultMutableTreeNode node = getTreeNode(e.getPoint());
    if (node != null) {
      boolean leaf = node.isLeaf();
      if (!leaf) {
        addPopupMenuItem(getCheckAllMenuItem(node, true));
        addPopupMenuItem(getCheckAllMenuItem(node, false));
      }
      addPopupMenuItem(getColorMenuItem(node));
      addPopupMenuItem(getOpacityMenuItem(node, e.getPoint()));
      if (leaf) {
        addPopupMenuItem(getSelectionMenuItem(node));
        addPopupMenuItem(getStatisticMenuItem(node));
      }
      popupMenu.show(SegRegionTree.this, e.getX(), e.getY());
    }
  }

  protected void addPopupMenuItem(JMenuItem menuItem) {
    if (menuItem != null) {
      popupMenu.add(menuItem);
    }
  }

  public void initListeners() {
    MouseAdapter adapter =
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent e) {
            SegRegionTree.this.mousePressed(e);
          }

          @Override
          public void mouseMoved(MouseEvent e) {
            setHoveredSwatchRow(swatchRowAt(e.getPoint()));
          }

          @Override
          public void mouseExited(MouseEvent e) {
            setHoveredSwatchRow(-1);
          }
        };
    addMouseListener(adapter);
    addMouseMotionListener(adapter);
  }

  /** Returns {@code true} when the color swatch of the given row is hovered by the mouse. */
  public boolean isSwatchHovered(int row) {
    return row >= 0 && row == hoveredSwatchRow;
  }

  private int swatchRowAt(Point pt) {
    TreePath path = getPathForLocation(pt.x, pt.y);
    if (path != null
        && path.getLastPathComponent() instanceof DefaultMutableTreeNode node
        && isOnColorSwatch(pt, path, node)) {
      return getRowForPath(path);
    }
    return -1;
  }

  private void setHoveredSwatchRow(int row) {
    if (row == hoveredSwatchRow) {
      return;
    }
    int previous = hoveredSwatchRow;
    hoveredSwatchRow = row;
    // A null cursor inherits the parent one, restoring the default tree cursor.
    setCursor(row < 0 ? null : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    repaintRow(previous);
    repaintRow(row);
  }

  private void repaintRow(int row) {
    Rectangle bounds = row < 0 ? null : getRowBounds(row);
    if (bounds != null) {
      repaint(bounds);
    }
  }

  @Override
  public String getToolTipText(MouseEvent evt) {
    TreePath curPath = getPathForLocation(evt.getX(), evt.getY());
    return StructToolTipTreeNode.getSegItemToolTipText(curPath);
  }

  protected DefaultMutableTreeNode getTreeNode(Point mousePosition) {
    TreePath treePath = getPathForLocation(mousePosition.x, mousePosition.y);
    if (treePath != null) {
      Object userObject = treePath.getLastPathComponent();
      if (userObject instanceof DefaultMutableTreeNode) {
        return (DefaultMutableTreeNode) userObject;
      }
    }
    return null;
  }

  protected JMenuItem getCheckAllMenuItem(DefaultMutableTreeNode node, boolean selected) {
    JMenuItem selectAllMenuItem =
        new JMenuItem(
            selected
                ? Messages.getString("select.all.the.child.nodes")
                : Messages.getString("unselect.all.the.child.nodes"));
    selectAllMenuItem.addActionListener(
        e -> {
          if (node != null) {
            Enumeration<?> children = node.children();
            while (children.hasMoreElements()) {
              Object child = children.nextElement();
              if (child instanceof DefaultMutableTreeNode dtm) {
                TreePath tp = new TreePath(dtm.getPath());
                TreeBuilder.setPathSelection(SegRegionTree.this, tp, selected);
              }
            }
          }
        });
    return selectAllMenuItem;
  }

  protected JMenuItem getColorMenuItem(DefaultMutableTreeNode node) {
    JMenuItem jMenuItem = new JMenuItem(Messages.getString("MeasureTool.pick_color"));
    jMenuItem.addActionListener(_ -> pickColor(collectRegions(node)));
    return jMenuItem;
  }

  /** Opens the color chooser when the click lands on the color swatch of a region node. */
  private void pickColorOnSwatch(Point pt) {
    TreePath path = getPathForLocation(pt.x, pt.y);
    if (path != null
        && path.getLastPathComponent() instanceof DefaultMutableTreeNode node
        && isOnColorSwatch(pt, path, node)) {
      List<SegRegion<?>> segRegions = collectRegions(node);
      // Let the tree finish handling the click before showing the modal chooser.
      SwingUtilities.invokeLater(() -> pickColor(segRegions));
    }
  }

  private boolean isOnColorSwatch(Point pt, TreePath path, DefaultMutableTreeNode node) {
    Component comp =
        getCellRenderer()
            .getTreeCellRendererComponent(
                this, node, false, isExpanded(path), node.isLeaf(), getRowForPath(path), false);
    if (comp instanceof SegRegionCellRenderer renderer) {
      Rectangle swatch = renderer.getSwatchBounds(getPathBounds(path));
      return swatch != null && swatch.contains(pt);
    }
    return false;
  }

  /** Applies a user-picked color to the given regions, preserving their current opacity. */
  protected void pickColor(List<SegRegion<?>> segRegions) {
    if (segRegions.isEmpty()) {
      return;
    }
    Color color =
        JColorChooser.showDialog(
            this, Messages.getString("MeasureTool.pick_color"), segRegions.getFirst().getColor());
    if (color == null) {
      return;
    }
    for (SegRegion<?> region : segRegions) {
      Color previous = region.getColor();
      int alpha = previous == null ? 255 : previous.getAlpha();
      region.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
    }
    repaint();
    segRegionTool.updateVisibleNode();
  }

  /** Returns the region of a leaf node, or the regions of all the children of a group node. */
  protected static List<SegRegion<?>> collectRegions(DefaultMutableTreeNode node) {
    if (node == null) {
      return List.of();
    }
    if (node.isLeaf()) {
      return node.getUserObject() instanceof SegRegion<?> region ? List.of(region) : List.of();
    }
    List<SegRegion<?>> segRegions = new ArrayList<>(node.getChildCount());
    Enumeration<?> children = node.children();
    while (children.hasMoreElements()) {
      if (children.nextElement() instanceof DefaultMutableTreeNode dtm
          && dtm.getUserObject() instanceof SegRegion<?> region) {
        segRegions.add(region);
      }
    }
    return segRegions;
  }

  protected JMenuItem getOpacityMenuItem(DefaultMutableTreeNode node, Point pt) {
    JMenuItem jMenuItem = new JMenuItem(PropertiesDialog.FILL_OPACITY);
    jMenuItem.addActionListener(_ -> showSliderInPopup(node, pt));
    return jMenuItem;
  }

  private void showSliderInPopup(DefaultMutableTreeNode node, Point pt) {
    if (node != null) {
      List<SegRegion<?>> segRegions = collectRegions(node);
      if (segRegions.isEmpty()) {
        return;
      }
      // Create a popup menu
      JPopupMenu menu = new JPopupMenu();
      JSliderW jSlider = PropertiesDialog.createOpacitySlider(PropertiesDialog.FILL_OPACITY);
      GuiUtils.setPreferredWidth(jSlider, 250);
      jSlider.setValue((int) (segRegions.getFirst().getInteriorOpacity() * 100f));
      PropertiesDialog.updateSlider(jSlider, PropertiesDialog.FILL_OPACITY);
      jSlider.addChangeListener(
          l -> {
            float value = PropertiesDialog.updateSlider(jSlider, PropertiesDialog.FILL_OPACITY);
            for (SegRegion<?> c : segRegions) {
              c.setInteriorOpacity(value);
            }
            segRegionTool.updateVisibleNode();
          });
      menu.add(jSlider);
      menu.show(SegRegionTree.this, pt.x, pt.y);
    }
  }

  public void updateVisibleNode(DefaultMutableTreeNode start, GroupTreeNode parent) {
    for (Enumeration<TreeNode> children = start.children(); children.hasMoreElements(); ) {
      DefaultMutableTreeNode dtm = (DefaultMutableTreeNode) children.nextElement();
      if (dtm.isLeaf()) {
        TreePath tp = new TreePath(dtm.getPath());
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tp.getLastPathComponent();
        if (node.getUserObject() instanceof SegRegion<?> region) {
          boolean selected = getCheckingModel().isPathChecked(tp);
          region.setSelected(selected);
          region.setVisible(selected && parent.isParentVisible());
        }
      } else if (dtm instanceof GroupTreeNode groupTreeNode) {
        TreePath tp = new TreePath(dtm.getPath());
        boolean selected = getCheckingModel().isPathChecked(tp);
        groupTreeNode.setSelected(selected);
        updateVisibleNode(dtm, groupTreeNode);
      }
    }
  }

  protected JMenuItem getSelectionMenuItem(DefaultMutableTreeNode node) {
    JMenuItem selectAllMenuItem = new JMenuItem(Messages.getString("show.in.the.image.view"));
    selectAllMenuItem.addActionListener(
        _ -> {
          if (node != null
              && node.isLeaf()
              && node.getUserObject() instanceof SegRegion<?> region) {
            segRegionTool.show(region);
          }
        });
    return selectAllMenuItem;
  }

  protected JMenuItem getStatisticMenuItem(DefaultMutableTreeNode node) {
    JMenuItem selectAllMenuItem = new JMenuItem(Messages.getString("pixel.statistics"));
    selectAllMenuItem.addActionListener(
        _ -> {
          if (node != null
              && node.isLeaf()
              && node.getUserObject() instanceof SegRegion<?> region) {
            segRegionTool.computeStatistics(region);
          }
        });
    return selectAllMenuItem;
  }

  public void showStatistics(SegContour contour, MeasurableLayer layer) {
    if (contour == null) {
      return;
    }
    List<MeasureItem> measList =
        ImageRegionStatistics.getImageStatistics(contour.getSegGraphic(), layer, true);

    JPanel tableContainer = HistogramView.buildStatisticsTable(measList);
    JOptionPane.showMessageDialog(
        this.getParent(),
        tableContainer,
        Messages.getString("HistogramView.stats"),
        JOptionPane.PLAIN_MESSAGE,
        null);
  }

  public void setPathSelection(TreePath path, boolean selected) {
    TreeBuilder.setPathSelection(this, path, selected);
  }

  public boolean hasAllParentsChecked(TreePath path) {
    boolean allParentsChecked = true;
    Object[] pathArray = path.getPath();
    // Start from 1 to skip the root node
    for (int i = 1; i < pathArray.length; i++) {
      TreePath parentPath = new TreePath(java.util.Arrays.copyOfRange(pathArray, 0, i + 1));
      if (!getCheckingModel().isPathChecked(parentPath)) {
        allParentsChecked = false;
        break;
      }
    }
    return allParentsChecked;
  }
}
