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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.ui.model.graphic.imp.seg.SegContour;
import org.weasis.core.ui.model.graphic.imp.seg.SegRegion;
import org.weasis.core.util.SoftHashMap;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.seg.Region;
import org.weasis.opencv.seg.Segment;

/**
 * Lazy contour loader for LABELMAP DICOM SEG frames (DICOM PS3.3 C.8.20.2). A label-map frame
 * stores all segments of a slice in a single image where each pixel value equals the {@code
 * SegmentNumber} it belongs to (segments do not overlap, {@code SegmentsOverlap} is {@code NO}).
 *
 * <p>This loader splits the source frame into one binary mask per segment present in the frame and
 * returns one {@link SegContour} per segment.
 */
public final class LabelMapContourLoader extends CachedContourLoader {
  private static final Logger LOGGER = LoggerFactory.getLogger(LabelMapContourLoader.class);
  private static final SoftHashMap<String, Set<SegContour>> CACHE = new SoftHashMap<>();

  private final DicomImageElement frame;
  private final int id;
  private final Map<Integer, ? extends SegRegion<?>> segments;

  /**
   * @param frame the DICOM SEG frame containing label-map pixel data
   * @param id the frame index (1-based)
   * @param segments map of segment number → region attributes (color, label, visibility)
   */
  public LabelMapContourLoader(
      DicomImageElement frame, int id, Map<Integer, ? extends SegRegion<?>> segments) {
    super(CACHE, frame.toString() + "_label_" + id);
    this.frame = frame;
    this.id = id;
    this.segments = segments;
  }

  @Override
  protected Set<SegContour> build() {
    return MaskFrames.withImage(
        frame,
        Set.of(),
        image -> {
          try {
            Mat src = image.toMat();
            int[] labels = LabelMapScanner.sortedDistinctLabels(src, segments::containsKey);
            if (labels.length == 0) {
              return Set.of();
            }
            Set<SegContour> result = new LinkedHashSet<>(labels.length);
            for (int label : labels) {
              SegContour contour = buildLabelContour(src, label);
              if (contour != null) {
                result.add(contour);
              }
            }
            return result;
          } catch (RuntimeException e) {
            LOGGER.error("Error building label-map contours for frame {}", id, e);
            return Set.of();
          }
        });
  }

  private SegContour buildLabelContour(Mat src, int label) {
    SegRegion<?> region = segments.get(label);
    if (region == null) {
      return null;
    }
    ImageCV binary = new ImageCV();
    try {
      Core.compare(src, new Scalar(label), binary, Core.CMP_EQ);
      int pixelCount = Core.countNonZero(binary);
      if (pixelCount == 0) {
        return null;
      }
      List<Segment> segmentList = Region.buildSegmentList(binary);
      SegContour contour = new SegContour(label + "_" + id, segmentList, pixelCount);
      region.addPixels(contour);
      contour.setAttributes(region);
      return contour;
    } finally {
      binary.release();
    }
  }
}
