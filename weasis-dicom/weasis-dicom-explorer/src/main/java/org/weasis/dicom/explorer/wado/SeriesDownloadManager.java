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
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JProgressBar;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
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
import org.weasis.core.api.net.AuthResponse;
import org.weasis.core.api.net.ClosableURLConnection;
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
import org.weasis.dicom.explorer.wado.LoadSeries.Status;
import org.weasis.dicom.mf.SopInstance;
import org.weasis.dicom.mf.WadoParameters;
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
    this.dicomSeries = dicomSeries;
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

  /** Handles individual DICOM instance download task. */
  class Download implements Callable<Boolean> {
    private final String url;
    private Status status;

    public Download(String url) {
      this.url = url;
      this.status = Status.DOWNLOADING;
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
      status = Status.ERROR;
      dicomSeries.setTag(LoadSeries.DOWNLOAD_ERRORS, errors.incrementAndGet());
    }

    private void process() throws IOException, URISyntaxException {
      boolean isFirstImage =
          dicomSeries.size(null) == 0 && seriesInitialized.compareAndSet(false, true);

      HttpStream httpStream = HttpUtils.getHttpResponse(url, urlParams, authMethod);
      handleAuthenticationIfNeeded(httpStream);

      try (InputStream stream = httpStream.getInputStream()) {
        Path tempFile = writeInCache ? createTempFile() : Path.of(URIUtils.getURI(url));

        if (writeInCache) {
          LOGGER.debug("Downloading DICOM instance {} to {}", url, tempFile.getFileName());
          int bytesTransferred = downloadToCache(httpStream, tempFile);
          if (bytesTransferred >= 0) {
            return;
          }
          tempFile = moveToExportDir(tempFile);
        }

        StreamUtil.safeClose(stream);
        processDownloadedFile(tempFile, isFirstImage);
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

    private Path createTempFile() throws IOException {
      return Files.createTempFile(ensureDicomTmpDir(), "image_", ".dcm");
    }

    private Path moveToExportDir(Path tempFile) throws IOException {
      Path targetPath = DicomMediaIO.DICOM_EXPORT_DIR.resolve(tempFile.getFileName());
      return Files.move(tempFile, targetPath);
    }

    private void processDownloadedFile(Path file, boolean isFirstImage) throws IOException {
      DicomMediaIO dicomReader = new DicomMediaIO(file.toFile());

      if (dicomReader.isReadableDicom() && isFirstImage) {
        updateSeriesMetadata(dicomReader);
      }

      if (status == Status.DOWNLOADING) {
        status = Status.COMPLETE;
        handleReadableFile(dicomReader, file, isFirstImage);
      }
      incrementProgressBarValue();
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
      }
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
      String contentType =
          response instanceof ClosableURLConnection(java.net.URLConnection urlConnection)
              ? urlConnection.getContentType()
              : ((AuthResponse) response).response().getHeader("Content-Type");

      byte[] boundary =
          BoundaryExtractor.extractBoundary(contentType, MultipartConstants.MULTIPART_RELATED);
      if (boundary == null) {
        throw new IOException("No boundary in Content-Type: " + contentType);
      }

      try (MultipartReader reader = new MultipartReader(response.getInputStream(), boundary)) {

        if (!reader.skipFirstBoundary()) {
          throw new MultipartStreamException("Failed to skip first boundary");
        }

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
      if (overrideList == null) {
        return;
      }

      MediaSeriesGroup study = dicomModel.getParent(dicomSeries, DicomModel.study);
      MediaSeriesGroup patient = dicomModel.getParent(dicomSeries, DicomModel.patient);
      ElementDictionary dic = ElementDictionary.getStandardElementDictionary();

      for (int tag : overrideList) {
        TagW tagElement = patient.getTagElement(tag);
        Object value =
            tagElement != null
                ? patient.getTagValue(tagElement)
                : study.getTagValue(study.getTagElement(tag));

        if (tagElement != null || study.getTagElement(tag) != null) {
          DicomMediaUtils.fillAttributes(
              dataset, tagElement != null ? tagElement : study.getTagElement(tag), value, dic);
        }
      }
    }

    private void cleanupBulkDataFiles(DicomInputStream dis) {
      List<java.io.File> bulkFiles = dis.getBulkDataFiles();
      if (bulkFiles != null) {
        bulkFiles.forEach(file -> FileUtil.delete(file.toPath()));
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
  }
}
