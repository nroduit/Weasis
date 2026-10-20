/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.pr;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.AbstractWizardDialog;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.util.tree.TreeCheckingModel;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.display.Modality;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.exp.CheckTreeModel;
import org.weasis.dicom.explorer.exp.DicomExportFactory;
import org.weasis.dicom.explorer.exp.ExportDicom;
import org.weasis.dicom.explorer.main.DicomExplorer;

public class DicomExportPR extends AbstractWizardDialog {

  private static final Logger LOGGER = LoggerFactory.getLogger(DicomExportPR.class);

  private boolean containsPR;

  private final DicomModel dicomModel;
  private final CheckTreeModel treeModel;

  /**
   * The export of the presentation states drawn in Weasis, as a dialog of its own: the only export
   * left as a dialog, with the send page of the send plugin as its single page.
   */
  public DicomExportPR(Window parent, DicomModel dicomModel) {
    super(
        parent,
        Messages.getString("DicomExport.exp_dicom"),
        ModalityType.APPLICATION_MODAL,
        new Dimension(650, 500));
    this.dicomModel = dicomModel;
    this.treeModel = new CheckTreeModel(dicomModel);
    JButton exportAndClose = new JButton(Messages.getString("DicomExport.exp_close"));
    exportAndClose.addActionListener(
        _ -> {
          exportSelection();
          cancel();
        });
    JButton exportButton = new JButton(Messages.getString("DicomExport.exp"));
    exportButton.addActionListener(_ -> exportSelection());
    JButton jButtonHelp = GuiUtils.createHelpButton("dicom-export/#exporting"); // NON-NLS
    jPanelBottom.removeAll();
    jPanelBottom.add(
        GuiUtils.getFlowLayoutPanel(
            FlowLayout.TRAILING,
            HORIZONTAL_GAP,
            VERTICAL_GAP,
            jButtonHelp,
            exportButton,
            exportAndClose,
            jButtonClose));
    initializePages();
    pack();
  }

  public DicomModel getDicomModel() {
    return dicomModel;
  }

  public CheckTreeModel getTreeModel() {
    return treeModel;
  }

  private void exportSelection() {
    Object object = null;
    try {
      object = jScrollPanePage.getViewport().getComponent(0);
    } catch (Exception e) {
      LOGGER.debug("Failed to extract DICOM export", e);
    }
    if (object instanceof final ExportDicom selectedPage) {
      try {
        selectedPage.exportDICOM(treeModel, null);
      } catch (IOException e) {
        LOGGER.error("DICOM export failed", e);
      }
    }
  }

  @Override
  public void cancel() {
    dispose();
  }

  @Override
  public void dispose() {
    closeAllPages();
    super.dispose();
  }

  @Override
  protected void initializePages() {
    Map<String, Object> properties = new HashMap<>();
    properties.put(getDicomModel().getClass().getName(), getDicomModel());
    properties.put(getTreeModel().getClass().getName(), getTreeModel());

    initTreeCheckingModel();

    ArrayList<AbstractItemDialogPage> list = new ArrayList<>();
    String plugInId = GuiUtils.getUICore().getPluginManager().getPluginId(this.getClass());
    if (plugInId == null) {
      LOGGER.error("PplugInId is null, cannot load DICOM export plugins");
      return;
    }
    try {
      List<DicomExportFactory> factories =
          GuiUtils.getUICore().getPluginManager().getExtensions(DicomExportFactory.class);
      for (DicomExportFactory factory : factories) {
        // Filter to only include SendDicomFactory (by class name to avoid direct dependency)
        if (factory != null
            && "org.weasis.dicom.send.SendDicomFactory".equals(factory.getClass().getName())) {
          ExportDicom page = factory.createDicomExportPage(properties);
          if (page instanceof AbstractItemDialogPage dialogPage) {
            list.add(dialogPage);
          }
        }
      }
    } catch (Exception e) {
      LOGGER.error("Insert DICOM export plugins", e);
    }

    InsertableUtil.sortInsertable(list);
    for (AbstractItemDialogPage page : list) {
      pagesRoot.add(new DefaultMutableTreeNode(page));
    }

    iniTree();
  }

  protected void initTreeCheckingModel() {
    TreeCheckingModel checkingModel = getTreeModel().getCheckingModel();
    checkingModel.setCheckingMode(TreeCheckingModel.CheckingMode.PROPAGATE_PRESERVING_UNCHECK);

    List<DefaultMutableTreeNode> nodesToDelete = new ArrayList<>();

    if (GuiUtils.getUICore().getExplorerPlugin(DicomExplorer.NAME)
        instanceof DicomExplorer explorer) {

      Set<DicomSeries> openSeriesSet = explorer.getSelectedPatientOpenSeries();
      Object rootNode = getTreeModel().getModel().getRoot();

      if (!openSeriesSet.isEmpty() && rootNode instanceof DefaultMutableTreeNode mutableTreeNode) {
        List<TreePath> selectedSeriesPathsList = new ArrayList<>();

        Enumeration<?> enumTreeNode = mutableTreeNode.breadthFirstEnumeration();
        while (enumTreeNode.hasMoreElements()) {
          Object child = enumTreeNode.nextElement();
          if (child instanceof DefaultMutableTreeNode treeNode) {
            if (treeNode.getLevel() == 3) { // 3 stands for Series Level
              Object userObject = treeNode.getUserObject();
              if (userObject instanceof DicomSeries series) {
                // If the modality is PR and it is new
                if (series.getTagValue(TagD.get(Tag.Modality)).equals("PR")
                    && series.getTagValue(TagW.ObjectToSave) != null
                    && isSourceSeriesSuitableForPRExport(
                        series.getTagValue(CheckTreeModel.SourceSeriesForPR))) {
                  selectedSeriesPathsList.add(new TreePath(treeNode.getPath()));
                  containsPR = true;
                } else {
                  nodesToDelete.add(treeNode);
                }
              }
            }
          }
        }

        if (!selectedSeriesPathsList.isEmpty()) {
          TreePath[] seriesCheckingPaths = selectedSeriesPathsList.toArray(new TreePath[0]);
          checkingModel.setCheckingPaths(seriesCheckingPaths);
          getTreeModel().setDefaultSelectionPaths(selectedSeriesPathsList);
        }
        if (!nodesToDelete.isEmpty()) {
          for (DefaultMutableTreeNode node : nodesToDelete) {
            // Flagged nodes to delete are at the Series level
            // Delete all instances
            node.removeAllChildren();
            // Explore the path to delete the parent's nodes if they don't have any children
            TreeNode[] path = node.getPath();
            for (int i = path.length - 1; i >= 0; i--) {
              DefaultMutableTreeNode n = (DefaultMutableTreeNode) path[i];
              if (n.getLevel() == 0) {
                continue; // Root Node All Patients
              } else if (n.isLeaf()) {
                // At the Series, Study or Patient Level, if the node is a Leaf then discard it
                n.removeFromParent();
              }
            }
          }
        }
      }
    }
  }

  public boolean isContainsPR() {
    return containsPR;
  }

  private boolean isSourceSeriesSuitableForPRExport(Object sourceSeries) {
    // List of modalities that can export Presentation States
    List<String> modalities = List.of(Modality.XC.name());
    if (sourceSeries instanceof DicomSeries series) {
      return modalities.contains(series.getTagValue(TagD.get(Tag.Modality)).toString());
    }
    return false;
  }
}
