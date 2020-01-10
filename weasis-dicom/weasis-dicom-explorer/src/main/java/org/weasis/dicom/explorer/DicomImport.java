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

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.Window;
import java.util.ArrayList;

import javax.swing.JButton;
import javax.swing.tree.DefaultMutableTreeNode;

import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.AbstractWizardDialog;

public class DicomImport extends AbstractWizardDialog {
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomImport.class);

    private boolean cancelVeto = false;
    private final DicomModel dicomModel;

    public DicomImport(Window parent, final DicomModel dicomModel) {
        super(parent, Messages.getString("DicomImport.imp_dicom"), ModalityType.APPLICATION_MODAL, //$NON-NLS-1$
            new Dimension(640, 480));
        this.dicomModel = dicomModel;

        final JButton importandClose = new JButton(Messages.getString("DicomImport.impAndClose0")); //$NON-NLS-1$
        importandClose.addActionListener(e -> {
            importSelection();
            cancel();
        });
        final GridBagConstraints gridBagConstraints0 = new GridBagConstraints();
        gridBagConstraints0.insets = new Insets(10, 15, 10, 0);
        gridBagConstraints0.anchor = GridBagConstraints.EAST;
        gridBagConstraints0.gridy = 0;
        gridBagConstraints0.gridx = 0;
        gridBagConstraints0.weightx = 1.0;
        jPanelButtom.add(importandClose, gridBagConstraints0);

        final JButton importButton = new JButton();
        importButton.addActionListener(e -> importSelection());
        importButton.setText(Messages.getString("DicomImport.imp")); //$NON-NLS-1$
        final GridBagConstraints gridBagConstraints1 = new GridBagConstraints();
        gridBagConstraints1.insets = new Insets(10, 15, 10, 0);
        gridBagConstraints1.anchor = GridBagConstraints.EAST;
        gridBagConstraints1.gridy = 0;
        gridBagConstraints1.gridx = 1;
        jPanelButtom.add(importButton, gridBagConstraints1);

        initializePages();
        pack();
        showPageFirstPage();
    }

    @Override
    protected void initializePages() {
        ArrayList<AbstractItemDialogPage> list = new ArrayList<>();
        list.add(new LocalImport());
        list.add(new DicomZipImport());
        list.add(new DicomDirImport());

        BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
        try {
            for (ServiceReference<DicomImportFactory> service : context.getServiceReferences(DicomImportFactory.class,
                null)) {
                DicomImportFactory factory = context.getService(service);
                if (factory != null) {
                    ImportDicom page = factory.createDicomImportPage(null);
                    if (page instanceof AbstractItemDialogPage) {
                        list.add((AbstractItemDialogPage) page);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("init import pages", e); //$NON-NLS-1$
        }

        InsertableUtil.sortInsertable(list);
        for (AbstractItemDialogPage page : list) {
            pagesRoot.add(new DefaultMutableTreeNode(page));
        }
        iniTree();
    }

    private void importSelection() {
        Object object = null;
        try {
            object = jScrollPanePage.getViewport().getComponent(0);
        } catch (Exception ex) {
        }
        if (object instanceof ImportDicom) {
            ImportDicom selectedPage = (ImportDicom) object;
            selectedPage.importDICOM(dicomModel, null);
        }
    }

    public void setCancelVeto(boolean cancelVeto) {
        this.cancelVeto = cancelVeto;
    }

    @Override
    public void cancel() {
        if (cancelVeto) {
            cancelVeto = false;
        } else {
            dispose();
        }
    }

    @Override
    public void dispose() {
        closeAllPages();
        super.dispose();
    }

}
