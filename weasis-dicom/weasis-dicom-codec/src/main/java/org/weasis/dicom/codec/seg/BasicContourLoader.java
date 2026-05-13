/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.seg;

import java.util.List;
import java.util.Set;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.weasis.core.ui.model.graphic.imp.seg.SegContour;
import org.weasis.core.ui.model.graphic.imp.seg.SegRegion;
import org.weasis.core.util.SoftHashMap;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.opencv.seg.Region;
import org.weasis.opencv.seg.Segment;

/** Lazy contour loader for legacy BINARY DICOM SEG frames (one segment per frame). */
public final class BasicContourLoader extends CachedContourLoader {
  private static final SoftHashMap<String, Set<SegContour>> CACHE = new SoftHashMap<>();

  private final DicomImageElement binaryMask;
  private final int id;
  private final SegRegion<?> region;

  public BasicContourLoader(DicomImageElement binaryMask, int id, SegRegion<?> region) {
    super(CACHE, binaryMask.toString() + "_" + id);
    this.binaryMask = binaryMask;
    this.id = id;
    this.region = region;
  }

  @Override
  protected Set<SegContour> build() {
    return MaskFrames.withImage(
        binaryMask,
        Set.of(),
        binary -> {
          Mat mat = binary.toMat();
          List<Segment> segmentList = Region.buildSegmentList(binary);
          int pixelCount = Core.countNonZero(mat);
          SegContour contour = new SegContour(String.valueOf(id), segmentList, pixelCount);
          region.addPixels(contour);
          contour.setAttributes(region);
          return Set.of(contour);
        });
  }
}
