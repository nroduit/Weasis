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

import java.util.Set;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.ui.model.graphic.imp.seg.SegContour;
import org.weasis.core.ui.model.graphic.imp.seg.SegRegion;
import org.weasis.core.util.SoftHashMap;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.opencv.data.ImageCV;

/**
 * Lazy contour loader for FRACTIONAL DICOM SEG frames. Instead of vectorizing the mask into
 * contours, this loader retains the raw fractional pixel data as a grayscale image (CV_8UC1,
 * normalized to [0, 255]). A LUT is applied at render time to map the grayscale values to the
 * segment's color with per-pixel alpha proportional to the fractional value.
 */
public final class FractionalContourLoader extends CachedContourLoader {
  private static final Logger LOGGER = LoggerFactory.getLogger(FractionalContourLoader.class);
  private static final SoftHashMap<String, Set<SegContour>> CACHE = new SoftHashMap<>();

  private final DicomImageElement frame;
  private final int id;
  private final SegRegion<?> region;
  private final int maxFractionalValue;

  public FractionalContourLoader(
      DicomImageElement frame, int id, SegRegion<?> region, int maxFractionalValue) {
    super(CACHE, frame.toString() + "_frac_" + id);
    this.frame = frame;
    this.id = id;
    this.region = region;
    this.maxFractionalValue = Math.max(1, maxFractionalValue);
  }

  @Override
  protected Set<SegContour> build() {
    return MaskFrames.withImage(
        frame,
        Set.of(),
        image -> {
          try {
            Mat src = image.toMat();
            ImageCV normalized = normalizeToByteRange(src);
            double weightedPixelCount = Core.sumElems(src).val[0] / maxFractionalValue;
            SegContour contour = new SegContour(String.valueOf(id), normalized, weightedPixelCount);
            region.addPixels(contour);
            contour.setAttributes(region);
            return Set.of(contour);
          } catch (RuntimeException e) {
            LOGGER.error("Error building fractional contour for frame {}", id, e);
            return Set.of();
          }
        });
  }

  /** CV_8UC1 view of {@code src} scaled to [0, 255]. */
  private ImageCV normalizeToByteRange(Mat src) {
    ImageCV result = new ImageCV();
    if (maxFractionalValue == 255 && src.type() == CvType.CV_8UC1) {
      // Defensive copy: the source Mat belongs to the cached PlanarImage that the surrounding
      // withImage(...) helper releases on exit; the contour must own its own buffer.
      src.copyTo(result);
    } else {
      // Single-pass convert + scale (no intermediate float Mat).
      src.convertTo(result, CvType.CV_8UC1, 255.0 / maxFractionalValue);
    }
    return result;
  }
}
