/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.qr;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import javax.swing.JOptionPane;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.task.CircularProgressBar;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.URLParameters;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.utils.DicomResource;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.ExplorerTask;
import org.weasis.dicom.explorer.HangingProtocols.OpeningViewer;
import org.weasis.dicom.explorer.LoadLocalDicom;
import org.weasis.dicom.explorer.pref.download.SeriesDownloadPrefView;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode.RetrieveType;
import org.weasis.dicom.explorer.pref.node.DefaultDicomNode;
import org.weasis.dicom.explorer.pref.node.DicomWebNode;
import org.weasis.dicom.explorer.pref.node.DicomWebNode.WebType;
import org.weasis.dicom.explorer.rs.RsQueryParams;
import org.weasis.dicom.explorer.rs.RsQueryResult;
import org.weasis.dicom.explorer.wado.DownloadManager;
import org.weasis.dicom.explorer.wado.DownloadManager.PriorityTaskComparator;
import org.weasis.dicom.explorer.wado.DownloadPriority;
import org.weasis.dicom.explorer.wado.LoadRemoteDicomManifest;
import org.weasis.dicom.explorer.wado.LoadSeries;
import org.weasis.dicom.explorer.wado.SeriesInstanceList;
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
import org.weasis.dicom.web.Multipart;

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
        new ObservableEvent(
            ObservableEvent.BasicAction.LOADING_START, explorerDcmModel, null, this));

    ExplorerTask<Boolean, String> loadingTask = null;
    String errorMessage = null;
    final CircularProgressBar progressBar = getBar();
    DicomProgress progress = new DicomProgress();
    progress.addProgressListener(
        p ->
            GuiExecutor.instance()
                .execute(
                    () -> {
                      int c =
                          p.getNumberOfCompletedSuboperations()
                              + p.getNumberOfFailedSuboperations();
                      int r = p.getNumberOfRemainingSuboperations();
                      int t = c + r;
                      if (t > 0) {
                        progressBar.setValue((c * 100) / t);
                      }
                    }));

    addCancelListener(progress);

    DicomParam[] dcmParams = {new DicomParam(Tag.StudyInstanceUID, studies.toArray(new String[0]))};

    Object selectedItem = dicomQrView.getComboDestinationNode().getSelectedItem();
    if (selectedItem instanceof final DefaultDicomNode node) {
      DefaultDicomNode callingNode =
          (DefaultDicomNode) dicomQrView.getComboCallingNode().getSelectedItem();
      if (callingNode == null) {
        errorMessage = Messages.getString("RetrieveTask.no_calling_node");
      } else {
        final DicomState state;
        RetrieveType type =
            (RetrieveType) dicomQrView.getComboDicomRetrieveType().getSelectedItem();
        AdvancedParams params = new AdvancedParams();
        ConnectOptions connectOptions = new ConnectOptions();
        connectOptions.setConnectTimeout(3000);
        connectOptions.setAcceptTimeout(5000);
        params.setConnectOptions(connectOptions);

        if (RetrieveType.CGET == type) {
          File sopClass = ResourceUtil.getResource(DicomResource.CGET_SOP_UID);
          URL url = null;
          if (sopClass.canRead()) {
            try {
              url = sopClass.toURI().toURL();
            } catch (MalformedURLException e) {
              LOGGER.error("SOP Class url conversion", e);
            }
          }
          state =
              CGet.process(
                  params,
                  callingNode.getDicomNodeWithOnlyAET(),
                  node.getDicomNode(),
                  progress,
                  DicomQrView.tempDir,
                  url,
                  dcmParams);
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
            errorMessage = String.format("%s: %s.", msg, e.getMessage()); // NON-NLS
            LOGGER.error("Start DICOM listener", e);
          }

          if (errorMessage != null) {
            state = new DicomState(Status.UnableToProcess, errorMessage, null);
          } else {
            state =
                CMove.process(
                    params,
                    callingNode.getDicomNode(),
                    node.getDicomNode(),
                    callingNode.getAeTitle(),
                    progress,
                    dcmParams);
            if (dicomListener != null) {
              dicomListener.stop();
            }
          }
        } else if (RetrieveType.WADO == type) {
          List<AbstractDicomNode> webNodes =
              AbstractDicomNode.loadDicomNodes(
                  AbstractDicomNode.Type.WEB, AbstractDicomNode.UsageType.RETRIEVE, WebType.WADO);
          String host = getHostname(node.getDicomNode().getHostname());
          String m1 = Messages.getString("RetrieveTask.no_wado_url_match");
          DicomWebNode wnode = getWadoUrl(dicomQrView, host, webNodes, m1);
          DicomModel dicomModel = dicomQrView.getDicomModel();
          if (wnode == null || dicomModel == null) return null;
          WadoParameters wadoParameters =
              new WadoParameters(
                  "local", wnode.getUrl().toString(), false, null, null, null); // NON-NLS
          wnode.getHeaders().forEach(wadoParameters::addHttpTag);

          CFindQueryResult query = new CFindQueryResult(wadoParameters);
          query.fillSeries(
              params,
              callingNode.getDicomNodeWithOnlyAET(),
              node.getDicomNode(),
              dicomModel,
              studies);
          ArcQuery arquery = new ArcQuery(Collections.singletonList(query));
          String wadoXmlGenerated = arquery.xmlManifest(null);
          if (wadoXmlGenerated == null) {
            state =
                new DicomState(
                    Status.UnableToProcess,
                    Messages.getString("RetrieveTask.msg_build_manifest"),
                    null);
          } else {
            List<String> xmlFiles = new ArrayList<>(1);
            try {
              File tempFile = File.createTempFile("wado_", ".xml", AppProperties.APP_TEMP_DIR);
              FileUtil.writeStreamWithIOException(
                  new ByteArrayInputStream(wadoXmlGenerated.getBytes(StandardCharsets.UTF_8)),
                  tempFile);
              xmlFiles.add(tempFile.getPath());

            } catch (Exception e) {
              LOGGER.info("ungzip manifest", e);
            }

            return new LoadRemoteDicomManifest(xmlFiles, explorerDcmModel);
          }
        } else {
          state =
              new DicomState(
                  Status.UnableToProcess,
                  Messages.getString("RetrieveTask.msg_retrieve_type"),
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
            new LoadLocalDicom(
                new File[] {new File(DicomQrView.tempDir.getPath())},
                false,
                explorerDcmModel,
                OpeningViewer.ALL_PATIENTS);
      }

    } else if (selectedItem instanceof DicomWebNode) {
      fillSeries();
    }

    if (errorMessage != null) {
      final String mes = errorMessage;
      final String errorTitle =
          StringUtil.getEmptyStringIfNull(
              dicomQrView.getComboDicomRetrieveType().getSelectedItem());
      GuiExecutor.instance()
          .execute(
              () ->
                  JOptionPane.showMessageDialog(
                      dicomQrView, mes, errorTitle, JOptionPane.ERROR_MESSAGE));
    }

    return loadingTask;
  }

  @Override
  protected void done() {
    this.removeAllCancelListeners();
    explorerDcmModel.firePropertyChange(
        new ObservableEvent(
            ObservableEvent.BasicAction.LOADING_STOP, explorerDcmModel, null, this));
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

  static DicomWebNode getWadoUrl(
      DicomQrView dicomQrView, String host, List<AbstractDicomNode> webNodes, String message1) {
    List<DicomWebNode> wadoURLs = new ArrayList<>();
    for (AbstractDicomNode n : webNodes) {
      if (n instanceof DicomWebNode wn) {
        URL url = wn.getUrl();
        if (WebType.WADO.equals(wn.getWebType())
            && url != null
            && getHostname(url.getHost()).contains(host)) {
          wadoURLs.add(wn);
        }
      }
    }
    if (wadoURLs.isEmpty()) {
      GuiExecutor.instance()
          .execute(
              () ->
                  JOptionPane.showMessageDialog(
                      dicomQrView, message1, null, JOptionPane.ERROR_MESSAGE));
      return null;
    } else if (wadoURLs.size() > 1) {
      GuiExecutor.instance()
          .invokeAndWait(
              () -> {
                Object[] options = wadoURLs.toArray();
                Object response =
                    JOptionPane.showInputDialog(
                        dicomQrView,
                        Messages.getString("RetrieveTask.several_wado_urls"),
                        wadoURLs.get(0).getWebType().toString(),
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        options,
                        options[0]);

                if (response != null) {
                  wadoURLs.clear();
                  wadoURLs.add((DicomWebNode) response);
                }
              });
    }
    return wadoURLs.get(0);
  }

  static String getHostname(String host) {
    if ("127.0.0.1".equals(host) || "127.0.1.1".equals(host) || "::1".equals(host)) { // NON-NLS
      return "localhost"; // NON-NLS
    }
    return host;
  }

  private void fillSeries() {
    DicomModel dicomModel = dicomQrView.getDicomModel();
    if (dicomModel == null) {
      return;
    }
    DicomWebNode retrieveNode = dicomQrView.getRetrieveNode();

    String baseUrl = retrieveNode.getUrl().toString();
    Properties props = new Properties();
    props.setProperty(RsQueryParams.P_DICOMWEB_URL, baseUrl);
    props.setProperty(RsQueryParams.P_ACCEPT_EXT, "transfer-syntax=*"); // NON-NLS

    Map<String, LoadSeries> loadMap = new HashMap<>();
    boolean startDownloading =
        BundleTools.SYSTEM_PREFERENCES.getBooleanProperty(
            SeriesDownloadPrefView.DOWNLOAD_IMMEDIATELY, true);

    WadoParameters wadoParameters = new WadoParameters("", true, true);

    retrieveNode.getHeaders().forEach(wadoParameters::addHttpTag);
    wadoParameters.addHttpTag(
        "Accept", // NON-NLS
        Multipart.MULTIPART_RELATED
            + ";type=\"" // NON-NLS
            + Multipart.ContentType.DICOM // NON-NLS
            + "\";"
            + props.getProperty(RsQueryParams.P_ACCEPT_EXT));

    for (String studyInstanceUID : studies) {
      MediaSeriesGroup study = getStudyNode(dicomModel, studyInstanceUID);

      StringBuilder buf = new StringBuilder(baseUrl);
      buf.append("/studies/"); // NON-NLS
      buf.append(studyInstanceUID);
      buf.append("/series?includefield="); // NON-NLS
      buf.append(RsQueryResult.SERIES_QUERY);
      buf.append(props.getProperty(RsQueryParams.P_QUERY_EXT, ""));

      try {
        LOGGER.debug(RsQueryResult.QIDO_REQUEST, buf);
        URLParameters urlParameters = new URLParameters(retrieveNode.getHeaders());
        List<Attributes> series =
            RsQueryResult.parseJSON(buf.toString(), dicomQrView.getAuthMethod(), urlParameters);
        if (!series.isEmpty()) {
          for (Attributes seriesDataset : series) {
            Series<?> dicomSeries =
                getSeries(study, seriesDataset, loadMap, wadoParameters, baseUrl, startDownloading);
            fillInstance(seriesDataset, dicomSeries, props, urlParameters);
          }
        }
      } catch (Exception e) {
        LOGGER.error("QIDO-RS all series with studyUID {}", studyInstanceUID, e);
      }
    }

    WadoParameters wp = new WadoParameters("", true, true);
    retrieveNode.getHeaders().forEach(wp::addHttpTag);
    wp.addHttpTag("Accept", "image/jpeg"); // NON-NLS

    for (final LoadSeries loadSeries : loadMap.values()) {
      String modality = TagD.getTagValue(loadSeries.getDicomSeries(), Tag.Modality, String.class);
      boolean ps = "PR".equals(modality) || "KO".equals(modality); // NON-NLS
      if (!ps) {
        loadSeries.startDownloadImageReference(wp);
      }
      DownloadManager.addLoadSeries(loadSeries, explorerDcmModel, loadSeries.isStartDownloading());
    }

    // Sort tasks from the download priority order (low number has a higher priority), TASKS
    // is sorted from low to high priority.
    DownloadManager.TASKS.sort(Collections.reverseOrder(new PriorityTaskComparator()));

    DownloadManager.CONCURRENT_EXECUTOR.prestartAllCoreThreads();
  }

  public MediaSeriesGroup getStudyNode(DicomModel dicomModel, String studyUID) {
    Objects.requireNonNull(studyUID);
    for (MediaSeriesGroup pt : dicomModel.getChildren(MediaSeriesGroupNode.rootNode)) {
      for (MediaSeriesGroup st : dicomModel.getChildren(pt)) {
        if (st.matchIdValue(studyUID)) {
          MediaSeriesGroup patient =
              explorerDcmModel.getHierarchyNode(
                  MediaSeriesGroupNode.rootNode, pt.getTagValue(pt.getTagID()));
          if (patient == null) {
            MediaSeriesGroupNode p =
                new MediaSeriesGroupNode(
                    pt.getTagID(), pt.getTagValue(pt.getTagID()), DicomModel.patient.tagView());
            pt.getTagEntrySetIterator().forEachRemaining(e -> p.setTag(e.getKey(), e.getValue()));
            explorerDcmModel.addHierarchyNode(MediaSeriesGroupNode.rootNode, p);
            patient = p;
          }
          MediaSeriesGroup study = explorerDcmModel.getHierarchyNode(patient, studyUID);
          if (study == null) {
            MediaSeriesGroupNode s =
                new MediaSeriesGroupNode(
                    st.getTagID(), st.getTagValue(st.getTagID()), DicomModel.study.tagView());
            st.getTagEntrySetIterator().forEachRemaining(e -> s.setTag(e.getKey(), e.getValue()));
            explorerDcmModel.addHierarchyNode(patient, s);
            study = s;
          }
          return study;
        }
      }
    }
    return null;
  }

  private Series getSeries(
      MediaSeriesGroup study,
      final Attributes seriesDataset,
      Map<String, LoadSeries> loadMap,
      WadoParameters wadoParameters,
      String baseUrl,
      boolean startDownloading) {
    if (seriesDataset == null) {
      throw new IllegalArgumentException("seriesDataset cannot be null");
    }
    String seriesUID = seriesDataset.getString(Tag.SeriesInstanceUID);
    Series dicomSeries = (Series) explorerDcmModel.getHierarchyNode(study, seriesUID);
    if (dicomSeries == null) {
      dicomSeries = new DicomSeries(seriesUID);
      dicomSeries.setTag(TagD.get(Tag.SeriesInstanceUID), seriesUID);
      dicomSeries.setTag(TagW.ExplorerModel, explorerDcmModel);
      dicomSeries.setTag(TagW.WadoParameters, wadoParameters);
      dicomSeries.setTag(TagW.WadoInstanceReferenceList, new SeriesInstanceList());

      TagW[] tags =
          TagD.getTagFromIDs(
              Tag.Modality, Tag.SeriesNumber, Tag.SeriesDescription, Tag.RetrieveURL);
      for (TagW tag : tags) {
        tag.readValue(seriesDataset, dicomSeries);
      }
      if (!StringUtil.hasText(TagD.getTagValue(dicomSeries, Tag.RetrieveURL, String.class))) {
        StringBuilder buf = new StringBuilder(baseUrl);
        buf.append("/studies/"); // NON-NLS
        buf.append(study.getTagValue(TagD.get(Tag.StudyInstanceUID)));
        buf.append("/series/"); // NON-NLS
        buf.append(seriesUID);
        dicomSeries.setTag(TagD.get(Tag.RetrieveURL), buf.toString());
      }

      explorerDcmModel.addHierarchyNode(study, dicomSeries);

      final LoadSeries loadSeries =
          new LoadSeries(
              dicomSeries,
              explorerDcmModel,
              dicomQrView.getAuthMethod(),
              BundleTools.SYSTEM_PREFERENCES.getIntProperty(
                  LoadSeries.CONCURRENT_DOWNLOADS_IN_SERIES, 4),
              true,
              startDownloading);
      loadSeries.setPriority(
          new DownloadPriority(
              explorerDcmModel.getParent(study, DicomModel.patient), study, dicomSeries, true));
      loadMap.put(TagD.getTagValue(dicomSeries, Tag.SeriesInstanceUID, String.class), loadSeries);
    }
    return dicomSeries;
  }

  private void fillInstance(
      Attributes seriesDataset,
      Series<?> dicomSeries,
      Properties props,
      URLParameters urlParameters) {
    String serieInstanceUID = seriesDataset.getString(Tag.SeriesInstanceUID);
    String seriesRetrieveURL = TagD.getTagValue(dicomSeries, Tag.RetrieveURL, String.class);
    if (StringUtil.hasText(serieInstanceUID) && StringUtil.hasText(seriesRetrieveURL)) {
      StringBuilder buf = new StringBuilder(seriesRetrieveURL);
      buf.append("/instances?includefield="); // NON-NLS
      buf.append(RsQueryResult.INSTANCE_QUERY);
      buf.append(props.getProperty(RsQueryParams.P_QUERY_EXT, ""));

      try {
        LOGGER.debug(RsQueryResult.QIDO_REQUEST, buf);
        List<Attributes> instances =
            RsQueryResult.parseJSON(buf.toString(), dicomQrView.getAuthMethod(), urlParameters);
        if (!instances.isEmpty()) {
          SeriesInstanceList seriesInstanceList =
              (SeriesInstanceList) dicomSeries.getTagValue(TagW.WadoInstanceReferenceList);
          if (seriesInstanceList != null) {
            for (Attributes instanceDataSet : instances) {
              RsQueryResult.addSopInstance(instanceDataSet, seriesInstanceList, seriesRetrieveURL);
            }
          }
        }
      } catch (Exception e) {
        LOGGER.error("QIDO-RS all instances with seriesUID {}", serieInstanceUID, e);
      }
    }
  }
}
