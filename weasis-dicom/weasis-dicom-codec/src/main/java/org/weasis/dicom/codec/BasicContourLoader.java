/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import java.util.List;
import java.util.Set;
import org.opencv.core.Core;
import org.weasis.core.ui.model.graphic.imp.seg.SegContour;
import org.weasis.core.ui.model.graphic.imp.seg.SegRegion;
import org.weasis.core.util.SoftHashMap;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageConversion;
import org.weasis.opencv.seg.Region;
import org.weasis.opencv.seg.Segment;

public class BasicContourLoader implements LazyContourLoader {
  private static final SoftHashMap<String, SegContour> contourCache = new SoftHashMap<>();

  private final DicomImageElement binaryMask;
  private final int id;
  private final String uid;
  private final SegRegion<?> region;

  public BasicContourLoader(DicomImageElement binaryMask, int id, SegRegion<?> region) {
    this.binaryMask = binaryMask;
    this.id = id;
    this.region = region;
    this.uid = binaryMask.toString() + "_" + id;
  }

  @Override
  public Set<SegContour> getLazyContours() {
    SegContour cachedContour;
    synchronized (contourCache) {
      cachedContour = contourCache.get(uid);
    }

    if (cachedContour == null) {
      SegContour newContour = buildContour();
      synchronized (contourCache) {
        contourCache.put(uid, newContour);
      }
      if (newContour == null) {
        return Set.of();
      }
      return Set.of(newContour);
    }

    return Set.of(cachedContour);
  }

  private SegContour buildContour() {
    PlanarImage binary = binaryMask.getImage();
    if (binary == null || binary.width() <= 0) {
      return null;
    }
    List<Segment> segmentList = Region.buildSegmentList(binary);
    int pixelCount = Core.countNonZero(binary.toMat());
    ImageConversion.releasePlanarImage(binary);
    binaryMask.removeImageFromCache();

    SegContour contour = new SegContour(String.valueOf(id), segmentList, pixelCount);
    region.addPixels(contour);
    contour.setAttributes(region);

    return contour;
  }
}
