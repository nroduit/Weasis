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
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.swing.JProgressBar;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.ObservableEvent.BasicAction;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.data.MediaSeries.MEDIA_POSITION;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.SeriesThumbnail;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Thumbnail;
import org.weasis.core.api.net.HttpStream;
import org.weasis.core.api.net.HttpUtils;
import org.weasis.core.api.net.URLParameters;
import org.weasis.core.api.net.auth.AuthMethod;
import org.weasis.core.api.util.ResourceUtil.ResourceIconPath;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.HiddenSeriesManager;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.main.ThumbnailMouseAndKeyAdapter;
import org.weasis.dicom.explorer.rs.RsQueryResult;
import org.weasis.dicom.mf.SopInstance;
import org.weasis.dicom.mf.WadoParameters;

/**
 * Manages thumbnail operations for DICOM series, including creation, downloading, and UI
 * interactions.
 */
public record ThumbnailManager(
    DicomSeries dicomSeries, DicomModel dicomModel, URLParameters urlParams) {

  private static final Logger LOGGER = LoggerFactory.getLogger(ThumbnailManager.class);

  private static final String JPEG_EXTENSION = ".jpg";
  private static final String IMAGE_JPEG_MIME = "image/jpeg";
  private static final String DICOM_JSON_MIME = "application/dicom+json"; // NON-NLS
  private static final int THUMBNAIL_QUALITY = 75;

  /** How a given DICOMweb server delivers thumbnails. */
  private enum ThumbnailMode {
    RS_THUMBNAIL,
    RENDERED
  }

  // WADO-RS thumbnail-service support, probed once and reused per DICOMweb server for the session.
  private static final Map<String, ThumbnailMode> SERVER_THUMBNAIL_MODE = new ConcurrentHashMap<>();

  /**
   * Loads and displays a thumbnail for the given SOP instance.
   *
   * @param instance the SOP instance
   * @param wadoParameters the WADO parameters
   * @param authMethod authentication method for HTTP requests
   */
  public void loadThumbnail(
      SopInstance instance, WadoParameters wadoParameters, AuthMethod authMethod) {
    Path thumbnailPath;
    if (wadoParameters.isWadoRS()
        && instance.getDirectDownloadFile() != null
        && dicomSeries.getTagValue(TagW.DirectDownloadThumbnail) == null) {
      // WADO-RS: capability-aware instance thumbnail with rendered fallback.
      thumbnailPath =
          fetchInstanceThumbnail(wadoParameters, instance.getSopInstanceUID(), authMethod);
    } else if (instance.getDirectDownloadFile() == null) {
      thumbnailPath = downloadThumbnailFromWado(instance, wadoParameters, authMethod);
    } else {
      thumbnailPath = downloadDirectThumbnail(wadoParameters, authMethod);
    }

    if (thumbnailPath != null) {
      updateSeriesThumbnail(thumbnailPath);
    }
  }

  /**
   * Loads a preview thumbnail for a series-level bulk retrieve, where no instance is known yet.
   * Uses the WADO-RS series thumbnail service, falling back to the rendered service on a
   * representative instance.
   */
  public void loadSeriesThumbnail(WadoParameters wadoParameters, AuthMethod authMethod) {
    if (!DicomMediaIO.SERIES_MIMETYPE.equals(dicomSeries.getMimeType())) {
      return;
    }
    MediaSeriesGroup study = dicomModel.getParent(dicomSeries, DicomModel.study);
    String studyUID = TagD.getTagValue(study, Tag.StudyInstanceUID, String.class);
    String seriesUID = TagD.getTagValue(dicomSeries, Tag.SeriesInstanceUID, String.class);
    if (!StringUtil.hasText(studyUID) || !StringUtil.hasText(seriesUID)) {
      return;
    }
    // A null instance UID selects the series-level thumbnail service.
    Path thumbnailPath = fetchRsThumbnail(wadoParameters, studyUID, seriesUID, null, authMethod);
    if (thumbnailPath != null) {
      updateSeriesThumbnail(thumbnailPath);
    }
  }

  private Path fetchInstanceThumbnail(
      WadoParameters wadoParameters, String sopInstanceUID, AuthMethod authMethod) {
    MediaSeriesGroup study = dicomModel.getParent(dicomSeries, DicomModel.study);
    String studyUID = TagD.getTagValue(study, Tag.StudyInstanceUID, String.class);
    String seriesUID = TagD.getTagValue(dicomSeries, Tag.SeriesInstanceUID, String.class);
    if (!StringUtil.hasText(studyUID) || !StringUtil.hasText(seriesUID)) {
      return null;
    }
    return fetchRsThumbnail(wadoParameters, studyUID, seriesUID, sopInstanceUID, authMethod);
  }

  /**
   * Fetches a WADO-RS thumbnail for the series ({@code sopInstanceUID == null}) or a single
   * instance, then caches — per DICOMweb server, for the session — whether the thumbnail service is
   * available. When it is not implemented (HTTP 404/405/501), the server is switched to the
   * DICOMweb rendered service and every later series reuses that decision without re-probing.
   */
  private Path fetchRsThumbnail(
      WadoParameters wadoParameters,
      String studyUID,
      String seriesUID,
      String sopInstanceUID,
      AuthMethod authMethod) {
    String baseUrl = LoadSeries.dicomWebBaseUrl(wadoParameters, dicomSeries);
    if (SERVER_THUMBNAIL_MODE.getOrDefault(baseUrl, ThumbnailMode.RS_THUMBNAIL)
        == ThumbnailMode.RS_THUMBNAIL) {
      String url =
          sopInstanceUID == null
              ? "%s/studies/%s/series/%s/thumbnail?viewport=%d%%2C%d"
                  .formatted(baseUrl, studyUID, seriesUID, Thumbnail.MAX_SIZE, Thumbnail.MAX_SIZE)
              : "%s/studies/%s/series/%s/instances/%s/thumbnail?viewport=%d%%2C%d"
                  .formatted(
                      baseUrl,
                      studyUID,
                      seriesUID,
                      sopInstanceUID,
                      Thumbnail.MAX_SIZE,
                      Thumbnail.MAX_SIZE);
      FetchResult result = download(url, JPEG_EXTENSION, createWadoRsParams(), authMethod);
      if (result.file() != null) {
        return result.file();
      }
      // Any RS failure falls back to /rendered below; only a definitive "not implemented" also
      // downgrades the server for the session. A transient failure keeps probing /thumbnail later.
      if (isThumbnailServiceUnsupported(result.code())) {
        LOGGER.info(
            "WADO-RS thumbnail service unavailable on {} (HTTP {}); switching to rendered service",
            baseUrl,
            result.code());
        SERVER_THUMBNAIL_MODE.put(baseUrl, ThumbnailMode.RENDERED);
      }
    }
    return fetchRenderedThumbnail(baseUrl, studyUID, seriesUID, sopInstanceUID, authMethod);
  }

  /**
   * DICOMweb rendered fallback. Only the instance-level {@code /rendered} resource is portable
   * (series-level rendering is rejected by some archives), so a bulk series first resolves a
   * representative instance.
   */
  private Path fetchRenderedThumbnail(
      String baseUrl,
      String studyUID,
      String seriesUID,
      String sopInstanceUID,
      AuthMethod authMethod) {
    String instanceUID =
        sopInstanceUID != null
            ? sopInstanceUID
            : fetchRepresentativeInstanceUID(baseUrl, studyUID, seriesUID, authMethod);
    if (!StringUtil.hasText(instanceUID)) {
      return null;
    }
    String url =
        "%s/studies/%s/series/%s/instances/%s/rendered?viewport=%d%%2C%d"
            .formatted(
                baseUrl, studyUID, seriesUID, instanceUID, Thumbnail.MAX_SIZE, Thumbnail.MAX_SIZE);
    return download(url, JPEG_EXTENSION, createWadoRsParams(), authMethod).file();
  }

  private static boolean isThumbnailServiceUnsupported(int code) {
    return code == HttpURLConnection.HTTP_NOT_FOUND
        || code == HttpURLConnection.HTTP_BAD_METHOD
        || code == HttpURLConnection.HTTP_NOT_IMPLEMENTED;
  }

  private String fetchRepresentativeInstanceUID(
      String baseUrl, String studyUID, String seriesUID, AuthMethod authMethod) {
    // Target a middle instance (representative slice) without listing the series: get the instance
    // count, then fetch a single UID at that offset (order is server-defined).
    int offset =
        RsQueryResult.seriesInstanceCount(baseUrl, studyUID, seriesUID, urlParams, authMethod) / 2;
    String url =
        "%s/studies/%s/series/%s/instances?includefield=00080018&limit=1&offset=%d" // NON-NLS
            .formatted(baseUrl, studyUID, seriesUID, offset);
    var headers = new HashMap<>(urlParams.headers());
    headers.put("Accept", DICOM_JSON_MIME);
    try {
      for (Attributes instance :
          RsQueryResult.parseJSON(url, authMethod, new URLParameters(headers))) {
        String sopUID = instance.getString(Tag.SOPInstanceUID);
        if (StringUtil.hasText(sopUID)) {
          return sopUID;
        }
      }
    } catch (Exception e) {
      LOGGER.debug("Cannot fetch a representative instance for series {}", seriesUID, e);
    }
    return null;
  }

  private Path downloadThumbnailFromWado(
      SopInstance instance, WadoParameters wadoParameters, AuthMethod authMethod) {
    if (!DicomMediaIO.SERIES_MIMETYPE.equals(dicomSeries.getMimeType())) {
      return null;
    }
    String url =
        buildWadoUrl(
            wadoParameters.getBaseURL(),
            getStudyUID(wadoParameters),
            getSeriesUID(wadoParameters),
            instance.getSopInstanceUID(),
            wadoParameters.getAdditionalParameters());
    return download(url, JPEG_EXTENSION, urlParams, authMethod).file();
  }

  private Path downloadDirectThumbnail(WadoParameters wadoParameters, AuthMethod authMethod) {
    String thumbURL = getWadoRsThumbnailUrl(wadoParameters);
    if (thumbURL == null) {
      thumbURL = (String) dicomSeries.getTagValue(TagW.DirectDownloadThumbnail);
      if (StringUtil.hasLength(thumbURL)) {
        if (thumbURL.startsWith(Thumbnail.THUMBNAIL_CACHE_DIR.toString())) {
          return Path.of(thumbURL);
        } else {
          thumbURL = wadoParameters.getBaseURL() + thumbURL;
        }
      }
    }

    URLParameters params = wadoParameters.isWadoRS() ? createWadoRsParams() : urlParams;
    String extension = wadoParameters.isWadoRS() ? JPEG_EXTENSION : FileUtil.getExtension(thumbURL);

    return downloadFromUrl(thumbURL, extension, params, authMethod);
  }

  private String getStudyUID(WadoParameters wadoParameters) {
    if (wadoParameters.isRequireOnlySOPInstanceUID()) {
      return "";
    }
    MediaSeriesGroup study = dicomModel.getParent(dicomSeries, DicomModel.study);
    return TagD.getTagValue(study, Tag.StudyInstanceUID, String.class);
  }

  private String getSeriesUID(WadoParameters wadoParameters) {
    return wadoParameters.isRequireOnlySOPInstanceUID()
        ? ""
        : TagD.getTagValue(dicomSeries, Tag.SeriesInstanceUID, String.class);
  }

  private String getWadoRsThumbnailUrl(WadoParameters wadoParameters) {
    if (wadoParameters.isWadoRS()) {
      String baseUrl = TagD.getTagValue(dicomSeries, Tag.RetrieveURL, String.class);
      return baseUrl != null
          ? "%s/thumbnail?viewport=%d%%2C%d"
              .formatted(baseUrl, Thumbnail.MAX_SIZE, Thumbnail.MAX_SIZE)
          : null;
    }
    return null;
  }

  private URLParameters createWadoRsParams() {
    var headers = new HashMap<>(urlParams.headers());
    headers.put("Accept", IMAGE_JPEG_MIME);
    return new URLParameters(headers);
  }

  /**
   * Downloaded thumbnail file (or {@code null}) together with the HTTP status ({@code -1} on I/O).
   */
  private record FetchResult(Path file, int code) {}

  private FetchResult download(
      String url, String extension, URLParameters params, AuthMethod authMethod) {
    try (HttpStream httpCon = HttpUtils.getHttpResponse(url, params, authMethod)) {
      int code = httpCon.getResponseCode();
      if (code >= HttpURLConnection.HTTP_OK && code < HttpURLConnection.HTTP_BAD_REQUEST) {
        return new FetchResult(saveThumbnailFile(httpCon, extension), code);
      }
      if (code == HttpURLConnection.HTTP_UNAUTHORIZED && authMethod != null) {
        authMethod.resetToken();
        authMethod.getToken();
      }
      return new FetchResult(null, code);
    } catch (Exception e) {
      LOGGER.error("Error downloading thumbnail from {}", url, e);
      return new FetchResult(null, -1);
    }
  }

  private Path downloadFromUrl(
      String url, String extension, URLParameters params, AuthMethod authMethod) {
    return download(url, extension, params, authMethod).file();
  }

  private Path saveThumbnailFile(HttpStream httpCon, String extension) throws IOException {
    Path outFile = Files.createTempFile(Thumbnail.THUMBNAIL_CACHE_DIR, "thumb_", extension);
    FileUtil.writeStreamWithIOException(httpCon.getInputStream(), outFile);

    if (Files.size(outFile) == 0) {
      Files.deleteIfExists(outFile);
      throw new IllegalStateException("Thumbnail file is empty");
    }
    return outFile;
  }

  private String buildWadoUrl(
      String baseUrl,
      String studyUID,
      String seriesUID,
      String sopInstanceUID,
      String additionalParameters) {
    String addParams = filterAdditionalParameters(additionalParameters);
    return "%s?requestType=WADO&studyUID=%s&seriesUID=%s&objectUID=%s&contentType=%s&imageQuality=%d&rows=%d&columns=%d%s"
        .formatted(
            baseUrl,
            studyUID,
            seriesUID,
            sopInstanceUID,
            IMAGE_JPEG_MIME,
            THUMBNAIL_QUALITY,
            Thumbnail.MAX_SIZE,
            Thumbnail.MAX_SIZE,
            addParams);
  }

  private String filterAdditionalParameters(String params) {
    if (!StringUtil.hasText(params)) {
      return "";
    }
    return Arrays.stream(params.split("&"))
        .filter(p -> !p.startsWith("transferSyntax") && !p.startsWith("anonymize"))
        .collect(Collectors.joining("&", "&", ""));
  }

  private void updateSeriesThumbnail(Path thumbnailPath) {
    GuiExecutor.execute(
        () -> {
          SeriesThumbnail thumbnail = (SeriesThumbnail) dicomSeries.getTagValue(TagW.Thumbnail);
          if (thumbnail != null) {
            thumbnail.reBuildThumbnail(thumbnailPath.toFile(), MEDIA_POSITION.MIDDLE);
          }
        });
  }

  /**
   * Creates and initializes a series thumbnail with proper listeners.
   *
   * @param loadSeries the associated LoadSeries instance
   * @param progressBar the progress bar to associate with the thumbnail
   */
  public void createSeriesThumbnail(LoadSeries loadSeries, JProgressBar progressBar) {
    GuiExecutor.execute(
        () -> {
          SeriesThumbnail thumbnail = getOrCreateThumbnail();
          thumbnail.setProgressBar(loadSeries.isDone() ? null : progressBar);
          thumbnail.registerListeners();
          addListenerToThumbnail(thumbnail, loadSeries);
          dicomSeries.setTag(TagW.Thumbnail, thumbnail);
          dicomModel.firePropertyChange(
              new ObservableEvent(BasicAction.ADD, dicomModel, null, dicomSeries));
        });
  }

  private SeriesThumbnail getOrCreateThumbnail() {
    SeriesThumbnail thumbnail = (SeriesThumbnail) dicomSeries.getTagValue(TagW.Thumbnail);
    if (thumbnail == null) {
      int thumbnailSize = SeriesThumbnail.getThumbnailSizeFromPreferences();
      Function<String, Set<ResourceIconPath>> drawIcons = HiddenSeriesManager::getRelatedIcons;
      thumbnail = new SeriesThumbnail(dicomSeries, thumbnailSize, drawIcons);
    }
    return thumbnail;
  }

  /**
   * Removes all ThumbnailMouseAndKeyAdapter listeners from a thumbnail.
   *
   * @param thumbnail the thumbnail to clean up
   */
  public static void removeThumbnailMouseAndKeyAdapter(Thumbnail thumbnail) {
    removeListeners(thumbnail.getMouseListeners(), thumbnail::removeMouseListener);
    removeListeners(thumbnail.getMouseMotionListeners(), thumbnail::removeMouseMotionListener);
    removeListeners(thumbnail.getMouseWheelListeners(), thumbnail::removeMouseWheelListener);
    removeListeners(thumbnail.getKeyListeners(), thumbnail::removeKeyListener);
  }

  private static <T> void removeListeners(T[] listeners, java.util.function.Consumer<T> remover) {
    Arrays.stream(listeners).filter(ThumbnailMouseAndKeyAdapter.class::isInstance).forEach(remover);
  }

  void addListenerToThumbnail(Thumbnail thumbnail, LoadSeries loadSeries) {
    var thumbAdapter =
        new ThumbnailMouseAndKeyAdapter(loadSeries.getDicomSeries(), dicomModel, loadSeries);
    thumbnail.addMouseListener(thumbAdapter);
    thumbnail.addKeyListener(thumbAdapter);
    if (thumbnail instanceof SeriesThumbnail seriesThumbnail) {
      seriesThumbnail.setProgressBar(loadSeries.getProgressBar());
    }
  }
}
