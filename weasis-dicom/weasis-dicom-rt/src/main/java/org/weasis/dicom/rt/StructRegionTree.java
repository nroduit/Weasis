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

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import org.weasis.core.ui.util.CsvExporter;
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
    JMenuItem jMenuItem = new JMenuItem(Messages.getString("export.as.csv"));
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
        writeToCsv(segRegions);
      }
    }
  }

  private static void writeToCsv(List<StructRegion> segRegions) {
    CsvExporter csv = new CsvExporter();
    csv.addQuotedNameAndSeparator("Label"); // NON-NLS
    csv.addQuotedNameAndSeparator("ROI Observation Label"); // NON-NLS
    csv.addQuotedNameAndSeparator(Messages.getString("thickness") + " [mm]"); // NON-NLS
    csv.addQuotedNameAndSeparator(Messages.getString("volume") + " [cm³]"); // NON-NLS
    if (hasDvh(segRegions)) {
      csv.addQuotedNameAndSeparator(Messages.getString("min.dose") + " [%]");
      csv.addQuotedNameAndSeparator(Messages.getString("max.dose") + " [%]");
      csv.addQuotedName(Messages.getString("mean.dose") + " [%]");
    }
    csv.addEndOfLine();

    StringBuilder sb = csv.getBuilder();
    for (StructRegion region : segRegions) {
      Dvh structureDvh = region.getDvh();
      csv.addQuotedName(region.getLabel());
      csv.addQuotedName(region.getRoiObservationLabel());
      csv.addSeparator();
      sb.append(region.getThickness());
      csv.addSeparator();
      sb.append(region.getVolume());
      csv.addSeparator();
      sb.append(
          structureDvh == null
              ? StringUtil.EMPTY_STRING
              : Dose.calculateRelativeDose(
                  structureDvh.getDvhMinimumDoseCGy(), structureDvh.getPlan().getRxDose()));
      csv.addSeparator();
      sb.append(
          structureDvh == null
              ? StringUtil.EMPTY_STRING
              : Dose.calculateRelativeDose(
                  structureDvh.getDvhMaximumDoseCGy(), structureDvh.getPlan().getRxDose()));
      csv.addSeparator();
      sb.append(
          structureDvh == null
              ? StringUtil.EMPTY_STRING
              : Dose.calculateRelativeDose(
                  structureDvh.getDvhMeanDoseCGy(), structureDvh.getPlan().getRxDose()));
      csv.addEndOfLine();
    }

    csv.copyToClipboard();
  }

  static boolean hasDvh(List<StructRegion> segRegions) {
    for (StructRegion region : segRegions) {
      if (region.getDvh() != null) {
        return true;
      }
    }
    return false;
  }
}
