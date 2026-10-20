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

import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.color.ICC_ProfileRGB;
import java.awt.image.ColorConvertOp;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferUShort;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds the {@link ColorTransform} between two profiles and keeps it per pair. The platform color
 * management module (LittleCMS behind {@link ColorConvertOp}) computes the conversion; it is
 * sampled once into tables:
 *
 * <ul>
 *   <li>a pair that changes no color by more than half an 8-bit level is the identity;
 *   <li>two matrix/TRC profiles give decode tables, a matrix and encode tables, kept only when they
 *       match the module within one level (a profile may also carry LUT tags the module prefers);
 *   <li>any other pair is sampled on a 33³ grid.
 * </ul>
 */
public final class ColorTransforms {
  private static final Logger LOGGER = LoggerFactory.getLogger(ColorTransforms.class);

  private static final int MAX_CACHED = 32;
  private static final int DECODE_SAMPLES = 1024;
  private static final double IDENTITY_TOLERANCE = 0.5;
  private static final int MATRIX_TOLERANCE = 1;
  private static final int CHECK_STEP = 17;
  private static final float RGB_SCALE = 257f;
  private static final float XYZ_SCALE = 32768f;

  private record Pair(IccProfile source, IccProfile destination) {}

  private static final Map<Pair, ColorTransform> CACHE = new ConcurrentHashMap<>();

  private ColorTransforms() {}

  /** The conversion of colors described by {@code source} into colors described by {@code dest}. */
  public static ColorTransform of(IccProfile source, IccProfile destination) {
    if (source == null || destination == null || source.equals(destination)) {
      return ColorTransform.IDENTITY;
    }
    if (CACHE.size() > MAX_CACHED) {
      CACHE.clear();
    }
    return CACHE.computeIfAbsent(new Pair(source, destination), ColorTransforms::build);
  }

  private static ColorTransform build(Pair pair) {
    if (!pair.source().isRgb() || !pair.destination().isRgb()) {
      LOGGER.debug(
          "No color conversion from {} to {}: RGB profiles only",
          pair.source(),
          pair.destination());
      return ColorTransform.IDENTITY;
    }
    try {
      ICC_Profile src = pair.source().toIccProfile();
      ICC_Profile dst = pair.destination().toIccProfile();
      ColorTransform transform = build(src, dst);
      LOGGER.info(
          "Color conversion {} from {} to {}", transform.kind(), pair.source(), pair.destination());
      return transform;
    } catch (RuntimeException e) {
      LOGGER.warn(
          "Cannot build the color conversion from {} to {}", pair.source(), pair.destination(), e);
      return ColorTransform.IDENTITY;
    }
  }

  static ColorTransform build(ICC_Profile src, ICC_Profile dst) {
    ColorConvertOp op = new ColorConvertOp(new ICC_Profile[] {src, dst}, null);
    int[] grid = gridValues();
    if (maxDeviation(convert(op, grid, RGB_SCALE), grid) <= IDENTITY_TOLERANCE) {
      return ColorTransform.IDENTITY;
    }
    if (src instanceof ICC_ProfileRGB && dst instanceof ICC_ProfileRGB) {
      MatrixShaperTransform matrix = matrixShaper(src, dst);
      if (matches(matrix, op)) {
        return matrix;
      }
    }
    return cube(src, dst);
  }

  /** The 33³ sampling of the pair, whatever its kind. */
  static CubeTransform cube(ICC_Profile src, ICC_Profile dst) {
    int[] grid = gridValues();
    if (dst instanceof ICC_ProfileRGB) {
      Rgb destination = new Rgb(dst);
      ColorConvertOp toXyz =
          new ColorConvertOp(
              new ICC_Profile[] {src, ICC_Profile.getInstance(ColorSpace.CS_CIEXYZ)}, null);
      DestinationStage stage =
          new DestinationStage(flatten(invert(destination.columns())), destination.encodeTables());
      return new CubeTransform(convert(toXyz, grid, XYZ_SCALE), stage);
    }
    ColorConvertOp op = new ColorConvertOp(new ICC_Profile[] {src, dst}, null);
    return new CubeTransform(convert(op, grid, RGB_SCALE), null);
  }

