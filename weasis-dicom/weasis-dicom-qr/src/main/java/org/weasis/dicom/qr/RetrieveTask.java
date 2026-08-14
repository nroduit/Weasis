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

import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JOptionPane;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.service.QueryRetrieveLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.net.URLParameters;
import org.weasis.core.ui.tp.raven.spinner.SpinnerProgress;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.utils.SeriesInstanceList;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.PluginOpeningStrategy;
import org.weasis.dicom.explorer.exp.ExplorerTask;
import org.weasis.dicom.explorer.pref.download.DicomExplorerPrefView;
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
import org.weasis.dicom.explorer.wado.LoadSeries;
import org.weasis.dicom.mf.WadoParameters;
import org.weasis.dicom.op.CFind;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.ConnectOptions;
import org.weasis.dicom.param.DicomParam;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.tool.DicomListener;
import org.weasis.dicom.web.MultipartConstants;

public class RetrieveTask extends ExplorerTask<ExplorerTask<Boolean, String>, String> {
  private static final Logger LOGGER = LoggerFactory.getLogger(RetrieveTask.class);

  private final RetrieveSelection selection;
  private final DicomModel explorerDcmModel;
  private final DicomQrView dicomQrView;
  protected final PluginOpeningStrategy openingStrategy;

  private final AtomicBoolean cancelRequested = new AtomicBoolean();

  public RetrieveTask(
      RetrieveSelection selection, DicomModel explorerDcmModel, DicomQrView dicomQrView) {
    super(AbstractDicomNode.UsageType.RETRIEVE.toString(), false);
    this.selection = selection;
    this.explorerDcmModel = explorerDcmModel;
    this.dicomQrView = dicomQrView;
    this.openingStrategy = new PluginOpeningStrategy(DownloadManager.getOpeningViewer());
  }

  @Override
  protected ExplorerTask<Boolean, String> doInBackground() throws Exception {
    explorerDcmModel.firePropertyChange(
        new ObservableEvent(
            ObservableEvent.BasicAction.LOADING_START, explorerDcmModel, null, this));

    String errorMessage = null;

    Object selectedItem = dicomQrView.getComboDestinationNode().getSelectedItem();
    if (selectedItem instanceof final DefaultDicomNode node) {
      DefaultDicomNode callingNode =
          (DefaultDicomNode) dicomQrView.getComboCallingNode().getSelectedItem();
      if (callingNode == null) {
        errorMessage = Messages.getString("RetrieveTask.no_calling_node");
      } else {
        RetrieveType type =
            (RetrieveType) dicomQrView.getComboDicomRetrieveType().getSelectedItem();
        AdvancedParams params = new AdvancedParams();
        ConnectOptions connectOptions = new ConnectOptions();
        connectOptions.setConnectTimeout(3000);
        connectOptions.setAcceptTimeout(5000);
        params.setConnectOptions(connectOptions);

        if (RetrieveType.CGET == type || RetrieveType.CMOVE == type) {
          errorMessage = queueSeriesRetrieve(params, callingNode, node, type, null);
        } else if (RetrieveType.WADO == type) {
          WadoParameters wadoParameters = buildWadoUriParameters(node);
          if (wadoParameters == null) {
            return null; // No WADO node matches the archive, the reason is already reported
          }
          errorMessage = queueSeriesRetrieve(params, callingNode, node, type, wadoParameters);
        } else {
          errorMessage = Messages.getString("RetrieveTask.msg_retrieve_type");
          LOGGER.error("Dicom retrieve error: {}", errorMessage);
        }
      }

    } else if (selectedItem instanceof DicomWebNode) {
      fillSeries();
    }

    if (errorMessage != null && !cancelRequested.get()) {
      final String mes = errorMessage;
      final String errorTitle =
          StringUtil.getEmptyStringIfNull(
              dicomQrView.getComboDicomRetrieveType().getSelectedItem());
      GuiExecutor.execute(
          () ->
              JOptionPane.showMessageDialog(
                  WinUtil.getValidComponent(dicomQrView),
                  mes,
                  errorTitle,
                  JOptionPane.ERROR_MESSAGE));
    }

    return null;
  }

  /** Retrieve parameters of the WADO node serving the archive, null when there is no match. */
  private WadoParameters buildWadoUriParameters(DefaultDicomNode node) {
    List<AbstractDicomNode> webNodes =
        AbstractDicomNode.loadDicomNodes(
            AbstractDicomNode.Type.WEB, AbstractDicomNode.UsageType.RETRIEVE, WebType.WADO);
    String host = getHostname(node.getDicomNode().getHostname());
    String m1 = Messages.getString("RetrieveTask.no_wado_url_match");
    DicomWebNode wnode = getWadoUrl(dicomQrView, host, webNodes, m1);
    if (wnode == null) {
      return null;
    }
    WadoParameters wadoParameters =
        new WadoParameters("local", wnode.getUrl().toString(), false, null, null, null); // NON-NLS
    wnode.getHeaders().forEach(wadoParameters::addHttpTag);
    return wadoParameters;
  }

