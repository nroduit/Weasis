package org.weasis.dicom.qr;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.codec.wado.WadoParameters;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.ExplorerTask;
import org.weasis.dicom.explorer.LoadLocalDicom;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode.RetrieveType;
import org.weasis.dicom.explorer.pref.node.DefaultDicomNode;
import org.weasis.dicom.explorer.pref.node.DicomWebNode;
import org.weasis.dicom.explorer.wado.LoadRemoteDicomManifest;
import org.weasis.dicom.op.CGet;
import org.weasis.dicom.op.CMove;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.ConnectOptions;
import org.weasis.dicom.param.DicomParam;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.qr.manisfest.ManifestBuilder;
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
                errorMessage = "No calling DICOM node configured for retrieve";
            } else {
                final DicomState state;
                RetrieveType type = (RetrieveType) dicomQrView.getComboDicomRetrieveType().getSelectedItem();
                AdvancedParams params = new AdvancedParams();
                ConnectOptions connectOptions = new ConnectOptions();
                connectOptions.setConnectTimeout(3000);
                connectOptions.setAcceptTimeout(5000);
                params.setConnectOptions(connectOptions);
                if (RetrieveType.CGET == type) {
                    File sopClass = ResourceUtil.getResource("store-tcs.properties");
                    URL url = null;
                    if (sopClass.canRead()) {
                        try {
                            url = sopClass.toURI().toURL();
                        } catch (MalformedURLException e) {
                            LOGGER.error("SOP Class url conversion", e);
                        }
                    }
                    state = CGet.process(params, callingNode.getDicomNode(), node.getDicomNode(), progress,
                        DicomQrView.tempDir, url, dcmParams);
                } else if (RetrieveType.CMOVE == type) {
                    DicomListener dicomListener = dicomQrView.getDicomListener();
                    try {
                        if (dicomListener == null) {
                            errorMessage = "Cannot start a DICOM listener";
                        } else {
                            dicomListener.setParams(params);
                            if (dicomListener.isRunning()) {
                                errorMessage = "A DICOM C-Move already running";
                            } else {
                                dicomListener.start(callingNode.getDicomNode());
                            }
                        }
                    } catch (Exception e) {
                        dicomListener.stop();
                        errorMessage = String.format("Cannot a start DICOM listener: %s.", e.getMessage());
                        LOGGER.error("Start DICOM listener", e);
                    }

                    if (errorMessage != null) {
                        state = new DicomState(Status.UnableToProcess, errorMessage, null);
                    } else {
                        state = CMove.process(params, callingNode.getDicomNode(), node.getDicomNode(),
                            callingNode.getAeTitle(), progress, dcmParams);
                        dicomListener.stop();
                    }
                } else if (RetrieveType.WADO == type) {
                    List<AbstractDicomNode> webNodes = AbstractDicomNode.loadDicomNodes(AbstractDicomNode.Type.WEB,
                        AbstractDicomNode.UsageType.RETRIEVE);
                    String host = getHostname(node.getDicomNode().getHostname());
                    List<URL> wadoURLs = new ArrayList<>();
                    for (AbstractDicomNode n : webNodes) {
                        if (n instanceof DicomWebNode) {
                            DicomWebNode wn = (DicomWebNode) n;
                            URL url = wn.getUrl();
                            if (DicomWebNode.WebType.WADO == wn.getWebType() && url != null
                                && host.equals(getHostname(url.getHost()))) {
                                wadoURLs.add(url);
                            }
                        }
                    }
                    if (wadoURLs.isEmpty()) {
                        GuiExecutor.instance()
                            .execute(() -> JOptionPane.showMessageDialog(dicomQrView.getBasePanel(),
                                "No URL matchs with DICOM query hostname. Add a new WEB node.",
                                RetrieveType.WADO.toString(), JOptionPane.ERROR_MESSAGE));
                        return null;
                    }
                    if (wadoURLs.size() > 1) {
                        GuiExecutor.instance().invokeAndWait(() -> {
                            Object[] options = wadoURLs.toArray();
                            Object response = JOptionPane.showInputDialog(dicomQrView.getBasePanel(),
                                "Several URLs match, please select one", RetrieveType.WADO.toString(),
                                JOptionPane.QUESTION_MESSAGE, null, options, options[0]);

                            if (response != null) {
                                wadoURLs.clear();
                                wadoURLs.add((URL) response);
                            }
                        });
                    }

                    WadoParameters wadoParameters =
                        new WadoParameters(wadoURLs.get(0).toString(), false, null, null, null);
                    ManifestBuilder manifest = new ManifestBuilder();
                    manifest.fillSeries(params, callingNode.getDicomNode(), node.getDicomNode(), dicomQrView.getDicomModel(), studies);
                    String wadoXmlGenerated = manifest.xmlManifest(wadoParameters, null);
                    if (wadoXmlGenerated == null) {
                        state = new DicomState(Status.UnableToProcess, "Cannot build WADO manifest", null);
                    } else {
                        List<String> xmlFiles = new ArrayList<>(1);
                        try {
                            File tempFile = File.createTempFile("wado_", ".xml", AppProperties.APP_TEMP_DIR); //$NON-NLS-1$ //$NON-NLS-2$
                            FileUtil.writeStreamWithIOException(
                                new ByteArrayInputStream(wadoXmlGenerated.getBytes(StandardCharsets.UTF_8)), tempFile);
                            xmlFiles.add(tempFile.getPath());

                        } catch (Exception e) {
                            LOGGER.info("ungzip manifest", e); //$NON-NLS-1$
                        }

                        return new LoadRemoteDicomManifest(xmlFiles, explorerDcmModel);
                    }
                } else {
                    state = new DicomState(Status.UnableToProcess, "Not supported retrieve type", null);
                }

                if (state.getStatus() != Status.Success && state.getStatus() != Status.Cancel) {
                    errorMessage = state.getMessage();
                    if (!StringUtil.hasText(errorMessage)) {
                        DicomState.buildMessage(state, null, null);
                    }
                    if (!StringUtil.hasText(errorMessage)) {
                        errorMessage = "Unexpected DICOM error";
                    }
                    LOGGER.error("Dicom retrieve error: {}", errorMessage); //$NON-NLS-1$
                }

                loadingTask = new LoadLocalDicom(new File[] { new File(DicomQrView.tempDir.getPath()) }, false, explorerDcmModel);
            }

        } else if (selectedItem instanceof DicomWebNode) {
            throw new IllegalAccessError("Not implemented yet");
        } else {
            errorMessage = "No calling DICOM node configured for retrieve";
        }

        if (errorMessage != null) {
            final String mes = errorMessage;
            final String errorTitle = StringUtil.getEmpty2NullObject(dicomQrView.getComboDicomRetrieveType().getSelectedItem());
            GuiExecutor.instance()
                .execute(() -> JOptionPane.showMessageDialog(dicomQrView.getBasePanel(), mes, errorTitle, JOptionPane.ERROR_MESSAGE));
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
            if(task != null) {
                DicomModel.LOADING_EXECUTOR.execute(task);
            }
       } catch (InterruptedException e) {
           LOGGER.warn("Retrieving task Interruption"); //$NON-NLS-1$
       } catch (ExecutionException e) {
           LOGGER.error("Retrieving task", e); //$NON-NLS-1$
       }
    }

    private static String getHostname(String host) {
        if ("127.0.0.1".equals(host) || "127.0.1.1".equals(host) || "::1".equals(host)) {
            return "localhost";
        }
        return host;
    }

}
