/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.weasis.core.ui.model.utils.ImageStatistics.IMAGE_ENTROPY;
import static org.weasis.core.ui.model.utils.ImageStatistics.IMAGE_MAX;
import static org.weasis.core.ui.model.utils.ImageStatistics.IMAGE_MIN;
import static org.weasis.core.ui.model.utils.ImageStatistics.IMAGE_PIXELS;
import static org.weasis.core.ui.model.utils.ImageStatistics.IMAGE_STD;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.ui.editor.image.HistogramData.Model;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.bean.Measurement;

/**
 * Native-backed tests for {@link HistogramData#computeHistogram}. They guard the OpenCV 5
 * regression where {@code Imgproc.calcHist} returns a 1-row vector (OpenCV 4 returned a column of
 * {@code nbBins} rows): every Weasis reader sizes its buffer with {@code hist.rows()}, so a row
 * vector exposes only the first bin — which silently emptied ROI statistics and the histogram
 * chart.
 *
 * <p>These require the OpenCV native library; when it cannot be located (unsupported arch, module
 * not yet built) the whole class is skipped rather than failed.
 */
class HistogramDataTest {

  private static boolean nativeLoaded;

  @BeforeAll
  static void loadOpenCV() {
    nativeLoaded = tryLoadOpenCV();
    assumeTrue(nativeLoaded, "OpenCV native library unavailable — skipping native histogram tests");
  }

  /**
   * Loads {@code opencv_java} from a sibling {@code weasis-opencv-core-*} module's build output.
   */
  private static boolean tryLoadOpenCV() {
    String os = System.getProperty("os.name", "").toLowerCase();
    String libFile =
        os.contains("win")
            ? "opencv_java.dll"
            : os.contains("mac") ? "libopencv_java.dylib" : "libopencv_java.so";
    Path opencvModules =
        Paths.get(System.getProperty("user.dir")).getParent().resolve("weasis-opencv");
    if (!Files.isDirectory(opencvModules)) {
      return false;
    }
    try (Stream<Path> dirs = Files.list(opencvModules)) {
      List<Path> candidates =
          dirs.filter(Files::isDirectory)
              .map(d -> d.resolve("target").resolve("classes").resolve(libFile))
              .filter(Files::isRegularFile)
              .toList();
      for (Path lib : candidates) {
        try {
          System.load(lib.toAbsolutePath().toString());
          return true; // the arch-matching library loads; the rest throw and are skipped
        } catch (Throwable ignore) {
          // wrong architecture or incompatible binary — try the next candidate
        }
      }
    } catch (IOException e) {
      return false;
    }
    return false;
  }

  private static Mat grayImage(int side, java.util.function.IntUnaryOperator valueForIndex) {
    Mat src = new Mat(side, side, CvType.CV_16SC1);
    short[] data = new short[side * side];
    for (int i = 0; i < data.length; i++) {
      data[i] = (short) valueForIndex.applyAsInt(i);
    }
    src.put(0, 0, data);
    return src;
  }

  private static float[] readBins(Mat hist) {
    float[] bins = new float[(int) hist.total()];
    hist.get(0, 0, bins);
    return bins;
  }

  @Test
  void computeHistogram_returnsColumnOfAllBinsWithEveryPixelCounted() {
    int side = 100; // 10000 pixels, values cycling 0..255
    Mat src = grayImage(side, i -> i % 256);
    int nbBins = 256;

    List<Mat> hists =
        HistogramData.computeHistogram(src, null, nbBins, new int[] {0}, Model.GRAY, 0, 255);

    assertEquals(1, hists.size());
    Mat hist = hists.get(0);
    // The crux: a column of nbBins rows, NOT a 1-row vector (the OpenCV 5 shape).
    assertEquals(nbBins, hist.rows(), "calcHist result must expose nbBins via rows()");
    assertEquals(1, hist.cols());

    float[] bins = readBins(hist);
    double sum = 0;
    for (float b : bins) {
      sum += b;
    }
    assertEquals(side * side, sum, "every pixel must be histogrammed, not only bin 0");
    // 10000 = 39*256 + 16, so values 0..15 occur 40 times and 16..255 occur 39 times.
    assertEquals(40.0f, bins[0]);
    assertEquals(40.0f, bins[15]);
    assertEquals(39.0f, bins[16]);
    assertEquals(39.0f, bins[255]);
  }

