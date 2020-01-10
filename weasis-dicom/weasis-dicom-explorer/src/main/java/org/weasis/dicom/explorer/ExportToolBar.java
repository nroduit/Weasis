/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
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
        super(Messages.getString("ExportToolBar.dcm_export_bar"), index); //$NON-NLS-1$
        setAttachedInsertable(explorer);
        
        final DicomModel model = (DicomModel) explorer.getDataExplorerModel();

        if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.export.dicom", true)) { //$NON-NLS-1$
            final JButton btnExport =
                new JButton(new ImageIcon(ExportToolBar.class.getResource("/icon/32x32/dcm-export.png"))); //$NON-NLS-1$
            btnExport.setToolTipText(Messages.getString("ExportToolBar.export_dcm")); //$NON-NLS-1$
            btnExport.addActionListener(e -> ImportToolBar.showAction(ExportToolBar.this, model,
                Messages.getString("LocalExport.local_dev"), true)); //$NON-NLS-1$
            add(btnExport);
        }
    }

    public static DefaultAction buildExportAction(Component parent, DicomModel model, String actionName) {
        return new DefaultAction(actionName,
            new ImageIcon(ExportToolBar.class.getResource("/icon/16x16/dcm-export.png")), event -> { //$NON-NLS-1$
                if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.export.dicom", true)) { //$NON-NLS-1$
                    ImportToolBar.showAction(parent, model, Messages.getString("LocalExport.local_dev"), true); //$NON-NLS-1$
                } else {
                    JOptionPane.showMessageDialog((Component) event.getSource(),
                        Messages.getString("DicomExplorer.export_perm")); //$NON-NLS-1$
                }
            });
    }
}
