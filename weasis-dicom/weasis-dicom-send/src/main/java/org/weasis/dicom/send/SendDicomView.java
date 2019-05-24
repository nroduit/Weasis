package org.weasis.dicom.send;
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

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

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
import org.dcm4che3.util.UIDUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.task.CircularProgressBar;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.LangUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.api.util.ThreadUtil;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.dicom.codec.DicomImageElement;
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
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.ConnectOptions;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.web.AbstractStowrs.ContentType;
import org.weasis.dicom.web.StowrsMultiFiles;


public class SendDicomView extends AbstractItemDialogPage implements ExportDicom {

    private static final Logger LOGGER = LoggerFactory.getLogger(SendDicomView.class);

    private static final String LAST_SEL_NODE = "lastSelNode"; //$NON-NLS-1$

    private final DicomModel dicomModel;
    private final ExportTree exportTree;
    private final ExecutorService executor = ThreadUtil.buildNewFixedThreadExecutor(3, "Dicom Send task"); //$NON-NLS-1$

    private final JPanel panel = new JPanel();
    private final JComboBox<AbstractDicomNode> comboNode = new JComboBox<>();

    public SendDicomView(DicomModel dicomModel, CheckTreeModel treeModel) {
        super(Messages.getString("SendDicomView.title")); //$NON-NLS-1$
        this.dicomModel = dicomModel;
        this.exportTree = new ExportTree(treeModel);
        initGUI();
        initialize(true);
    }

    public void initGUI() {
        setLayout(new BorderLayout());

        FlowLayout flowLayout = (FlowLayout) panel.getLayout();
        flowLayout.setAlignment(FlowLayout.LEFT);

        final JLabel lblDest = new JLabel(Messages.getString("SendDicomView.destination") + StringUtil.COLON); //$NON-NLS-1$
        panel.add(lblDest);
        AbstractDicomNode.addTooltipToComboList(comboNode);
        panel.add(comboNode);

        add(panel, BorderLayout.NORTH);

        add(exportTree, BorderLayout.CENTER);
    }

    protected void initialize(boolean afirst) {
        if (afirst) {
            AbstractDicomNode.loadDicomNodes(comboNode, AbstractDicomNode.Type.DICOM, UsageType.STORAGE);
            AbstractDicomNode.loadDicomNodes(comboNode, AbstractDicomNode.Type.WEB, UsageType.STORAGE);
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
        final AbstractDicomNode node = (AbstractDicomNode) comboNode.getSelectedItem();
        if (node != null) {
            SendDicomFactory.EXPORT_PERSISTENCE.setProperty(LAST_SEL_NODE, node.getDescription());
        }
    }

    protected void updateChanges() {
    }

    @Override
    public void closeAdditionalWindow() {
        applyChange();
        executor.shutdown();
    }

    @Override
    public void resetoDefaultValues() {
    }

    @Override
    public void exportDICOM(final CheckTreeModel model, JProgressBar info) throws IOException {

        ExplorerTask<Boolean, String> task = new ExplorerTask<Boolean, String>(getTitle(), false) {

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
        executor.execute(task);
    }

    private boolean sendDicomFiles(final CheckTreeModel model, final ExplorerTask<Boolean, String> t)
        throws IOException {
        dicomModel
            .firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.LOADING_START, dicomModel, null, t));
        File exportDir = FileUtil.createTempDir(AppProperties.buildAccessibleTempDirectory("tmp", "send")); //$NON-NLS-1$ //$NON-NLS-2$
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
            dicomProgress.addProgressListener(p -> GuiExecutor.instance().execute(() -> {
                int c = p.getNumberOfCompletedSuboperations() + p.getNumberOfFailedSuboperations();
                int r = p.getNumberOfRemainingSuboperations();
                progressBar.setValue((c * 100) / (c + r));
            }));
            t.addCancelListener(dicomProgress);

            Object selectedItem = comboNode.getSelectedItem();
            if (selectedItem instanceof DefaultDicomNode) {
                final DefaultDicomNode node = (DefaultDicomNode) selectedItem;
                AdvancedParams params = new AdvancedParams();
                ConnectOptions connectOptions = new ConnectOptions();
                connectOptions.setConnectTimeout(3000);
                connectOptions.setAcceptTimeout(5000);
                params.setConnectOptions(connectOptions);
                final DicomState state =
                    CStore.process(params, new DicomNode(weasisAet), node.getDicomNode(), files, dicomProgress);
                if (state.getStatus() != Status.Success && state.getStatus() != Status.Cancel) {
                    showErrorMessage(null, null, state);
                } else {
                    LOGGER.info("Dicom send: {}", state.getMessage());
                }
            } else if (selectedItem instanceof DicomWebNode) {
                DicomWebNode destination = (DicomWebNode) selectedItem;
                try (StowrsMultiFiles stowRS = new StowrsMultiFiles(destination.getUrl().toString(), ContentType.DICOM, AppProperties.WEASIS_NAME, destination.getHeaders())) {
                    stowRS.uploadDicom(files, true);
                } catch (Exception e) {
                    showErrorMessage("StowRS error: {}", e, null);
                }
            }
        } finally {
            FileUtil.recursiveDelete(exportDir);
        }

        return true;
    }

    private void showErrorMessage(String title, Exception e, DicomState state) {
        if (e != null) {
            LOGGER.error(title, e.getMessage()); // $NON-NLS-1$
        }
        GuiExecutor.instance().execute(() -> JOptionPane.showMessageDialog(exportTree,
            state == null ? e.getMessage() : state.getMessage(), getTitle(), JOptionPane.ERROR_MESSAGE));
    }

    private void writeDicom(ExplorerTask<Boolean, String> task, File writeDir, CheckTreeModel model)
        throws IOException {
        synchronized (this) {
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
                        // Write only once the file for multiframes
                        continue;
                    }

                    String path = LocalExport.buildPath(img, false, false, false, node);
                    File destinationDir = new File(writeDir, path);
                    destinationDir.mkdirs();

                    File destinationFile = new File(destinationDir, iuid);
                    if (!img.saveToFile(destinationFile)) {
                        LOGGER.error("Cannot export DICOM file: {}", img.getFileCache().getOriginalFile().orElse(null)); //$NON-NLS-1$
                    }
                } else if (node.getUserObject() instanceof MediaElement) {
                    MediaElement dcm = (MediaElement) node.getUserObject();
                    String iuid = TagD.getTagValue(dcm, Tag.SOPInstanceUID, String.class);

                    String path = LocalExport.buildPath(dcm, false, false, false, node);
                    File destinationDir = new File(writeDir, path);
                    destinationDir.mkdirs();

                    dcm.saveToFile(new File(destinationDir, iuid));
                } else if (node.getUserObject() instanceof Series) {
                    MediaSeries<?> s = (MediaSeries<?>) node.getUserObject();
                    if (LangUtil.getNULLtoFalse((Boolean) s.getTagValue(TagW.ObjectToSave))) {
                        Series<?> series = (Series<?>) s.getTagValue(CheckTreeModel.SourceSeriesForPR);
                        if (series != null) {
                            String seriesInstanceUID = UIDUtils.createUID();
                            for (MediaElement dcm : series.getMedias(null, null)) {
                                GraphicModel grModel = (GraphicModel) dcm.getTagValue(TagW.PresentationModel);
                                if (grModel != null && grModel.hasSerializableGraphics()) {
                                    String path = LocalExport.buildPath(dcm, false, false, false, node);
                                    LocalExport.buildAndWritePR(dcm, false, new File(writeDir, path), null, node,
                                        seriesInstanceUID);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