  /**
   * Enumerates the studies with C-FIND and hands one retrieve task per series to the download
   * manager, so that the retrieve is stopped and resumed series by series like a DICOMweb download.
   *
   * @param wadoParameters the WADO node downloading the images, null to retrieve them with the
   *     DIMSE service of the archive
   * @return null on success, otherwise the message to report
   */
  private String queueSeriesRetrieve(
      AdvancedParams params,
      DefaultDicomNode callingNode,
      DefaultDicomNode calledNode,
      RetrieveType type,
      WadoParameters wadoParameters) {
    DicomModel queryModel = dicomQrView.getDicomModel();
    if (queryModel == null) {
      return Messages.getString("RetrieveTask.msg_unexpected_error");
    }

    DicomListener dicomListener = dicomQrView.getDicomListener();
    Path storageDir;
    if (RetrieveType.CMOVE == type) {
      if (dicomListener == null) {
        return Messages.getString("RetrieveTask.msg_start_listener");
      }
      if (dicomListener.isRunning()) {
        return Messages.getString("RetrieveTask.msg_running_listener");
      }
      storageDir = dicomListener.getStoreSCP().getStorageDir();
    } else {
      storageDir = DicomQrView.getSessionTempFolder();
    }

    openingStrategy.setFullImportSession(false);
    RetrieveContext context =
        new RetrieveContext(
            type,
            params,
            callingNode,
            calledNode,
            explorerDcmModel,
            openingStrategy,
            dicomListener,
            storageDir);

    // Each study is queued as soon as it is enumerated: the images of the first one transfer while
    // the remaining studies are still being queried
    boolean queued = false;
    for (String studyUid : selection.getStudyUids()) {
      if (cancelRequested.get()) {
        break;
      }
      List<LoadSeries> tasks = buildStudyTasks(context, queryModel, studyUid, wadoParameters);
      if (!tasks.isEmpty()) {
        if (!queued) {
          openingStrategy.prepareImport();
          queued = true;
        }
        startTasks(context, tasks);
      }
    }

    if (!queued) {
      return cancelRequested.get() ? null : Messages.getString("RetrieveTask.no_series_found");
    }
    return null;
  }

  private List<LoadSeries> buildStudyTasks(
      RetrieveContext context,
      DicomModel queryModel,
      String studyUid,
      WadoParameters wadoParameters) {
    DicomParam[] keys = {
      new DicomParam(Tag.StudyInstanceUID, studyUid),
      CFind.SeriesInstanceUID,
      CFind.Modality,
      CFind.SeriesNumber,
      CFind.SeriesDescription,
      // Gives the progress bar a total without an extra query
      new DicomParam(Tag.NumberOfSeriesRelatedInstances)
    };
    DicomState state =
        CFind.process(
            context.getParams(),
            context.getCallingNodeForQuery(),
            context.getCalledNode(),
            0,
            QueryRetrieveLevel.SERIES,
            keys);
    List<Attributes> seriesRsp = state.getDicomRSP();
    if (seriesRsp == null || seriesRsp.isEmpty()) {
      LOGGER.warn("No series found for study {}: {}", studyUid, state.getMessage());
      return List.of();
    }

    MediaSeriesGroup study = getStudyNode(queryModel, studyUid);
    if (study == null) {
      return List.of();
    }

    boolean startDownloading =
        GuiUtils.getUICore()
            .getSystemPreferences()
            .getBooleanProperty(DicomExplorerPrefView.DOWNLOAD_IMMEDIATELY, true);
    List<LoadSeries> tasks = new ArrayList<>(seriesRsp.size());
    int enumerated = 0;
    for (Attributes seriesDataset : seriesRsp) {
      if (cancelRequested.get()) {
        break;
      }
      if (!selection.contains(studyUid, seriesDataset.getString(Tag.SeriesInstanceUID))) {
        continue;
      }
      DicomSeries dicomSeries = getCFindSeries(study, seriesDataset);
      if (dicomSeries == null) {
        continue;
      }
      LoadSeries task = createSeriesTask(context, dicomSeries, wadoParameters, startDownloading);
      task.setPriority(
          new DownloadPriority(
              explorerDcmModel.getParent(study, DicomModel.patient),
              study,
              dicomSeries,
              context.hasConcurrentRetrieve()));
      tasks.add(task);
      updateEnumerationProgress(++enumerated, seriesRsp.size());
    }
    return tasks;
  }

