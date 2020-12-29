/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.weasis.dicom.qr;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.swing.JOptionPane;

import org.dcm4che3.data.Tag;
import org.dcm4che3.net.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.task.CircularProgressBar;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.util.FileUtil;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.ExplorerTask;
import org.weasis.dicom.explorer.LoadLocalDicom;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode.RetrieveType;
import org.weasis.dicom.explorer.pref.node.DefaultDicomNode;
import org.weasis.dicom.explorer.pref.node.DicomWebNode;
import org.weasis.dicom.explorer.wado.LoadRemoteDicomManifest;
import org.weasis.dicom.mf.ArcQuery;
import org.weasis.dicom.mf.WadoParameters;
import org.weasis.dicom.op.CGet;
import org.weasis.dicom.op.CMove;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.ConnectOptions;
import org.weasis.dicom.param.DicomParam;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.param.ListenerParams;
import org.weasis.dicom.qr.manisfest.CFindQueryResult;
import org.weasis.dicom.tool.DicomListener;

public class RetrieveTask extends ExplorerTask<ExplorerTask<Boolean, String>, String> {
    private static final Logger LOGGER = LoggerFactory.getLogger(RetrieveTask.class);

    private final List<String> studies;
    private final DicomModel explorerDcmModel;
    private final DicomQrView dicomQrView;

    public RetrieveTask(List<String> studies, DicomModel explorerDcmModel, DicomQrView dicomQrView) {
        super(AbstractDicomNode.UsageType.RETRIEVE.toString(), false);
        this.studies = studies;
        this.explorerDcmModel = explorerDcmModel;
        this.dicomQrView = dicomQrView;
    }