  private static int[] gridValues() {
    int grid = CubeTransform.GRID;
    int[] values = new int[grid];
    for (int i = 0; i < grid; i++) {
      values[i] = Math.round(i * 65535f / (grid - 1));
    }
    return values;
  }

  /**
   * Converts every RGB combination of {@code values} (16-bit) and returns the outputs divided by
   * {@code scale}: {@link #RGB_SCALE} for 8-bit RGB, {@link #XYZ_SCALE} for XYZ, whose 16-bit
   * encoding is 1.15 fixed point.
   */
  private static float[] convert(ColorConvertOp op, int[] values, float scale) {
    int n = values.length;
    int count = n * n * n;
    WritableRaster in = Raster.createInterleavedRaster(DataBuffer.TYPE_USHORT, count, 1, 3, null);
    short[] samples = ((DataBufferUShort) in.getDataBuffer()).getData();
    int k = 0;
    for (int r : values) {
      for (int g : values) {
        for (int b : values) {
          samples[k++] = (short) r;
          samples[k++] = (short) g;
          samples[k++] = (short) b;
        }
      }
    }
    WritableRaster out = Raster.createInterleavedRaster(DataBuffer.TYPE_USHORT, count, 1, 3, null);
    op.filter(in, out);
    short[] converted = ((DataBufferUShort) out.getDataBuffer()).getData();
    float[] result = new float[converted.length];
    for (int i = 0; i < converted.length; i++) {
      result[i] = (converted[i] & 0xFFFF) / scale;
    }
    return result;
  }

  private static double maxDeviation(float[] cube, int[] values) {
    int n = values.length;
    double max = 0;
    int k = 0;
    for (int r = 0; r < n; r++) {
      for (int g = 0; g < n; g++) {
        for (int b = 0; b < n; b++) {
          max = Math.max(max, Math.abs(cube[k++] - values[r] / 257.0));
          max = Math.max(max, Math.abs(cube[k++] - values[g] / 257.0));
          max = Math.max(max, Math.abs(cube[k++] - values[b] / 257.0));
        }
      }
    }
    return max;
  }

  /** Whether the tables give the module's result within one level on a grid of 8-bit colors. */
  private static boolean matches(MatrixShaperTransform matrix, ColorConvertOp op) {
    int n = 255 / CHECK_STEP + 1;
    int[] values = new int[n];
    for (int i = 0; i < n; i++) {
      values[i] = i * CHECK_STEP * 257;
    }
    float[] reference = convert(op, values, RGB_SCALE);
    byte[] bgr = new byte[reference.length];
    int k = 0;
    for (int r = 0; r < n; r++) {
      for (int g = 0; g < n; g++) {
        for (int b = 0; b < n; b++) {
          bgr[k++] = (byte) (b * CHECK_STEP);
          bgr[k++] = (byte) (g * CHECK_STEP);
          bgr[k++] = (byte) (r * CHECK_STEP);
        }
      }
    }
    byte[] out = new byte[bgr.length];
    matrix.convertRow(bgr, out, bgr.length);
    for (int i = 0; i < reference.length; i += 3) {
      for (int c = 0; c < 3; c++) {
        // reference in RGB, out in BGR
        if (Math.abs(Math.round(reference[i + c]) - (out[i + 2 - c] & 0xFF)) > MATRIX_TOLERANCE) {
          return false;
        }
      }
    }
    return true;
  }

  private static MatrixShaperTransform matrixShaper(ICC_Profile src, ICC_Profile dst) {
    Rgb source = new Rgb(src);
    Rgb destination = new Rgb(dst);
    float[][] decode = new float[3][256];
    for (int c = 0; c < 3; c++) {
      for (int v = 0; v < 256; v++) {
        decode[c][v] = (float) source.linear(c, v / 255f);
      }
    }
    // destination linear RGB = inverse(destination primaries) · source primaries · source linear
    float[] matrix = flatten(multiply(invert(destination.columns()), source.columns()));
    return new MatrixShaperTransform(
        decode, new DestinationStage(matrix, destination.encodeTables()));
  }

