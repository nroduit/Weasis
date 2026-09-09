/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.wado;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.dcm4che3.data.Tag;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.net.HttpStatusException;
import org.weasis.core.api.net.HttpUtils;
import org.weasis.core.api.net.URLParameters;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.mf.SopInstance;
import org.weasis.dicom.mf.WadoParameters;

/**
 * Thumbnail retrieval over HTTP: which service is requested for a series and what the archive's
 * answer teaches for the next one. Each test uses its own base URL, as the learned modes are shared
 * by the session and the tests run in parallel.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class ThumbnailManagerTest {

  /** What AUTO probes on a DICOMweb archive. */
  private static final List<ThumbnailMode> PROBE_ORDER =
      List.of(ThumbnailMode.RS_THUMBNAIL, ThumbnailMode.RENDERED);

  @Test
  void a_DICOMDIR_series_without_icon_image_sequence_attempts_no_download() {
    DicomSeries dicomSeries = mock(DicomSeries.class);
    // readDicomDirIcon() returns null when the DICOMDIR holds no Icon Image Sequence.
    when(dicomSeries.getTagValue(TagW.DirectDownloadThumbnail)).thenReturn(null);

    SopInstance instance = new SopInstance("1.2.840.10008.1.2.3.4", null);
    instance.setDirectDownloadFile("file:///media/DICOM/IMG00001");

    ThumbnailManager thumbnailManager =
        new ThumbnailManager(dicomSeries, mock(DicomModel.class), new URLParameters(Map.of()));

    try (MockedStatic<HttpUtils> httpUtils = mockStatic(HttpUtils.class)) {
      thumbnailManager.loadThumbnail(instance, new WadoParameters("", true), null);

      // any() rather than anyString(): the bug was a request issued with a null URL.
      httpUtils.verify(() -> HttpUtils.getHttpResponse(any(), any(), any()), never());
    }
  }

  @Test
  void an_archive_without_thumbnail_service_switches_to_the_rendered_service() {
    String baseUrl = "http://arc-not-implemented/dicomweb";
    ThumbnailManager thumbnailManager = thumbnailManager(baseUrl);

    try (MockedStatic<HttpUtils> httpUtils = mockStatic(HttpUtils.class)) {
      failWith(httpUtils, 501);
      thumbnailManager.loadSeriesThumbnail(wadoRs(baseUrl), null);

      httpUtils.verify(() -> HttpUtils.getHttpResponse(contains("/thumbnail"), any(), any()));
      assertEquals(
          ThumbnailMode.RENDERED, ThumbnailServiceRegistry.currentMode(baseUrl, PROBE_ORDER));
    }
  }

  @Test
  void a_configured_service_answering_404_stops_the_thumbnail_requests() {
    String baseUrl = "http://arc-no-rendering/dicomweb";
    ThumbnailServiceRegistry.configure(baseUrl, "RENDERED");
    ThumbnailManager thumbnailManager = thumbnailManager(baseUrl);

    try (MockedStatic<HttpUtils> httpUtils = mockStatic(HttpUtils.class)) {
      failWith(httpUtils, 404);
      thumbnailManager.loadThumbnail(new SopInstance("IM1", null), wadoRs(baseUrl), null);

      httpUtils.verify(() -> HttpUtils.getHttpResponse(contains("/rendered"), any(), any()));
      assertEquals(ThumbnailMode.NONE, ThumbnailServiceRegistry.currentMode(baseUrl, PROBE_ORDER));
    }
  }

  @Test
  void an_archive_implementing_no_dicomweb_rendering_stops_after_the_two_services() {
    String baseUrl = "http://arc-no-rendering-at-all/dicomweb";
    ThumbnailManager thumbnailManager = thumbnailManager(baseUrl);

    try (MockedStatic<HttpUtils> httpUtils = mockStatic(HttpUtils.class)) {
      failWith(httpUtils, 404);
      thumbnailManager.loadThumbnail(new SopInstance("IM1", null), wadoRs(baseUrl), null);

      httpUtils.verify(() -> HttpUtils.getHttpResponse(contains("/thumbnail"), any(), any()));
      httpUtils.verify(() -> HttpUtils.getHttpResponse(contains("/rendered"), any(), any()));
      // WADO-URI has its own base URI, unrelated to the DICOMweb one: it is never probed.
      httpUtils.verify(
          () -> HttpUtils.getHttpResponse(contains("requestType=WADO"), any(), any()), never());
      assertEquals(ThumbnailMode.NONE, ThumbnailServiceRegistry.currentMode(baseUrl, PROBE_ORDER));
    }
  }

  @Test
  void a_dicomweb_archive_can_serve_its_thumbnails_through_the_wado_uri_service() {
    String baseUrl = "http://arc-wado-uri/dicomweb";
    // Only when configured: the archive then declares that its base URL serves the URI service.
    ThumbnailServiceRegistry.configure(baseUrl, "WADO_URI");
    ThumbnailManager thumbnailManager = thumbnailManager(baseUrl);

    try (MockedStatic<HttpUtils> httpUtils = mockStatic(HttpUtils.class)) {
      failWith(httpUtils, 404);
      thumbnailManager.loadThumbnail(new SopInstance("IM1", null), wadoRs(baseUrl), null);

      httpUtils.verify(
          () -> HttpUtils.getHttpResponse(contains(baseUrl + "?requestType=WADO"), any(), any()));
      assertEquals(ThumbnailMode.NONE, ThumbnailServiceRegistry.currentMode(baseUrl, PROBE_ORDER));
    }
  }

  @Test
  void an_archive_configured_without_thumbnail_service_is_never_requested() {
    String baseUrl = "http://arc-none/dicomweb";
    ThumbnailServiceRegistry.configure(baseUrl, "NONE");
    ThumbnailManager thumbnailManager = thumbnailManager(baseUrl);

    try (MockedStatic<HttpUtils> httpUtils = mockStatic(HttpUtils.class)) {
      thumbnailManager.loadSeriesThumbnail(wadoRs(baseUrl), null);
      thumbnailManager.loadThumbnail(new SopInstance("IM1", null), wadoRs(baseUrl), null);

      httpUtils.verify(() -> HttpUtils.getHttpResponse(any(), any(), any()), never());
    }
  }

  @Test
  void only_an_image_payload_is_kept_as_a_thumbnail(@TempDir Path dir) throws IOException {
    // An archive answering 200 with an error page, a JSON payload or the DICOM object itself.
    assertAll(
        () -> assertTrue(ThumbnailManager.isImage(file(dir, "jpeg", 0xFF, 0xD8, 0xFF, 0xE0))),
        () -> assertTrue(ThumbnailManager.isImage(file(dir, "png", 0x89, 'P', 'N', 'G'))),
        () -> assertFalse(ThumbnailManager.isImage(file(dir, "html", '<', 'h', 't', 'm'))),
        () -> assertFalse(ThumbnailManager.isImage(file(dir, "json", '{', '"', 'a', '"'))),
        () -> assertFalse(ThumbnailManager.isImage(file(dir, "dicom", 'D', 'I', 'C', 'M'))),
        () -> assertFalse(ThumbnailManager.isImage(file(dir, "empty"))));
  }

  private static Path file(Path dir, String name, int... content) throws IOException {
    byte[] bytes = new byte[content.length];
    for (int i = 0; i < content.length; i++) {
      bytes[i] = (byte) content[i];
    }
    return Files.write(dir.resolve(name), bytes);
  }

  private static void failWith(MockedStatic<HttpUtils> httpUtils, int statusCode) {
    httpUtils
        .when(() -> HttpUtils.getHttpResponse(anyString(), any(), any()))
        .thenThrow(new HttpStatusException(statusCode));
  }

  private static WadoParameters wadoRs(String baseUrl) {
    return new WadoParameters("arc", baseUrl, false, null, null, null, true);
  }

  private static ThumbnailManager thumbnailManager(String baseUrl) {
    DicomSeries dicomSeries = mock(DicomSeries.class);
    when(dicomSeries.getMimeType()).thenReturn(DicomMediaIO.SERIES_MIMETYPE);
    when(dicomSeries.getTagValue(TagD.get(Tag.SeriesInstanceUID))).thenReturn("SE1");

    MediaSeriesGroup study = mock(MediaSeriesGroup.class);
    when(study.getTagValue(TagD.get(Tag.StudyInstanceUID))).thenReturn("ST1");
    DicomModel dicomModel = mock(DicomModel.class);
    when(dicomModel.getParent(dicomSeries, DicomModel.study)).thenReturn(study);

    return new ThumbnailManager(dicomSeries, dicomModel, new URLParameters(Map.of()));
  }
}
