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
import java.util.function.ToDoubleFunction;
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

  @Override
  protected void mousePressed(MouseEvent e) {
    popupMenu.removeAll();
    if (!SwingUtilities.isRightMouseButton(e)) {
      return;
    }
    DefaultMutableTreeNode node = getTreeNode(e.getPoint());
    if (node == null) {
      return;
    }
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
    popupMenu.show(this, e.getX(), e.getY());
  }

  private JMenuItem getExportMenuItem(DefaultMutableTreeNode node) {
    JMenuItem item = new JMenuItem(Messages.getString("export.as.csv"));
    item.addActionListener(_ -> exportToCSV(node));
    return item;
  }

  private void exportToCSV(DefaultMutableTreeNode node) {
    if (node == null) {
      return;
    }
    List<StructRegion> segRegions = collectStructRegions(node);
    if (!segRegions.isEmpty()) {
      writeToCsv(segRegions);
    }
  }

  private static List<StructRegion> collectStructRegions(DefaultMutableTreeNode node) {
    if (node.isLeaf() && node.getUserObject() instanceof StructRegion region) {
      return List.of(region);
    }
    List<StructRegion> regions = new ArrayList<>(node.getChildCount());
    Enumeration<?> children = node.children();
    while (children.hasMoreElements()) {
      if (children.nextElement() instanceof DefaultMutableTreeNode dtm
          && dtm.getUserObject() instanceof StructRegion region) {
        regions.add(region);
      }
    }
    return regions;
  }

  private static void writeToCsv(List<StructRegion> segRegions) {
    CsvExporter csv = new CsvExporter();
    csv.addQuotedNameAndSeparator("Label"); // NON-NLS
    csv.addQuotedNameAndSeparator("ROI Observation Label"); // NON-NLS
    csv.addQuotedNameAndSeparator(Messages.getString("thickness") + " [mm]"); // NON-NLS
    csv.addQuotedNameAndSeparator(Messages.getString("volume") + " [cm³]"); // NON-NLS
    boolean withDvh = hasDvh(segRegions);
    if (withDvh) {
      csv.addQuotedNameAndSeparator(Messages.getString("min.dose") + " [%]");
      csv.addQuotedNameAndSeparator(Messages.getString("max.dose") + " [%]");
      csv.addQuotedName(Messages.getString("mean.dose") + " [%]");
    }
    csv.addEndOfLine();

    StringBuilder sb = csv.getBuilder();
    for (StructRegion region : segRegions) {
      Dvh dvh = region.getDvh();
      csv.addQuotedName(region.getLabel());
      csv.addQuotedName(region.getRoiObservationLabel());
      csv.addSeparator();
      sb.append(region.getThickness());
      csv.addSeparator();
      sb.append(region.getVolume());
      csv.addSeparator();
      appendRelativeDose(sb, dvh, Dvh::getDvhMinimumDoseCGy);
      csv.addSeparator();
      appendRelativeDose(sb, dvh, Dvh::getDvhMaximumDoseCGy);
      csv.addSeparator();
      appendRelativeDose(sb, dvh, Dvh::getDvhMeanDoseCGy);
      csv.addEndOfLine();
    }
    csv.copyToClipboard();
  }

  private static void appendRelativeDose(
      StringBuilder sb, Dvh dvh, ToDoubleFunction<Dvh> doseInCGy) {
    if (dvh == null) {
      sb.append(StringUtil.EMPTY_STRING);
    } else {
      sb.append(
          Dose.calculateRelativeDose(doseInCGy.applyAsDouble(dvh), dvh.getPlan().getRxDose()));
    }
  }

  static boolean hasDvh(List<StructRegion> segRegions) {
    return segRegions.stream().anyMatch(r -> r.getDvh() != null);
  }
}
