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

import eu.essilab.lablib.checkboxtree.TreeCheckingModel;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import org.dcm4che3.data.Tag;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.display.Modality;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.exp.CheckTreeModel;
import org.weasis.dicom.explorer.exp.DicomExport;
import org.weasis.dicom.explorer.exp.DicomExportFactory;
import org.weasis.dicom.explorer.exp.ExportDicom;
import org.weasis.dicom.explorer.main.DicomExplorer;

public class DicomExportPR extends DicomExport {

  private static final Logger LOGGER = LoggerFactory.getLogger(DicomExportPR.class);

  private boolean containsPR;

  public DicomExportPR(Window parent, DicomModel dicomModel) {
    super(parent, dicomModel);
  }

  @Override
  protected void initializePages() {
    Hashtable<String, Object> properties = new Hashtable<>();
    properties.put(getDicomModel().getClass().getName(), getDicomModel());
    properties.put(getTreeModel().getClass().getName(), getTreeModel());

    initTreeCheckingModel();

    ArrayList<AbstractItemDialogPage> list = new ArrayList<>();
    BundleContext context = AppProperties.getBundleContext(this.getClass());
    try {
      for (ServiceReference<DicomExportFactory> service :
          context.getServiceReferences(
              DicomExportFactory.class,
              "(component.name=org.weasis.dicom.send.SendDicomFactory)")) {
        DicomExportFactory factory = context.getService(service);
        if (factory != null) {
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
