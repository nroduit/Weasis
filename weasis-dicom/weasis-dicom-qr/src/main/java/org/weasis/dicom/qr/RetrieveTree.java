/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.qr;

import it.cnr.imaa.essi.lablib.gui.checkboxtree.CheckboxTree;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.swing.JPanel;
import javax.swing.tree.TreePath;
import org.weasis.core.api.gui.util.AbstractWizardDialog;
import org.weasis.core.ui.util.CheckBoxTreeBuilder;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.qr.RetrieveTreeModel.ToolTipSeriesNode;
import org.weasis.dicom.qr.RetrieveTreeModel.ToolTipStudyNode;

public class RetrieveTree extends JPanel {

  private RetrieveTreeModel retrieveTreeModel;

  public RetrieveTree() {
    this(new RetrieveTreeModel());
  }

  public RetrieveTree(DicomModel dicomModel) {
    this(new RetrieveTreeModel(dicomModel));
  }

  public RetrieveTree(RetrieveTreeModel retrieveTreeModel) {
    this.setLayout(new BorderLayout());
    setRetrieveTreeModel(retrieveTreeModel);
  }

  public RetrieveTreeModel getRetrieveTreeModel() {
    return retrieveTreeModel;
  }

  public void setRetrieveTreeModel(RetrieveTreeModel retrieveTreeModel) {
    this.retrieveTreeModel = Objects.requireNonNull(retrieveTreeModel);
    CheckboxTree checkboxTree =
        new CheckboxTree(retrieveTreeModel.getModel()) {
          @Override
          public String getToolTipText(MouseEvent evt) {
            if (getRowForLocation(evt.getX(), evt.getY()) == -1) {
              return null;
            }
            TreePath curPath = getPathForLocation(evt.getX(), evt.getY());
            if (curPath != null) {
              Object object = curPath.getLastPathComponent();
              if (object instanceof ToolTipStudyNode tipStudyNode) {
                return tipStudyNode.getToolTipText();
              } else if (object instanceof ToolTipSeriesNode tipSeriesNode) {
                return tipSeriesNode.getToolTipText();
              }
            }
            return null;
          }
        };

    checkboxTree.setCellRenderer(CheckBoxTreeBuilder.buildNoIconCheckboxTreeCellRenderer());
    // Register tooltips
    checkboxTree.setToolTipText("");

    /**
     * At this point checking Paths are supposed to be bound at Series Level but depending on the
     * CheckingMode it may also contain parents treeNode paths.<br>
     * For medical use recommendation is to default select the whole series related to the studies
     * to be analyzed
     */
    TreeCheckingModel checkingModel = retrieveTreeModel.getCheckingModel();
    TreePath[] checkingPaths = retrieveTreeModel.getCheckingPaths();
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

      List<TreePath> selectedPaths = retrieveTreeModel.getDefaultSelectedPaths();
      if (!selectedPaths.isEmpty()) {
        checkboxTree.setSelectionPaths(selectedPaths.toArray(new TreePath[0]));
      }
    }

    AbstractWizardDialog.expandTree(
        checkboxTree, retrieveTreeModel.getRootNode(), 2); // 2 stands for Study Level
    removeAll();
    add(checkboxTree, BorderLayout.CENTER);
  }

  public CheckboxTree getCheckboxTree() {
    for (int i = 0; i < getComponentCount(); i++) {
      Component c = getComponent(i);
      if (c instanceof CheckboxTree tree) {
        return tree;
      }
    }
    throw new IllegalStateException("CheckboxTree cannot be null");
  }
}
