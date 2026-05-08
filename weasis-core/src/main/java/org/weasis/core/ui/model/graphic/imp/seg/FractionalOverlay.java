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

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.weasis.opencv.data.PlanarImage;

/**
 * Utility class for rendering fractional segmentation overlays. Applies a color LUT to a grayscale
 * fractional mask (CV_8UC1, 0–255) to produce an ARGB overlay image where:
 *
 * <ul>
 *   <li>RGB = the segment's recommended display color
 *   <li>Alpha = proportional to the fractional value × global opacity
 * </ul>
 *
 * <p>The colorize step is performed by OpenCV's native {@link Core#LUT(Mat, Mat, Mat)} on a
 * 4-channel ABGR LUT ({@link ByteLutAlpha}), then bulk-copied into a {@link
 * BufferedImage#TYPE_4BYTE_ABGR} raster. This avoids per-row JNI round-trips and the per-pixel Java
 * loop of the previous implementation.
 *
 * <p>The raw grayscale data is preserved so the same mask can be reused for MPR reslicing and
 * volume rendering with different color/opacity settings.
 */
public final class FractionalOverlay {

  /** Cache of {@link ByteLutAlpha} keyed by (rgb, quantised opacity). */
  private static final Map<LutKey, ByteLutAlpha> LUT_CACHE = new ConcurrentHashMap<>();

  private FractionalOverlay() {}

  /** Composite cache key: 24-bit RGB + 8-bit quantised opacity. */
  private record LutKey(int rgb, int opacity) {}

  /** Returns a cached LUT for the given color and opacity (opacity quantised to 8 bits). */
  public static ByteLutAlpha getOrBuildLut(Color color, float opacity) {
    int rgb = color.getRGB() & 0xFFFFFF;
    int op = Math.clamp(Math.round(opacity * 255.0f), 0, 255);
    return LUT_CACHE.computeIfAbsent(
        new LutKey(rgb, op),
        _ -> ByteLutAlpha.forSegment("seg-" + Integer.toHexString(rgb), color, op / 255.0f));
  }

  /** Clears the internal LUT cache. */
  public static void clearLutCache() {
    LUT_CACHE.clear();
  }

  /**
   * Applies the ABGR LUT to a grayscale fractional mask using OpenCV's native {@link Core#LUT(Mat,
   * Mat, Mat)} and bulk-copies the result into a {@link BufferedImage#TYPE_4BYTE_ABGR} image.
   *
   * @param mask the fractional mask (CV_8UC1, 0–255)
   * @param lut the ABGR lookup table
   * @return a TYPE_4BYTE_ABGR BufferedImage ready for alpha-composite rendering
   */
  public static BufferedImage applyLut(PlanarImage mask, ByteLutAlpha lut) {
    int w = mask.width();
    int h = mask.height();

    Mat src = mask.toMat();
    Mat src4 = new Mat();
    Mat dst = new Mat();
    Mat lutMat = lut.toLutMat();
    try {
      // Broadcast the single-channel mask into 4 identical channels in one fused JNI call,
      // avoiding the list/nCopies allocation of Core.merge.
      Imgproc.cvtColor(src, src4, Imgproc.COLOR_GRAY2BGRA);
      Core.LUT(src4, lutMat, dst);

      BufferedImage overlay = new BufferedImage(w, h, BufferedImage.TYPE_4BYTE_ABGR);
      byte[] raster = ((DataBufferByte) overlay.getRaster().getDataBuffer()).getData();
      // Channel order in dst is A,B,G,R — matches the byte layout of TYPE_4BYTE_ABGR.
      dst.get(0, 0, raster);
      return overlay;
    } finally {
      lutMat.release();
      src4.release();
      dst.release();
    }
  }

  /**
   * Composites multiple fractional overlays into a single ARGB BufferedImage. Each contour's mask
   * is colorized with its region color and alpha-blended in order (SRC_OVER).
   *
   * @return a composited TYPE_4BYTE_ABGR BufferedImage, or {@code null} if no overlays
   */
  public static BufferedImage compositeOverlays(
      Iterable<SegContour> contours, int width, int height) {
    BufferedImage composite = null;
    Graphics2D g2d = null;
    try {
      for (SegContour contour : contours) {
        var mask = contour.getFractionalMask();
        var attrs = contour.getAttributes();
        if (mask == null || attrs == null || !attrs.isVisible()) {
          continue;
        }
        if (composite == null) {
          composite = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
          g2d = composite.createGraphics();
          g2d.setComposite(AlphaComposite.SrcOver);
        }
        drawContourInto(g2d, mask, attrs.getColor());
      }
    } finally {
      if (g2d != null) {
        g2d.dispose();
      }
    }
    return composite;
  }

  private static void drawContourInto(Graphics2D g2d, PlanarImage mask, Color color) {
    var lut = getOrBuildLut(color, color.getAlpha() / 255.0f);
    g2d.drawImage(applyLut(mask, lut), 0, 0, null);
  }
}
