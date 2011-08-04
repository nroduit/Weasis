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
import javax.swing.JProgressBar;
import javax.swing.tree.DefaultMutableTreeNode;

import org.weasis.core.api.gui.util.AbstractWizardDialog;
import org.weasis.core.api.util.FontTools;

public class DicomExport extends AbstractWizardDialog {

    private final DicomModel dicomModel;

    public DicomExport(final DicomModel dicomModel) {
        super(null,
            Messages.getString("DicomExport.exp_dicom"), ModalityType.APPLICATION_MODAL, new Dimension(640, 480)); //$NON-NLS-1$
        this.dicomModel = dicomModel;

        jPanelButtom.removeAll();
        final GridBagLayout gridBagLayout = new GridBagLayout();
        jPanelButtom.setLayout(gridBagLayout);

        final JProgressBar info = new JProgressBar();
        info.setFont(FontTools.getFont10());
        final GridBagConstraints gridBagConstraints_0 = new GridBagConstraints();
        gridBagConstraints_0.insets = new Insets(10, 10, 10, 30);
        gridBagConstraints_0.anchor = GridBagConstraints.WEST;
        gridBagConstraints_0.fill = GridBagConstraints.HORIZONTAL;
        gridBagConstraints_0.gridy = 0;
        gridBagConstraints_0.gridx = 0;
        gridBagConstraints_0.weightx = 1.0;
        jPanelButtom.add(info, gridBagConstraints_0);

        final JButton exportButton = new JButton();
        exportButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(final ActionEvent e) {
                Object object = null;
                try {
                    object = jScrollPanePage.getViewport().getComponent(0);
                } catch (Exception ex) {
                }
                if (object instanceof ExportDicom) {
                    ExportDicom selectedPage = (ExportDicom) object;
                    selectedPage.exportDICOM(dicomModel, info);
                }
            }
        });
        exportButton.setText(Messages.getString("DicomExport.exp")); //$NON-NLS-1$
        final GridBagConstraints gridBagConstraints_1 = new GridBagConstraints();
        gridBagConstraints_1.insets = new Insets(10, 50, 10, 0);
        gridBagConstraints_1.anchor = GridBagConstraints.EAST;
        gridBagConstraints_1.gridy = 0;
        gridBagConstraints_1.gridx = 1;
        // gridBagConstraints_1.weightx = 1.0;
        jPanelButtom.add(exportButton, gridBagConstraints_1);

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
        pagesRoot.add(new DefaultMutableTreeNode(new LocalExport()));
        // synchronized (UIManager.PREFERENCES_ENTRY) {
        // List<PageProps> prefs = UIManager.PREFERENCES_ENTRY;
        // for (final PageProps page : prefs) {
        // pagesRoot.add(new DefaultMutableTreeNode(page));
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
    }
}
