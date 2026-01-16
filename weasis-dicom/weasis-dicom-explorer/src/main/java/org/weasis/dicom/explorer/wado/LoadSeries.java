/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.wado;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JProgressBar;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.SeriesImporter;
import org.weasis.core.api.media.data.SeriesThumbnail;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.TagW.TagType;
import org.weasis.core.api.media.data.Thumbnail;
import org.weasis.core.api.model.PerformanceModel;
import org.weasis.core.api.net.ClosableURLConnection;
import org.weasis.core.api.net.NetworkUtil;
import org.weasis.core.api.net.URLParameters;
import org.weasis.core.api.net.auth.AuthMethod;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.*;
import org.weasis.dicom.codec.utils.SeriesInstanceList;
import org.weasis.dicom.explorer.*;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.exp.ExplorerTask;
import org.weasis.dicom.mf.HttpTag;
import org.weasis.dicom.mf.SopInstance;
import org.weasis.dicom.mf.WadoParameters;

public class LoadSeries extends ExplorerTask<Boolean, String> implements SeriesImporter {

  private static final Logger LOGGER = LoggerFactory.getLogger(LoadSeries.class);
  public static final String CONCURRENT_DOWNLOADS_IN_SERIES = "download.concurrent.series.images";

  public static final Path DICOM_TMP_DIR =
      AppProperties.buildAccessibleTempDirectory("downloading"); // NON-NLS
  public static final TagW DOWNLOAD_START_TIME = new TagW("DownloadStartTime", TagType.TIME);
  public static final TagW DOWNLOAD_TIME = new TagW("DownloadTime", TagType.TIME);
  public static final TagW DOWNLOAD_ERRORS = new TagW("DownloadErrors", TagType.INTEGER);

  public static final String LOAD_TYPE_DICOMDIR = "DICOMDIR";
  public static final String LOAD_TYPE_URL = "URL";
  public static final String LOAD_TYPE_LOCAL = "local"; // NON-NLS
  public static final String LOAD_TYPE_WADO = "WADO";

  private PluginOpeningStrategy openingStrategy;

  public void setPOpeningStrategy(PluginOpeningStrategy openingStrategy) {
    this.openingStrategy = openingStrategy;
  }

  public PluginOpeningStrategy getOpeningStrategy() {
    return openingStrategy;
  }

  public enum Status {
    DOWNLOADING,
    COMPLETE,
    ERROR
  }

  public final int concurrentDownloads;
  private final DicomModel dicomModel;
  private final DicomSeries dicomSeries;
  private final SeriesInstanceList seriesInstanceList;
  private final JProgressBar progressBar;
  private final URLParameters urlParams;
  private DownloadPriority priority = null;
  private final boolean writeInCache;
  private final boolean startDownloading;
  private final AuthMethod authMethod;

  private final AtomicInteger errors;
  private volatile boolean hasError = false;
  private final AtomicBoolean seriesInitialized = new AtomicBoolean(false);

  private final ThumbnailManager thumbnailManager;
  private final SeriesDownloadManager downloadManager;

  public LoadSeries(
      DicomSeries dicomSeries,
      DicomModel dicomModel,
      int concurrentDownloads,
      boolean writeInCache) {
    this(dicomSeries, dicomModel, null, concurrentDownloads, writeInCache, true);
  }

  public LoadSeries(
      DicomSeries dicomSeries,
      DicomModel dicomModel,
      AuthMethod authMethod,
      int concurrentDownloads,
      boolean writeInCache,
      boolean startDownloading) {
    this(
        dicomSeries,
        dicomModel,
        authMethod,
        null,
        concurrentDownloads,
        writeInCache,
        startDownloading,
        false);
  }

  public LoadSeries(
      DicomSeries dicomSeries,
      DicomModel dicomModel,
      AuthMethod authMethod,
      JProgressBar progressBar,
      int concurrentDownloads,
      boolean writeInCache,
      boolean startDownloading) {
    this(
        dicomSeries,
        dicomModel,
        authMethod,
        progressBar,
        concurrentDownloads,
        writeInCache,
        startDownloading,
        true);
  }

