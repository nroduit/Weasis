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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;

/**
 * Workaround for an OpenCV native DICOM-raw decoder bug that affects BINARY {@code BitsAllocated=1}
 * SEG frames whose width is not a multiple of 8. The native decoder allocates a row buffer of
 * {@code width/8} bytes (integer-truncated) instead of {@code ceil(width/8)} and then asks the
 * unpacker to fill {@code width} pixels from it, reading uninitialised memory past the buffer for
 * the row tail. The stream is also advanced by {@code width/8} bytes per row, which is {@code
 * width%8} bits short, so every row after the first decodes from a shifted bit offset. The visible
 * symptom is a phantom column of non-zero pixels at the right edge of every frame and progressive
 * horizontal smearing of the underlying structures.
 *
 * <p>This class re-decodes the affected frames from the SEG's raw {@code Pixel Data} byte array
 * using DICOM-correct continuous LSB-first bit packing. It is a temporary shim until the native fix
 * in {@code grfmt_dcm_raw.cpp} ships in a new OpenCV native library; once Weasis ships with that
 * build, the two call sites — {@code SegmentationVolumeBuilder} (3D volume stamping) and {@code
 * BasicContourLoader} (2D overlay contours) — can simply stop invoking {@link #reDecodeFrame} and
 * this class can be deleted.
 */
public final class SegBinaryMaskWorkaround {

  private static final Logger LOGGER = LoggerFactory.getLogger(SegBinaryMaskWorkaround.class);

  /** SOP UIDs we have already logged once, so the warn line does not flood. */
  private static final java.util.Set<String> WARNED = ConcurrentHashMap.newKeySet();

  /** Cache: SOP UID → {@code true} once we have observed the OpenCV bug for this SEG. */
  private static final java.util.Map<String, AtomicBoolean> NEEDS_WORKAROUND =
      new ConcurrentHashMap<>();

  private SegBinaryMaskWorkaround() {}

  /**
   * Returns {@code true} when the SEG's frame width is not a multiple of 8 and the SEG declares
   * {@code BitsAllocated=1}, i.e. when it can hit the OpenCV native decoder bug. Cheap to call;
   * safe to call for every frame.
   */
  public static boolean isAffected(Attributes dicom) {
    if (dicom == null) {
      return false;
    }
    int bitsAllocated = dicom.getInt(Tag.BitsAllocated, 0);
    if (bitsAllocated != 1) {
      return false;
    }
    int columns = dicom.getInt(Tag.Columns, 0);
    return columns > 0 && (columns % 8) != 0;
  }

  /**
   * Re-decodes a single SEG frame from the raw {@code Pixel Data} byte array using DICOM's
   * continuous LSB-first bit packing (CP 1132). The native OpenCV decoder bug only fires for 1-bit
   * SEGs whose width is not a multiple of 8 — callers should gate on {@link #isAffected} so square
   * or width-multiple-of-8 SEGs are not re-decoded unnecessarily.
   *
   * @param dicom the SEG's top-level DICOM dataset; must contain raw uncompressed {@code Pixel
   *     Data} (this workaround does not support encapsulated / compressed SEG payloads).
   * @param frameIndex zero-based frame index inside the SEG's multi-frame {@code Pixel Data}.
   * @param width SEG's declared {@code (0028,0011) Columns}.
   * @param height SEG's declared {@code (0028,0010) Rows}.
   * @return a freshly-allocated {@link ImageCV} ({@code CV_8UC1}, 0/255) of dimensions {@code width
   *     × height} carrying the correctly-decoded mask, or {@code null} when the raw pixel data is
   *     unavailable / truncated (caller should fall back to the original decoded mask).
   */
  public static PlanarImage reDecodeFrame(Attributes dicom, int frameIndex, int width, int height) {
    if (dicom == null || frameIndex < 0 || width <= 0 || height <= 0) {
      return null;
    }
    byte[] pixelData;
    try {
      pixelData = dicom.getBytes(Tag.PixelData);
    } catch (java.io.IOException ioe) {
      logOnce(dicom, "BINARY SEG Pixel Data read failed: " + ioe.getMessage());
      return null;
    }
    if (pixelData == null) {
      // Encapsulated (compressed) Pixel Data does not return a byte array here.
      // BINARY SEG is almost always uncompressed, but bail safely if not.
      logOnce(
          dicom,
          "BINARY SEG with width % 8 != 0 but Pixel Data is not a contiguous byte array — workaround skipped");
      return null;
    }
    long framePixels = (long) width * height;
    long firstBit = framePixels * frameIndex;
    long lastBit = firstBit + framePixels; // exclusive
    long totalBitsAvailable = (long) pixelData.length * 8;
    if (lastBit > totalBitsAvailable) {
      logOnce(
          dicom,
          "BINARY SEG Pixel Data truncated (need "
              + lastBit
              + " bits, have "
              + totalBitsAvailable
              + ") — workaround skipped for frame "
              + frameIndex);
      return null;
    }

    ImageCV out = new ImageCV(height, width, CvType.CV_8UC1);
    Mat mat = out.toMat();
    byte[] row = new byte[width];
    long bit = firstBit;
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++, bit++) {
        // DICOM Pixel Data convention for 1-bit per pixel: bits packed LSB-first within each
        // byte, continuously across rows and frames (no per-row/per-frame padding).
        int b = pixelData[(int) (bit >> 3)] & 0xFF;
        int v = (b >> ((int) (bit & 7))) & 1;
        row[x] = v == 0 ? (byte) 0 : (byte) 255;
      }
      mat.put(y, 0, row);
    }
    logOnce(dicom, "Re-decoded BINARY SEG with width=" + width + " (workaround active)");
    return out;
  }

  private static void logOnce(Attributes dicom, String message) {
    String key = dicom.getString(Tag.SOPInstanceUID, "<no-uid>");
    NEEDS_WORKAROUND.computeIfAbsent(key, _ -> new AtomicBoolean(false));
    if (WARNED.add(key)) {
      LOGGER.info("SEG {}: {}", key, message);
    }
  }
}
