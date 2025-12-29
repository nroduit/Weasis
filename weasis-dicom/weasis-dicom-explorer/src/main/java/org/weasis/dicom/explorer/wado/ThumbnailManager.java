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
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.swing.JProgressBar;
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

  /**
   * Loads and displays a thumbnail for the given SOP instance.
   *
   * @param instance the SOP instance
   * @param wadoParameters the WADO parameters
   * @param authMethod authentication method for HTTP requests
   */
  public void loadThumbnail(
      SopInstance instance, WadoParameters wadoParameters, AuthMethod authMethod) {
    Path thumbnailPath =
        instance.getDirectDownloadFile() == null
            ? downloadThumbnailFromWado(instance, wadoParameters, authMethod)
            : downloadDirectThumbnail(wadoParameters, authMethod);

    if (thumbnailPath != null) {
      updateSeriesThumbnail(thumbnailPath);
    }
  }

  private Path downloadThumbnailFromWado(
      SopInstance instance, WadoParameters wadoParameters, AuthMethod authMethod) {
    if (!DicomMediaIO.SERIES_MIMETYPE.equals(dicomSeries.getMimeType())) {
      return null;
    }
    try {
      String studyUID = getStudyUID(wadoParameters);
      String seriesUID = getSeriesUID(wadoParameters);
      return fetchJpegThumbnail(
          wadoParameters, studyUID, seriesUID, instance.getSopInstanceUID(), authMethod);
    } catch (Exception e) {
      LOGGER.error("Error downloading thumbnail from WADO", e);
      return null;
    }
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

  private Path downloadFromUrl(
      String url, String extension, URLParameters params, AuthMethod authMethod) {
    try (HttpStream httpCon = HttpUtils.getHttpResponse(url, params, authMethod)) {
      int code = httpCon.getResponseCode();
      if (code >= HttpURLConnection.HTTP_OK && code < HttpURLConnection.HTTP_BAD_REQUEST) {
        return saveThumbnailFile(httpCon, extension);
      } else if (code == HttpURLConnection.HTTP_UNAUTHORIZED && authMethod != null) {
        authMethod.resetToken();
        authMethod.getToken();
      }
    } catch (Exception e) {
      LOGGER.error("Error downloading thumbnail from {}", url, e);
    }
    return null;
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

  private Path fetchJpegThumbnail(
      WadoParameters wadoParameters,
      String studyUID,
      String seriesUID,
      String sopInstanceUID,
      AuthMethod authMethod)
      throws Exception {
    String url = buildWadoUrl(wadoParameters, studyUID, seriesUID, sopInstanceUID);
    Path outFile = Files.createTempFile(Thumbnail.THUMBNAIL_CACHE_DIR, "thumb_", JPEG_EXTENSION);

    LOGGER.debug("Downloading JPEG thumbnail from {} to {}", url, outFile.getFileName());

    try (HttpStream httpCon = HttpUtils.getHttpResponse(url, urlParams, authMethod)) {
      int code = httpCon.getResponseCode();
      if (code >= HttpURLConnection.HTTP_OK && code < HttpURLConnection.HTTP_BAD_REQUEST) {
        FileUtil.writeStreamWithIOException(httpCon.getInputStream(), outFile);
      } else if (code == HttpURLConnection.HTTP_UNAUTHORIZED && authMethod != null) {
        authMethod.resetToken();
        authMethod.getToken();
      }
      if (Files.size(outFile) == 0) {
        throw new IllegalStateException("Thumbnail file is empty");
      }
      return outFile;
    } catch (Exception e) {
      Files.deleteIfExists(outFile);
      throw e;
    }
  }

  private String buildWadoUrl(
      WadoParameters wadoParameters, String studyUID, String seriesUID, String sopInstanceUID) {
    String addParams = filterAdditionalParameters(wadoParameters.getAdditionalParameters());
    return "%s?requestType=WADO&studyUID=%s&seriesUID=%s&objectUID=%s&contentType=%s&imageQuality=%d&rows=%d&columns=%d%s"
        .formatted(
            wadoParameters.getBaseURL(),
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
