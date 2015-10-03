/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.dicom.explorer;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

import javax.swing.JButton;
import javax.swing.tree.DefaultMutableTreeNode;

import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.AbstractWizardDialog;

public class DicomImport extends AbstractWizardDialog {

    private boolean cancelVeto = false;
    private final DicomModel dicomModel;

    public DicomImport(Window parent, final DicomModel dicomModel) {
        super(parent, Messages.getString("DicomImport.imp_dicom"), ModalityType.APPLICATION_MODAL, //$NON-NLS-1$
            new Dimension(640, 480));
        this.dicomModel = dicomModel;

        final JButton importandClose = new JButton(Messages.getString("DicomImport.impAndClose0")); //$NON-NLS-1$
        importandClose.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(final ActionEvent e) {
                importSelection();
                cancel();
            }
        });
        final GridBagConstraints gridBagConstraints_0 = new GridBagConstraints();
        gridBagConstraints_0.insets = new Insets(10, 15, 10, 0);
        gridBagConstraints_0.anchor = GridBagConstraints.EAST;
        gridBagConstraints_0.gridy = 0;
        gridBagConstraints_0.gridx = 0;
        gridBagConstraints_0.weightx = 1.0;
        jPanelButtom.add(importandClose, gridBagConstraints_0);

        final JButton importButton = new JButton();
        importButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(final ActionEvent e) {
                importSelection();
            }
        });
        importButton.setText(Messages.getString("DicomImport.imp")); //$NON-NLS-1$
        final GridBagConstraints gridBagConstraints_1 = new GridBagConstraints();
        gridBagConstraints_1.insets = new Insets(10, 15, 10, 0);
        gridBagConstraints_1.anchor = GridBagConstraints.EAST;
        gridBagConstraints_1.gridy = 0;
        gridBagConstraints_1.gridx = 1;
        jPanelButtom.add(importButton, gridBagConstraints_1);

        initializePages();
        pack();
        showPageFirstPage();
    }

    @Override
    protected void initializePages() {
        ArrayList<AbstractItemDialogPage> list = new ArrayList<AbstractItemDialogPage>();
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
        } catch (InvalidSyntaxException e) {
            e.printStackTrace();
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
