/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.rt;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import org.weasis.core.ui.util.SegRegionTool;
import org.weasis.core.ui.util.SegRegionTree;
import org.weasis.core.util.StringUtil;

public class StructRegionTree extends SegRegionTree {

  public StructRegionTree(SegRegionTool segRegionTool) {
    super(segRegionTool);
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
        popupMenu.add(getExportMenuItem(node));
        if (leaf) {
          popupMenu.add(getStatisticMenuItem(node));
        }
        popupMenu.show(StructRegionTree.this, e.getX(), e.getY());
      }
    }
  }

  private JMenuItem getExportMenuItem(DefaultMutableTreeNode node) {
    JMenuItem jMenuItem = new JMenuItem("Export to clipboard as CSV");
    jMenuItem.addActionListener(_ -> exportToCSV(node));
    return jMenuItem;
  }

  private void exportToCSV(DefaultMutableTreeNode node) {
    if (node != null) {
      List<StructRegion> segRegions = new ArrayList<>();
      if (node.isLeaf() && node.getUserObject() instanceof StructRegion region) {
        segRegions.add(region);
      } else {
        Enumeration<?> children = node.children();
        while (children.hasMoreElements()) {
          Object child = children.nextElement();
          if (child instanceof DefaultMutableTreeNode dtm
              && dtm.getUserObject() instanceof StructRegion region) {
            segRegions.add(region);
          }
        }
      }

      if (!segRegions.isEmpty()) {
        StringBuilder sb = new StringBuilder();
        CsvExporter.addQuotedName(sb, "ROI Observation Label");
        sb.append(CsvExporter.separator);
        CsvExporter.addQuotedName(sb, Messages.getString("thickness"));
        sb.append(CsvExporter.separator);
        CsvExporter.addQuotedName(sb, Messages.getString("volume") + " [cm³]");
        sb.append(CsvExporter.separator);
        CsvExporter.addQuotedName(sb, Messages.getString("min.dose") + " [%]");
        sb.append(CsvExporter.separator);
        CsvExporter.addQuotedName(sb, Messages.getString("max.dose") + " [%]");
        sb.append(CsvExporter.separator);
        CsvExporter.addQuotedName(sb, Messages.getString("mean.dose") + " [%]");
        sb.append(System.lineSeparator());

        for (StructRegion region : segRegions) {
          Dvh structureDvh = region.getDvh();

          CsvExporter.addQuotedName(sb, region.getRoiObservationLabel());
          sb.append(CsvExporter.separator);
          sb.append(region.getThickness());
          sb.append(CsvExporter.separator);
          sb.append(region.getVolume());
          sb.append(CsvExporter.separator);
          sb.append(
              structureDvh == null
                  ? StringUtil.EMPTY_STRING
                  : 100.0
                      * Dose.calculateRelativeDose(
                          structureDvh.getDvhMinimumDoseCGy(), structureDvh.getPlan().getRxDose()));
          sb.append(CsvExporter.separator);
          sb.append(
              structureDvh == null
                  ? StringUtil.EMPTY_STRING
                  : 100.0
                      * Dose.calculateRelativeDose(
                          structureDvh.getDvhMaximumDoseCGy(), structureDvh.getPlan().getRxDose()));
          sb.append(CsvExporter.separator);
          sb.append(
              structureDvh == null
                  ? StringUtil.EMPTY_STRING
                  : 100.0
                      * Dose.calculateRelativeDose(
                          structureDvh.getDvhMeanDoseCGy(), structureDvh.getPlan().getRxDose()));
          sb.append(System.lineSeparator());
        }

        StringSelection stringSelection = new StringSelection(sb.toString());
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, null);
      }
    }
  }
}
