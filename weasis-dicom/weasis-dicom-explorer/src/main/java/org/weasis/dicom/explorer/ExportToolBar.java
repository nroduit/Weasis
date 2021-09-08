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
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.ui.util.DefaultAction;
import org.weasis.core.ui.util.WtoolBar;

public class ExportToolBar extends WtoolBar {

  public ExportToolBar(int index, DicomExplorer explorer) {
    super(Messages.getString("ExportToolBar.dcm_export_bar"), index);
    setAttachedInsertable(explorer);

    final DicomModel model = (DicomModel) explorer.getDataExplorerModel();

    if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.export.dicom", true)) {
      final JButton btnExport =
          new JButton(new ImageIcon(ExportToolBar.class.getResource("/icon/32x32/dcm-export.png")));
      btnExport.setToolTipText(Messages.getString("ExportToolBar.export_dcm"));
      btnExport.addActionListener(
          e -> ImportToolBar.showAction(ExportToolBar.this, model, null, true));
      add(btnExport);
    }
  }

  public static DefaultAction buildExportAction(
      Component parent, DicomModel model, String actionName) {
    return new DefaultAction(
        actionName,
        new ImageIcon(ExportToolBar.class.getResource("/icon/16x16/dcm-export.png")),
        event -> {
          if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.export.dicom", true)) {
            ImportToolBar.showAction(
                parent, model, Messages.getString("LocalExport.local_dev"), true);
          } else {
            JOptionPane.showMessageDialog(
                (Component) event.getSource(), Messages.getString("DicomExplorer.export_perm"));
          }
        });
  }
}
