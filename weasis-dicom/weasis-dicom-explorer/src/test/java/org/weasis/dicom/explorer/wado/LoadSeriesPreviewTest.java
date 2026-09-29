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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.dcm4che3.data.Tag;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.SeriesThumbnail;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.utils.SeriesInstanceList;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.mf.SopInstance;
import org.weasis.dicom.mf.WadoParameters;

/**
 * The preview of a series is fetched from the archive off the thread that queues the series, so an
 * archive slow to render thumbnails never delays the downloads.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class LoadSeriesPreviewTest {

  private static final long SERVER_DELAY_MS = 1500;
  private static final int SERIES_COUNT = 4;
  private static final byte[] JPEG = {
    (byte) 0xFF,
    (byte) 0xD8,
    (byte) 0xFF,
    (byte) 0xE0,
    0,
    0x10,
    'J',
    'F',
    'I',
    'F',
    0,
    1,
    (byte) 0xFF,
    (byte) 0xD9
  };

  private static final CountDownLatch ANSWERED = new CountDownLatch(SERIES_COUNT);
  private static final AtomicInteger IN_FLIGHT = new AtomicInteger();
  private static final AtomicInteger MAX_IN_FLIGHT = new AtomicInteger();

  private static HttpServer server;
  private static String baseUrl;

  @BeforeAll
  static void startSlowArchive() throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    // The default executor serves one request at a time, which would hide any parallelism.
    server.setExecutor(Executors.newCachedThreadPool());
    server.createContext(
        "/",
        ex -> {
          MAX_IN_FLIGHT.accumulateAndGet(IN_FLIGHT.incrementAndGet(), Math::max);
          try {
            Thread.sleep(SERVER_DELAY_MS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          ex.getResponseHeaders().add("Content-Type", "image/jpeg");
          ex.sendResponseHeaders(200, JPEG.length);
          try (var os = ex.getResponseBody()) {
            os.write(JPEG);
          }
          IN_FLIGHT.decrementAndGet();
          ANSWERED.countDown();
        });
    server.start();
    baseUrl = "http://localhost:" + server.getAddress().getPort() + "/dicomweb";
    ThumbnailServiceRegistry.configure(baseUrl, "RS_THUMBNAIL");
  }

  @AfterAll
  static void stopArchive() {
    server.stop(0);
  }

  @Test
  void queuing_series_never_waits_for_their_previews() throws Exception {
    WadoParameters wado = new WadoParameters("arc", baseUrl, false, null, null, null, true);

    // Built before timing: on a cold JVM, creating the mocks alone can exceed the server delay.
    List<LoadSeries> seriesList =
        IntStream.range(0, SERIES_COUNT).mapToObj(i -> loadSeries("SE" + i, wado)).toList();

    long start = System.nanoTime();
    seriesList.forEach(s -> s.startDownloadImageReference(wado));
    long queuingMs = elapsedMs(start);
    assertTrue(
        queuingMs < SERVER_DELAY_MS,
        "queuing %d series took %d ms while the archive needs %d ms per preview"
            .formatted(SERIES_COUNT, queuingMs, SERVER_DELAY_MS));

    // Fetched one after the other, the previews would need SERIES_COUNT times the server delay.
    assertTrue(
        ANSWERED.await(2 * SERVER_DELAY_MS + 1000, TimeUnit.MILLISECONDS),
        "the previews must be fetched in the background and in parallel");
    assertTrue(MAX_IN_FLIGHT.get() > 1, "the archive must see concurrent preview requests");
  }

  private static long elapsedMs(long startNanos) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
  }

  private static LoadSeries loadSeries(String seriesUID, WadoParameters wado) {
    DicomSeries series = mock(DicomSeries.class);
    when(series.getMimeType()).thenReturn(DicomMediaIO.SERIES_MIMETYPE);
    when(series.getTagValue(TagD.get(Tag.SeriesInstanceUID))).thenReturn(seriesUID);
    when(series.getTagValue(TagW.WadoParameters)).thenReturn(wado);
    when(series.getTagValue(TagW.Thumbnail)).thenReturn(mock(SeriesThumbnail.class));
    SeriesInstanceList instances = new SeriesInstanceList();
    instances.addSopInstance(new SopInstance("IM1", null));
    when(series.getTagValue(TagW.WadoInstanceReferenceList)).thenReturn(instances);

    MediaSeriesGroup study = mock(MediaSeriesGroup.class);
    when(study.getTagValue(TagD.get(Tag.StudyInstanceUID))).thenReturn("ST1");
    DicomModel model = mock(DicomModel.class);
    when(model.getParent(series, DicomModel.study)).thenReturn(study);

    return new LoadSeries(series, model, 4, false);
  }
}
