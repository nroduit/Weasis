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
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.weasis.core.ui.model.graphic.imp.seg.SegContour;
import org.weasis.core.ui.model.graphic.imp.seg.SegRegion;
import org.weasis.core.util.SoftHashMap;
import org.weasis.dicom.codec.DcmMediaReader;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageConversion;
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
    // OpenCV-native-decoder workaround: BINARY SEG frames whose Columns is not a multiple of 8
    // are decoded incorrectly by the native 1-bit decoder (phantom right-edge column + per-row
    // bit-stream shift). Re-decode the affected frame from the raw Pixel Data bytes so the 2D
    // overlay contours match what SegmentationVolumeBuilder already does for the 3D volume.
    // TEMPORARY: remove this branch once the native fix in grfmt_dcm_raw.cpp ships in a new
    // OpenCV native library — see SegBinaryMaskWorkaround.
    Attributes dicom = segDicomObject();
    if (SegBinaryMaskWorkaround.isAffected(dicom)) {
      PlanarImage reDecoded =
          SegBinaryMaskWorkaround.reDecodeFrame(
              dicom, id - 1, dicom.getInt(Tag.Columns, 0), dicom.getInt(Tag.Rows, 0));
      if (reDecoded != null) {
        try {
          return buildContours(reDecoded);
        } finally {
          ImageConversion.releasePlanarImage(reDecoded);
        }
      }
    }
    return MaskFrames.withImage(binaryMask, Set.of(), this::buildContours);
  }

  /** SEG's top-level DICOM dataset (shared by every frame), or {@code null} when unavailable. */
  private Attributes segDicomObject() {
    DcmMediaReader reader = binaryMask.getMediaReader();
    return reader == null ? null : reader.getDicomObject();
  }

  private Set<SegContour> buildContours(PlanarImage binary) {
    Mat mat = binary.toMat();
    List<Segment> segmentList = Region.buildSegmentList(binary);
    int pixelCount = Core.countNonZero(mat);
    SegContour contour = new SegContour(String.valueOf(id), segmentList, pixelCount);
    region.addPixels(contour);
    contour.setAttributes(region);
    return Set.of(contour);
  }
}
