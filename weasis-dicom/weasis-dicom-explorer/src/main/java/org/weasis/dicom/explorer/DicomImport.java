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
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.tree.DefaultMutableTreeNode;

import org.osgi.util.tracker.ServiceTracker;
import org.weasis.core.api.gui.PreferencesPageFactory;
import org.weasis.core.api.gui.util.AbstractWizardDialog;
import org.weasis.dicom.explorer.internal.Activator;

public class DicomImport extends AbstractWizardDialog {

    private final ServiceTracker prefs_tracker;
    private final DicomModel dicomModel;

    public DicomImport(final DicomModel dicomModel) {
        super(null,
            Messages.getString("DicomImport.imp_dicom"), ModalityType.APPLICATION_MODAL, new Dimension(640, 480)); //$NON-NLS-1$
        this.dicomModel = dicomModel;
        prefs_tracker = new ServiceTracker(Activator.getBundleContext(), PreferencesPageFactory.class.getName(), null);
        jPanelButtom.removeAll();
        final GridBagLayout gridBagLayout = new GridBagLayout();
        jPanelButtom.setLayout(gridBagLayout);

        final JButton importandClose = new JButton(Messages.getString("DicomImport.impAndClose0")); //$NON-NLS-1$
        importandClose.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(final ActionEvent e) {
                Object object = null;
                try {
                    object = jScrollPanePage.getViewport().getComponent(0);
                } catch (Exception ex) {
                }
                if (object instanceof ImportDicom) {
                    ImportDicom selectedPage = (ImportDicom) object;
                    selectedPage.importDICOM(dicomModel, null);
                }
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
        });
        importButton.setText(Messages.getString("DicomImport.imp")); //$NON-NLS-1$
        final GridBagConstraints gridBagConstraints_1 = new GridBagConstraints();
        gridBagConstraints_1.insets = new Insets(10, 15, 10, 0);
        gridBagConstraints_1.anchor = GridBagConstraints.EAST;
        gridBagConstraints_1.gridy = 0;
        gridBagConstraints_1.gridx = 1;
        // gridBagConstraints_1.weightx = 1.0;
        jPanelButtom.add(importButton, gridBagConstraints_1);

        jButtonClose.setText(Messages.getString("DicomExport.close")); //$NON-NLS-1$
        final GridBagConstraints gridBagConstraints_2 = new GridBagConstraints();
        gridBagConstraints_2.insets = new Insets(10, 15, 10, 15);
        gridBagConstraints_2.gridy = 0;
        gridBagConstraints_2.gridx = 2;
        jPanelButtom.add(jButtonClose, gridBagConstraints_2);

        initializePages();
        pack();
        initGUI();
    }

    @Override
    protected void initializePages() {
        pagesRoot.add(new DefaultMutableTreeNode(new LocalImport()));
        pagesRoot.add(new DefaultMutableTreeNode(new DicomDirImport()));

        try {
            prefs_tracker.open();

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        // final Object[] servicesPref = prefs_tracker.getServices();
        // ArrayList<AbstractItemDialogPage> list = new ArrayList<AbstractItemDialogPage>();
        // for (int i = 0; (servicesPref != null) && (i < servicesPref.length); i++) {
        // if (servicesPref[i] instanceof PreferencesPageFactory) {
        // AbstractItemDialogPage page =
        // ((PreferencesPageFactory) servicesPref[i]).createPreferencesPage(properties);
        // if (page != null) {
        // list.add(page);
        // }
        // }
        // }
        iniTree();
    }

    @Override
    public void cancel() {
        dispose();
    }

    @Override
    public void dispose() {
        closeAllPages();
        super.dispose();
    }

    private void initGUI() {
        showPageFirstPage();
    }
}
