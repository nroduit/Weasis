/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.exp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.swing.Icon;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.ui.util.tree.TreeCheckingModel;
import org.weasis.core.ui.util.tree.TreeCheckingModel.CheckingMode;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.TransferKind;
import org.weasis.dicom.explorer.TransferTool;
import org.weasis.dicom.explorer.main.DicomExplorer;

/**
 * A transfer tool whose pages act on a checked tree of the loaded studies: the tree is a snapshot
 * of the model taken on each show, with the open series of the selected patient checked.
 */
public abstract class ExportHostTool extends TransferTool {

  private static final Logger LOGGER = LoggerFactory.getLogger(ExportHostTool.class);

  /** The site switch of the DICOM export, local and network alike. */
  public static final String FEATURE = "weasis.export.dicom"; // NON-NLS

  private final TransferKind kind;
  private CheckTreeModel treeModel;

  protected ExportHostTool(
      String id,
      String title,
      int position,
      Icon icon,
      double share,
      String actionLabel,
      String helpTopic,
      DicomModel model,
      TransferKind kind) {
    super(id, title, position, icon, share, actionLabel, helpTopic, model);
    this.kind = Objects.requireNonNull(kind);
  }

  /** Whether the site allows the DICOM export ({@value #FEATURE}). */
  public static boolean isAllowed() {
    return isAllowed(FEATURE);
  }

  /** The tree of the current content, null while the tool has no content. */
  public CheckTreeModel getTreeModel() {
    return treeModel;
  }

  @Override
  protected boolean isRebuiltOnShow() {
    return true;
  }

  @Override
  protected List<AbstractItemDialogPage> createPages() {
    CheckTreeModel tree = new CheckTreeModel(getModel());
    initTreeCheckingModel(tree);
    treeModel = tree;
    List<AbstractItemDialogPage> list = new ArrayList<>(createOwnPages(tree));
    list.addAll(createPluginPages(kind, getModel(), tree));
    InsertableUtil.sortInsertable(list);
    return list;
  }

  /** The pages of the tool itself, before the plugin contributions. */
  protected List<AbstractItemDialogPage> createOwnPages(CheckTreeModel tree) {
    return List.of();
  }

  @Override
  protected void run(AbstractItemDialogPage page) {
    if (page instanceof ExportDicom exportPage && treeModel != null) {
      try {
        exportPage.exportDICOM(treeModel, null);
      } catch (IOException e) {
        LOGGER.error("DICOM export failed", e);
      }
    }
  }

  /** The export factories of that kind the site enables. */
  public static List<DicomExportFactory> factories(TransferKind kind) {
    try {
      return GuiUtils.getUICore()
          .getPluginManager()
          .getExtensions(DicomExportFactory.class)
          .stream()
          .filter(Objects::nonNull)
          .filter(factory -> factory.getKind() == kind && factory.isAvailable())
          .toList();
    } catch (Exception e) {
      LOGGER.error("List export factories", e);
      return List.of();
    }
  }

  /** The pages of the export factories of that kind, bound to the model and the tree. */
  public static List<AbstractItemDialogPage> createPluginPages(
      TransferKind kind, DicomModel dicomModel, CheckTreeModel treeModel) {
    Map<String, Object> properties = new HashMap<>();
    properties.put(dicomModel.getClass().getName(), dicomModel);
    properties.put(treeModel.getClass().getName(), treeModel);
    List<AbstractItemDialogPage> list = new ArrayList<>();
    for (DicomExportFactory factory : factories(kind)) {
      try {
        if (factory.createDicomExportPage(properties) instanceof AbstractItemDialogPage page) {
          list.add(page);
        }
      } catch (Exception e) {
        LOGGER.error("Create export page of {}", factory.getClass().getName(), e);
      }
    }
    return list;
  }

  /** Checks in the tree the open series of the selected patient. */
  public static void initTreeCheckingModel(CheckTreeModel treeModel) {
    TreeCheckingModel checkingModel = treeModel.getCheckingModel();
    checkingModel.setCheckingMode(CheckingMode.PROPAGATE_PRESERVING_UNCHECK);
    if (GuiUtils.getUICore().getExplorerPlugin(DicomExplorer.NAME)
        instanceof DicomExplorer explorer) {
      Set<DicomSeries> openSeriesSet = explorer.getSelectedPatientOpenSeries();
      Object rootNode = treeModel.getModel().getRoot();
      if (!openSeriesSet.isEmpty() && rootNode instanceof DefaultMutableTreeNode mutableTreeNode) {
        List<TreePath> selectedSeriesPathsList = new ArrayList<>();
        Enumeration<?> enumTreeNode = mutableTreeNode.breadthFirstEnumeration();
        while (enumTreeNode.hasMoreElements()) {
          Object child = enumTreeNode.nextElement();
          if (child instanceof DefaultMutableTreeNode treeNode) {
            if (treeNode.getLevel() != 3) { // 3 stands for Series Level
              continue;
            }
            Object userObject = treeNode.getUserObject();
            if (userObject instanceof DicomSeries && openSeriesSet.contains(userObject)) {
              selectedSeriesPathsList.add(new TreePath(treeNode.getPath()));
            }
          }
        }
        if (!selectedSeriesPathsList.isEmpty()) {
          TreePath[] seriesCheckingPaths = selectedSeriesPathsList.toArray(new TreePath[0]);
          checkingModel.setCheckingPaths(seriesCheckingPaths);
          treeModel.setDefaultSelectionPaths(selectedSeriesPathsList);
        }
      }
    }
  }
}
