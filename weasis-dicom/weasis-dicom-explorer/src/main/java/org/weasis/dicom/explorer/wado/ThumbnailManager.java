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
import java.util.List;
import java.util.Set;
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
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.media.data.MediaSeries.MEDIA_POSITION;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.SeriesThumbnail;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Thumbnail;
import org.weasis.core.api.net.HttpStatusException;
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
  private static final int THUMBNAIL_QUALITY = 75;

  /** Status of a request the archive cannot serve at all, e.g. no URL for the service. */
  private static final int NOT_APPLICABLE = 0;

  private static final FetchResult UNAVAILABLE = new FetchResult(null, NOT_APPLICABLE);

  /**
   * Failure telling nothing about the archive: an I/O error, or a series this service cannot
   * render. The mode in use stays the one of the archive.
   */
  private static final FetchResult INCONCLUSIVE = new FetchResult(null, -1);

  /**
   * Downloaded thumbnail file (or {@code null}) together with the HTTP status ({@code -1} on I/O).
   */
  private record FetchResult(Path file, int code) {}

  /** Identifies the series (and optionally the instance) a thumbnail is requested for. */
  private record ThumbnailRequest(
      WadoParameters wadoParameters,
      String baseUrl,
      String studyUID,
      String seriesUID,
      String sopInstanceUID,
      AuthMethod authMethod) {}

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
    String directThumbnail = (String) dicomSeries.getTagValue(TagW.DirectDownloadThumbnail);
    if (StringUtil.hasLength(directThumbnail)) {
      // The manifest (or a DICOMDIR icon) points at the thumbnail: no service to query.
      thumbnailPath = downloadDirectThumbnail(directThumbnail, wadoParameters, authMethod);
    } else if (wadoParameters.isWadoRS() || instance.getDirectDownloadFile() == null) {
      thumbnailPath = fetchThumbnail(wadoParameters, instance.getSopInstanceUID(), authMethod);
    } else {
      // Local files (DICOMDIR, direct URL) without an icon: the thumbnail is decoded from the
      // image.
      thumbnailPath = null;
    }

    if (thumbnailPath != null) {
      updateSeriesThumbnail(thumbnailPath);
    }
  }

  /**
   * Loads a preview thumbnail for a series-level bulk retrieve, where no instance is known yet. The
   * services needing one resolve a representative instance through QIDO-RS.
   */
  public void loadSeriesThumbnail(WadoParameters wadoParameters, AuthMethod authMethod) {
    if (!DicomMediaIO.SERIES_MIMETYPE.equals(dicomSeries.getMimeType())) {
      return;
    }
    String directThumbnail = (String) dicomSeries.getTagValue(TagW.DirectDownloadThumbnail);
    // A null instance UID selects the series-level thumbnail service.
    Path thumbnailPath =
        StringUtil.hasLength(directThumbnail)
            ? downloadDirectThumbnail(directThumbnail, wadoParameters, authMethod)
            : fetchThumbnail(wadoParameters, null, authMethod);
    if (thumbnailPath != null) {
      updateSeriesThumbnail(thumbnailPath);
    }
  }

  /**
   * Runs the thumbnail services of the archive in the order given by its {@link ThumbnailMode},
   * caching in {@link ThumbnailServiceRegistry} what it does not implement, so that every later
   * series of the session starts with a service the archive answers.
   */
  private Path fetchThumbnail(
      WadoParameters wadoParameters, String sopInstanceUID, AuthMethod authMethod) {
    MediaSeriesGroup study = dicomModel.getParent(dicomSeries, DicomModel.study);
    String studyUID = TagD.getTagValue(study, Tag.StudyInstanceUID, String.class);
    String seriesUID = TagD.getTagValue(dicomSeries, Tag.SeriesInstanceUID, String.class);
    String baseUrl =
        wadoParameters.isWadoRS()
            ? LoadSeries.dicomWebBaseUrl(wadoParameters, dicomSeries)
            : wadoParameters.getBaseURL();
    if (!StringUtil.hasText(baseUrl)) {
      return null;
    }
    ThumbnailRequest request =
        new ThumbnailRequest(
            wadoParameters, baseUrl, studyUID, seriesUID, sopInstanceUID, authMethod);

    List<ThumbnailMode> probeOrder = probedModes(request);
    ThumbnailMode mode = ThumbnailServiceRegistry.currentMode(baseUrl, probeOrder);
    String instanceUID = sopInstanceUID;
    while (mode != ThumbnailMode.NONE) {
      FetchResult result = UNAVAILABLE;
      if (isAddressable(mode, request)) {
        if (requiresInstance(mode) && !StringUtil.hasText(instanceUID)) {
          instanceUID = fetchRepresentativeInstanceUID(request);
          if (!StringUtil.hasText(instanceUID)) {
            return null; // Nothing to render: keep the mode and retry with the next series.
          }
        }
        result = fetch(mode, request, instanceUID);
        if (result.file() != null) {
          return result.file();
        }
        if (!isServiceUnsupported(result.code())) {
          return null; // Transient failure: the service stays the one to use.
        }
      }
      ThumbnailMode next = ThumbnailServiceRegistry.downgrade(baseUrl, mode, probeOrder);
      LOGGER.info(
          "Thumbnail service {} unavailable on {} ({}); switching to {}",
          mode,
          baseUrl,
          statusText(result.code()),
          next);
      mode = next;
    }
    return null;
  }

  /**
   * Services {@link ThumbnailMode#AUTO} probes, in order. A DICOMweb archive is never probed with
   * {@link ThumbnailMode#WADO_URI}: PS3.18 leaves the base URI of the URI service to the
   * implementation, and archives publish it beside the DICOMweb one ({@code .../wado} next to
   * {@code .../rs}) rather than on it, so that service is only requested when configured.
   */
  private static List<ThumbnailMode> probedModes(ThumbnailRequest request) {
    return request.wadoParameters().isWadoRS()
        ? List.of(ThumbnailMode.RS_THUMBNAIL, ThumbnailMode.RENDERED)
        : List.of(ThumbnailMode.WADO_URI);
  }

  /** The DICOMweb services need a DICOMweb base URL; WADO-URI is addressable on any archive. */
  private static boolean isAddressable(ThumbnailMode mode, ThumbnailRequest request) {
    return mode == ThumbnailMode.WADO_URI || request.wadoParameters().isWadoRS();
  }

  private static boolean hasSeriesPath(ThumbnailRequest request) {
    return StringUtil.hasText(request.studyUID()) && StringUtil.hasText(request.seriesUID());
  }

  private static boolean requiresInstance(ThumbnailMode mode) {
    return mode == ThumbnailMode.RENDERED || mode == ThumbnailMode.WADO_URI;
  }

  private FetchResult fetch(ThumbnailMode mode, ThumbnailRequest request, String instanceUID) {
    return switch (mode) {
      case RS_THUMBNAIL -> fetchRsThumbnail(request);
      case RENDERED -> fetchRenderedThumbnail(request, instanceUID);
      case WADO_URI -> fetchWadoUriThumbnail(request, instanceUID);
      case AUTO, NONE -> UNAVAILABLE;
    };
  }

  /** DICOMweb thumbnail service, at the series level when no instance is known. */
  private FetchResult fetchRsThumbnail(ThumbnailRequest request) {
    if (!hasSeriesPath(request)) {
      return INCONCLUSIVE;
    }
    String url =
        request.sopInstanceUID() == null
            ? "%s/studies/%s/series/%s/thumbnail?viewport=%d%%2C%d"
                .formatted(
                    request.baseUrl(),
                    request.studyUID(),
                    request.seriesUID(),
                    Thumbnail.MAX_SIZE,
                    Thumbnail.MAX_SIZE)
            : "%s/studies/%s/series/%s/instances/%s/thumbnail?viewport=%d%%2C%d"
                .formatted(
                    request.baseUrl(),
                    request.studyUID(),
                    request.seriesUID(),
                    request.sopInstanceUID(),
                    Thumbnail.MAX_SIZE,
                    Thumbnail.MAX_SIZE);
    return download(url, JPEG_EXTENSION, createWadoRsParams(), request.authMethod());
  }

  /**
   * DICOMweb rendered service. Only the instance-level resource is portable (series-level rendering
   * is rejected by some archives), so a bulk series first resolves a representative instance.
   */
  private FetchResult fetchRenderedThumbnail(ThumbnailRequest request, String instanceUID) {
    if (!hasSeriesPath(request)) {
      return INCONCLUSIVE;
    }
    String url =
        "%s/studies/%s/series/%s/instances/%s/rendered?viewport=%d%%2C%d"
            .formatted(
                request.baseUrl(),
                request.studyUID(),
                request.seriesUID(),
                instanceUID,
                Thumbnail.MAX_SIZE,
                Thumbnail.MAX_SIZE);
    return download(url, JPEG_EXTENSION, createWadoRsParams(), request.authMethod());
  }

  /**
   * WADO-URI rendering on the archive base URL, which PS3.18 serves from the same DICOMweb service
   * as WADO-RS.
   */
  private FetchResult fetchWadoUriThumbnail(ThumbnailRequest request, String instanceUID) {
    WadoParameters wadoParameters = request.wadoParameters();
    // The WADO-URI rendering only applies to an image series, unlike the DICOMweb services.
    if (!DicomMediaIO.SERIES_MIMETYPE.equals(dicomSeries.getMimeType())) {
      return INCONCLUSIVE;
    }
    boolean onlySopUID = wadoParameters.isRequireOnlySOPInstanceUID();
    String studyUID = onlySopUID ? "" : request.studyUID();
    String seriesUID = onlySopUID ? "" : request.seriesUID();
    if (studyUID == null || seriesUID == null) {
      return INCONCLUSIVE;
    }
    String url =
        buildWadoUrl(
            request.baseUrl(),
            studyUID,
            seriesUID,
            instanceUID,
            wadoParameters.getAdditionalParameters());
    return download(url, JPEG_EXTENSION, previewParameters(urlParams), request.authMethod());
  }

  /** HTTP statuses telling that the archive does not implement the service. */
  private static boolean isServiceUnsupported(int code) {
    return code == NOT_APPLICABLE
        || code == HttpURLConnection.HTTP_NOT_FOUND
        || code == HttpURLConnection.HTTP_BAD_METHOD
        || code == HttpURLConnection.HTTP_NOT_ACCEPTABLE
        || code == HttpURLConnection.HTTP_GONE
        || code == HttpURLConnection.HTTP_NOT_IMPLEMENTED;
  }

  private static String statusText(int code) {
    return code == NOT_APPLICABLE ? "not applicable" : "HTTP " + code; // NON-NLS
  }

  private String fetchRepresentativeInstanceUID(ThumbnailRequest request) {
    if (!StringUtil.hasText(request.studyUID()) || !StringUtil.hasText(request.seriesUID())) {
      return null;
    }
    // Target a middle instance (representative slice) without listing the series: get the instance
    // count, then fetch a single UID at that offset (order is server-defined).
    String baseUrl = request.baseUrl();
    URLParameters queryParams = RsQueryResult.jsonQueryParameters(previewParameters(urlParams));
    int offset =
        RsQueryResult.seriesInstanceCount(
                baseUrl, request.studyUID(), request.seriesUID(), queryParams, request.authMethod())
            / 2;
    String url =
        "%s/studies/%s/series/%s/instances?includefield=00080018&limit=1&offset=%d" // NON-NLS
            .formatted(baseUrl, request.studyUID(), request.seriesUID(), offset);
    try {
      for (Attributes instance : RsQueryResult.parseJSON(url, request.authMethod(), queryParams)) {
        String sopUID = instance.getString(Tag.SOPInstanceUID);
        if (StringUtil.hasText(sopUID)) {
          return sopUID;
        }
      }
    } catch (Exception e) {
      LOGGER.debug("Cannot fetch a representative instance for series {}", request.seriesUID(), e);
    }
    return null;
  }

  private Path downloadDirectThumbnail(
      String thumbURL, WadoParameters wadoParameters, AuthMethod authMethod) {
    if (thumbURL.startsWith(Thumbnail.THUMBNAIL_CACHE_DIR.toString())) {
      return Path.of(thumbURL);
    }
    String url = thumbURL.contains("://") ? thumbURL : wadoParameters.getBaseURL() + thumbURL;
    URLParameters params =
        wadoParameters.isWadoRS() ? createWadoRsParams() : previewParameters(urlParams);
    String extension = wadoParameters.isWadoRS() ? JPEG_EXTENSION : FileUtil.getExtension(url);
    return download(url, extension, params, authMethod).file();
  }

  private URLParameters createWadoRsParams() {
    var headers = new HashMap<>(urlParams.headers());
    headers.put("Accept", IMAGE_JPEG_MIME);
    return previewParameters(urlParams.toBuilder().headers(headers).build());
  }

  /**
   * A preview is worth waiting for only as long as a healthy transfer may stall: an archive that
   * needs minutes to answer must not hold back the series the preview illustrates, so the generous
   * response budget of a bulk retrieve does not apply here.
   */
  private static URLParameters previewParameters(URLParameters parameters) {
    return parameters.toBuilder()
        .responseTimeoutMillis(parameters.inactivityTimeoutMillis())
        .build();
  }

  private FetchResult download(
      String url, String extension, URLParameters params, AuthMethod authMethod) {
    try (HttpStream httpCon = HttpUtils.getHttpResponse(url, params, authMethod)) {
      int code = httpCon.getResponseCode();
      if (!isSuccess(code)) {
        // The authenticated client reports the status instead of throwing it.
        return failedStatus(url, code, authMethod);
      }
      Path outFile = saveThumbnailFile(httpCon, extension);
      if (outFile == null) {
        // A service answering 200 with an error page, a JSON payload or a DICOM object does not
        // implement the thumbnail rendering: treated as unsupported rather than as an image.
        LOGGER.debug(
            "The response of {} is not an image (Content-Type: {})",
            url,
            httpCon.getHeaderField("Content-Type"));
        return UNAVAILABLE;
      }
      return new FetchResult(outFile, code);
    } catch (HttpStatusException e) {
      return failedStatus(url, e.getStatusCode(), authMethod);
    } catch (Exception e) {
      LOGGER.error("Error downloading thumbnail from {}", url, e);
      return INCONCLUSIVE;
    }
  }

  private static boolean isSuccess(int code) {
    return code >= HttpURLConnection.HTTP_OK && code < HttpURLConnection.HTTP_MULT_CHOICE;
  }

  private static FetchResult failedStatus(String url, int code, AuthMethod authMethod) {
    LOGGER.debug("No thumbnail at {} (HTTP {})", url, code);
    if (code == HttpURLConnection.HTTP_UNAUTHORIZED && authMethod != null) {
      authMethod.resetToken();
      authMethod.getToken();
    }
    return new FetchResult(null, code);
  }

  /** Saves the response, or {@code null} when its content is not an image. */
  private static Path saveThumbnailFile(HttpStream httpCon, String extension) throws IOException {
    Path outFile = Files.createTempFile(Thumbnail.THUMBNAIL_CACHE_DIR, "thumb_", extension);
    FileUtil.writeStreamWithIOException(httpCon.getInputStream(), outFile);

    if (isImage(outFile)) {
      return outFile;
    }
    Files.deleteIfExists(outFile);
    return null;
  }

  /**
   * An archive answering 200 with an HTML error page, a JSON payload or a DICOM object would
   * otherwise be cached as a thumbnail that no decoder can read.
   */
  static boolean isImage(Path file) {
    String mimeType = MimeInspector.getMimeTypeFromMagicNumber(file);
    return mimeType != null && mimeType.startsWith("image/"); // NON-NLS
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
          if (thumbnail == null || isBuiltFromDownloadedImages()) {
            return;
          }
          thumbnail.reBuildThumbnail(thumbnailPath.toFile(), MEDIA_POSITION.MIDDLE);
        });
  }

  /**
   * The preview is fetched off the loading thread, so it may arrive once the series is downloaded
   * and its thumbnail already decoded from the images, which is the better one.
   */
  private boolean isBuiltFromDownloadedImages() {
    return dicomSeries.getSeriesLoader() instanceof LoadSeries loader
        && loader.isDone()
        && dicomSeries.size(null) > 0;
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
