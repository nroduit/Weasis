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

import java.util.BitSet;
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

  /** Reads one pixel as an unsigned int from a typed bulk buffer. */
  @FunctionalInterface
  private interface PixelReader {
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
