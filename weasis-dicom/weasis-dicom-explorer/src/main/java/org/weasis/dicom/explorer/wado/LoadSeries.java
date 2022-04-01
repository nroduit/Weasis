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
import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.swing.JProgressBar;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.util.SafeClose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.auth.AuthMethod;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.task.SeriesProgressMonitor;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.SeriesImporter;
import org.weasis.core.api.media.data.SeriesThumbnail;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.TagW.TagType;
import org.weasis.core.api.media.data.Thumbnail;
import org.weasis.core.api.model.PerformanceModel;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.AuthResponse;
import org.weasis.core.api.util.ClosableURLConnection;
import org.weasis.core.api.util.HttpResponse;
import org.weasis.core.api.util.NetworkUtil;
import org.weasis.core.api.util.ThreadUtil;
import org.weasis.core.api.util.URLParameters;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.ReferencedImage;
import org.weasis.core.ui.model.ReferencedSeries;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.StreamIOException;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.codec.TransferSyntax;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.ExplorerTask;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.MimeSystemAppFactory;
import org.weasis.dicom.explorer.ThumbnailMouseAndKeyAdapter;
import org.weasis.dicom.mf.HttpTag;
import org.weasis.dicom.mf.SopInstance;
import org.weasis.dicom.mf.WadoParameters;
import org.weasis.dicom.web.Multipart;

public class LoadSeries extends ExplorerTask<Boolean, String> implements SeriesImporter {

  private static final Logger LOGGER = LoggerFactory.getLogger(LoadSeries.class);
  public static final String CONCURRENT_DOWNLOADS_IN_SERIES = "download.concurrent.series.images";

  public static final File DICOM_TMP_DIR =
      AppProperties.buildAccessibleTempDirectory("downloading"); // NON-NLS
  public static final TagW DOWNLOAD_START_TIME = new TagW("DownloadStartTime", TagType.TIME);
  public static final TagW DOWNLOAD_TIME = new TagW("DownloadTime", TagType.TIME);
  public static final TagW DOWNLOAD_ERRORS = new TagW("DownloadErrors", TagType.INTEGER);

  public enum Status {
    DOWNLOADING,
    COMPLETE,
    ERROR
  }

  public final int concurrentDownloads;
  private final DicomModel dicomModel;
  private final Series<?> dicomSeries;
  private final SeriesInstanceList seriesInstanceList;
  private final JProgressBar progressBar;
  private final URLParameters urlParams;
  private DownloadPriority priority = null;
  private final boolean writeInCache;
  private final boolean startDownloading;
  private final AuthMethod authMethod;

  private final AtomicInteger errors;
  private volatile boolean hasError = false;

  public LoadSeries(
      Series<?> dicomSeries, DicomModel dicomModel, int concurrentDownloads, boolean writeInCache) {
    this(dicomSeries, dicomModel, null, concurrentDownloads, writeInCache, true);
  }

  public LoadSeries(
      Series<?> dicomSeries,
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
      Series<?> dicomSeries,
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
      Series<?> dicomSeries,
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
  }