  public LoadSeries(
      DicomSeries dicomSeries,
      DicomModel dicomModel,
      AuthMethod authMethod,
      JProgressBar progressBar,
      int concurrentDownloads,
      boolean writeInCache,
      boolean startDownloading,
      boolean externalProgress) {
    super(Messages.getString("DicomExplorer.loading"), writeInCache, true);
    if (dicomModel == null || dicomSeries == null || (progressBar == null && externalProgress)) {
      throw new IllegalArgumentException("null parameters");
    }
    this.dicomModel = dicomModel;
    this.dicomSeries = dicomSeries;
    this.authMethod = authMethod;
    this.seriesInstanceList =
        Optional.ofNullable(
                (SeriesInstanceList) dicomSeries.getTagValue(TagW.WadoInstanceReferenceList))
            .orElseGet(SeriesInstanceList::new);
    this.writeInCache = writeInCache;
    this.progressBar = externalProgress ? progressBar : getBar();
    if (!externalProgress && !writeInCache) {
      this.progressBar.setVisible(false);
    }
    this.dicomSeries.setSeriesLoader(this);
    this.concurrentDownloads = concurrentDownloads;
    this.urlParams =
        new URLParameters(
            getHttpTags((WadoParameters) dicomSeries.getTagValue(TagW.WadoParameters)));
    this.startDownloading = startDownloading;
    Integer downloadErrors = (Integer) dicomSeries.getTagValue(DOWNLOAD_ERRORS);
    if (downloadErrors == null) {
      downloadErrors = 0;
    }
    this.errors = new AtomicInteger(downloadErrors);
    this.thumbnailManager = new ThumbnailManager(dicomSeries, dicomModel, urlParams);
    this.downloadManager =
        new SeriesDownloadManager(
            this,
            dicomModel,
            dicomSeries,
            this.progressBar,
            urlParams,
            authMethod,
            writeInCache,
            concurrentDownloads,
            errors,
            seriesInitialized);
  }

  @Override
  protected Boolean doInBackground() {
    return downloadManager.startDownload();
  }

  @Override
  public JProgressBar getProgressBar() {
    return progressBar;
  }

  @Override
  public boolean isStopped() {
    return isCancelled();
  }

  @Override
  public boolean stop() {
    if (!isDone()) {
      boolean val = cancel();
      dicomSeries.setSeriesLoader(this);
      dicomSeries.setTag(DOWNLOAD_TIME, getDownloadTime());
      notifyDownloadCompletion(dicomModel);
      return val;
    }
    return true;
  }

  static void notifyDownloadCompletion(DicomModel model) {
    if (!DownloadManager.hasRunningTasks()) {
      model.firePropertyChange(
          new ObservableEvent(
              ObservableEvent.BasicAction.LOADING_GLOBAL_MSG,
              model,
              null,
              Messages.getString("stopped")));
    }
  }

  @Override
  public void resume() {
    if (isStopped()) {
      this.getPriority().setPriority(DownloadPriority.COUNTER.getAndDecrement());
      cancelAndReplace(this, true);
    }
  }

