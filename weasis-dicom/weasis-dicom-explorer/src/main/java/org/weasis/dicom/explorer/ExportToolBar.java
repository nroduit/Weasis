/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
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
        super("DICOM Export Bar", index);
        setAttachedInsertable(explorer);
        
        final DicomModel model = (DicomModel) explorer.getDataExplorerModel();

        if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.export.dicom", true)) { //$NON-NLS-1$
            final JButton btnExport =
                new JButton(new ImageIcon(ExportToolBar.class.getResource("/icon/32x32/dcm-export.png"))); //$NON-NLS-1$
            btnExport.setToolTipText("Export DICOM");
            btnExport.addActionListener(e -> ImportToolBar.showAction(ExportToolBar.this, model,
                Messages.getString("LocalExport.local_dev"), true)); // $NON-NLS-1$
            add(btnExport);
        }
    }

    public static DefaultAction buildExportAction(Component parent, DicomModel model, String actionName) {
        return new DefaultAction(actionName,
            new ImageIcon(ExportToolBar.class.getResource("/icon/16x16/dcm-export.png")), event -> {
                if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.export.dicom", true)) { //$NON-NLS-1$
                    ImportToolBar.showAction(parent, model, Messages.getString("LocalExport.local_dev"), true); //$NON-NLS-1$
                } else {
                    JOptionPane.showMessageDialog((Component) event.getSource(),
                        Messages.getString("DicomExplorer.export_perm")); //$NON-NLS-1$
                }
            });
    }
}
