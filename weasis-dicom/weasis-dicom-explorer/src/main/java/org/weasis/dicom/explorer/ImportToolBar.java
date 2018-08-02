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
import java.awt.Window;
import java.io.File;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.AbstractWizardDialog;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.ui.util.ColorLayerUI;
import org.weasis.core.ui.util.DefaultAction;
import org.weasis.core.ui.util.WtoolBar;
import org.weasis.dicom.explorer.wado.LoadSeries;

public class ImportToolBar extends WtoolBar {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImportToolBar.class);

    public ImportToolBar(int index, DicomExplorer explorer) {
        super("DICOM Import Bar", index);
        setAttachedInsertable(explorer);
        
        final DicomModel model = (DicomModel) explorer.getDataExplorerModel();

        if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.import.dicom", true)) { //$NON-NLS-1$
            final JButton btnImport =
                new JButton(new ImageIcon(ImportToolBar.class.getResource("/icon/32x32/dcm-import.png"))); //$NON-NLS-1$
            btnImport.setToolTipText("Import DICOM");
            btnImport.addActionListener(
                e -> showAction(ImportToolBar.this, model, Messages.getString("LocalImport.local_dev"), false) //$NON-NLS-1$
            );
            add(btnImport);
        }

        if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.import.dicom", true)) { //$NON-NLS-1$
            final JButton btnImport =
                new JButton(new ImageIcon(ImportToolBar.class.getResource("/icon/32x32/dcm-import-cd.png"))); //$NON-NLS-1$
            btnImport.setToolTipText("Import DICOM CD");
            btnImport.addActionListener(
                e -> openImportDicomCdAction(ImportToolBar.this, model, Messages.getString("DicomExplorer.dcmCD"))); // $NON-NLS-1$
            add(btnImport);
        }
    }

    public static void openImportDicomCdAction(Component parent, DicomModel model, String actionName) {
        File file = DicomDirImport.getDcmDirFromMedia();
        if (file == null) {
            int response = JOptionPane.showConfirmDialog(SwingUtilities.getWindowAncestor(parent),
                "Cannot find DICOMDIR on media device, do you want to import manually?", actionName,
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

            if (response == JOptionPane.YES_OPTION) {
                showAction(parent, model, Messages.getString("DicomDirImport.dicomdir"), false); //$NON-NLS-1$
            }
        } else {
            List<LoadSeries> loadSeries = DicomDirImport.loadDicomDir(file, model, true);
            if (loadSeries != null && !loadSeries.isEmpty()) {
                DicomModel.LOADING_EXECUTOR.execute(new LoadDicomDir(loadSeries, model));
            } else {
                LOGGER.error("Cannot import DICOM from {}", file); //$NON-NLS-1$

                int response = JOptionPane.showConfirmDialog(SwingUtilities.getWindowAncestor(parent),
                    Messages.getString("DicomExplorer.mes_import_manual"), //$NON-NLS-1$
                    actionName, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

                if (response == JOptionPane.YES_OPTION) {
                    AbstractWizardDialog dialog =
                        showAction(parent, model, Messages.getString("LocalImport.local_dev"), false); //$NON-NLS-1$
                    AbstractItemDialogPage page = dialog.getCurrentPage();
                    if (page instanceof LocalImport) {
                        ((LocalImport) page).setImportPath(file.getParent());
                    }
                }
            }
        }
    }

    static AbstractWizardDialog showAction(Component parent, DicomModel model, String pageName, boolean export) {
        ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(parent);
        Window win = SwingUtilities.getWindowAncestor(parent);
        AbstractWizardDialog dialog = export ? new DicomExport(win, model) : new DicomImport(win, model);
        dialog.showPage(pageName);
        ColorLayerUI.showCenterScreen(dialog, layer);
        return dialog;
    }

    public static DefaultAction buildImportAction(Component parent, DicomModel model, String actionName) {
        return new DefaultAction(actionName,
            new ImageIcon(ImportToolBar.class.getResource("/icon/16x16/dcm-import.png")), event -> {
                if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.import.dicom", true)) { //$NON-NLS-1$
                    showAction(parent, model, Messages.getString("LocalImport.local_dev"), false); //$NON-NLS-1$
                } else {
                    JOptionPane.showMessageDialog((Component) event.getSource(),
                        Messages.getString("DicomExplorer.export_perm")); //$NON-NLS-1$
                }
            });
    }

}
