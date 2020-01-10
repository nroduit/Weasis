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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.AbstractWizardDialog;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.explorer.internal.Activator;

import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingModel;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.TreeCheckingModel.CheckingMode;

public class DicomExport extends AbstractWizardDialog {
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomExport.class);

    private final DicomModel dicomModel;
    private final CheckTreeModel treeModel;

    public DicomExport(Window parent, final DicomModel dicomModel) {
        super(parent, Messages.getString("DicomExport.exp_dicom"), ModalityType.APPLICATION_MODAL, //$NON-NLS-1$
            new Dimension(640, 480));
        this.dicomModel = dicomModel;
        this.treeModel = new CheckTreeModel(dicomModel);

        final JButton exportandClose = new JButton(Messages.getString("DicomExport.exp_close")); //$NON-NLS-1$
        exportandClose.addActionListener(e -> {
            exportSelection();
            cancel();

        });
        final GridBagConstraints gridBagConstraints0 = new GridBagConstraints();
        gridBagConstraints0.insets = new Insets(10, 15, 10, 0);
        gridBagConstraints0.anchor = GridBagConstraints.EAST;
        gridBagConstraints0.gridy = 0;
        gridBagConstraints0.gridx = 0;
        gridBagConstraints0.weightx = 1.0;
        jPanelButtom.add(exportandClose, gridBagConstraints0);

        final JButton exportButton = new JButton();
        exportButton.addActionListener(e -> exportSelection());
        exportButton.setText(Messages.getString("DicomExport.exp")); //$NON-NLS-1$
        final GridBagConstraints gridBagConstraints1 = new GridBagConstraints();
        gridBagConstraints1.insets = new Insets(10, 15, 10, 0);
        gridBagConstraints1.anchor = GridBagConstraints.EAST;
        gridBagConstraints1.gridy = 0;
        gridBagConstraints1.gridx = 1;
        jPanelButtom.add(exportButton, gridBagConstraints1);

        initializePages();
        pack();
        showPageFirstPage();
    }

    @Override
    protected void initializePages() {
        Hashtable<String, Object> properties = new Hashtable<>();
        properties.put(dicomModel.getClass().getName(), dicomModel);
        properties.put(treeModel.getClass().getName(), treeModel);

        initTreeCheckingModel();

        ArrayList<AbstractItemDialogPage> list = new ArrayList<>();
        list.add(new LocalExport(dicomModel, treeModel));

        BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
        try {
            for (ServiceReference<DicomExportFactory> service : context.getServiceReferences(DicomExportFactory.class,
                null)) {
                DicomExportFactory factory = context.getService(service);
                if (factory != null) {
                    ExportDicom page = factory.createDicomExportPage(properties);
                    if (page instanceof AbstractItemDialogPage) {
                        list.add((AbstractItemDialogPage) page);
                    }
                }
            }
        } catch (InvalidSyntaxException e) {
            LOGGER.error("Insert DICOM export plugins", e); //$NON-NLS-1$
        }

        InsertableUtil.sortInsertable(list);
        for (AbstractItemDialogPage page : list) {
            pagesRoot.add(new DefaultMutableTreeNode(page));
        }

        iniTree();
    }

    /**
     * Set the checking Paths for the CheckTreeModel to the open Series for the current selected Patient only <br>
     *
     * @return
     */
    private void initTreeCheckingModel() {
        TreeCheckingModel checkingModel = treeModel.getCheckingModel();
        checkingModel.setCheckingMode(CheckingMode.PROPAGATE_PRESERVING_UNCHECK);

        DataExplorerView explorer = UIManager.getExplorerplugin(DicomExplorer.NAME);
        if (explorer instanceof DicomExplorer) {

            Set<Series> openSeriesSet = ((DicomExplorer) explorer).getSelectedPatientOpenSeries();
            Object rootNode = treeModel.getModel().getRoot();

            if (!openSeriesSet.isEmpty() && rootNode instanceof DefaultMutableTreeNode) {
                List<TreePath> selectedSeriesPathsList = new ArrayList<>();

                if (rootNode instanceof DefaultMutableTreeNode) {
                    Enumeration<?> enumTreeNode = ((DefaultMutableTreeNode) rootNode).breadthFirstEnumeration();
                    while (enumTreeNode.hasMoreElements()) {
                        Object child = enumTreeNode.nextElement();
                        if (child instanceof DefaultMutableTreeNode) {
                            DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) child;
                            if (treeNode.getLevel() != 3) { // 3 stands for Series Level
                                continue;
                            }

                            Object userObject = treeNode.getUserObject();
                            if (userObject instanceof DicomSeries && openSeriesSet.contains(userObject)) {
                                selectedSeriesPathsList.add(new TreePath(treeNode.getPath()));
                            }
                        }
                    }
                }

                if (!selectedSeriesPathsList.isEmpty()) {
                    TreePath[] seriesCheckingPaths =
                        selectedSeriesPathsList.toArray(new TreePath[selectedSeriesPathsList.size()]);
                    checkingModel.setCheckingPaths(seriesCheckingPaths);
                    treeModel.setDefaultSelectionPaths(selectedSeriesPathsList);
                }
            }
        }
    }

    private void exportSelection() {
        Object object = null;
        try {
            object = jScrollPanePage.getViewport().getComponent(0);
        } catch (Exception e) {
            LOGGER.debug("Failed to extract DICOM export", e); //$NON-NLS-1$
        }
        if (object instanceof ExportDicom) {
            final ExportDicom selectedPage = (ExportDicom) object;
            try {
                selectedPage.exportDICOM(treeModel, null);
            } catch (IOException e) {
                LOGGER.error("DICOM export failed", e); //$NON-NLS-1$
            }
        }
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

    public static Properties getImportExportProperties() {
        return Activator.IMPORT_EXPORT_PERSISTENCE;
    }

}
