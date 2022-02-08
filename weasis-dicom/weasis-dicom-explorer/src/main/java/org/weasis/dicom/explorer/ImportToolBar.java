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

import java.awt.Component;
import java.awt.Window;
import java.io.File;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.AbstractWizardDialog;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.util.ColorLayerUI;
import org.weasis.core.ui.util.DefaultAction;
import org.weasis.core.ui.util.WtoolBar;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.explorer.wado.LoadSeries;

public class ImportToolBar extends WtoolBar {
  private static final Logger LOGGER = LoggerFactory.getLogger(ImportToolBar.class);

  public ImportToolBar(int index, DicomExplorer explorer) {
    super(Messages.getString("ImportToolBar.dcm_import_bar"), index);
    setAttachedInsertable(explorer);

    final DicomModel model = (DicomModel) explorer.getDataExplorerModel();

    if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.import.dicom", true)) {
      final JButton btnImport = new JButton(ResourceUtil.getToolBarIcon(ActionIcon.IMPORT_DICOM));
      btnImport.setToolTipText(Messages.getString("ImportToolBar.import_dcm"));
      btnImport.addActionListener(e -> showAction(ImportToolBar.this, model, null, false));
      add(btnImport);
    }

    if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.import.dicom", true)) {
      final JButton btnImport = new JButton(ResourceUtil.getToolBarIcon(ActionIcon.IMPORT_CD));
      btnImport.setToolTipText(Messages.getString("ImportToolBar.import_dcm_cd"));
      btnImport.addActionListener(
          e ->
              openImportDicomCdAction(
                  ImportToolBar.this, model, Messages.getString("DicomExplorer.dcmCD")));
      add(btnImport);
    }
  }

  public static void openImportDicomCdAction(
      Component parent, DicomModel model, String actionName) {
    File file = DicomDirImport.getDcmDirFromMedia();
    if (file == null) {
      int response =
          JOptionPane.showConfirmDialog(
              SwingUtilities.getWindowAncestor(parent),
              Messages.getString("ImportToolBar.import_cd_question"),
              actionName,
              JOptionPane.YES_NO_OPTION,
              JOptionPane.WARNING_MESSAGE);

      if (response == JOptionPane.YES_OPTION) {
        showAction(parent, model, Messages.getString("DicomDirImport.dicomdir"), false);
      }
    } else {
      List<LoadSeries> loadSeries = DicomDirImport.loadDicomDir(file, model, true);
      if (loadSeries != null && !loadSeries.isEmpty()) {
        DicomModel.LOADING_EXECUTOR.execute(new LoadDicomDir(loadSeries, model));
      } else {
        LOGGER.error("Cannot import DICOM from {}", file);

        int response =
            JOptionPane.showConfirmDialog(
                SwingUtilities.getWindowAncestor(parent),
                Messages.getString("DicomExplorer.mes_import_manual"),
                actionName,
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (response == JOptionPane.YES_OPTION) {
          AbstractWizardDialog dialog =
              showAction(parent, model, Messages.getString("LocalImport.local_dev"), false);
          AbstractItemDialogPage page = dialog.getCurrentPage();
          if (page instanceof LocalImport localImport) {
            localImport.setImportPath(file.getParent());
          }
        }
      }
    }
  }

  static AbstractWizardDialog showAction(
      Component parent, DicomModel model, String pageName, boolean export) {
    ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(parent);
    Window win = SwingUtilities.getWindowAncestor(parent);
    AbstractWizardDialog dialog =
        export ? new DicomExport(win, model) : new DicomImport(win, model);
    if (StringUtil.hasText(pageName)) {
      dialog.showPage(pageName);
    }
    ColorLayerUI.showCenterScreen(dialog, layer);
    return dialog;
  }

  public static DefaultAction buildImportAction(
      Component parent, DicomModel model, String actionName) {
    return new DefaultAction(
        actionName,
        ResourceUtil.getIcon(ActionIcon.IMPORT_DICOM),
        event -> {
          if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.import.dicom", true)) {
            showAction(parent, model, null, false);
          } else {
            JOptionPane.showMessageDialog(
                (Component) event.getSource(), Messages.getString("DicomExplorer.export_perm"));
          }
        });
  }
}
