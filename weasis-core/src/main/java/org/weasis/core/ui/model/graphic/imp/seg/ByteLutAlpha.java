/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic.imp.seg;

import java.awt.Color;
import java.util.Arrays;
import java.util.Objects;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

/**
 * A 4-channel (BGRA) byte lookup table dedicated to fractional segmentation overlays.
 *
 * <p>Unlike {@link org.weasis.opencv.op.lut.ByteLut} which is a 3-channel BGR LUT, this LUT carries
 * a per-entry alpha so that a grayscale fractional mask can be turned into an alpha-modulated color
 * overlay in a single native {@link org.opencv.core.Core#LUT(Mat, Mat, Mat)} call.
 *
 * <p>The channel order ({@code A, B, G, R}) matches the byte layout of {@link
 * java.awt.image.BufferedImage#TYPE_4BYTE_ABGR}, allowing bulk-copy without per-pixel reordering.
 * Entry {@code 0} of the alpha channel is always {@code 0} so background pixels (mask value 0) are
 * fully transparent.
 */
public record ByteLutAlpha(String name, byte[][] lutTable) {

  public static final int CHANNEL_COUNT = 4;
  public static final int CHANNEL_SIZE = 256;

  public static final int A = 0;
  public static final int B = 1;
  public static final int G = 2;
  public static final int R = 3;

  public ByteLutAlpha {
    Objects.requireNonNull(name, "Name cannot be null");
    Objects.requireNonNull(lutTable, "LUT table cannot be null");
    if (lutTable.length != CHANNEL_COUNT) {
      throw new IllegalArgumentException(
          "LUT must have exactly %d channels (ABGR)".formatted(CHANNEL_COUNT));
    }
    for (var channel : lutTable) {
      if (channel == null || channel.length != CHANNEL_SIZE) {
        throw new IllegalArgumentException(
            "Each LUT channel must have exactly %d values".formatted(CHANNEL_SIZE));
      }
    }
  }

  /**
   * Builds an ABGR LUT for a single segment color and opacity. Color channels are constant; alpha
   * ramps linearly with the input intensity and {@code opacity}, with entry {@code 0} forced to
   * fully transparent.
   *
   * @param name a human-readable LUT name
   * @param color the segment's display color (alpha channel ignored)
   * @param opacity the global opacity multiplier in {@code [0.0, 1.0]}
   */
  public static ByteLutAlpha forSegment(String name, Color color, float opacity) {
    Objects.requireNonNull(color, "Color cannot be null");
    byte[] alpha = buildAlphaRamp(Math.clamp(opacity, 0.0f, 1.0f));
    byte[] blue = filled((byte) color.getBlue());
    byte[] green = filled((byte) color.getGreen());
    byte[] red = filled((byte) color.getRed());
    return new ByteLutAlpha(name, new byte[][] {alpha, blue, green, red});
  }

  /**
   * Builds an ABGR LUT from a 3-channel BGR color LUT, with a per-entry alpha that ramps with the
   * input intensity and {@code opacity} (entry {@code 0} forced fully transparent). Turns a
   * grayscale image into an alpha-modulated color overlay where both the color <em>and</em> the
   * transparency follow the pixel intensity, which is the standard way to overlay a continuous
   * functional image (e.g. PET) on top of an anatomical one.
   *
   * @param name a human-readable LUT name
   * @param bgrLut a {@code [3][256]} table in {@code B, G, R} channel order (the layout used by
   *     {@link org.weasis.opencv.op.lut.ByteLut})
   * @param opacity the global opacity multiplier in {@code [0.0, 1.0]}
   */
  public static ByteLutAlpha fromColorLut(String name, byte[][] bgrLut, float opacity) {
    Objects.requireNonNull(bgrLut, "Color LUT cannot be null");
    if (bgrLut.length < 3) {
      throw new IllegalArgumentException("Color LUT must have at least 3 channels (BGR)");
    }
    for (int c = 0; c < 3; c++) {
      if (bgrLut[c] == null || bgrLut[c].length != CHANNEL_SIZE) {
        throw new IllegalArgumentException(
            "Each color LUT channel must have exactly %d values".formatted(CHANNEL_SIZE));
      }
    }
    byte[] alpha = buildAlphaRamp(Math.clamp(opacity, 0.0f, 1.0f));
    return new ByteLutAlpha(
        name, new byte[][] {alpha, bgrLut[0].clone(), bgrLut[1].clone(), bgrLut[2].clone()});
  }

  private static byte[] filled(byte value) {
    byte[] out = new byte[CHANNEL_SIZE];
    Arrays.fill(out, value);
    return out;
  }

  private static byte[] buildAlphaRamp(float clampedOpacity) {
    byte[] alpha = new byte[CHANNEL_SIZE];
    // alpha[0] stays 0 (transparent background); ramp the rest.
    for (int i = 1; i < CHANNEL_SIZE; i++) {
      int a = Math.round((i / 255.0f) * clampedOpacity * 255.0f);
      alpha[i] = (byte) Math.clamp(a, 0, 255);
    }
    return alpha;
  }

  /**
   * Builds the OpenCV LUT {@code Mat} (1×256, {@code CV_8UC4}) suitable for {@link
   * org.opencv.core.Core#LUT(Mat, Mat, Mat)}. The returned {@code Mat} is freshly allocated;
   * callers must release it. Interleaved byte order is {@code A, B, G, R} per entry.
   */
  public Mat toLutMat() {
    Mat lutMat = new Mat(1, CHANNEL_SIZE, CvType.CV_8UC4);
    byte[] interleaved = new byte[CHANNEL_SIZE * CHANNEL_COUNT];
    byte[] aCh = lutTable[A];
    byte[] bCh = lutTable[B];
    byte[] gCh = lutTable[G];
    byte[] rCh = lutTable[R];
    for (int i = 0; i < CHANNEL_SIZE; i++) {
      int o = i << 2;
      interleaved[o] = aCh[i];
      interleaved[o + 1] = bCh[i];
      interleaved[o + 2] = gCh[i];
      interleaved[o + 3] = rCh[i];
    }
    lutMat.put(0, 0, interleaved);
    return lutMat;
  }

  @Override
  public String toString() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    return this == o
        || (o instanceof ByteLutAlpha(String otherName, byte[][] otherLut)
            && name.equals(otherName)
            && Arrays.deepEquals(lutTable, otherLut));
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, Arrays.deepHashCode(lutTable));
  }
}