  /** A matrix/TRC profile seen through the module: its primaries and tone curves. */
  private static final class Rgb {
    private final ICC_ColorSpace space;
    private final double[][] primaries = new double[3][];

    Rgb(ICC_Profile profile) {
      this.space = new ICC_ColorSpace(profile);
      for (int c = 0; c < 3; c++) {
        float[] rgb = new float[3];
        rgb[c] = 1f;
        float[] xyz = space.toCIEXYZ(rgb);
        primaries[c] = new double[] {xyz[0], xyz[1], xyz[2]};
      }
    }

    /** Matrix whose columns are the XYZ of the primaries. */
    double[][] columns() {
      double[][] m = new double[3][3];
      for (int row = 0; row < 3; row++) {
        for (int col = 0; col < 3; col++) {
          m[row][col] = primaries[col][row];
        }
      }
      return m;
    }

    /** Linear light of one channel: the XYZ of the channel alone over the XYZ of its primary. */
    double linear(int channel, float v) {
      float[] rgb = new float[3];
      rgb[channel] = v;
      float[] xyz = space.toCIEXYZ(rgb);
      double[] full = primaries[channel];
      double sum = full[0] + full[1] + full[2];
      return sum <= 0 ? v : Math.max(0, (xyz[0] + xyz[1] + xyz[2]) / sum);
    }

    byte[][] encodeTables() {
      byte[][] tables = new byte[3][];
      for (int c = 0; c < 3; c++) {
        tables[c] = encodeTable(c);
      }
      return tables;
    }

    /** Inverse of the tone curve of one channel, over {@code ENCODE_MAX + 1} linear values. */
    private byte[] encodeTable(int channel) {
      double[] curve = new double[DECODE_SAMPLES];
      double max = 0;
      for (int i = 0; i < DECODE_SAMPLES; i++) {
        // monotone, as a tone curve must be
        max = Math.max(max, linear(channel, i / (DECODE_SAMPLES - 1f)));
        curve[i] = max;
      }
      int size = DestinationStage.ENCODE_MAX + 1;
      byte[] table = new byte[size];
      int j = 0;
      for (int k = 0; k < size; k++) {
        double target = k / (double) DestinationStage.ENCODE_MAX;
        while (j < DECODE_SAMPLES - 2 && curve[j + 1] < target) {
          j++;
        }
        double span = curve[j + 1] - curve[j];
        double t = span <= 0 ? 0 : Math.clamp((target - curve[j]) / span, 0, 1);
        table[k] = (byte) Math.round(Math.clamp((j + t) / (DECODE_SAMPLES - 1), 0, 1) * 255);
      }
      return table;
    }
  }

  private static float[] flatten(double[][] m) {
    float[] values = new float[9];
    for (int i = 0; i < 9; i++) {
      values[i] = (float) m[i / 3][i % 3];
    }
    return values;
  }

  private static double[][] multiply(double[][] a, double[][] b) {
    double[][] m = new double[3][3];
    for (int i = 0; i < 3; i++) {
      for (int j = 0; j < 3; j++) {
        m[i][j] = a[i][0] * b[0][j] + a[i][1] * b[1][j] + a[i][2] * b[2][j];
      }
    }
    return m;
  }

  private static double[][] invert(double[][] m) {
    double a = m[0][0], b = m[0][1], c = m[0][2];
    double d = m[1][0], e = m[1][1], f = m[1][2];
    double g = m[2][0], h = m[2][1], i = m[2][2];
    double det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g);
    if (Math.abs(det) < 1e-12) {
      throw new IllegalArgumentException("Singular primaries matrix");
    }
    return new double[][] {
      {(e * i - f * h) / det, (c * h - b * i) / det, (b * f - c * e) / det},
      {(f * g - d * i) / det, (a * i - c * g) / det, (c * d - a * f) / det},
      {(d * h - e * g) / det, (b * g - a * h) / det, (a * e - b * d) / det}
    };
  }
}
