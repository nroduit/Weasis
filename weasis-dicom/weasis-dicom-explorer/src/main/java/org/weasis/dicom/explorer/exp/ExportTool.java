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

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.AbstractWizardDialog;
import org.weasis.core.api.gui.util.EmbeddedWizardPanel;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.docking.SidePanel;
import org.weasis.core.ui.docking.Slot;
import org.weasis.core.ui.util.tree.TreeCheckingModel;
import org.weasis.core.ui.util.tree.TreeCheckingModel.CheckingMode;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.LocalPersistence;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.main.DicomExplorer;

/**
 * The DICOM export as a tool of the bottom slot of the explorer side: the export pages (local
 * device, the send page of a plugin) over the tree of loaded series, with the export button under
 * them. It replaces the export dialog and the footer. The tree is built again each time the tool is
 * shown, with the open series of the selected patient checked, so it reflects what is loaded.
 */
public class ExportTool extends PluginTool {

  private static final Logger LOGGER = LoggerFactory.getLogger(ExportTool.class);

  public static final String ID = "export"; // NON-NLS
  public static final String LAST_PAGE = "last.dicom.export.page"; // NON-NLS

  private final DicomModel model;
  private EmbeddedWizardPanel wizard;
  private CheckTreeModel treeModel;

  public ExportTool(DicomModel model) {
    super(ID, Messages.getString("DicomExport.exp_dicom"), Insertable.Type.EXPLORER, 31);
    this.model = Objects.requireNonNull(model);
    setIcon(ResourceUtil.getIcon(ActionIcon.EXPORT_DICOM));
    setLayout(new BorderLayout());
  }

  @Override
  public Slot getPreferredSlot() {
    return Slot.BOTTOM;
  }

  /** Whether the site allows the DICOM export ({@code weasis.export.dicom}). */
  public static boolean isAllowed() {
    return GuiUtils.getUICore()
        .getSystemPreferences()
        .getBooleanProperty("weasis.export.dicom", true);
  }

  /** The export tool of the DICOM explorer, null before the explorer exists. */
  public static ExportTool find() {
    return GuiUtils.getUICore().getExplorerPlugin(DicomExplorer.NAME)
            instanceof DicomExplorer explorer
        ? explorer.getExportTool()
        : null;
  }

  /**
   * Opens the export tool in the bottom slot of the explorer side, on the page of that title when
   * given.
   *
   * @return the wizard the tool shows, null when there is no explorer or no shell
   */
  public static EmbeddedWizardPanel open(String pageTitle) {
    ExportTool tool = find();
    return tool == null ? null : tool.openOn(pageTitle);
  }

  /** {@link #open(String)} on this tool. */
  public EmbeddedWizardPanel openOn(String pageTitle) {
    ensureContent();
    SidePanel host = GuiUtils.getUICore().getSidePanel(getRole());
    if (host != null) {
      if (!host.contains(this)) {
        host.show(this);
      }
      host.open(ID, Slot.BOTTOM);
      host.expand();
    }
    if (pageTitle != null && wizard != null) {
      wizard.selectPage(pageTitle);
    }
    return wizard;
  }

  /** The wizard the tool shows, null while the tool has no content. */
  public EmbeddedWizardPanel getWizard() {
    return wizard;
  }

  /** The tree of the current content, null while the tool has no content. */
  public CheckTreeModel getTreeModel() {
    return treeModel;
  }

  @Override
  public void addNotify() {
    super.addNotify();
    ensureContent();
  }

  @Override
  public void removeNotify() {
    // the tree is a snapshot of the model: built again on the next show
    release();
    super.removeNotify();
  }

  @Override
  public void closeDockable() {
    release();
    super.closeDockable();
  }

  private void ensureContent() {
    if (wizard != null) {
      return;
    }
    CheckTreeModel tree = new CheckTreeModel(model);
    initTreeCheckingModel(tree);
    JButton exportButton = new JButton(Messages.common("export"));
    JButton help = GuiUtils.createHelpButton("dicom-export/#exporting"); // NON-NLS
    EmbeddedWizardPanel panel =
        new EmbeddedWizardPanel(
            createPages(model, tree),
            GuiUtils.getFlowLayoutPanel(
                FlowLayout.TRAILING,
                AbstractWizardDialog.HORIZONTAL_GAP,
                AbstractWizardDialog.VERTICAL_GAP,
                help,
                exportButton));
    exportButton.addActionListener(
        e -> {
          if (panel.getSelectedPage() instanceof ExportDicom page) {
            try {
              page.exportDICOM(tree, null);
            } catch (IOException ex) {
              LOGGER.error("DICOM export failed", ex);
            }
          }
        });
    panel.selectPage(LocalPersistence.getProperties().getProperty(LAST_PAGE));
    treeModel = tree;
    wizard = panel;
    add(panel, BorderLayout.CENTER);
    revalidate();
    repaint();
  }

  private void release() {
    if (wizard == null) {
      return;
    }
    if (wizard.getSelectedPage() != null) {
      LocalPersistence.getProperties().setProperty(LAST_PAGE, wizard.getSelectedPage().getTitle());
    }
    wizard.closeAllPages();
    remove(wizard);
    wizard = null;
    treeModel = null;
  }

  /** Builds the sorted export pages (local device and plugin contributions). */
  public static List<AbstractItemDialogPage> createPages(
      DicomModel dicomModel, CheckTreeModel treeModel) {
    Map<String, Object> properties = new HashMap<>();
    properties.put(dicomModel.getClass().getName(), dicomModel);
    properties.put(treeModel.getClass().getName(), treeModel);
    ArrayList<AbstractItemDialogPage> list = new ArrayList<>();
    list.add(new LocalExport(dicomModel, treeModel));
    try {
      List<DicomExportFactory> factories =
          GuiUtils.getUICore().getPluginManager().getExtensions(DicomExportFactory.class);
      for (DicomExportFactory factory : factories) {
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
