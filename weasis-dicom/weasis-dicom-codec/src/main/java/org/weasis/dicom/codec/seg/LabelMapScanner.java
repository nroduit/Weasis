/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.seg;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.function.IntPredicate;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scans a DICOM SEG LABELMAP frame ({@code Mat}) once and returns the sorted, distinct, non-zero
 * pixel values that pass an optional acceptance test. Used by {@link LabelMapContourLoader} and
 * {@link SegmentationVolumeBuilder} to enumerate the segments present in a label-map slice.
 *
 * <p>The implementation uses a {@link BitSet} keyed by the raw label value so that the hot loop
 * over every voxel does no autoboxing, then materialises a sorted {@code int[]} once at the end.
 * Supports CV_8U/8S, CV_16U/16S, CV_32S and CV_32F single-channel masks (which covers all label-map
 * encodings the DICOM SEG IOD permits). Non-integer or multichannel inputs return an empty array.
 */
public final class LabelMapScanner {

  private static final Logger LOGGER = LoggerFactory.getLogger(LabelMapScanner.class);
  private static final int[] EMPTY = new int[0];

  /** Accepts every non-zero label. */
  public static final IntPredicate ACCEPT_ALL = _ -> true;

  private LabelMapScanner() {}

  /** Returns the distinct non-zero pixel values present in {@code src}, sorted ascending. */
  public static int[] sortedDistinctLabels(Mat src) {
    return sortedDistinctLabels(src, ACCEPT_ALL);
  }

  /** Returns the distinct non-zero pixel values matching {@code accept}, sorted ascending. */
  public static int[] sortedDistinctLabels(Mat src, IntPredicate accept) {
    if (src == null || src.channels() != 1) {
      return EMPTY;
    }
    int total = src.rows() * src.cols();
    if (total == 0) {
      return EMPTY;
    }
    int depth = src.depth();
    PixelReader reader = readerFor(src, total, depth);
    if (reader == null) {
      LOGGER.warn("Unsupported label-map depth: {} (frame skipped)", depth);
      return EMPTY;
    }
    BitSet seen = new BitSet();
    for (int i = 0; i < total; i++) {
      int v = reader.read(i);
      if (v > 0 && accept.test(v)) {
        seen.set(v);
      }
    }
    return seen.stream().toArray();
  }

  /**
   * Pixel count and inclusive bounding box of one label.
   *
   * @param label the pixel value
   */
  public record LabelRegion(int label, int pixelCount, Rectangle bounds) {}

  /**
   * A label-map frame read once: its pixels and the regions of the accepted labels.
   *
   * @param regions the accepted non-zero labels, sorted ascending
   */
  public record LabelFrame(int width, int height, PixelReader pixels, List<LabelRegion> regions) {

    /** Returns the label of the pixel at {@code (x, y)}. */
    public int labelAt(int x, int y) {
      return pixels.read(y * width + x);
    }
  }

  /**
   * Reads {@code src} in one pass and returns the bounding box and pixel count of every non-zero
   * label matching {@code accept}, or {@code null} for an unsupported or empty frame.
   */
  public static LabelFrame scanRegions(Mat src, IntPredicate accept) {
    if (src == null || src.channels() != 1) {
      return null;
    }
    int width = src.cols();
    int height = src.rows();
    int depth = src.depth();
    PixelReader reader = width * height == 0 ? null : readerFor(src, width * height, depth);
    if (reader == null) {
      LOGGER.warn("Unsupported label-map frame: depth {}, size {}x{}", depth, width, height);
      return null;
    }
    // Per-label accumulators indexed by label value: accepted state, count, minX, minY, maxX, maxY
    LabelStats stats = new LabelStats(accept);
    for (int y = 0; y < height; y++) {
      int row = y * width;
      for (int x = 0; x < width; x++) {
        int v = reader.read(row + x);
        if (v > 0) {
          stats.add(v, x, y);
        }
      }
    }
    return new LabelFrame(width, height, reader, stats.regions());
  }

  private static final class LabelStats {
    private static final byte UNKNOWN = 0;
    private static final byte ACCEPTED = 1;
    private static final byte REJECTED = 2;
    private static final int MAX_SEGMENT_NUMBER = 0xFFFF; // Segment Number is US

    private final IntPredicate accept;
    private byte[] state = new byte[256];
    private int[] count = new int[256];
    private int[] minX = new int[256];
    private int[] minY = new int[256];
    private int[] maxX = new int[256];
    private int[] maxY = new int[256];

    LabelStats(IntPredicate accept) {
      this.accept = accept;
    }

    void add(int label, int x, int y) {
      if (label > MAX_SEGMENT_NUMBER) {
        return;
      }
      if (label >= state.length) {
        grow(label + 1);
      }
      byte s = state[label];
      if (s == UNKNOWN) {
        s = accept.test(label) ? ACCEPTED : REJECTED;
        state[label] = s;
        minX[label] = x;
        minY[label] = y;
      }
      if (s == REJECTED) {
        return;
      }
      count[label]++;
      minX[label] = Math.min(minX[label], x);
      maxX[label] = Math.max(maxX[label], x);
      maxY[label] = y; // Rows are scanned downwards
    }

    private void grow(int size) {
      int n = Math.max(size, state.length * 2);
      state = Arrays.copyOf(state, n);
      count = Arrays.copyOf(count, n);
      minX = Arrays.copyOf(minX, n);
      minY = Arrays.copyOf(minY, n);
      maxX = Arrays.copyOf(maxX, n);
      maxY = Arrays.copyOf(maxY, n);
    }

    List<LabelRegion> regions() {
      List<LabelRegion> regions = new ArrayList<>();
      for (int label = 1; label < state.length; label++) {
        if (count[label] > 0) {
          Rectangle bounds =
              new Rectangle(
                  minX[label],
                  minY[label],
                  maxX[label] - minX[label] + 1,
                  maxY[label] - minY[label] + 1);
          regions.add(new LabelRegion(label, count[label], bounds));
        }
      }
      return regions;
    }
  }

  /** Reads one pixel as an unsigned int from a typed bulk buffer. */
  @FunctionalInterface
  public interface PixelReader {
    int read(int index);
  }

  private static PixelReader readerFor(Mat src, int total, int depth) {
    return switch (depth) {
      case CvType.CV_8U, CvType.CV_8S -> {
        byte[] buf = new byte[total];
        src.get(0, 0, buf);
        yield i -> buf[i] & 0xFF;
      }
      case CvType.CV_16U, CvType.CV_16S -> {
        short[] buf = new short[total];
        src.get(0, 0, buf);
        yield depth == CvType.CV_16U ? i -> buf[i] & 0xFFFF : i -> buf[i];
      }
      case CvType.CV_32S -> {
        int[] buf = new int[total];
        src.get(0, 0, buf);
        yield i -> buf[i];
      }
      case CvType.CV_32F -> {
        float[] buf = new float[total];
        src.get(0, 0, buf);
        yield i -> Math.round(buf[i]);
      }
      default -> null;
    };
  }
}
