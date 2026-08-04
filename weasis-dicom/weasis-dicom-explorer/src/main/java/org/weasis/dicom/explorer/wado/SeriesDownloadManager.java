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

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.swing.JProgressBar;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.img.util.DicomUtils;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.dcm4che3.io.DicomOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.task.SeriesProgressMonitor;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Thumbnail;
import org.weasis.core.api.net.HttpStream;
import org.weasis.core.api.net.HttpUtils;
import org.weasis.core.api.net.URIUtils;
import org.weasis.core.api.net.URLParameters;
import org.weasis.core.api.net.auth.AuthMethod;
import org.weasis.core.api.util.ThreadUtil;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.StreamIOException;
import org.weasis.core.util.StreamUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomMediaIO.Reading;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.codec.utils.SeriesInstanceList;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.rs.RsQueryResult;
import org.weasis.dicom.mf.SopInstance;
import org.weasis.dicom.mf.WadoParameters;
import org.weasis.dicom.param.CancelListener;
import org.weasis.dicom.web.BoundaryExtractor;
import org.weasis.dicom.web.MultipartConstants;
import org.weasis.dicom.web.MultipartReader;
import org.weasis.dicom.web.MultipartStreamException;

/**
 * Manages concurrent downloading of DICOM series from WADO servers. Handles progress tracking,
 * error management, and cache operations.
 */