  @Test
  void computeHistogram_honoursMask() {
    int side = 100;
    Mat src = grayImage(side, i -> i % 256);
    Mat mask = Mat.zeros(side, side, CvType.CV_8UC1);
    mask.submat(new Rect(0, 0, 10, 10)).setTo(new Scalar(255)); // 100 selected pixels

    List<Mat> hists =
        HistogramData.computeHistogram(src, mask, 256, new int[] {0}, Model.GRAY, 0, 255);

    Mat hist = hists.get(0);
    assertEquals(256, hist.rows());
    double sum = 0;
    for (float b : readBins(hist)) {
      sum += b;
    }
    assertEquals(100.0, sum, "only masked pixels must be counted");
  }

  @Test
  void computeHistogram_multiChannelReturnsOneColumnPerBand() {
    int side = 40;
    Mat src = new Mat(side, side, CvType.CV_8UC3, new Scalar(10, 20, 30));
    int nbBins = 256;

    List<Mat> hists =
        HistogramData.computeHistogram(src, null, nbBins, new int[] {0, 1, 2}, Model.RGB, 0, 255);

    assertEquals(3, hists.size());
    for (Mat hist : hists) {
      assertEquals(nbBins, hist.rows(), "each band histogram must be a column of nbBins rows");
      assertEquals(1, hist.cols());
    }
  }

  @Test
  void computeHistogram_fullPipelineReproducesReportedCtRoi() {
    // CT-like ROI: half air (stored 0 -> -1024 HU), half tissue spread over 200..1999.
    int side = 200; // 40000 pixels
    Mat src = grayImage(side, i -> (i % 2 == 0) ? 0 : 200 + (i % 1800));
    int nbBins = 2000; // covers stored values 0..1999

    List<Mat> hists =
        HistogramData.computeHistogram(src, null, nbBins, new int[] {0}, Model.GRAY, 0, 1999);
    Mat hist = hists.get(0);
    assertEquals(nbBins, hist.rows());

    // Mimic the real Weasis read (sizes the buffer by rows()) and feed the statistics pipeline.
    float[] bins = new float[hist.rows()];
    hist.get(0, 0, bins);

    MeasurableLayer layer = mock(MeasurableLayer.class);
    when(layer.hasContent()).thenReturn(true);
    when(layer.getPixelValueUnit()).thenReturn("HU"); // NON-NLS
    when(layer.getSourceTagValue(any())).thenReturn(null);
    when(layer.pixelToRealValue(any()))
        .thenAnswer(inv -> ((Number) inv.getArgument(0)).doubleValue() - 1024.0); // CT intercept
    HistogramData data = new HistogramData(bins, null, 0, Model.GRAY, null, 0, 1999, layer);

    Map<Measurement, Double> s = new HashMap<>();
    for (MeasureItem item : ImageRegionStatistics.getStatistics(data, null, true)) {
      s.put(item.getMeasurement(), ((Number) item.getValue()).doubleValue());
    }

    assertEquals(
        40000.0, s.get(IMAGE_PIXELS), 0.5); // all pixels counted (guards the row-vector bug)
    assertEquals(-1024.0, s.get(IMAGE_MIN), 1.0e-6); // air floor
    assertEquals(975.0, s.get(IMAGE_MAX), 1.0e-6); // 1999 - 1024
    assertTrue(s.get(IMAGE_STD) > 0.0, "a spread ROI must have non-zero standard deviation");
    assertTrue(s.get(IMAGE_ENTROPY) > 0.0, "a spread ROI must carry non-zero entropy");
  }
}