  @Override
  protected void done() {
    if (!isStopped()) {
      // Ensure to stop downloading and must be set before reusing LoadSeries to download again
      progressBar.setIndeterminate(false);
      this.dicomSeries.setSeriesLoader(null);
      DownloadManager.removeLoadSeries(this, dicomModel);
      notifyDownloadCompletion(dicomModel);

      LoadLocalDicom.seriesPostProcessing(dicomSeries, dicomModel);

      String loadType = getLoadType();
      String seriesUID = (String) dicomSeries.getTagValue(dicomSeries.getTagID());
      String modality = TagD.getTagValue(dicomSeries, Tag.Modality, String.class);
      int imageNumber = getImageNumber();
      long fileSize = dicomSeries.getFileSize();
      long time = getDownloadTime();
      String rate = getDownloadRate(time);
      Integer downloadErrors = (Integer) dicomSeries.getTagValue(DOWNLOAD_ERRORS);
      if (downloadErrors == null) {
        downloadErrors = 0;
      }

      LOGGER.info(
          "{} Downloading, type:{} seriesUID:{} modality:{} nbImages:{} size:{} time:{} rate:{} errors:{}",
          AuditLog.MARKER_PERF,
          loadType,
          seriesUID,
          modality,
          imageNumber,
          fileSize,
          time,
          rate,
          downloadErrors);

      if (LOAD_TYPE_WADO.equals(loadType)) {
        String statisticServicePath = GuiUtils.getUICore().getStatisticServiceUrl();

        if (StringUtil.hasText(statisticServicePath)) {

          Map<String, String> map = new HashMap<>();
          map.put("Content-Type", "application/json"); // NON-NLS
          URLParameters urlParameters = new URLParameters(map, true);

          PerformanceModel model =
              new PerformanceModel(
                  loadType, seriesUID, modality, imageNumber, fileSize, time, rate, downloadErrors);

          try {
            ClosableURLConnection http =
                NetworkUtil.getUrlConnection(statisticServicePath, urlParameters);
            try (OutputStream out = http.getOutputStream()) {
              OutputStreamWriter writer =
                  new OutputStreamWriter(out, StandardCharsets.UTF_8); // NON-NLS
              writer.write(new ObjectMapper().writeValueAsString(model));
            }
            if (http.urlConnection() instanceof HttpURLConnection httpURLConnection) {
              NetworkUtil.readResponse(httpURLConnection, urlParameters.headers());
            }
          } catch (Exception e) {
            LOGGER.error("Cannot send log to the statisticService service", e);
          }
        }
      }

      dicomSeries.removeTag(DOWNLOAD_START_TIME);
      dicomSeries.removeTag(DOWNLOAD_TIME);
      dicomSeries.removeTag(DOWNLOAD_ERRORS);

      final SeriesThumbnail thumbnail = (SeriesThumbnail) dicomSeries.getTagValue(TagW.Thumbnail);

      if (thumbnail != null) {
        thumbnail.setProgressBar(null);
        if (thumbnail.getThumbnailPath() == null
            || dicomSeries.getTagValue(TagW.DirectDownloadThumbnail) != null) {
          thumbnail.reBuildThumbnail(MediaSeries.MEDIA_POSITION.MIDDLE);
        } else {
          thumbnail.repaint();
        }
      }

      if (DicomModel.isHiddenModality(dicomSeries)) {
        Set<HiddenSpecialElement> list =
            HiddenSeriesManager.getInstance().series2Elements.get(seriesUID);
        if (list != null) {
          list.stream()
              .filter(Objects::nonNull)
              .forEach(
                  d ->
                      dicomModel.firePropertyChange(
                          new ObservableEvent(
                              ObservableEvent.BasicAction.UPDATE, dicomModel, null, d)));
        }
      }

      Integer splitNb = (Integer) dicomSeries.getTagValue(TagW.SplitSeriesNumber);
      if (splitNb != null) {
        dicomModel.firePropertyChange(
            new ObservableEvent(ObservableEvent.BasicAction.UPDATE, dicomModel, null, dicomSeries));
      } else if (dicomSeries.size(null) == 0
          && dicomSeries.getTagValue(TagW.DicomSpecialElementList) == null
          && !hasDownloadFailed()) {
        // Remove in case of split Series and all the SopInstanceUIDs already exist
        dicomModel.firePropertyChange(
            new ObservableEvent(ObservableEvent.BasicAction.REMOVE, dicomModel, null, dicomSeries));
      }
    }
  }

  public boolean hasDownloadFailed() {
    return hasError;
  }

  public boolean isStartDownloading() {
    return startDownloading;
  }

  private String getLoadType() {
    final WadoParameters wado = (WadoParameters) dicomSeries.getTagValue(TagW.WadoParameters);
    if (wado == null || !StringUtil.hasText(wado.getBaseURL())) {
      if (wado != null) {
        return wado.isRequireOnlySOPInstanceUID() ? LOAD_TYPE_DICOMDIR : LOAD_TYPE_URL; // NON-NLS
      }
      return LOAD_TYPE_LOCAL;
    } else {
      return LOAD_TYPE_WADO;
    }
  }

  private int getImageNumber() {
    int val = dicomSeries.size(null);
    Integer splitNb = (Integer) dicomSeries.getTagValue(TagW.SplitSeriesNumber);
    if (splitNb != null) {
      MediaSeriesGroup study = dicomModel.getParent(dicomSeries, DicomModel.study);
      if (study != null) {
        String uid = TagD.getTagValue(dicomSeries, Tag.SeriesInstanceUID, String.class);
        if (uid != null) {
          Collection<MediaSeriesGroup> list = dicomModel.getChildren(study);
          list.remove(dicomSeries);
          for (MediaSeriesGroup s : list) {
            if (s instanceof Series && uid.equals(TagD.getTagValue(s, Tag.SeriesInstanceUID))) {
              val += ((Series<?>) s).size(null);
            }
          }
        }
      }
    }
    return val;
  }

