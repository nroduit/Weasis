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

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;

/**
 * Utility to normalise the in-plane orientation of a decoded DICOM SEG mask frame so that its
 * dimensions match the SEG's declared {@code (Columns, Rows)} pair.
 *
 * <p>Some highdicom-style packed-bit binary SEG objects with non-square frames (e.g. 268×238) come
 * back from the dcm4che image reader with their dimensions <em>swapped</em> (decoded as 238×268),
 * which silently rotates the data by 90° once it is stamped through any downstream code that
 * iterates {@code (my, mx)} over the decoded {@code Mat}. The bug is invisible for square masks
 * (both axes are identical) and only ever breaks non-square SEGs, so it is safest to fix it once at
 * the decoder boundary.
 *
 * <p>This class exposes a single {@link #normalize} method that:
 *
 * <ul>
 *   <li>returns the input unchanged when the decoded dimensions already match the declared ones;
 *   <li>returns a transposed {@link ImageCV} when the dimensions are exactly swapped;
 *   <li>returns {@code null} when the dimensions match neither orientation (caller should skip).
 * </ul>
 *
 * <p>A warn-once log per SEG SOP Instance UID is emitted when a swap is detected, so the issue is
 * visible in production without flooding the log.
 */
public final class SegMaskOrientation {

  private static final Logger LOGGER = LoggerFactory.getLogger(SegMaskOrientation.class);

  /** Tracks SOP UIDs already warned about, so we log the swap only once per SEG. */
  private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

  private SegMaskOrientation() {}

  /**
   * Aligns a decoded SEG mask frame to the SEG's declared {@code (Columns, Rows)} grid.
   *
   * @param mask the decoded mask image (must not be {@code null})
   * @param declaredCols the SEG's declared {@code (0028,0011) Columns}
   * @param declaredRows the SEG's declared {@code (0028,0010) Rows}
   * @param sopInstanceUid the SEG SOP Instance UID (used to warn-once); may be {@code null}
   * @return the input image when dims already match, a transposed {@link ImageCV} when dims are
   *     swapped, or {@code null} when neither matches (caller should skip the frame).
   */
  public static PlanarImage normalize(
      PlanarImage mask, int declaredCols, int declaredRows, String sopInstanceUid) {
    if (mask == null || declaredCols <= 0 || declaredRows <= 0) {
      return mask;
    }
    int w = mask.width();
    int h = mask.height();
    return switch (classify(w, h, declaredCols, declaredRows)) {
      case MATCH -> mask;
      case TRANSPOSED -> transpose(mask, w, h, declaredCols, declaredRows, sopInstanceUid);
      case MISMATCH -> {
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug(
              "SEG {}: decoded mask {}×{} matches neither declared {}×{} nor its transpose; frame skipped",
              sopInstanceUid,
              w,
              h,
              declaredCols,
              declaredRows);
        }
        yield null;
      }
    };
  }

  private enum Orientation {
    MATCH,
    TRANSPOSED,
    MISMATCH
  }

  private static Orientation classify(int w, int h, int dc, int dr) {
    if (w == dc && h == dr) return Orientation.MATCH;
    if (w == dr && h == dc) return Orientation.TRANSPOSED;
    return Orientation.MISMATCH;
  }

  private static ImageCV transpose(
      PlanarImage mask, int w, int h, int dc, int dr, String sopInstanceUid) {
    // Dimensions exactly swapped — physically transpose so all downstream loops stay correct.
    Mat src = mask.toMat();
    ImageCV out = new ImageCV();
    Core.transpose(src, out);
    String key = Objects.requireNonNullElse(sopInstanceUid, "<no-uid>");
    if (WARNED.add(key)) {
      LOGGER.warn(
          "SEG {}: decoded mask delivered transposed ({}×{}) — re-aligning to declared {}×{}",
          key,
          w,
          h,
          dc,
          dr);
    }
    return out;
  }
}