  @Override
  protected Boolean doInBackground() {
    return startDownload();
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
      return val;
    }
    return true;
  }

  @Override
  public void resume() {
    if (isStopped()) {
      this.getPriority().setPriority(DownloadPriority.COUNTER.getAndDecrement());
      cancelAndReplace(this);
    }
  }

  @Override
  protected void done() {
    if (!isStopped()) {
      // Ensure to stop downloading and must be set before reusing LoadSeries to download again
      progressBar.setIndeterminate(false);
      this.dicomSeries.setSeriesLoader(null);
      DownloadManager.removeLoadSeries(this, dicomModel);

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
          "{} type:{} seriesUID:{} modality:{} nbImages:{} size:{} time:{} rate:{} errors:{}",
          AuditLog.MARKER_PERF,
          loadType,
          seriesUID,
          modality,
          imageNumber,
          fileSize,
          time,
          rate,
          downloadErrors);

      if ("WADO".equals(loadType)) {
        String configServicePath = BundleTools.getConfigServiceUrl();
        if (StringUtil.hasText(configServicePath)) {
          Map<String, String> map = new HashMap<>();
          map.put("Content-Type", "application/json"); // NON-NLS
          try {
            URL url = new URL(configServicePath);
            Map<String, String> params = URLParameters.splitParameter(url);
            URLParameters urlParameters = new URLParameters(map, true);
            String user = params.get("user"); // NON-NLS
            String host = params.get("host"); // NON-NLS
            PerformanceModel model =
                new PerformanceModel(
                    StringUtil.hasText(user) ? user : AppProperties.WEASIS_USER,
                    StringUtil.hasText(host) ? host : InetAddress.getLocalHost().getHostName(),
                    loadType,
                    seriesUID,
                    modality,
                    imageNumber,
                    fileSize,
                    time,
                    rate,
                    downloadErrors);

            ClosableURLConnection http = NetworkUtil.getUrlConnection(url, urlParameters);
            try (OutputStream out = http.getOutputStream()) {
              OutputStreamWriter writer =
                  new OutputStreamWriter(out, StandardCharsets.UTF_8); // NON-NLS
              writer.write(new ObjectMapper().writeValueAsString(model));
            }
            if (http.getUrlConnection() instanceof HttpURLConnection httpURLConnection) {
              NetworkUtil.readResponse(httpURLConnection, urlParameters.getUnmodifiableHeaders());
            }
          } catch (Exception e) {
            LOGGER.error("Cannot send log to the launchConfig service", e);
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

      if (DicomModel.isSpecialModality(dicomSeries)) {
        dicomModel.addSpecialModality(dicomSeries);
        List<DicomSpecialElement> list =
            (List<DicomSpecialElement>) dicomSeries.getTagValue(TagW.DicomSpecialElementList);
        if (list != null) {
          list.stream()
              .filter(Objects::nonNull)
              .findFirst()
              .ifPresent(
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
        return wado.isRequireOnlySOPInstanceUID() ? "DICOMDIR" : "URL"; // NON-NLS
      }
      return "local"; // NON-NLS
    } else {
      return "WADO";
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

  private boolean isSOPInstanceUIDExist(
      MediaSeriesGroup study, Series<?> dicomSeries, String sopUID) {
    TagW sopTag = TagD.getUID(Level.INSTANCE);
    if (dicomSeries.hasMediaContains(sopTag, sopUID)) {
      return true;
    }
    // Search in split Series, cannot use "has this series a SplitNumber" because splitting can be
    // executed later
    // for Dicom Video and other special Dicom
    String uid = TagD.getTagValue(dicomSeries, Tag.SeriesInstanceUID, String.class);
    if (study != null && uid != null) {
      for (MediaSeriesGroup group : dicomModel.getChildren(study)) {
        if (dicomSeries != group
            && group instanceof Series<?> s
            && uid.equals(TagD.getTagValue(group, Tag.SeriesInstanceUID))
            && s.hasMediaContains(sopTag, sopUID)) {
          return true;
        }
      }
    }

    return false;
  }

  private void incrementProgressBarValue() {
    GuiExecutor.instance().execute(() -> progressBar.setValue(progressBar.getValue() + 1));
  }

  private Boolean startDownload() {

    MediaSeriesGroup patient = dicomModel.getParent(dicomSeries, DicomModel.patient);
    MediaSeriesGroup study = dicomModel.getParent(dicomSeries, DicomModel.study);
    LOGGER.info("Downloading series of {} [{}]", patient, dicomSeries);

    WadoParameters wado = (WadoParameters) dicomSeries.getTagValue(TagW.WadoParameters);
    if (wado == null) {
      return false;
    }

    List<SopInstance> sopList = seriesInstanceList.getSortedList();

    ExecutorService imageDownloader =
        ThreadUtil.buildNewFixedThreadExecutor(concurrentDownloads, "Image Downloader"); // NON-NLS
    ArrayList<Callable<Boolean>> tasks = new ArrayList<>(sopList.size());
    int[] dindex = generateDownloadOrder(sopList.size());
    GuiExecutor.instance()
        .execute(
            () -> {
              progressBar.setMaximum(sopList.size());
              progressBar.setValue(0);
            });
    for (int k = 0; k < sopList.size(); k++) {
      SopInstance instance = sopList.get(dindex[k]);
      if (isCancelled()) {
        return true;
      }

      if (seriesInstanceList.isContainsMultiframes()
          && seriesInstanceList.getSopInstance(instance.getSopInstanceUID()) != instance) {
        // Do not handle wado query for multiframes
        continue;
      }

      // Test if SOPInstanceUID already exists
      if (isSOPInstanceUIDExist(study, dicomSeries, instance.getSopInstanceUID())) {
        incrementProgressBarValue();
        LOGGER.debug("DICOM instance {} already exists, skip.", instance.getSopInstanceUID());
        continue;
      }

      String studyUID = "";
      String seriesUID = "";
      if (!wado.isRequireOnlySOPInstanceUID()) {
        studyUID = TagD.getTagValue(study, Tag.StudyInstanceUID, String.class);
        seriesUID = TagD.getTagValue(dicomSeries, Tag.SeriesInstanceUID, String.class);
      }
      StringBuilder request = new StringBuilder(wado.getBaseURL());
      if (instance.getDirectDownloadFile() == null) {
        request.append("?requestType=WADO&studyUID="); // NON-NLS
        request.append(studyUID);
        request.append("&seriesUID="); // NON-NLS
        request.append(seriesUID);
        request.append("&objectUID="); // NON-NLS
        request.append(instance.getSopInstanceUID());
        request.append("&contentType=application%2Fdicom"); // NON-NLS

        // for dcm4chee: it gets original DICOM files when no TransferSyntax is specified
        String wadoTsuid = (String) dicomSeries.getTagValue(TagW.WadoTransferSyntaxUID);
        if (StringUtil.hasText(wadoTsuid)) {
          request.append("&transferSyntax="); // NON-NLS
          request.append(wadoTsuid);
          Integer rate = (Integer) dicomSeries.getTagValue(TagW.WadoCompressionRate);
          if (rate != null && rate > 0) {
            request.append("&imageQuality="); // NON-NLS
            request.append(rate);
          }
        }
      } else {
        request.append(instance.getDirectDownloadFile());
      }
      request.append(wado.getAdditionnalParameters());
      String url = request.toString();

      LOGGER.debug("Download DICOM instance {} index {}.", url, k);
      Download ref = new Download(url);
      tasks.add(ref);
    }

    try {
      dicomSeries.setTag(DOWNLOAD_START_TIME, System.currentTimeMillis());
      imageDownloader.invokeAll(tasks);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    imageDownloader.shutdown();
    return true;
  }

  private static Map<String, String> getHttpTags(WadoParameters wadoParams) {
    boolean hasBundleTags = !BundleTools.SESSION_TAGS_FILE.isEmpty();
    boolean hasWadoTags = wadoParams != null && wadoParams.getHttpTaglist() != null;
    boolean hasWadoLogin = wadoParams != null && wadoParams.getWebLogin() != null;

    if (hasWadoTags || hasWadoLogin || hasBundleTags) {
      HashMap<String, String> map = new HashMap<>(BundleTools.SESSION_TAGS_FILE);
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

      GuiExecutor.instance()
          .execute(
              () -> {
                SeriesThumbnail thumbnail =
                    (SeriesThumbnail) dicomSeries.getTagValue(TagW.Thumbnail);
                if (thumbnail == null) {
                  int thumbnailSize =
                      BundleTools.SYSTEM_PREFERENCES.getIntProperty(
                          Thumbnail.KEY_SIZE, Thumbnail.DEFAULT_SIZE);
                  thumbnail = new SeriesThumbnail(dicomSeries, thumbnailSize);
                }
                // In case series is downloaded or canceled
                thumbnail.setProgressBar(LoadSeries.this.isDone() ? null : progressBar);
                thumbnail.registerListeners();
                addListenerToThumbnail(thumbnail, LoadSeries.this, dicomModel);
                dicomSeries.setTag(TagW.Thumbnail, thumbnail);
                dicomModel.firePropertyChange(
                    new ObservableEvent(
                        ObservableEvent.BasicAction.ADD, dicomModel, null, dicomSeries));
              });

      loadThumbnail(instance, wadoParameters);
    }
  }

  public void loadThumbnail(SopInstance instance, WadoParameters wadoParameters) {
    File file = null;
    URLParameters params = urlParams;
    if (instance.getDirectDownloadFile() == null) {
      String studyUID = "";
      String seriesUID = "";
      if (!wadoParameters.isRequireOnlySOPInstanceUID()) {
        MediaSeriesGroup study = dicomModel.getParent(dicomSeries, DicomModel.study);
        studyUID = TagD.getTagValue(study, Tag.StudyInstanceUID, String.class);
        seriesUID = TagD.getTagValue(dicomSeries, Tag.SeriesInstanceUID, String.class);
      }
      try {
        file = getJpegThumbnails(wadoParameters, studyUID, seriesUID, instance.getSopInstanceUID());
      } catch (Exception e) {
        LOGGER.error("Downloading thumbnail", e);
      }
    } else {
      String thumbURL;
      String extension = ".jpg";
      if (wadoParameters.isWadoRS()) {
        thumbURL = TagD.getTagValue(dicomSeries, Tag.RetrieveURL, String.class);
        if (thumbURL != null) {
          thumbURL +=
              "/thumbnail?viewport=" + Thumbnail.MAX_SIZE + "%2C" + Thumbnail.MAX_SIZE; // NON-NLS
          HashMap<String, String> headers = new HashMap<>(urlParams.getUnmodifiableHeaders());
          headers.put("Accept", "image/jpeg"); // NON-NLS
          params = new URLParameters(headers);
        }
      } else {
        thumbURL = (String) dicomSeries.getTagValue(TagW.DirectDownloadThumbnail);
        if (StringUtil.hasLength(thumbURL)) {
          if (thumbURL.startsWith(Thumbnail.THUMBNAIL_CACHE_DIR.getPath())) {
            file = new File(thumbURL);
            thumbURL = null;
          } else {
            thumbURL = wadoParameters.getBaseURL() + thumbURL;
            extension = FileUtil.getExtension(thumbURL);
          }
        }
      }

      if (thumbURL != null) {
        try (HttpResponse httpCon = NetworkUtil.getHttpResponse(thumbURL, params, authMethod)) {
          int code = httpCon.getResponseCode();
          if (code >= HttpURLConnection.HTTP_OK && code < HttpURLConnection.HTTP_BAD_REQUEST) {
            File outFile = File.createTempFile("thumb_", extension, Thumbnail.THUMBNAIL_CACHE_DIR);
            FileUtil.writeStreamWithIOException(httpCon.getInputStream(), outFile);
            if (outFile.length() == 0) {
              FileUtil.delete(outFile);
              throw new IllegalStateException("Thumbnail file is empty");
            }
            file = outFile;
          } else if (authMethod != null && code == HttpURLConnection.HTTP_UNAUTHORIZED) {
            authMethod.resetToken();
            authMethod.getToken();
          }
        } catch (Exception e) {
          LOGGER.error("Downloading thumbnail with {}", thumbURL, e);
        }
      }
    }
    if (file != null) {
      final File finalfile = file;
      GuiExecutor.instance()
          .execute(
              () -> {
                SeriesThumbnail thumbnail =
                    (SeriesThumbnail) dicomSeries.getTagValue(TagW.Thumbnail);
                if (thumbnail != null) {
                  thumbnail.reBuildThumbnail(finalfile, MediaSeries.MEDIA_POSITION.MIDDLE);
                }
              });
    }
  }

  public static void removeThumbnailMouseAndKeyAdapter(Thumbnail thumbnail) {
    MouseListener[] listener = thumbnail.getMouseListeners();
    MouseMotionListener[] motionListeners = thumbnail.getMouseMotionListeners();
    KeyListener[] keyListeners = thumbnail.getKeyListeners();
    MouseWheelListener[] wheelListeners = thumbnail.getMouseWheelListeners();
    for (MouseListener mouseListener : listener) {
      if (mouseListener instanceof ThumbnailMouseAndKeyAdapter) {
        thumbnail.removeMouseListener(mouseListener);
      }
    }
    for (MouseMotionListener motionListener : motionListeners) {
      if (motionListener instanceof ThumbnailMouseAndKeyAdapter) {
        thumbnail.removeMouseMotionListener(motionListener);
      }
    }
    for (MouseWheelListener wheelListener : wheelListeners) {
      if (wheelListener instanceof ThumbnailMouseAndKeyAdapter) {
        thumbnail.removeMouseWheelListener(wheelListener);
      }
    }
    for (KeyListener keyListener : keyListeners) {
      if (keyListener instanceof ThumbnailMouseAndKeyAdapter) {
        thumbnail.removeKeyListener(keyListener);
      }
    }
  }

  private static void addListenerToThumbnail(
      final Thumbnail thumbnail, final LoadSeries loadSeries, final DicomModel dicomModel) {
    ThumbnailMouseAndKeyAdapter thumbAdapter =
        new ThumbnailMouseAndKeyAdapter(loadSeries.getDicomSeries(), dicomModel, loadSeries);
    thumbnail.addMouseListener(thumbAdapter);
    thumbnail.addKeyListener(thumbAdapter);
    if (thumbnail instanceof SeriesThumbnail seriesThumbnail) {
      seriesThumbnail.setProgressBar(loadSeries.getProgressBar());
    }
  }

  public Series<?> getDicomSeries() {
    return dicomSeries;
  }

  public File getJpegThumbnails(
      WadoParameters wadoParameters, String studyUID, String seriesUID, String sopInstanceUID)
      throws Exception {
    String addParams = wadoParameters.getAdditionnalParameters();
    if (StringUtil.hasText(addParams)) {
      addParams =
          Arrays.stream(addParams.split("&"))
              .filter(p -> !p.startsWith("transferSyntax") && !p.startsWith("anonymize")) // NON-NLS
              .collect(Collectors.joining("&")); // NON-NLS
    }
    // TODO set quality as a preference
    URL url =
        new URL(
            wadoParameters.getBaseURL()
                + "?requestType=WADO&studyUID="
                + studyUID
                + "&seriesUID="
                + seriesUID
                + "&objectUID="
                + sopInstanceUID
                + "&contentType=image/jpeg&imageQuality=70"
                + "&rows="
                + Thumbnail.MAX_SIZE
                + "&columns="
                + Thumbnail.MAX_SIZE
                + addParams);

    File outFile = File.createTempFile("thumb_", ".jpg", Thumbnail.THUMBNAIL_CACHE_DIR);
    LOGGER.debug("Start to download JPEG thumbnail {} to {}.", url, outFile.getName());
    try (HttpResponse httpCon =
        NetworkUtil.getHttpResponse(url.toString(), urlParams, authMethod)) {
      int code = httpCon.getResponseCode();
      if (code >= HttpURLConnection.HTTP_OK && code < HttpURLConnection.HTTP_BAD_REQUEST) {
        FileUtil.writeStreamWithIOException(httpCon.getInputStream(), outFile);
      } else if (authMethod != null && code == HttpURLConnection.HTTP_UNAUTHORIZED) {
        authMethod.resetToken();
        authMethod.getToken();
      }
      if (outFile.length() == 0) {
        throw new IllegalStateException("Thumbnail file is empty");
      }
    } finally {
      if (outFile.length() == 0) {
        FileUtil.delete(outFile);
      }
    }
    return outFile;
  }

  private int[] generateDownloadOrder(final int size) {
    int[] dindex = new int[size];
    if (size < 4) {
      for (int i = 0; i < dindex.length; i++) {
        dindex[i] = i;
      }
      return dindex;
    }
    boolean[] map = new boolean[size];
    int pos = 0;
    dindex[pos++] = 0;
    map[0] = true;
    dindex[pos++] = size - 1;
    map[size - 1] = true;
    int k = (size - 1) / 2;
    dindex[pos++] = k;
    map[k] = true;

    while (k > 0) {
      int i = 1;
      int start = 0;
      while (i < map.length) {
        if (map[i]) {
          if (!map[i - 1]) {
            int mid = start + (i - start) / 2;
            map[mid] = true;
            dindex[pos++] = mid;
          }
          start = i;
        }
        i++;
      }
      k /= 2;
    }
    return dindex;
  }

  class Download implements Callable<Boolean> {

    private final String url; // download URL
    private Status status; // current status of download

    public Download(String url) {
      this.url = url;
      this.status = Status.DOWNLOADING;
    }

    private void error() {
      status = Status.ERROR;
      dicomSeries.setTag(DOWNLOAD_ERRORS, errors.incrementAndGet());
    }

    private HttpResponse replaceToDefaultTSUID() throws IOException {
      StringBuilder buffer = new StringBuilder();
      int start = url.indexOf("&transferSyntax="); // NON-NLS
      if (start != -1) {
        int end = url.indexOf('&', start + 16);
        buffer.append(url, 0, start + 16);
        buffer.append(TransferSyntax.EXPLICIT_VR_LE.getTransferSyntaxUID());
        if (end != -1) {
          buffer.append(url.substring(end));
        }
      } else {
        buffer.append(url);
        buffer.append("&transferSyntax="); // NON-NLS
        buffer.append(TransferSyntax.EXPLICIT_VR_LE.getTransferSyntaxUID());
      }

      return NetworkUtil.getHttpResponse(buffer.toString(), urlParams, authMethod);
    }

    @Override
    public Boolean call() {
      try {
        process();
      } catch (StreamIOException es) {
        hasError = true; // network issue (allow retrying)
        error();
        LOGGER.error("Downloading", es);
      } catch (IOException | URISyntaxException e) {
        error();
        LOGGER.error("Downloading", e);
      }
      return Boolean.TRUE;
    }

    // Solves missing tmp folder problem (on Windows).
    private File getDicomTmpDir() {
      if (!DICOM_TMP_DIR.exists()) {
        LOGGER.info("DICOM tmp dir not found. Re-creating it!");
        AppProperties.buildAccessibleTempDirectory("downloading"); // NON-NLS
      }
      return DICOM_TMP_DIR;
    }

    /** Download file. */
    private boolean process() throws IOException, URISyntaxException {
      boolean cache = true;
      File tempFile = null;
      DicomMediaIO dicomReader = null;
      HttpResponse urlcon = NetworkUtil.getHttpResponse(url, urlParams, authMethod);
      int code = urlcon.getResponseCode();
      if (code >= HttpURLConnection.HTTP_BAD_REQUEST) {
        if (authMethod != null && code == HttpURLConnection.HTTP_UNAUTHORIZED) {
          authMethod.resetToken();
          authMethod.getToken();
        }
        throw new IllegalStateException("Response code of server: " + urlcon.getResponseCode());
      }
      try (InputStream stream = urlcon.getInputStream()) {

        if (!writeInCache && url.startsWith("file:")) { // NON-NLS
          cache = false;
        }
        if (cache) {
          tempFile = File.createTempFile("image_", ".dcm", getDicomTmpDir());
        }

        // Cannot resume with WADO because the stream is modified on the fly by the wado server. In
        // dcm4chee,
        // see
        // http://www.dcm4che.org/jira/browse/DCMEE-421
        progressBar.setIndeterminate(progressBar.getMaximum() < 3);

        if (dicomSeries != null) {
          if (cache) {
            LOGGER.debug("Start to download DICOM instance {} to {}.", url, tempFile.getName());
            int bytesTransferred = downloadInFileCache(urlcon, tempFile);
            if (bytesTransferred == -1) {
              LOGGER.info("End of downloading {} ", url);
            } else if (bytesTransferred >= 0) {
              return false;
            }

            File renameFile = new File(DicomMediaIO.DICOM_EXPORT_DIR, tempFile.getName());
            if (tempFile.renameTo(renameFile)) {
              tempFile = renameFile;
            }
          } else {
            tempFile = new File(NetworkUtil.getURI(url));
          }
          // Ensure the stream is closed if image is not written in cache
          FileUtil.safeClose(stream);

          dicomReader = new DicomMediaIO(tempFile);
          if (dicomReader.isReadableDicom() && dicomSeries.size(null) == 0) {
            // Override the group (patient, study and series) by the dicom fields except the UID of
            // the
            // group
            MediaSeriesGroup patient = dicomModel.getParent(dicomSeries, DicomModel.patient);
            dicomReader.writeMetaData(patient);
            MediaSeriesGroup study = dicomModel.getParent(dicomSeries, DicomModel.study);
            dicomReader.writeMetaData(study);
            dicomReader.writeMetaData(dicomSeries);
            GuiExecutor.instance()
                .invokeAndWait(
                    () -> {
                      Thumbnail thumb = (Thumbnail) dicomSeries.getTagValue(TagW.Thumbnail);
                      if (thumb != null) {
                        thumb.repaint();
                      }
                      dicomModel.firePropertyChange(
                          new ObservableEvent(
                              ObservableEvent.BasicAction.UPDATE_PARENT,
                              dicomModel,
                              null,
                              dicomSeries));
                    });
          }
        }
      }

      // Change status to complete if this point was reached because downloading has finished.
      if (status == Status.DOWNLOADING) {
        status = Status.COMPLETE;
        if (tempFile != null && dicomSeries != null && dicomReader.isReadableDicom()) {
          if (cache) {
            dicomReader.getFileCache().setOriginalTempFile(tempFile);
          }
          final DicomMediaIO reader = dicomReader;
          // Necessary to wait the runnable because the dicomSeries must be added to the dicomModel
          // before reaching done() of SwingWorker
          GuiExecutor.instance().invokeAndWait(() -> updateUI(reader));
        }
      }
      // Increment progress bar in EDT and repaint when downloaded
      incrementProgressBarValue();
      return true;
    }

    private int downloadInFileCache(HttpResponse response, File tempFile) throws IOException {
      final WadoParameters wadoParams =
          (WadoParameters) dicomSeries.getTagValue(TagW.WadoParameters);
      int[] overrideList =
          Optional.ofNullable(wadoParams)
              .map(WadoParameters::getOverrideDicomTagIDList)
              .orElse(null);

      int bytesTransferred;
      if (overrideList == null) {
        if (wadoParams != null && wadoParams.isWadoRS()) {

          int[] readBytes = {0};
          Multipart.Handler handler =
              (multipartReader, partNumber, headers) -> {
                // At sop instance level must have only one part
                try (InputStream in = multipartReader.newPartInputStream()) {
                  readBytes[0] =
                      FileUtil.writeStream(
                          new SeriesProgressMonitor(dicomSeries, in), tempFile, false);
                }
              };

          if (response instanceof ClosableURLConnection urlConnection) {
            Multipart.parseMultipartRelated(
                urlConnection.getUrlConnection().getContentType(),
                response.getInputStream(),
                handler);
          } else {
            AuthResponse authResponse = (AuthResponse) response;
            Multipart.parseMultipartRelated(
                authResponse.getResponse().getHeader("Content-Type"), // NON-NLS
                response.getInputStream(),
                handler);
          }
          bytesTransferred = readBytes[0];
        } else {
          bytesTransferred =
              FileUtil.writeStream(
                  new DicomSeriesProgressMonitor(dicomSeries, response.getInputStream(), false),
                  tempFile);
        }
      } else {
        bytesTransferred =
            writFile(
                new DicomSeriesProgressMonitor(dicomSeries, response.getInputStream(), false),
                tempFile,
                overrideList);
      }

      if (bytesTransferred == Integer.MIN_VALUE) {
        LOGGER.warn("Stop downloading unsupported TSUID, retry to download non compressed TSUID");
        InputStream stream2 = replaceToDefaultTSUID().getInputStream();
        if (overrideList == null) {
          bytesTransferred =
              FileUtil.writeStream(
                  new DicomSeriesProgressMonitor(dicomSeries, stream2, false), tempFile);
        } else {
          bytesTransferred =
              writFile(
                  new DicomSeriesProgressMonitor(dicomSeries, stream2, false),
                  tempFile,
                  overrideList);
        }
      }
      return bytesTransferred;
    }

    /**
     * @param in the InputStream value
     * @param tempFile the file path
     * @param overrideList the list of the DICOM tags to modify when writing
     * @return bytes transferred. O = error, -1 = all bytes has been transferred, other = bytes
     *     transferred before interruption
     * @throws StreamIOException reading or writing error
     */
    public int writFile(InputStream in, File tempFile, int[] overrideList)
        throws StreamIOException {
      if (in == null || tempFile == null) {
        return 0;
      }

      DicomInputStream dis = null;
      DicomOutputStream dos = null;

      try {
        String tsuid;
        Attributes dataset;
        dis = new DicomInputStream(in);
        try {
          dis.setIncludeBulkData(IncludeBulkData.URI);
          dataset = dis.readDataset();
          tsuid = dis.getTransferSyntax();
        } finally {
          dis.close();
        }

        dos = new DicomOutputStream(tempFile);

        if (overrideList != null) {
          MediaSeriesGroup study = dicomModel.getParent(dicomSeries, DicomModel.study);
          MediaSeriesGroup patient = dicomModel.getParent(dicomSeries, DicomModel.patient);
          ElementDictionary dic = ElementDictionary.getStandardElementDictionary();

          for (int tag : overrideList) {
            TagW tagElement = patient.getTagElement(tag);
            Object value;
            if (tagElement == null) {
              tagElement = study.getTagElement(tag);
              value = study.getTagValue(tagElement);
            } else {
              value = patient.getTagValue(tagElement);
            }

            DicomMediaUtils.fillAttributes(dataset, tagElement, value, dic);
          }
        }
        dos.writeDataset(dataset.createFileMetaInformation(tsuid), dataset);
        dos.finish();
        dos.flush();
        return -1;
      } catch (InterruptedIOException e) {
        FileUtil.delete(tempFile);
        LOGGER.error("Interruption when writing file: {}", e.getMessage());
        return e.bytesTransferred;
      } catch (IOException e) {
        FileUtil.delete(tempFile);
        throw new StreamIOException(e);
      } catch (Exception e) {
        FileUtil.delete(tempFile);
        LOGGER.error("Writing DICOM temp file", e);
        return 0;
      } finally {
        SafeClose.close(dos);
        if (dis != null) {
          List<File> blkFiles = dis.getBulkDataFiles();
          if (blkFiles != null) {
            for (File file : blkFiles) {
              FileUtil.delete(file);
            }
          }
        }
      }
    }

    private void updateUI(final DicomMediaIO reader) {
      boolean firstImageToDisplay = false;
      MediaElement[] medias = reader.getMediaElement();
      if (medias != null) {
        firstImageToDisplay = dicomSeries.size(null) == 0;
        if (firstImageToDisplay) {
          MediaSeriesGroup patient = dicomModel.getParent(dicomSeries, DicomModel.patient);
          if (patient != null) {
            String oldDicomPtUID = (String) patient.getTagValue(TagW.PatientPseudoUID);
            String dicomPtUID = (String) reader.getTagValue(TagW.PatientPseudoUID);
            if (!Objects.equals(oldDicomPtUID, dicomPtUID)) {
              // Fix when patientUID in xml have different patient name
              dicomModel.mergePatientUID(oldDicomPtUID, dicomPtUID);
            }
          }
          MediaSeriesGroup study = dicomModel.getParent(dicomSeries, DicomModel.study);
          if (study != null) {
            String oldStudyUID = (String) study.getTagValue(TagD.get(Tag.StudyInstanceUID));
            String studyUID = TagD.getTagValue(reader, Tag.StudyInstanceUID, String.class);
            if (!Objects.equals(oldStudyUID, studyUID)) {
              // Fix when StudyInstanceUID in xml have different patient name
              dicomModel.mergeStudyUID(oldStudyUID, studyUID);
            }
          }
        }

        for (MediaElement media : medias) {
          applyPresentationModel(media);
          dicomModel.applySplittingRules(dicomSeries, media);
        }
        if (firstImageToDisplay && dicomSeries.size(null) == 0) {
          firstImageToDisplay = false;
        }
      }

      Thumbnail thumb = (Thumbnail) dicomSeries.getTagValue(TagW.Thumbnail);
      if (thumb != null) {
        thumb.repaint();
      }

      if (firstImageToDisplay) {
        boolean openNewTab = true;
        MediaSeriesGroup entry1 = dicomModel.getParent(dicomSeries, DicomModel.patient);
        if (entry1 != null) {
          synchronized (UIManager.VIEWER_PLUGINS) {
            for (ViewerPlugin<?> p : UIManager.VIEWER_PLUGINS) {
              if (entry1.equals(p.getGroupID())) {
                if (p instanceof ImageViewerPlugin imageViewerPlugin) {
                  ViewCanvas<?> pane = imageViewerPlugin.getSelectedImagePane();
                  if (pane != null
                      && pane.getImageLayer() != null
                      && pane.getImageLayer().getSourceImage() == null) {
                    // When the selected view has no image send, open in it.
                    break;
                  }
                }
                openNewTab = false;
                break;
              }
            }
          }
        }
        if (openNewTab) {
          SeriesViewerFactory plugin = UIManager.getViewerFactory(dicomSeries.getMimeType());
          if (plugin != null && !(plugin instanceof MimeSystemAppFactory)) {
            ViewerPluginBuilder.openSequenceInPlugin(plugin, dicomSeries, dicomModel, true, true);
          } else if (plugin != null) {
            // Send event to select the related patient in Dicom Explorer.
            dicomModel.firePropertyChange(
                new ObservableEvent(
                    ObservableEvent.BasicAction.SELECT, dicomModel, null, dicomSeries));
          }
        }
      }
    }
  }

  private void applyPresentationModel(MediaElement media) {
    String sopUID = TagD.getTagValue(media, Tag.SOPInstanceUID, String.class);

    SopInstance sop;
    if (seriesInstanceList.isContainsMultiframes()) {
      sop =
          seriesInstanceList.getSopInstance(
              sopUID, TagD.getTagValue(media, Tag.InstanceNumber, Integer.class));
    } else {
      sop = seriesInstanceList.getSopInstance(sopUID);
    }

    if (sop != null && sop.getGraphicModel() instanceof GraphicModel model) {
      int frames = media.getMediaReader().getMediaElementNumber();
      if (frames > 1 && media.getKey() instanceof Integer) {
        String seriesUID = TagD.getTagValue(media, Tag.SeriesInstanceUID, String.class);

        for (ReferencedSeries s : model.getReferencedSeries()) {
          if (s.getUuid().equals(seriesUID)) {
            for (ReferencedImage refImg : s.getImages()) {
              if (refImg.getUuid().equals(sopUID)) {
                List<Integer> f = refImg.getFrames();
                if (f == null || f.contains(media.getKey())) {
                  media.setTag(TagW.PresentationModel, model);
                }
                break;
              }
            }
          }
        }
      } else {
        media.setTag(TagW.PresentationModel, model);
      }
    }
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
        synchronized (DownloadManager.TASKS) {
          for (LoadSeries s : DownloadManager.TASKS) {
            if (s != this && StateValue.STARTED.equals(s.getState())) {
              cancelAndReplace(s);
              break;
            }
          }
        }
      }
    }
  }

  public LoadSeries cancelAndReplace(LoadSeries s) {
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
    Thumbnail thumbnail = (Thumbnail) s.getDicomSeries().getTagValue(TagW.Thumbnail);
    if (thumbnail != null) {
      LoadSeries.removeThumbnailMouseAndKeyAdapter(thumbnail);
      addListenerToThumbnail(thumbnail, taskResume, dicomModel);
    }
    DownloadManager.addLoadSeries(taskResume, dicomModel, true);
    DownloadManager.removeLoadSeries(s, dicomModel);

    return taskResume;
  }

  public int getConcurrentDownloads() {
    return concurrentDownloads;
  }
}