    @Override
    protected ExplorerTask<Boolean, String> doInBackground() throws Exception {
        explorerDcmModel.firePropertyChange(
            new ObservableEvent(ObservableEvent.BasicAction.LOADING_START, explorerDcmModel, null, this));

        ExplorerTask<Boolean, String> loadingTask = null;
        String errorMessage = null;
        final CircularProgressBar progressBar = getBar();
        DicomProgress progress = new DicomProgress();
        progress.addProgressListener(p -> GuiExecutor.instance().execute(() -> {
            int c = p.getNumberOfCompletedSuboperations() + p.getNumberOfFailedSuboperations();
            int r = p.getNumberOfRemainingSuboperations();
            int t = c + r;
            if (t > 0) {
                progressBar.setValue((c * 100) / t);
            }
        }));

        addCancelListener(progress);

        DicomParam[] dcmParams = { new DicomParam(Tag.StudyInstanceUID, studies.toArray(new String[studies.size()])) };

        Object selectedItem = dicomQrView.getComboDestinationNode().getSelectedItem();
        if (selectedItem instanceof DefaultDicomNode) {
            final DefaultDicomNode node = (DefaultDicomNode) selectedItem;
            DefaultDicomNode callingNode = (DefaultDicomNode) dicomQrView.getComboCallingNode().getSelectedItem();
            if (callingNode == null) {
                errorMessage = Messages.getString("RetrieveTask.no_calling_node");
            } else {
                final DicomState state;
                RetrieveType type = (RetrieveType) dicomQrView.getComboDicomRetrieveType().getSelectedItem();
                AdvancedParams params = new AdvancedParams();
                ConnectOptions connectOptions = new ConnectOptions();
                connectOptions.setConnectTimeout(3000);
                connectOptions.setAcceptTimeout(5000);
                params.setConnectOptions(connectOptions);

                if (RetrieveType.CGET == type) {
                    File sopClass = ResourceUtil.getResource("store-tcs.properties");  //NON-NLS
                    URL url = null;
                    if (sopClass.canRead()) {
                        try {
                            url = sopClass.toURI().toURL();
                        } catch (MalformedURLException e) {
                            LOGGER.error("SOP Class url conversion", e);
                        }
                    }
                    state = CGet.process(params, callingNode.getDicomNodeWithOnlyAET(), node.getDicomNode(), progress,
                        DicomQrView.tempDir, url, dcmParams);
                } else if (RetrieveType.CMOVE == type) {
                    DicomListener dicomListener = dicomQrView.getDicomListener();
                    try {
                        if (dicomListener == null) {
                            errorMessage = Messages.getString("RetrieveTask.msg_start_listener");
                        } else {
                            if (dicomListener.isRunning()) {
                                errorMessage = Messages.getString("RetrieveTask.msg_running_listener");
                            } else {
                                ListenerParams lparams = new ListenerParams(params, true);
                                dicomListener.start(callingNode.getDicomNode(), lparams);
                            }
                        }
                    } catch (Exception e) {
                        if (dicomListener != null) {
                            dicomListener.stop();
                        }
                        String msg = Messages.getString("RetrieveTask.msg_start_listener");
                        errorMessage = String.format("%s: %s.", msg, e.getMessage());  //NON-NLS
                        LOGGER.error("Start DICOM listener", e);
                    }

                    if (errorMessage != null) {
                        state = new DicomState(Status.UnableToProcess, errorMessage, null);
                    } else {
                        state = CMove.process(params, callingNode.getDicomNode(), node.getDicomNode(),
                            callingNode.getAeTitle(), progress, dcmParams);
                        if (dicomListener != null) {
                            dicomListener.stop();
                        }
                    }
                } else if (RetrieveType.WADO == type) {
                    List<AbstractDicomNode> webNodes = AbstractDicomNode.loadDicomNodes(AbstractDicomNode.Type.WEB,
                        AbstractDicomNode.UsageType.RETRIEVE);
                    String host = getHostname(node.getDicomNode().getHostname());
                    List<DicomWebNode> wadoURLs = new ArrayList<>();
                    for (AbstractDicomNode n : webNodes) {
                        if (n instanceof DicomWebNode) {
                            DicomWebNode wn = (DicomWebNode) n;
                            URL url = wn.getUrl();
                            if (DicomWebNode.WebType.WADO == wn.getWebType() && url != null
                                && getHostname(url.getHost()).contains(host)) {
                                wadoURLs.add(wn);
                            }
                        }
                    }
                    if (wadoURLs.isEmpty()) {
                        GuiExecutor.instance()
                            .execute(() -> JOptionPane.showMessageDialog(dicomQrView.getBasePanel(),
                                Messages.getString("RetrieveTask.no_wado_url_match"),
                                RetrieveType.WADO.toString(), JOptionPane.ERROR_MESSAGE));
                        return null;
                    }
                    if (wadoURLs.size() > 1) {
                        GuiExecutor.instance().invokeAndWait(() -> {
                            Object[] options = wadoURLs.toArray();
                            Object response = JOptionPane.showInputDialog(dicomQrView.getBasePanel(),
                                Messages.getString("RetrieveTask.several_wado_urls"), RetrieveType.WADO.toString(),
                                JOptionPane.QUESTION_MESSAGE, null, options, options[0]);

                            if (response != null) {
                                wadoURLs.clear();
                                wadoURLs.add((DicomWebNode) response);
                            }
                        });
                    }

                    DicomWebNode wnode = wadoURLs.get(0);
                    WadoParameters wadoParameters =
                        new WadoParameters("local", wnode.getUrl().toString(), false, null, null, null);  //NON-NLS
                    wnode.getHeaders().forEach(wadoParameters::addHttpTag);
                  
                    CFindQueryResult query = new CFindQueryResult(wadoParameters);
                    query.fillSeries(params, callingNode.getDicomNodeWithOnlyAET(), node.getDicomNode(),
                        dicomQrView.getDicomModel(), studies);
                    ArcQuery arquery = new ArcQuery(Arrays.asList(query));
                    String wadoXmlGenerated = arquery.xmlManifest(null);
                    if (wadoXmlGenerated == null) {
                        state = new DicomState(Status.UnableToProcess,
                            Messages.getString("RetrieveTask.msg_build_manifest"), null);
                    } else {
                        List<String> xmlFiles = new ArrayList<>(1);
                        try {
                            File tempFile = File.createTempFile("wado_", ".xml", AppProperties.APP_TEMP_DIR);
                            FileUtil.writeStreamWithIOException(
                                new ByteArrayInputStream(wadoXmlGenerated.getBytes(StandardCharsets.UTF_8)), tempFile);
                            xmlFiles.add(tempFile.getPath());

                        } catch (Exception e) {
                            LOGGER.info("ungzip manifest", e);
                        }

                        return new LoadRemoteDicomManifest(xmlFiles, explorerDcmModel);
                    }
                } else {
                    state = new DicomState(Status.UnableToProcess, Messages.getString("RetrieveTask.msg_retrieve_type"),
                        null);
                }

                if (state.getStatus() != Status.Success && state.getStatus() != Status.Cancel) {
                    errorMessage = state.getMessage();
                    if (!StringUtil.hasText(errorMessage)) {
                        DicomState.buildMessage(state, null, null);
                    }
                    if (!StringUtil.hasText(errorMessage)) {
                        errorMessage = Messages.getString("RetrieveTask.msg_unexpected_error");
                    }
                    LOGGER.error("Dicom retrieve error: {}", errorMessage);
                }

                loadingTask =
                    new LoadLocalDicom(new File[] { new File(DicomQrView.tempDir.getPath()) }, false, explorerDcmModel);
            }

        } else if (selectedItem instanceof DicomWebNode) {
            throw new IllegalAccessError("Not implemented yet"); 
        } else {
            errorMessage = Messages.getString("RetrieveTask.no_calling_node");
        }

        if (errorMessage != null) {
            final String mes = errorMessage;
            final String errorTitle =
                StringUtil.getEmptyStringIfNull(dicomQrView.getComboDicomRetrieveType().getSelectedItem());
            GuiExecutor.instance().execute(() -> JOptionPane.showMessageDialog(dicomQrView.getBasePanel(), mes,
                errorTitle, JOptionPane.ERROR_MESSAGE));
        }

        return loadingTask;
    }

    @Override
    protected void done() {
        this.removeAllCancelListeners();
        explorerDcmModel.firePropertyChange(
            new ObservableEvent(ObservableEvent.BasicAction.LOADING_STOP, explorerDcmModel, null, this));
        try {
            ExplorerTask<Boolean, String> task = get();
            if (task != null) {
                DicomModel.LOADING_EXECUTOR.execute(task);
            }
        } catch (InterruptedException e) {
            LOGGER.warn("Retrieving task Interruption");
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            LOGGER.error("Retrieving task", e);
        }
    }

    private static String getHostname(String host) {
        if ("127.0.0.1".equals(host) || "127.0.1.1".equals(host) || "::1".equals(host)) { // NON-NLS
            return "localhost";  //NON-NLS
        }
        return host;
    }

}
