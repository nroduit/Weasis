package org.weasis.dicom;
/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import org.dcm4che3.data.Tag;
import org.dcm4che3.net.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.task.CircularProgressBar;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.CheckTreeModel;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.ExplorerTask;
import org.weasis.dicom.explorer.ExportDicom;
import org.weasis.dicom.explorer.ExportTree;
import org.weasis.dicom.explorer.LocalExport;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode.UsageType;
import org.weasis.dicom.explorer.pref.node.DefaultDicomNode;
import org.weasis.dicom.explorer.pref.node.DicomWebNode;
import org.weasis.dicom.op.CStore;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;

public class SendDicomView extends AbstractItemDialogPage implements ExportDicom {

    private static final Logger LOGGER = LoggerFactory.getLogger(SendDicomView.class);

    private static final String LAST_SEL_NODE = "lastSelNode";

    private final DicomModel dicomModel;
    private final ExportTree exportTree;

    private JPanel panel;
    private final JComboBox<AbstractDicomNode> comboNode = new JComboBox<>();

    public SendDicomView(DicomModel dicomModel, CheckTreeModel treeModel) {
        super("DICOM Send");
        this.dicomModel = dicomModel;
        this.exportTree = new ExportTree(treeModel);
        initGUI();
        initialize(true);
    }

    public void initGUI() {
        setLayout(new BorderLayout());
        panel = new JPanel();
        FlowLayout flowLayout = (FlowLayout) panel.getLayout();
        flowLayout.setAlignment(FlowLayout.LEFT);

        final JLabel lblDest = new JLabel("Destination" + StringUtil.COLON);
        panel.add(lblDest);
        AbstractDicomNode.addTooltipToComboList(comboNode);
        panel.add(comboNode);

        add(panel, BorderLayout.NORTH);

        add(exportTree, BorderLayout.CENTER);
    }

    protected void initialize(boolean afirst) {
        if (afirst) {
            DefaultDicomNode.loadDicomNodes(comboNode, AbstractDicomNode.Type.DICOM, UsageType.STORAGE);
            DefaultDicomNode.loadDicomNodes(comboNode, AbstractDicomNode.Type.WEB, UsageType.STORAGE);
            String desc = SendDicomFactory.EXPORT_PERSISTENCE.getProperty(LAST_SEL_NODE);
            if (StringUtil.hasText(desc)) {
                ComboBoxModel<AbstractDicomNode> model = comboNode.getModel();
                for (int i = 0; i < model.getSize(); i++) {
                    if (desc.equals(model.getElementAt(i).getDescription())) {
                        model.setSelectedItem(model.getElementAt(i));
                        break;
                    }
                }
            }
        }
    }

    public void resetSettingsToDefault() {
        initialize(false);
    }

    public void applyChange() {
        final DefaultDicomNode node = (DefaultDicomNode) comboNode.getSelectedItem();
        if (node != null) {
            SendDicomFactory.EXPORT_PERSISTENCE.setProperty(LAST_SEL_NODE, node.getDescription());
        }
    }

    protected void updateChanges() {
    }

    @Override
    public void closeAdditionalWindow() {
        applyChange();
    }

    @Override
    public void resetoDefaultValues() {
    }

    @Override
    public void exportDICOM(final CheckTreeModel model, JProgressBar info) throws IOException {

        ExplorerTask task = new ExplorerTask(getTitle(), false, new CircularProgressBar(0, 100), false) {

            @Override
            protected Boolean doInBackground() throws Exception {
                return sendDicomFiles(model, this);
            }

            @Override
            protected void done() {
                dicomModel.firePropertyChange(
                    new ObservableEvent(ObservableEvent.BasicAction.LOADING_STOP, dicomModel, null, this));
            }

        };
        task.execute();

    }