  private long getDownloadTime() {
    Long val = (Long) dicomSeries.getTagValue(DOWNLOAD_START_TIME);
    if (val == null) {
      val = 0L;
    } else {
      val = System.currentTimeMillis() - val;
      dicomSeries.setTag(DOWNLOAD_START_TIME, null);
    }
    Long time = (Long) dicomSeries.getTagValue(DOWNLOAD_TIME);
    if (time == null) {
      time = 0L;
    }
    return val + time;
  }

  private String getDownloadRate(long time) {
    // rate in kB/s or B/ms
    DecimalFormat format =
        new DecimalFormat("#.##", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
    return time <= 0 ? "0" : format.format((double) dicomSeries.getFileSize() / time);
  }

  private static Map<String, String> getHttpTags(WadoParameters wadoParams) {
    boolean hasWadoTags = wadoParams != null && wadoParams.getHttpTaglist() != null;
    boolean hasWadoLogin = wadoParams != null && wadoParams.getWebLogin() != null;

    if (hasWadoTags || hasWadoLogin) {
      HashMap<String, String> map = new HashMap<>();
      if (hasWadoTags) {
        for (HttpTag tag : wadoParams.getHttpTaglist()) {
          map.put(tag.getKey(), tag.getValue());
        }
      }
      if (hasWadoLogin) {
        // Set http login (no protection, only convert in base64)
        map.put("Authorization", "Basic " + wadoParams.getWebLogin()); // NON-NLS
      }
      return map;
    }
    return null;
  }

  public void startDownloadImageReference(final WadoParameters wadoParameters) {
    if (!seriesInstanceList.isEmpty()) {
      // Sort the UIDs for building the thumbnail that is in the middle of the Series
      List<SopInstance> sopList = seriesInstanceList.getSortedList();
      final SopInstance instance = sopList.get(sopList.size() / 2);

      thumbnailManager.createSeriesThumbnail(this, progressBar);
      thumbnailManager.loadThumbnail(instance, wadoParameters, authMethod);
    }
  }

  public DicomSeries getDicomSeries() {
    return dicomSeries;
  }

  public SeriesInstanceList getSeriesInstanceList() {
    return seriesInstanceList;
  }

  public void setHasError(boolean hasError) {
    this.hasError = hasError;
  }

  public synchronized DownloadPriority getPriority() {
    return priority;
  }

  public synchronized void setPriority(DownloadPriority priority) {
    this.priority = priority;
  }

  @Override
  public void setPriority() {
    DownloadPriority p = getPriority();
    if (p != null && StateValue.PENDING.equals(getState())) {
      boolean change = DownloadManager.removeSeriesInQueue(this);
      if (change) {
        // Set the priority to the current loadingSeries and stop a task.
        p.setPriority(DownloadPriority.COUNTER.getAndDecrement());
        DownloadManager.offerSeriesInQueue(this);
        synchronized (DownloadManager.getTasks()) {
          for (LoadSeries s : DownloadManager.getTasks()) {
            if (s != this && StateValue.STARTED.equals(s.getState())) {
              cancelAndReplace(s, true);
              break;
            }
          }
        }
      }
    }
  }

  public LoadSeries cancelAndReplace(LoadSeries s, boolean restartAllDownload) {
    LoadSeries taskResume =
        new LoadSeries(
            s.getDicomSeries(),
            dicomModel,
            s.authMethod,
            s.getProgressBar(),
            s.getConcurrentDownloads(),
            s.writeInCache,
            s.startDownloading);
    s.cancel();
    taskResume.setPriority(s.getPriority());
    taskResume.setPOpeningStrategy(s.getOpeningStrategy());
    Thumbnail thumbnail = (Thumbnail) s.getDicomSeries().getTagValue(TagW.Thumbnail);
    if (thumbnail != null) {
      ThumbnailManager.removeThumbnailMouseAndKeyAdapter(thumbnail);
      thumbnailManager.addListenerToThumbnail(thumbnail, taskResume);
    }

    if (restartAllDownload) {
      DownloadManager.addLoadSeries(taskResume, dicomModel, true);
    } else {
      taskResume.execute();
    }
    DownloadManager.removeLoadSeries(s, dicomModel);

    return taskResume;
  }

  public int getConcurrentDownloads() {
    return concurrentDownloads;
  }
}
