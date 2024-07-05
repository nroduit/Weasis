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
      DefaultMutableTreeNode node = getTreeNode(e.getPoint());
      if (node != null) {
        boolean leaf = node.isLeaf();
        if (!leaf) {
          addPopupMenuItem(getCheckAllMenuItem(node, true));
          addPopupMenuItem(getCheckAllMenuItem(node, false));
        }
        addPopupMenuItem(getOpacityMenuItem(node, e.getPoint()));
        if (leaf) {
          addPopupMenuItem(getSelectionMenuItem(node));
          addPopupMenuItem(getStatisticMenuItem(node));
        }
        popupMenu.show(SegRegionTree.this, e.getX(), e.getY());
      }
    }
  }

  protected void addPopupMenuItem(JMenuItem menuItem) {
    if (menuItem != null) {
      popupMenu.add(menuItem);
    }
  }

  public void initListeners() {
    addMouseListener(
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent e) {
            SegRegionTree.this.mousePressed(e);
          }
        });
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

  protected JMenuItem getOpacityMenuItem(DefaultMutableTreeNode node, Point pt) {
    JMenuItem jMenuItem = new JMenuItem(PropertiesDialog.FILL_OPACITY);
    jMenuItem.addActionListener(_ -> showSliderInPopup(node, pt));
    return jMenuItem;
  }

  private void showSliderInPopup(DefaultMutableTreeNode node, Point pt) {
    if (node != null) {
      List<SegRegion<?>> segRegions = new ArrayList<>();
      if (node.isLeaf() && node.getUserObject() instanceof SegRegion<?> region) {
        segRegions.add(region);
      } else {
        Enumeration<?> children = node.children();
        while (children.hasMoreElements()) {
          Object child = children.nextElement();
          if (child instanceof DefaultMutableTreeNode dtm
              && dtm.getUserObject() instanceof SegRegion<?> region) {
            segRegions.add(region);
          }
        }
      }

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