  /** Downloads the series over HTTP when a WADO node serves the archive, with DIMSE otherwise. */
  private static LoadSeries createSeriesTask(
      RetrieveContext context,
      DicomSeries dicomSeries,
      WadoParameters wadoParameters,
      boolean startDownloading) {
    if (wadoParameters == null) {
      return new LoadQrSeries(dicomSeries, context, startDownloading);
    }
    dicomSeries.setTag(TagW.WadoParameters, wadoParameters);
    return new LoadWadoUriSeries(
        dicomSeries, context, getConcurrentDownloadsInSeries(), startDownloading);
  }

  private static int getConcurrentDownloadsInSeries() {
    return GuiUtils.getUICore()
        .getSystemPreferences()
        .getIntProperty(LoadSeries.CONCURRENT_DOWNLOADS_IN_SERIES, 4);
  }

  /** Adds the series returned by C-FIND to the explorer model, without its instances yet. */
  private DicomSeries getCFindSeries(MediaSeriesGroup study, Attributes seriesDataset) {
    String seriesUid = seriesDataset.getString(Tag.SeriesInstanceUID);
    if (!StringUtil.hasText(seriesUid)) {
      return null;
    }
    if (explorerDcmModel.getHierarchyNode(study, seriesUid) instanceof DicomSeries existing) {
      return existing;
    }
    DicomSeries dicomSeries = new DicomSeries(seriesUid);
    dicomSeries.setTag(TagD.get(Tag.SeriesInstanceUID), seriesUid);
    dicomSeries.setTag(TagW.ExplorerModel, explorerDcmModel);
    dicomSeries.setTag(TagW.WadoInstanceReferenceList, new SeriesInstanceList());
    for (TagW tag :
        TagD.getTagFromIDs(
            Tag.Modality,
            Tag.SeriesNumber,
            Tag.SeriesDescription,
            Tag.NumberOfSeriesRelatedInstances)) {
      tag.readValue(seriesDataset, dicomSeries);
    }
    explorerDcmModel.addHierarchyNode(study, dicomSeries);
    return dicomSeries;
  }

  private void startTasks(RetrieveContext context, List<LoadSeries> tasks) {
    for (LoadSeries task : tasks) {
      if (!DicomModel.isHiddenModality(task.getDicomSeries())) {
        task.createSeriesThumbnail();
      }
      DownloadManager.addLoadSeries(task, explorerDcmModel, task.isStartDownloading());
    }

    // Sort tasks from the download priority order (low number has a higher priority), TASKS
    // is sorted from low to high priority.
    DownloadManager.getTasks().sort(Collections.reverseOrder(new PriorityTaskComparator()));

    if (context.hasConcurrentRetrieve()) {
      DownloadManager.CONCURRENT_EXECUTOR.prestartAllCoreThreads();
    } else {
      DownloadManager.UNIQUE_EXECUTOR.prestartAllCoreThreads();
    }
  }

  private void updateEnumerationProgress(int done, int total) {
    GuiExecutor.execute(
        () -> {
          if (cancelRequested.get()) {
            return;
          }
          SpinnerProgress bar = getBar();
          bar.setIndeterminate(false);
          bar.setMaximum(total);
          bar.setValue(done);
          bar.setString(done + "/" + total);
        });
  }

  /**
   * Stops the enumeration. The series already handed to the download manager keep their own stop
   * and resume controls.
   */
  @Override
  public boolean cancel() {
    cancelRequested.set(true);
    GuiExecutor.execute(() -> getBar().setString(Messages.getString("RetrieveTask.cancelling")));
    return super.cancel();
  }

