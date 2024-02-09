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
import org.weasis.core.ui.editor.image.ImageRegionStatistics;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
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
          popupMenu.add(getCheckAllMenuItem(node, true));
          popupMenu.add(getCheckAllMenuItem(node, false));
        }
        popupMenu.add(getOpacityMenuItem(node, e.getPoint()));
        if (leaf) {
          popupMenu.add(getStatisticMenuItem(node));
        }
        popupMenu.show(SegRegionTree.this, e.getX(), e.getY());
      }
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
        new JMenuItem(selected ? "Select all the child nodes" : "Unselect all the child nodes");
    selectAllMenuItem.addActionListener(
        e -> {
          if (node != null) {
            Enumeration<?> children = node.children();
            while (children.hasMoreElements()) {
              Object child = children.nextElement();
              if (child instanceof DefaultMutableTreeNode dtm) {
                TreePath tp = new TreePath(dtm.getPath());
                if (selected) {
                  getCheckingModel().addCheckingPath(tp);
                } else {
                  getCheckingModel().removeCheckingPath(tp);
                }
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

  protected JMenuItem getStatisticMenuItem(DefaultMutableTreeNode node) {
    JMenuItem selectAllMenuItem = new JMenuItem("Pixel statistics from selected view");
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

    JPanel tableContainer = new JPanel();
    tableContainer.setLayout(new BorderLayout());

    JTable jtable =
        MeasureTool.createMultipleRenderingTable(
            new SimpleTableModel(new String[] {}, new Object[][] {}));
    jtable.getTableHeader().setReorderingAllowed(false);

    String[] headers = {
      Messages.getString("MeasureTool.param"), Messages.getString("MeasureTool.val")
    };
    jtable.setModel(new SimpleTableModel(headers, MeasureTool.getLabels(measList)));
    jtable.getColumnModel().getColumn(1).setCellRenderer(new TableNumberRenderer());
    tableContainer.add(jtable.getTableHeader(), BorderLayout.PAGE_START);
    tableContainer.add(jtable, BorderLayout.CENTER);
    jtable.setShowVerticalLines(true);
    jtable.getColumnModel().getColumn(0).setPreferredWidth(120);
    jtable.getColumnModel().getColumn(1).setPreferredWidth(80);
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
}
