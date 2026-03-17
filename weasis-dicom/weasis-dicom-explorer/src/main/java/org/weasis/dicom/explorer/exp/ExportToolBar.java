/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.exp;

import java.awt.*;
import javax.swing.*;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.util.ColorLayerUI;
import org.weasis.core.ui.util.DefaultAction;
import org.weasis.core.ui.util.WtoolBar;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.imp.ImportToolBar;
import org.weasis.dicom.explorer.main.DicomExplorer;
import org.weasis.dicom.explorer.pr.DicomExportPR;

public class ExportToolBar extends WtoolBar {

  public ExportToolBar(int index, DicomExplorer explorer) {
    super(Messages.getString("ExportToolBar.export_dcm"), index);
    setAttachedInsertable(explorer);

    final DicomModel model = (DicomModel) explorer.getDataExplorerModel();

    if (GuiUtils.getUICore()
        .getSystemPreferences()
        .getBooleanProperty("weasis.export.dicom", true)) {
      final JButton btnExport = new JButton(ResourceUtil.getToolBarIcon(ActionIcon.EXPORT_DICOM));
      btnExport.setToolTipText(Messages.getString("ExportToolBar.export_dcm"));
      btnExport.addActionListener(
          e -> ImportToolBar.showAction(ExportToolBar.this, model, null, true));
      add(btnExport);
    }

    if (GuiUtils.getUICore()
        .getSystemPreferences()
        .getBooleanProperty("weasis.export.dicom", true)) {
      final JButton sendButton = new JButton();

      sendButton.setToolTipText(Messages.getString("AnnotationsToolBar.export_pr"));
      sendButton.setIcon(ResourceUtil.getToolBarIcon(ResourceUtil.ActionIcon.EXPORT_ANNOTATIONS));
      sendButton.addActionListener(
          e -> {
            Window win = SwingUtilities.getWindowAncestor(this);
            DicomExportPR dialog = new DicomExportPR(win, model);

            if (dialog.isContainsPR()) {

              dialog.showFirstPage();
              dialog.setAlwaysOnTop(true);
              ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(this);
              ColorLayerUI.showCenterScreen(dialog, layer);
            } else {
              JOptionPane.showMessageDialog(
                  win, Messages.getString("AnnotationsToolBar.error_no_pr"));
            }
          });

      add(sendButton);
    }
  }

  public static DefaultAction buildExportAction(
      Component parent, DicomModel model, String actionName) {
    return new DefaultAction(
        actionName,
        ResourceUtil.getIcon(ActionIcon.EXPORT_DICOM),
        event -> {
          if (GuiUtils.getUICore()
              .getSystemPreferences()
              .getBooleanProperty("weasis.export.dicom", true)) {
            ImportToolBar.showAction(
                parent, model, Messages.getString("LocalExport.local_dev"), true);
          } else {
            JOptionPane.showMessageDialog(
                (Component) event.getSource(), Messages.getString("DicomExplorer.export_perm"));
          }
        });
  }
}