  @Override
  protected void done() {
    this.removeAllCancelListeners();
    openingStrategy.reset();
    explorerDcmModel.firePropertyChange(
        new ObservableEvent(
            ObservableEvent.BasicAction.LOADING_STOP, explorerDcmModel, null, this));
    try {
      ExplorerTask<Boolean, String> task = get();
      if (task != null) {
        DicomModel.LOADING_EXECUTOR.execute(task);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (CancellationException e) {
      LOGGER.info("The DICOM retrieve has been cancelled");
    } catch (Exception e) {
      LOGGER.error("Retrieving DICOM data:", e);
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
      GuiExecutor.execute(
          () ->
              JOptionPane.showMessageDialog(
                  WinUtil.getValidComponent(dicomQrView),
                  message1,
                  null,
                  JOptionPane.ERROR_MESSAGE));
      return null;
    } else if (wadoURLs.size() > 1) {
      GuiExecutor.invokeAndWait(
          () -> {
            Object[] options = wadoURLs.toArray();
            Object response =
                JOptionPane.showInputDialog(
                    WinUtil.getValidComponent(dicomQrView),
                    Messages.getString("RetrieveTask.several_wado_urls"),
                    wadoURLs.getFirst().getWebType().toString(),
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
    return wadoURLs.getFirst();
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
        GuiUtils.getUICore()
            .getSystemPreferences()
            .getBooleanProperty(DicomExplorerPrefView.DOWNLOAD_IMMEDIATELY, true);

    WadoParameters wadoParameters = new WadoParameters("", true, true);

    retrieveNode.getHeaders().forEach(wadoParameters::addHttpTag);
    wadoParameters.addHttpTag(
        "Accept", // NON-NLS
        MultipartConstants.MULTIPART_RELATED
            + ";type=\"" // NON-NLS
            + MultipartConstants.DicomContentType.DICOM // NON-NLS
            + "\";"
            + props.getProperty(RsQueryParams.P_ACCEPT_EXT));

    // The retrieve headers carry the multipart Accept of the download, which a query must not send
    URLParameters queryParameters = RsQueryResult.jsonQueryParameters(retrieveNode.getHeaders());

    for (String studyInstanceUID : selection.getStudyUids()) {
      MediaSeriesGroup study = getStudyNode(dicomModel, studyInstanceUID);
      String url = RsQueryResult.seriesQueryUrl(baseUrl, studyInstanceUID, null);

      try {
        LOGGER.debug(RsQueryResult.QIDO_REQUEST, url);
        List<Attributes> series =
            RsQueryResult.parseJSON(url, dicomQrView.getAuthMethod(), queryParameters);
        for (Attributes seriesDataset : series) {
          if (!selection.contains(
              studyInstanceUID, seriesDataset.getString(Tag.SeriesInstanceUID))) {
            continue;
          }
          getSeries(study, seriesDataset, loadMap, wadoParameters, baseUrl, startDownloading);
        }
      } catch (Exception e) {
        LOGGER.error("QIDO-RS all series with studyUID {}", studyInstanceUID, e);
      }
    }

    if (!loadMap.isEmpty()) {
      openingStrategy.prepareImport();
      WadoParameters wp = new WadoParameters("", true, true);
      retrieveNode.getHeaders().forEach(wp::addHttpTag);
      wp.addHttpTag("Accept", "image/jpeg"); // NON-NLS

      for (final LoadSeries loadSeries : loadMap.values()) {
        if (!DicomModel.isHiddenModality(loadSeries.getDicomSeries())) {
          loadSeries.startDownloadImageReference(wp);
        }
        loadSeries.setPOpeningStrategy(openingStrategy);
        DownloadManager.addLoadSeries(
            loadSeries, explorerDcmModel, loadSeries.isStartDownloading());
      }

      // Sort tasks from the download priority order (low number has a higher priority), TASKS
      // is sorted from low to high priority.
      DownloadManager.getTasks().sort(Collections.reverseOrder(new PriorityTaskComparator()));

      DownloadManager.CONCURRENT_EXECUTOR.prestartAllCoreThreads();
    }
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

  private DicomSeries getSeries(
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
    DicomSeries dicomSeries = (DicomSeries) explorerDcmModel.getHierarchyNode(study, seriesUID);
    if (dicomSeries == null) {
      dicomSeries = new DicomSeries(seriesUID);
      dicomSeries.setTag(TagD.get(Tag.SeriesInstanceUID), seriesUID);
      dicomSeries.setTag(TagW.ExplorerModel, explorerDcmModel);
      dicomSeries.setTag(TagW.WadoParameters, wadoParameters);
      dicomSeries.setTag(TagW.WadoInstanceReferenceList, new SeriesInstanceList());
      // A series queried at the series level is retrieved with a single series-level WADO-RS
      // request, so its instances are never enumerated unless the download has to be resumed.
      dicomSeries.setTag(LoadSeries.SERIES_BULK_RETRIEVE, Boolean.TRUE);

      TagW[] tags =
          TagD.getTagFromIDs(
              Tag.Modality,
              Tag.SeriesNumber,
              Tag.SeriesDescription,
              Tag.RetrieveURL,
              Tag.NumberOfSeriesRelatedInstances);
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
              getConcurrentDownloadsInSeries(),
              true,
              startDownloading);
      loadSeries.setPriority(
          new DownloadPriority(
              explorerDcmModel.getParent(study, DicomModel.patient), study, dicomSeries, true));
      loadMap.put(TagD.getTagValue(dicomSeries, Tag.SeriesInstanceUID, String.class), loadSeries);
    }
    return dicomSeries;
  }
}
