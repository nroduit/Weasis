/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.layer.imp;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sun.management.ThreadMXBean;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.imgproc.Imgproc;
import org.weasis.core.api.image.util.ValueHistogram;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.natives.NativeLibrary;
import org.weasis.opencv.op.ImageConversion;

/**
 * Baseline of what {@code RenderedImageLayer.drawImage} pays on a repaint: the conversion of the
 * display {@code Mat} and the Java2D blit, against a kept image. The target is an offscreen {@code
 * TYPE_INT_RGB} image, i.e. the software loops: an accelerated back buffer can behave differently.
 * Not part of the normal test run (the class name does not match the Surefire pattern).
 *
 * <p>Run with {@code mvn -pl weasis-core test -Dtest=PaintBenchmark -Dbench.run=true
 * -Dbench.opencv=<path of libopencv_java>}. Optional: {@code -Dbench.out=<markdown file>}.
 */
class PaintBenchmark {
  private static final ThreadMXBean THREADS = (ThreadMXBean) ManagementFactory.getThreadMXBean();
  private static final int WARMUP = Integer.getInteger("bench.warmup", 5);
  private static final int ITERATIONS = Integer.getInteger("bench.iterations", 20);

  private final List<String> rows = new ArrayList<>();

  @BeforeAll
  static void loadNativeLibrary() {
    String library = System.getProperty("bench.opencv");
    if (library == null) {
      NativeLibrary.loadLibraryFromLibraryName();
    } else {
      System.load(library);
    }
  }

  @Test
  void baseline() throws Exception {
    assumeTrue(Boolean.getBoolean("bench.run"), "bench.run is not set");

    rows.add("| Viewport | Measure | Median ms | Max ms | Heap MB/call |");
    rows.add("|---|---|---|---|---|");
    int[][] sizes = {{1600, 1200}, {3840, 2160}};
    int[] types = {CvType.CV_8UC1, CvType.CV_8UC3};
    for (int[] size : sizes) {
      for (int type : types) {
        benchViewport(size[0], size[1], type);
      }
    }

    benchProbeAndHistogram();

    String report =
        "Warm-up %d, iterations %d, Java %s, target TYPE_INT_RGB offscreen%n%n"
                .formatted(WARMUP, ITERATIONS, Runtime.version())
            + String.join(System.lineSeparator(), rows)
            + System.lineSeparator();
    System.out.println(report);
    String out = System.getProperty("bench.out");
    if (out != null) {
      Files.writeString(Path.of(out), report);
    }
  }

  // A profile line across a radiograph, and the histogram behind the color map editor
  private void benchProbeAndHistogram() {
    int size = 3000;
    Mat image = new Mat(size, size, CvType.CV_16UC1);
    Core.randu(image, 0, 4095);
    String label = "%d × %d CV_16UC1".formatted(size, size);

    add(
        label,
        "intensity profile, current: `Mat.get(row, col)` per sample, 4243 samples (diagonal)",
        () -> {
          double sum = 0;
          for (int i = 0; i < 4243; i++) {
            int p = (int) (i * (size - 1) / 4242.0);
            sum += image.get(p, p)[0];
          }
          if (sum < 0) {
            throw new IllegalStateException();
          }
        });
    add(
        label,
        "histogram, current: `ValueHistogram.of` (convert to float, copy out, Java bins), 256 bins",
        () -> ValueHistogram.of(ImageCV.fromMat(image), 0, 4096, 256));
    add(
        label,
        "histogram, candidate: `Imgproc.calcHist`, 256 bins",
        () -> {
          Mat hist = new Mat();
          Imgproc.calcHist(
              List.of(image),
              new MatOfInt(0),
              new Mat(),
              hist,
              new MatOfInt(256),
              new MatOfFloat(0, 4096));
          float[] counts = new float[256];
          hist.get(0, 0, counts);
          hist.release();
        });
    image.release();
  }

  private void benchViewport(int width, int height, int type) {
    String label = "%d × %d %s".formatted(width, height, CvType.typeToString(type));
    Mat view = new Mat(height, width, type);
    Core.randu(view, 0, 255);
    var target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g2d = target.createGraphics();

    add(label, "current repaint: `toBufferedImage` + `drawImage`", () -> draw(g2d, view));
    BufferedImage kept = ImageConversion.toBufferedImage(view);
    add(label, "kept image: `drawImage` alone", () -> g2d.drawImage(kept, 0, 0, null));

    int standard =
        type == CvType.CV_8UC1 ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_3BYTE_BGR;
    String name = type == CvType.CV_8UC1 ? "TYPE_BYTE_GRAY" : "TYPE_3BYTE_BGR";
    var reused = new BufferedImage(width, height, standard);
    byte[] pixels = ((DataBufferByte) reused.getRaster().getDataBuffer()).getData();
    add(
        label,
        "image change with a reused %s image: `Mat.get` into it + `drawImage`".formatted(name),
        () -> {
          view.get(0, 0, pixels);
          g2d.drawImage(reused, 0, 0, null);
        });
    add(
        label,
        "repaint with the kept %s image: `drawImage` alone".formatted(name),
        () -> g2d.drawImage(reused, 0, 0, null));

    g2d.dispose();
    view.release();
  }

  private static void draw(Graphics2D g2d, Mat view) {
    g2d.drawImage(ImageConversion.toBufferedImage(view), 0, 0, null);
  }

  private void add(String label, String measure, Runnable step) {
    for (int i = 0; i < WARMUP; i++) {
      step.run();
    }
    long thread = Thread.currentThread().threadId();
    double[] times = new double[ITERATIONS];
    long allocated = 0;
    for (int i = 0; i < ITERATIONS; i++) {
      long heap = THREADS.getThreadAllocatedBytes(thread);
      long start = System.nanoTime();
      step.run();
      times[i] = (System.nanoTime() - start) / 1e6;
      allocated += THREADS.getThreadAllocatedBytes(thread) - heap;
    }
    Arrays.sort(times);
    rows.add(
        String.format(
            Locale.ROOT,
            "| %s | %s | %.2f | %.2f | %.2f |",
            label,
            measure,
            times[ITERATIONS / 2],
            times[ITERATIONS - 1],
            allocated / (double) ITERATIONS / (1 << 20)));
  }
}
