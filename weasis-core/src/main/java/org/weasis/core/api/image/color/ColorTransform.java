/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image.color;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.stream.IntStream;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

/**
 * A conversion between two ICC profiles baked into tables, applied to 8-bit BGR pixels. Built by
 * {@link ColorTransforms}; the color management module is only used to sample it once.
 */
public sealed interface ColorTransform
    permits ColorTransform.Identity, MatrixShaperTransform, CubeTransform {

  /** How the conversion is computed per pixel. */
  enum Kind {
    /** The two profiles describe the same colors: nothing to do. */
    IDENTITY,
    /** Decode curves, a 3×3 matrix in linear light and encode curves: two matrix/TRC profiles. */
    MATRIX,
    /** A 3D table interpolated per pixel: any other pair. */
    CUBE
  }

  ColorTransform IDENTITY = new Identity();

  Kind kind();

  default boolean isIdentity() {
    return kind() == Kind.IDENTITY;
  }

  /**
   * Converts the first {@code length} bytes of {@code in}, pixels in BGR order, into {@code out}.
   */
  void convertRow(byte[] in, byte[] out, int length);

  /**
   * Converts {@code src} into {@code dst}, two {@code CV_8UC3} images of the same size, rows in
   * parallel. {@code src} is not modified.
   */
  default void apply(Mat src, Mat dst) {
    if (src.type() != CvType.CV_8UC3
        || dst.type() != src.type()
        || !src.size().equals(dst.size())) {
      throw new IllegalArgumentException("Two 8-bit 3-channel images of the same size expected");
    }
    PixelRows.apply(this, src, dst);
  }

  /** No conversion. */
  final class Identity implements ColorTransform {
    private Identity() {}

    @Override
    public Kind kind() {
      return Kind.IDENTITY;
    }

    @Override
    public void convertRow(byte[] in, byte[] out, int length) {
      System.arraycopy(in, 0, out, 0, length);
    }
  }

  /** Walks the rows of the native buffers through FFM, without copying the whole image. */
  final class PixelRows {
    private static final int ROWS_PER_TASK = 16;

    private PixelRows() {}

    static void apply(ColorTransform transform, Mat src, Mat dst) {
      int rows = src.rows();
      int rowBytes = src.cols() * 3;
      long srcStep = src.step1(0);
      long dstStep = dst.step1(0);
      MemorySegment in = segment(src, srcStep, rowBytes);
      MemorySegment out = segment(dst, dstStep, rowBytes);
      int tasks = (rows + ROWS_PER_TASK - 1) / ROWS_PER_TASK;
      IntStream stream = IntStream.range(0, tasks);
      if (tasks > 1) {
        stream = stream.parallel();
      }
      stream.forEach(
          t -> {
            byte[] inRow = new byte[rowBytes];
            byte[] outRow = new byte[rowBytes];
            int end = Math.min(rows, (t + 1) * ROWS_PER_TASK);
            for (int y = t * ROWS_PER_TASK; y < end; y++) {
              MemorySegment.copy(in, ValueLayout.JAVA_BYTE, y * srcStep, inRow, 0, rowBytes);
              transform.convertRow(inRow, outRow, rowBytes);
              MemorySegment.copy(outRow, 0, out, ValueLayout.JAVA_BYTE, y * dstStep, rowBytes);
            }
          });
    }

    private static MemorySegment segment(Mat mat, long step, int rowBytes) {
      long length = (mat.rows() - 1L) * step + rowBytes;
      return MemorySegment.ofAddress(mat.dataAddr()).reinterpret(length);
    }
  }
}
