/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer;

import eu.essilab.lablib.checkboxtree.CheckboxTree;
import eu.essilab.lablib.checkboxtree.TreeCheckingModel;
import java.awt.BorderLayout;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.swing.JPanel;
import javax.swing.tree.TreePath;
import org.weasis.core.ui.util.TreeBuilder;
import org.weasis.dicom.explorer.CheckTreeModel.ToolTipPatientNode;
import org.weasis.dicom.explorer.CheckTreeModel.ToolTipSeriesNode;
import org.weasis.dicom.explorer.CheckTreeModel.ToolTipStudyNode;

public class ExportTree extends JPanel {

  public ExportTree(DicomModel dicomModel) {
    this(new CheckTreeModel(dicomModel));
  }

  public ExportTree(final CheckTreeModel checkTreeModel) {
    this.setLayout(new BorderLayout());
    setCheckboxTreeModel(checkTreeModel);
  }

  public void setCheckboxTreeModel(CheckTreeModel checkTreeModel) {
    Objects.requireNonNull(checkTreeModel);
    CheckboxTree checkboxTree = buildCheckboxTree(checkTreeModel);
    initTree(checkTreeModel, checkboxTree);
    add(checkboxTree, BorderLayout.CENTER);
  }

  public static CheckboxTree buildCheckboxTree(CheckTreeModel checkTreeModel) {
    return new CheckboxTree(checkTreeModel.getModel()) {
      @Override
      public String getToolTipText(MouseEvent evt) {
        if (getRowForLocation(evt.getX(), evt.getY()) == -1) {
          return null;
        }
        TreePath curPath = getPathForLocation(evt.getX(), evt.getY());
        if (curPath != null) {
          Object object = curPath.getLastPathComponent();
          if (object instanceof ToolTipPatientNode tipPatientNode) {
            return tipPatientNode.getToolTipText();
          } else if (object instanceof ToolTipStudyNode tipStudyNode) {
            return tipStudyNode.getToolTipText();
          } else if (object instanceof ToolTipSeriesNode tipSeriesNode) {
            return tipSeriesNode.getToolTipText();
          }
        }
        return null;
      }
    };
  }

  public static void initTree(CheckTreeModel checkTreeModel, CheckboxTree checkboxTree) {
    checkboxTree.setCellRenderer(TreeBuilder.buildNoIconCheckboxTreeCellRenderer());
    // Register tooltips
    checkboxTree.setToolTipText("");

    /*
     At this point checking Paths are supposed to be bound at Series Level but depending on the
     CheckingMode it may also contain parents treeNode paths.<br>
     For medical use recommendation is to default select the whole series related to the studies
     to be analyzed
    */
    TreeCheckingModel checkingModel = checkTreeModel.getCheckingModel();
    TreePath[] checkingPaths = checkTreeModel.getCheckingPaths();
    checkboxTree.setCheckingModel(
        checkingModel); // be aware that checkingPaths is cleared at this point

    if (checkingPaths != null && checkingPaths.length > 0) {
      Set<TreePath> studyPathsSet = new HashSet<>();

      for (TreePath checkingPath : checkingPaths) {
        if (checkingPath.getPathCount() == 4) { // 4 stands for Series Level
          studyPathsSet.add(checkingPath.getParentPath());
        }
      }

      if (!studyPathsSet.isEmpty()) {
        TreePath[] studyCheckingPaths = studyPathsSet.toArray(new TreePath[0]);
        checkboxTree.setCheckingPaths(studyCheckingPaths);
      }

      List<TreePath> selectedPaths = checkTreeModel.getDefaultSelectedPaths();
      if (!selectedPaths.isEmpty()) {
        checkboxTree.setSelectionPaths(selectedPaths.toArray(new TreePath[0]));
      }
    }

    TreeBuilder.expandTree(
        checkboxTree, checkTreeModel.getRootNode(), 2); // 2 stands for Study Level
  }
}