    private boolean sendDicomFiles(final CheckTreeModel model, final ExplorerTask t) throws IOException {
        dicomModel
            .firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.LOADING_START, dicomModel, null, t));
        File exportDir = FileUtil.createTempDir(AppProperties.buildAccessibleTempDirectory("tmp", "send"));
        try {
            writeDicom(t, exportDir, model);

            if (t.isCancelled()) {
                return false;
            }

            String weasisAet = BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.aet", "WEASIS_AE"); //$NON-NLS-1$ //$NON-NLS-2$

            List<String> files = new ArrayList<>();
            files.add(exportDir.getAbsolutePath());

            final CircularProgressBar progressBar = t.getBar();
            DicomProgress dicomProgress = new DicomProgress();
            dicomProgress.addProgressListener(p -> {
                if (t.isCancelled()) {
                    p.cancel();
                }
                GuiExecutor.instance().execute(() -> {
                    int c = p.getNumberOfCompletedSuboperations() + p.getNumberOfFailedSuboperations();
                    int r = p.getNumberOfRemainingSuboperations();
                    progressBar.setValue((c * 100) / (c + r));
                });
            });

            Object selectedItem = comboNode.getSelectedItem();
            if (selectedItem instanceof DefaultDicomNode) {
                final DefaultDicomNode node = (DefaultDicomNode) comboNode.getSelectedItem();
                final DicomState state =
                    CStore.process(new DicomNode(weasisAet), node.getDicomNode(), files, dicomProgress);
                if (state.getStatus() != Status.Success) {
                    LOGGER.error("Dicom send error: {}", state.getMessage());
                    GuiExecutor.instance().execute(() -> JOptionPane.showOptionDialog(exportTree, state.getMessage(),
                        null, JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE, null, null, null));
                }
            } else if (selectedItem instanceof DicomWebNode) {
                // TODO to implement
            }
        } finally {
            FileUtil.recursiveDelete(exportDir);
        }

        return true;
    }

    private void writeDicom(ExplorerTask task, File writeDir, CheckTreeModel model) throws IOException {
        synchronized (model) {
            ArrayList<String> uids = new ArrayList<>();
            TreePath[] paths = model.getCheckingPaths();
            for (TreePath treePath : paths) {
                if (task.isCancelled()) {
                    return;
                }
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();

                if (node.getUserObject() instanceof DicomImageElement) {
                    DicomImageElement img = (DicomImageElement) node.getUserObject();
                    String iuid = TagD.getTagValue(img, Tag.SOPInstanceUID, String.class);
                    int index = uids.indexOf(iuid);
                    if (index == -1) {
                        uids.add(iuid);
                    } else {
                        // Write only once the file for multiframe
                        continue;
                    }

                    String path = LocalExport.buildPath(img, false, false, false, node);
                    File destinationDir = new File(writeDir, path);
                    destinationDir.mkdirs();

                    File destinationFile = new File(destinationDir, iuid);
                    if (img.saveToFile(destinationFile)) {
                        // if (writeGraphics) {
                        // // TODO remove me and use PR
                        // DefaultSerializer.writeMeasurementGraphics(img, destinationFile);
                        // }
                    } else {
                        LOGGER.error("Cannot export DICOM file: {}", img.getFile()); //$NON-NLS-1$
                    }
                } else if (node.getUserObject() instanceof DicomSpecialElement) {
                    DicomSpecialElement dcm = (DicomSpecialElement) node.getUserObject();
                    String iuid = TagD.getTagValue(dcm, Tag.SOPInstanceUID, String.class);
                    String path = LocalExport.buildPath(dcm, false, false, false, node);
                    File destinationDir = new File(writeDir, path);
                    destinationDir.mkdirs();

                    File destinationFile = new File(destinationDir, iuid);
                    if (!dcm.saveToFile(destinationFile)) {
                        // LOGGER.error("Cannot export DICOM file: {}", img.getFile()); //$NON-NLS-1$
                    }
                } else if (node.getUserObject() instanceof MediaElement) {
                    MediaElement dcm = (MediaElement) node.getUserObject();
                    String iuid = TagD.getTagValue(dcm, Tag.SOPInstanceUID, String.class);

                    String path = LocalExport.buildPath(dcm, false, false, false, node);
                    File destinationDir = new File(writeDir, path);
                    destinationDir.mkdirs();

                    File destinationFile = new File(destinationDir, iuid);
                    if (!dcm.saveToFile(destinationFile)) {
                        // LOGGER.error("Cannot export DICOM file: {}", img.getFile()); //$NON-NLS-1$
                    }
                }
            }
        }
    }

}