public class SeriesDownloadManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(SeriesDownloadManager.class);

  private static final int INSTANCE_PAGE_SIZE = 1000;

  private final LoadSeries loadSeries;
  private final DicomModel dicomModel;
  private final DicomSeries dicomSeries;
  private final JProgressBar progressBar;
  private final URLParameters urlParams;
  private final AuthMethod authMethod;
  private final boolean writeInCache;
  private final AtomicInteger errors;
  private final AtomicBoolean seriesInitialized;
  private final Semaphore downloadSemaphore;

  public SeriesDownloadManager(
      LoadSeries loadSeries,
      DicomModel dicomModel,
      DicomSeries dicomSeries,
      JProgressBar progressBar,
      URLParameters urlParams,
      AuthMethod authMethod,
      boolean writeInCache,
      int concurrentDownloads,
      AtomicInteger errors,
      AtomicBoolean seriesInitialized) {
    this.loadSeries = loadSeries;
    this.dicomModel = dicomModel;
    this.dicomSeries = Objects.requireNonNull(dicomSeries);
    this.progressBar = progressBar;
    this.urlParams = urlParams;
    this.authMethod = authMethod;
    this.writeInCache = writeInCache;
    this.errors = errors;
    this.seriesInitialized = seriesInitialized;
    this.downloadSemaphore = new Semaphore(concurrentDownloads);
  }

  /**
   * Starts the download process for the series.
   *
   * @return true if download started successfully, false otherwise
   */
  public Boolean startDownload() {
    MediaSeriesGroup patient = dicomModel.getParent(dicomSeries, DicomModel.patient);
    LOGGER.info("Downloading series of {} [{}]", patient, dicomSeries);

    WadoParameters wado = (WadoParameters) dicomSeries.getTagValue(TagW.WadoParameters);
    if (wado == null) {
      return false;
    }

    if (Boolean.TRUE.equals(dicomSeries.getTagValue(LoadSeries.SERIES_BULK_RETRIEVE))) {
      // First pass on an empty series uses the single bulk stream. On resume, or when that stream
      // is truncated (some DICOMweb servers cap a retrieve), complete the missing instances one by
      // one rather than re-fetching the whole series.
      if (dicomSeries.size(null) == 0 && startBulkSeriesDownload(wado)) {
        return true;
      }
      if (!completeMissingInstances(wado)) {
        return startBulkSeriesDownload(wado);
      }
    }

    SeriesInstanceList seriesInstanceList = loadSeries.getSeriesInstanceList();
    List<SopInstance> sopList = seriesInstanceList.getSortedList();

    try (ExecutorService imageDownloader =
        ThreadUtil.newVirtualThreadPerTaskExecutor("Image Downloader")) {
      List<Callable<Boolean>> tasks = createDownloadTasks(sopList, seriesInstanceList, wado);

      dicomSeries.setTag(LoadSeries.DOWNLOAD_START_TIME, System.currentTimeMillis());
      imageDownloader.invokeAll(tasks);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return true;
  }

  private List<Callable<Boolean>> createDownloadTasks(
      List<SopInstance> sopList, SeriesInstanceList seriesInstanceList, WadoParameters wado) {
    MediaSeriesGroup study = dicomModel.getParent(dicomSeries, DicomModel.study);
    List<Callable<Boolean>> tasks = new ArrayList<>(sopList.size());
    int[] downloadOrder = generateDownloadOrder(sopList.size());

    initializeProgressBar(sopList.size());
    for (int k = 0; k < sopList.size(); k++) {
      if (loadSeries.isCancelled()) {
        break;
      }

      SopInstance instance = sopList.get(downloadOrder[k]);

      if (shouldSkipInstance(seriesInstanceList, instance, study)) {
        continue;
      }

      String url = buildDownloadUrl(wado, instance, study);
      LOGGER.debug("Download DICOM instance {} index {}.", url, k);
      tasks.add(new Download(url));
    }
    return tasks;
  }

  private boolean shouldSkipInstance(
      SeriesInstanceList seriesInstanceList, SopInstance instance, MediaSeriesGroup study) {
    if (seriesInstanceList.isContainsMultiframes()
        && seriesInstanceList.getSopInstance(instance.getSopInstanceUID()) != instance) {
      return true;
    }

    // Test if SOPInstanceUID already exists
    if (isSOPInstanceUIDExist(study, dicomSeries, instance.getSopInstanceUID())) {
      incrementProgressBarValue();
      LOGGER.debug("DICOM instance {} already exists, skip.", instance.getSopInstanceUID());
      return true;
    }
    return false;
  }

  private String buildDownloadUrl(
      WadoParameters wado, SopInstance instance, MediaSeriesGroup study) {
    StringBuilder url = new StringBuilder(wado.getBaseURL());

    if (instance.getDirectDownloadFile() != null) {
      url.append(instance.getDirectDownloadFile());
    } else {
      appendWadoQueryParameters(url, wado, instance, study);
    }

    url.append(wado.getAdditionalParameters());
    return url.toString();
  }

  private void appendWadoQueryParameters(
      StringBuilder url, WadoParameters wado, SopInstance instance, MediaSeriesGroup study) {
    url.append("?requestType=WADO");
    if (!wado.isRequireOnlySOPInstanceUID()) {
      String studyUID = TagD.getTagValue(study, Tag.StudyInstanceUID, String.class);
      String seriesUID = TagD.getTagValue(dicomSeries, Tag.SeriesInstanceUID, String.class);
      url.append("&studyUID=").append(studyUID);
      url.append("&seriesUID=").append(seriesUID);
    }
    url.append("&objectUID=").append(instance.getSopInstanceUID());
    url.append("&contentType=application%2Fdicom");

    appendTransferSyntaxParameters(url);
  }

  private void appendTransferSyntaxParameters(StringBuilder url) {
    String wadoTsuid = (String) dicomSeries.getTagValue(TagW.WadoTransferSyntaxUID);
    if (StringUtil.hasText(wadoTsuid)) {
      url.append("&transferSyntax=").append(wadoTsuid);
      Integer rate = (Integer) dicomSeries.getTagValue(TagW.WadoCompressionRate);
      if (rate != null && rate > 0) {
        url.append("&imageQuality=").append(rate);
      }
    }
  }

  private int[] generateDownloadOrder(int size) {
    int[] order = new int[size];
    if (size < 4) {
      for (int i = 0; i < size; i++) {
        order[i] = i;
      }
      return order;
    }
    boolean[] visited = new boolean[size];
    int pos = 0;
    // Download endpoints and middle first
    order[pos++] = 0;
    visited[0] = true;
    order[pos++] = size - 1;
    visited[size - 1] = true;

    int middle = (size - 1) / 2;
    order[pos++] = middle;
    visited[middle] = true;

    // Binary search pattern for remaining positions
    int step = middle;
    while (step > 0) {
      for (int i = 1; i < visited.length; i++) {
        if (visited[i] && !visited[i - 1]) {
          int start = i - 1;
          while (start > 0 && !visited[start]) start--;
          int mid = start + (i - start) / 2;
          if (!visited[mid]) {
            visited[mid] = true;
            order[pos++] = mid;
          }
        }
      }
      step /= 2;
    }
    return order;
  }

  private boolean isSOPInstanceUIDExist(MediaSeriesGroup study, Series<?> series, String sopUID) {
    TagW sopTag = TagD.getUID(Level.INSTANCE);
    if (series.hasMediaContains(sopTag, sopUID)) {
      return true;
    }
    // Check split series
    String seriesUID = TagD.getTagValue(series, Tag.SeriesInstanceUID, String.class);
    if (study != null && seriesUID != null) {
      return dicomModel.getChildren(study).stream()
          .filter(group -> series != group && group instanceof Series<?> s)
          .filter(group -> seriesUID.equals(TagD.getTagValue(group, Tag.SeriesInstanceUID)))
          .anyMatch(group -> ((Series<?>) group).hasMediaContains(sopTag, sopUID));
    }
    return false;
  }

  private void initializeProgressBar(int max) {
    GuiExecutor.execute(
        () -> {
          progressBar.setMaximum(max);
          progressBar.setValue(0);
        });
  }

  private void incrementProgressBarValue() {
    GuiExecutor.execute(() -> progressBar.setValue(progressBar.getValue() + 1));
  }

  private Path ensureDicomTmpDir() {
    if (!Files.exists(LoadSeries.DICOM_TMP_DIR)) {
      LOGGER.info("DICOM tmp dir not found. Re-creating it!");
      AppProperties.buildAccessibleTempDirectory("downloading");
    }
    return LoadSeries.DICOM_TMP_DIR;
  }

  private Path createTempFile() throws IOException {
    return Files.createTempFile(ensureDicomTmpDir(), "image_", ".dcm");
  }

  /**
   * Enumerates the series instances through QIDO-RS into {@link #loadSeries}'s instance list so a
   * resumed bulk series can be completed one instance at a time (the per-instance download skips
   * the instances already stored). Returns {@code false} when nothing could be enumerated, so the
   * caller falls back to the single bulk stream.
   */
  private boolean completeMissingInstances(WadoParameters wado) {
    SeriesInstanceList seriesInstanceList = loadSeries.getSeriesInstanceList();
    if (!seriesInstanceList.getSortedList().isEmpty()) {
      return true; // Already enumerated on a previous attempt.
    }
    MediaSeriesGroup study = dicomModel.getParent(dicomSeries, DicomModel.study);
    String studyUID = TagD.getTagValue(study, Tag.StudyInstanceUID, String.class);
    String seriesUID = TagD.getTagValue(dicomSeries, Tag.SeriesInstanceUID, String.class);
    if (!StringUtil.hasText(studyUID) || !StringUtil.hasText(seriesUID)) {
      return false;
    }

    String baseUrl = LoadSeries.dicomWebBaseUrl(wado, dicomSeries);
    // The manifest origin keeps a non-empty WadoParameters base and a relative retrieve path; the
    // RS-query origin has an empty base and needs an absolute one. buildDownloadUrl() prepends
    // wado.getBaseURL(), so match the retrieve path to whichever the origin uses.
    String retrievePrefix = StringUtil.hasText(wado.getBaseURL()) ? "" : baseUrl;

    Map<String, String> headers = new HashMap<>(urlParams.headers());
    headers.put("Accept", "application/dicom+json"); // NON-NLS
    URLParameters queryParams = new URLParameters(headers);
    String baseQuery =
        baseUrl
            + "/studies/" // NON-NLS
            + studyUID
            + "/series/" // NON-NLS
            + seriesUID
            + "/instances?includefield=00080016,00080018,00200013"; // NON-NLS

    int offset = 0;
    while (true) {
      String url = baseQuery + "&offset=" + offset + "&limit=" + INSTANCE_PAGE_SIZE; // NON-NLS
      List<Attributes> instances;
      try {
        instances = RsQueryResult.parseJSON(url, authMethod, queryParams);
      } catch (Exception e) {
        LOGGER.error("QIDO-RS enumeration failed for resumed bulk series {}", seriesUID, e);
        break;
      }
      if (instances.isEmpty()) {
        break;
      }
      for (Attributes instance : instances) {
        addSopInstance(instance, seriesInstanceList, studyUID, seriesUID, retrievePrefix);
      }
      offset += instances.size();
      if (instances.size() < INSTANCE_PAGE_SIZE) {
        break;
      }
    }
    return !seriesInstanceList.getSortedList().isEmpty();
  }

  private static void addSopInstance(
      Attributes instance,
      SeriesInstanceList seriesInstanceList,
      String studyUID,
      String seriesUID,
      String retrievePrefix) {
    String sopUID = instance.getString(Tag.SOPInstanceUID);
    if (!StringUtil.hasText(sopUID)) {
      return;
    }
    Integer frame = DicomUtils.getIntegerFromDicomElement(instance, Tag.InstanceNumber, null);
    if (seriesInstanceList.getSopInstance(sopUID, frame) == null) {
      SopInstance sop = new SopInstance(sopUID, instance.getString(Tag.SOPClassUID), frame);
      sop.setDirectDownloadFile(
          retrievePrefix
              + "/studies/" // NON-NLS
              + studyUID
              + "/series/" // NON-NLS
              + seriesUID
              + "/instances/" // NON-NLS
              + sopUID);
      seriesInstanceList.addSopInstance(sop);
    }
  }

  /**
   * Downloads a whole series in a single WADO-RS request ({@code
   * {base}/studies/{study}/series/{series}}) and ingests each part of the {@code multipart/related}
   * response. Used for partial {@code DICOM_WEB} manifests opted into the series-level bulk
   * retrieve on the first pass (see {@link #completeMissingInstances} for the resume path).
   */
  private Boolean startBulkSeriesDownload(WadoParameters wado) {
    MediaSeriesGroup study = dicomModel.getParent(dicomSeries, DicomModel.study);
    String studyUID = TagD.getTagValue(study, Tag.StudyInstanceUID, String.class);
    String seriesUID = TagD.getTagValue(dicomSeries, Tag.SeriesInstanceUID, String.class);
    if (!StringUtil.hasText(studyUID) || !StringUtil.hasText(seriesUID)) {
      return false;
    }

    String url =
        LoadSeries.dicomWebBaseUrl(wado, dicomSeries)
            + "/studies/" // NON-NLS
            + studyUID
            + "/series/" // NON-NLS
            + seriesUID
            + wado.getAdditionalParameters();
    LOGGER.info("Bulk downloading series {}", url);

    // A cheap QIDO count makes the progress bar determinate; fall back to indeterminate otherwise.
    Integer count = fetchSeriesInstanceCount(wado, studyUID, seriesUID);
    if (count != null && count > 0) {
      initializeProgressBar(count);
      GuiExecutor.execute(() -> progressBar.setIndeterminate(false));
    } else {
      GuiExecutor.execute(() -> progressBar.setIndeterminate(true));
    }

    dicomSeries.setTag(LoadSeries.DOWNLOAD_START_TIME, System.currentTimeMillis());
    HttpStream httpStream = null;
    CancelListener onCancel = null;
    int parts = 0;
    try {
      httpStream = HttpUtils.getHttpResponse(url, urlParams, authMethod);
      // Close the stream on cancel so a part read blocked mid-stream unblocks immediately instead
      // of waiting for the current part to finish.
      HttpStream toClose = httpStream;
      onCancel = () -> closeQuietly(toClose);
      loadSeries.addCancelListener(onCancel);

      int code = httpStream.getResponseCode();
      if (code >= HttpURLConnection.HTTP_BAD_REQUEST) {
        if (authMethod != null && code == HttpURLConnection.HTTP_UNAUTHORIZED) {
          authMethod.resetToken();
          authMethod.getToken();
        }
        throw new IOException("Server response code: " + code);
      }
      parts = downloadSeriesMultipart(httpStream);
    } catch (StreamIOException e) {
      if (!loadSeries.isCancelled()) {
        loadSeries.setHasError(true);
        dicomSeries.setTag(LoadSeries.DOWNLOAD_ERRORS, errors.incrementAndGet());
        LOGGER.error("Bulk series download failed: {}", url, e);
      }
    } catch (IOException e) {
      // A cancel closes the stream, which surfaces here as an IOException: not a real error.
      if (!loadSeries.isCancelled()) {
        dicomSeries.setTag(LoadSeries.DOWNLOAD_ERRORS, errors.incrementAndGet());
        LOGGER.error("Bulk series download failed: {}", url, e);
      }
    } finally {
      if (onCancel != null) {
        loadSeries.removeCancelListener(onCancel);
      }
      closeQuietly(httpStream);
      GuiExecutor.execute(() -> progressBar.setIndeterminate(false));
    }
    // The series is complete unless the server returned fewer parts than it holds (some DICOMweb
    // servers cap a retrieve); the caller then completes the remaining instances individually.
    boolean complete = loadSeries.isCancelled() || count == null || parts >= count;
    if (!complete) {
      LOGGER.info(
          "Bulk retrieve returned {}/{} instances for series {}; completing per-instance",
          parts,
          count,
          seriesUID);
    }
    return complete;
  }

  private static void closeQuietly(AutoCloseable closeable) {
    if (closeable != null) {
      try {
        closeable.close();
      } catch (Exception e) {
        LOGGER.debug("Error closing HTTP stream", e);
      }
    }
  }

  /**
   * Queries {@code NumberOfSeriesRelatedInstances} for the series so bulk progress can be
   * determinate. Returns {@code null} when the server does not provide it.
   */
  private Integer fetchSeriesInstanceCount(WadoParameters wado, String studyUID, String seriesUID) {
    int n =
        RsQueryResult.seriesInstanceCount(
            LoadSeries.dicomWebBaseUrl(wado, dicomSeries),
            studyUID,
            seriesUID,
            urlParams,
            authMethod);
    return n > 0 ? n : null;
  }

  private int downloadSeriesMultipart(HttpStream httpStream) throws IOException {
    String contentType = httpStream.getHeaderField("Content-Type"); // NON-NLS
    byte[] boundary =
        BoundaryExtractor.extractBoundary(contentType, MultipartConstants.MULTIPART_RELATED);
    if (boundary == null) {
      throw new IOException("No boundary in Content-Type: " + contentType);
    }

    int[] overrideList =
        Optional.ofNullable((WadoParameters) dicomSeries.getTagValue(TagW.WadoParameters))
            .map(WadoParameters::getOverrideDicomTagIDList)
            .orElse(null);

    int parts = 0;
    try (MultipartReader reader = new MultipartReader(httpStream.getInputStream(), boundary)) {
      reader.skipFirstBoundary();
      do {
        if (loadSeries.isCancelled()) {
          break;
        }
        reader.readHeaders();
        boolean isFirstImage =
            dicomSeries.size(null) == 0 && seriesInitialized.compareAndSet(false, true);
        Path tempFile = createTempFile();
        boolean written;
        try (var partStream = reader.newPartInputStream()) {
          InputStream monitored = new SeriesProgressMonitor(dicomSeries, partStream);
          // -1 means fully written; anything else means interrupted/failed (temp file deleted).
          written =
              overrideList == null
                  ? FileUtil.writeStream(monitored, tempFile, false) == -1
                  : writeFileWithOverrides(monitored, tempFile, overrideList) == -1;
        }
        if (!written) {
          // Interrupted or failed part: drop the temp file (the override path already deleted it).
          FileUtil.delete(tempFile);
          continue;
        }
        parts++;
        try {
          // Resume re-streams the whole series: skip instances already stored.
          ingest(tempFile, isFirstImage, true);
        } catch (IOException e) {
          dicomSeries.setTag(LoadSeries.DOWNLOAD_ERRORS, errors.incrementAndGet());
          LOGGER.error("Failed to ingest a series part", e);
        }
      } while (reader.readBoundary());
    } catch (MultipartStreamException e) {
      throw new IOException("Failed to parse multipart content", e);
    }
    return parts;
  }

  private int writeFileWithOverrides(InputStream in, Path targetFile, int[] overrideList)
      throws StreamIOException {
    try (DicomInputStream dis = new DicomInputStream(in)) {
      dis.setIncludeBulkData(IncludeBulkData.URI);
      Attributes dataset = dis.readDataset();
      String tsuid = dis.getTransferSyntax();

      applyOverrides(dataset, overrideList);

      try (DicomOutputStream dos = new DicomOutputStream(targetFile.toFile())) {
        dos.writeDataset(dataset.createFileMetaInformation(tsuid), dataset);
        dos.finish();
        dos.flush();
      }

      cleanupBulkDataFiles(dis);
      return -1;
    } catch (InterruptedIOException e) {
      FileUtil.delete(targetFile);
      LOGGER.error("Interrupted while writing: {}", e.getMessage());
      return e.bytesTransferred;
    } catch (IOException e) {
      FileUtil.delete(targetFile);
      throw new StreamIOException(e);
    } catch (Exception e) {
      FileUtil.delete(targetFile);
      LOGGER.error("Error writing DICOM file", e);
      return 0;
    }
  }

  private void applyOverrides(Attributes dataset, int[] overrideList) {
    applyOverrides(
        dataset,
        overrideList,
        dicomModel.getParent(dicomSeries, DicomModel.patient),
        dicomModel.getParent(dicomSeries, DicomModel.study));
  }

  /**
   * Replaces in the dataset the tags listed in the manifest {@code overrideDicomTagsList} by the
   * values held by the patient node, then the study node. Tags absent from both nodes and tags
   * without a value are left untouched, as is the Study Instance UID, which identifies the study
   * node and cannot be changed without breaking the model hierarchy.
   */
  static void applyOverrides(
      Attributes dataset, int[] overrideList, MediaSeriesGroup patient, MediaSeriesGroup study) {
    if (dataset == null || overrideList == null) {
      return;
    }
    List<MediaSeriesGroup> groups = Stream.of(patient, study).filter(Objects::nonNull).toList();
    ElementDictionary dic = ElementDictionary.getStandardElementDictionary();

    for (int tag : overrideList) {
      if (tag == Tag.StudyInstanceUID) {
        // Logged at debug level: this runs for every downloaded instance.
        LOGGER.debug("Study Instance UID cannot be overridden, ignoring it in the override list");
        continue;
      }
      for (MediaSeriesGroup group : groups) {
        TagW tagElement = group.getTagElement(tag);
        if (tagElement != null) {
          DicomMediaUtils.fillAttributes(dataset, tagElement, group.getTagValue(tagElement), dic);
          break;
        }
      }
    }
  }

  private void cleanupBulkDataFiles(DicomInputStream dis) {
    List<java.io.File> bulkFiles = dis.getBulkDataFiles();
    if (bulkFiles != null) {
      bulkFiles.forEach(file -> FileUtil.delete(file.toPath()));
    }
  }

  /** Reads a downloaded DICOM file into the model and refreshes the UI. */
  private void ingest(Path file, boolean isFirstImage, boolean skipIfExists) throws IOException {
    DicomMediaIO dicomReader = new DicomMediaIO(file.toFile());
    if (skipIfExists && dicomReader.isReadableDicom() && isAlreadyStored(dicomReader)) {
      dicomReader.close();
      FileUtil.delete(file);
      incrementProgressBarValue();
      return;
    }
    if (dicomReader.isReadableDicom() && isFirstImage) {
      updateSeriesMetadata(dicomReader);
    }
    handleReadableFile(dicomReader, file, isFirstImage);
    incrementProgressBarValue();
  }

  private boolean isAlreadyStored(DicomMediaIO reader) {
    String sopUID = TagD.getTagValue(reader, Tag.SOPInstanceUID, String.class);
    if (sopUID == null) {
      return false;
    }
    MediaSeriesGroup study = dicomModel.getParent(dicomSeries, DicomModel.study);
    return isSOPInstanceUIDExist(study, dicomSeries, sopUID);
  }

  private void updateSeriesMetadata(DicomMediaIO reader) {
    MediaSeriesGroup patient = dicomModel.getParent(dicomSeries, DicomModel.patient);
    MediaSeriesGroup study = dicomModel.getParent(dicomSeries, DicomModel.study);
    reader.writeMetaData(patient);
    reader.writeMetaData(study);
    reader.writeMetaData(dicomSeries);

    GuiExecutor.invokeAndWait(
        () -> {
          Thumbnail thumb = (Thumbnail) dicomSeries.getTagValue(TagW.Thumbnail);
          if (thumb != null) {
            thumb.repaint();
          }
          dicomModel.firePropertyChange(
              new ObservableEvent(
                  ObservableEvent.BasicAction.UPDATE_PARENT, dicomModel, null, dicomSeries));
        });
  }

  private void handleReadableFile(DicomMediaIO reader, Path file, boolean isFirstImage) {
    Reading reading = reader.getReadingStatus();
    if (reading == Reading.READABLE) {
      if (file.startsWith(AppProperties.APP_TEMP_DIR)) {
        reader.getFileCache().setOriginalTempFile(file);
      }
      updateUI(reader, isFirstImage);
    } else if (reading == Reading.ERROR) {
      errors.incrementAndGet();
    } else if (reading == Reading.UNSUPPORTED) {
      LOGGER.info("Skipping unsupported DICOM SOP Class for file: {}", file);
    }
  }

  private void updateUI(DicomMediaIO reader, boolean firstImageToDisplay) {
    DicomMediaIO.ResultContainer result =
        reader.getMediaElement(factory -> factory.buildDicomSpecialElement(reader));

    DicomImageElement[] medias = result.getImage();
    if (medias != null) {
      if (firstImageToDisplay) {
        reconcilePatientAndStudyUIDs(reader);
      }

      for (DicomImageElement media : medias) {
        applyPresentationModel(media);
        dicomModel.applySplittingRules(dicomSeries, media);
      }
    }

    DicomSpecialElement specialElement = result.getSpecialElement();
    if (specialElement != null) {
      dicomModel.applySplittingRules(dicomSeries, specialElement);
    }

    openViewerIfNeeded();
    refreshThumbnail();
  }

  private void reconcilePatientAndStudyUIDs(DicomMediaIO reader) {
    MediaSeriesGroup patient = dicomModel.getParent(dicomSeries, DicomModel.patient);
    if (patient != null) {
      String oldPatientUID = (String) patient.getTagValue(TagW.PatientPseudoUID);
      String newPatientUID = (String) reader.getTagValue(TagW.PatientPseudoUID);
      if (!Objects.equals(oldPatientUID, newPatientUID)) {
        dicomModel.mergePatientUID(oldPatientUID, newPatientUID, loadSeries.getOpeningStrategy());
      }
    }
    MediaSeriesGroup study = dicomModel.getParent(dicomSeries, DicomModel.study);
    if (study != null) {
      String oldStudyUID = (String) study.getTagValue(TagD.get(Tag.StudyInstanceUID));
      String newStudyUID = TagD.getTagValue(reader, Tag.StudyInstanceUID, String.class);
      if (!Objects.equals(oldStudyUID, newStudyUID)) {
        dicomModel.mergeStudyUID(oldStudyUID, newStudyUID);
      }
    }
  }

  private void openViewerIfNeeded() {
    MediaSeriesGroup patient = dicomModel.getParent(dicomSeries, DicomModel.patient);
    if (patient != null) {
      var openingStrategy = loadSeries.getOpeningStrategy();
      if (openingStrategy != null) {
        openingStrategy.openViewerPlugin(patient, dicomModel, dicomSeries);
      }
    }
  }

  private void refreshThumbnail() {
    GuiExecutor.execute(
        () -> {
          Thumbnail thumb = (Thumbnail) dicomSeries.getTagValue(TagW.Thumbnail);
          if (thumb != null) {
            thumb.repaint();
          }
        });
  }

  private void applyPresentationModel(DicomImageElement media) {
    String sopUID = TagD.getTagValue(media, Tag.SOPInstanceUID, String.class);

    SeriesInstanceList seriesInstanceList = loadSeries.getSeriesInstanceList();
    SopInstance sop =
        seriesInstanceList.isContainsMultiframes()
            ? seriesInstanceList.getSopInstance(
                sopUID, TagD.getTagValue(media, Tag.InstanceNumber, Integer.class))
            : seriesInstanceList.getSopInstance(sopUID);

    if (sop != null && sop.getGraphicModel() instanceof GraphicModel model) {
      if (shouldApplyModel(media, model, sopUID)) {
        media.setTag(TagW.PresentationModel, model);
      }
    }
  }

  private boolean shouldApplyModel(DicomImageElement media, GraphicModel model, String sopUID) {
    int frames = media.getMediaReader().getMediaElementNumber();
    if (frames <= 1 || !(media.getKey() instanceof Integer frameKey)) {
      return true;
    }
    String seriesUID = TagD.getTagValue(media, Tag.SeriesInstanceUID, String.class);

    return model.getReferencedSeries().stream()
        .filter(s -> s.getUuid().equals(seriesUID))
        .flatMap(s -> s.getImages().stream())
        .filter(img -> img.getUuid().equals(sopUID))
        .anyMatch(img -> img.getFrames() == null || img.getFrames().contains(frameKey));
  }

  /** Handles individual DICOM instance download task. */
  class Download implements Callable<Boolean> {
    private final String url;

    public Download(String url) {
      this.url = url;
    }

    @Override
    public Boolean call() {
      try {
        downloadSemaphore.acquire();
        try {
          process();
        } finally {
          downloadSemaphore.release();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return Boolean.FALSE;
      } catch (StreamIOException e) {
        loadSeries.setHasError(true);
        markAsError();
        LOGGER.error("Download failed: {}", url, e);
      } catch (IOException | URISyntaxException e) {
        markAsError();
        LOGGER.error("Download failed: {}", url, e);
      }
      return Boolean.TRUE;
    }

    private void markAsError() {
      dicomSeries.setTag(LoadSeries.DOWNLOAD_ERRORS, errors.incrementAndGet());
    }

    private void process() throws IOException, URISyntaxException {
      boolean isFirstImage =
          dicomSeries.size(null) == 0 && seriesInitialized.compareAndSet(false, true);

      // Handle local file:// URIs directly without HTTP download
      URI uri = URIUtils.getURI(url);
      if (URIUtils.isFileURI(uri)) {
        ingestLocalFile(uri, isFirstImage);
        return;
      }

      HttpStream httpStream = HttpUtils.getHttpResponse(url, urlParams, authMethod);
      handleAuthenticationIfNeeded(httpStream);

      try (InputStream stream = httpStream.getInputStream()) {
        Path tempFile = writeInCache ? createTempFile() : URIUtils.toPath(uri);

        if (writeInCache) {
          LOGGER.debug("Downloading DICOM instance {} to {}", url, tempFile.getFileName());
          int bytesTransferred = downloadToCache(httpStream, tempFile);
          if (bytesTransferred >= 0) {
            return;
          }
          tempFile = moveToExportDir(tempFile);
        }

        StreamUtil.safeClose(stream);
        ingest(tempFile, isFirstImage, false);
      } finally {
        progressBar.setIndeterminate(progressBar.getMaximum() < 3);
      }
    }

    /**
     * Reads an instance from a local source. When the cache is enabled, the file is first copied
     * into the application cache so decoding does not hit a slow source (CD/DVD, network share) on
     * every cache miss.
     */
    private void ingestLocalFile(URI uri, boolean isFirstImage) throws IOException {
      Path localFile = URIUtils.getAbsolutePath(uri);
      if (localFile == null || !Files.exists(localFile)) {
        throw new IOException("Local file not found: " + url);
      }
      try {
        Path file = localFile;
        if (writeInCache) {
          Path tempFile = createTempFile();
          LOGGER.debug("Copying DICOM instance {} to {}", localFile, tempFile.getFileName());
          try (InputStream in = Files.newInputStream(localFile)) {
            if (FileUtil.writeStream(
                    new DicomSeriesProgressMonitor(dicomSeries, in, false), tempFile, false)
                >= 0) {
              // Interrupted or truncated copy: do not ingest a partial instance.
              FileUtil.delete(tempFile);
              return;
            }
          }
          file = moveToExportDir(tempFile);
        }
        ingest(file, isFirstImage, false);
      } finally {
        progressBar.setIndeterminate(progressBar.getMaximum() < 3);
      }
    }

    private void handleAuthenticationIfNeeded(HttpStream httpStream) throws IOException {
      int code = httpStream.getResponseCode();
      if (code >= HttpURLConnection.HTTP_BAD_REQUEST) {
        if (authMethod != null && code == HttpURLConnection.HTTP_UNAUTHORIZED) {
          authMethod.resetToken();
          authMethod.getToken();
        }
        throw new IOException("Server response code: " + code);
      }
    }

    private Path moveToExportDir(Path tempFile) throws IOException {
      Path targetPath = DicomMediaIO.DICOM_EXPORT_DIR.resolve(tempFile.getFileName());
      return Files.move(tempFile, targetPath);
    }

    private int downloadToCache(HttpStream response, Path targetFile) throws IOException {
      WadoParameters wadoParams = (WadoParameters) dicomSeries.getTagValue(TagW.WadoParameters);
      int[] overrideList =
          Optional.ofNullable(wadoParams)
              .map(WadoParameters::getOverrideDicomTagIDList)
              .orElse(null);

      int bytesTransferred =
          overrideList == null
              ? downloadWithoutOverride(response, targetFile, wadoParams)
              : downloadWithOverride(response, targetFile, overrideList);

      if (bytesTransferred == Integer.MIN_VALUE) {
        LOGGER.warn("Unsupported TSUID, retrying with default");
        bytesTransferred = retryWithDefaultTransferSyntax(targetFile, overrideList);
      }

      return bytesTransferred;
    }

    private int downloadWithoutOverride(
        HttpStream response, Path targetFile, WadoParameters wadoParams) throws IOException {
      if (wadoParams != null && wadoParams.isWadoRS()) {

        return downloadWadoRS(response, targetFile);
      }
      return FileUtil.writeStream(
          new DicomSeriesProgressMonitor(dicomSeries, response.getInputStream(), false),
          targetFile);
    }

    private int downloadWadoRS(HttpStream response, Path targetFile) throws IOException {
      String contentType = response.getHeaderField("Content-Type");
      byte[] boundary =
          BoundaryExtractor.extractBoundary(contentType, MultipartConstants.MULTIPART_RELATED);
      if (boundary == null) {
        throw new IOException("No boundary in Content-Type: " + contentType);
      }

      try (MultipartReader reader = new MultipartReader(response.getInputStream(), boundary)) {
        reader.skipFirstBoundary();
        int totalBytes;
        do {
          reader.readHeaders();
          try (var partStream = reader.newPartInputStream()) {
            totalBytes =
                FileUtil.writeStream(
                    new SeriesProgressMonitor(dicomSeries, partStream), targetFile, false);
          }
        } while (reader.readBoundary());
        return totalBytes;
      } catch (MultipartStreamException e) {
        throw new IOException("Failed to parse multipart content", e);
      }
    }

    private int downloadWithOverride(HttpStream response, Path targetFile, int[] overrideList)
        throws IOException {
      return writeFileWithOverrides(
          new DicomSeriesProgressMonitor(dicomSeries, response.getInputStream(), false),
          targetFile,
          overrideList);
    }

    private int retryWithDefaultTransferSyntax(Path targetFile, int[] overrideList)
        throws IOException {
      try (InputStream stream = replaceToDefaultTSUID().getInputStream()) {
        return overrideList == null
            ? FileUtil.writeStream(
                new DicomSeriesProgressMonitor(dicomSeries, stream, false), targetFile)
            : writeFileWithOverrides(
                new DicomSeriesProgressMonitor(dicomSeries, stream, false),
                targetFile,
                overrideList);
      }
    }

    private HttpStream replaceToDefaultTSUID() throws IOException {
      String modifiedUrl =
          url.contains("&transferSyntax=")
              ? url.replaceFirst(
                  "&transferSyntax=[^&]*", "&transferSyntax=" + UID.ExplicitVRLittleEndian)
              : url + "&transferSyntax=" + UID.ExplicitVRLittleEndian;
      return HttpUtils.getHttpResponse(modifiedUrl, urlParams, authMethod);
    }
  }
}
