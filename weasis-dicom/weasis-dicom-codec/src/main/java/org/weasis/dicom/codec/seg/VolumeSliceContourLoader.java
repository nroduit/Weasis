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
import java.util.stream.Collectors;
import org.dcm4che3.data.Tag;
import org.joml.Vector3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.ui.model.graphic.imp.seg.SegContour;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.geometry.ImageOrientation;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageConversion;

/**
 * Lazy contour loader that resamples a {@link SegmentationVolume} on the plane of a {@link
 * DicomImageElement}, used as a 2D-overlay fallback when the image and the segmentation have
 * incompatible orientations (so the {@code positionMap} lookup is not possible).
 *
 * <p>The reslicing is performed once on first {@link #getLazyContours()} call and the resulting
 * contour set is cached.
 */
public final class VolumeSliceContourLoader implements LazyContourLoader {

  private static final Logger LOGGER = LoggerFactory.getLogger(VolumeSliceContourLoader.class);
  private static final double[] DEFAULT_PIXEL_SPACING = {1.0, 1.0};

  private final SegmentationVolume volume;
  private final DicomImageElement image;

  private volatile Set<SegContour> cached;

  public VolumeSliceContourLoader(SegmentationVolume volume, DicomImageElement image) {
    this.volume = volume;
    this.image = image;
  }

  @Override
  public Set<SegContour> getLazyContours() {
    Set<SegContour> result = cached;
    if (result != null) {
      return result;
    }
    synchronized (this) {
      if (cached == null) {
        cached = compute();
      }
      return cached;
    }
  }

  private Set<SegContour> compute() {
    if (volume == null || image == null) {
      return Set.of();
    }
    PlaneParams p = resolvePlaneParams();
    if (p == null) {
      return Set.of();
    }
    // DICOM PixelSpacing = [row spacing (Y), column spacing (X)].
    Map<Integer, List<SegContour>> map =
        volume.getContoursForImagePlane(p.ipp, p.row, p.col, p.ps[1], p.ps[0], p.w, p.h);
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(
          "Volume reslice: img IPP={} rowDir={} colDir={} ps=[{}, {}] size={}x{} → {} segments",
          p.ipp,
          p.row,
          p.col,
          p.ps[0],
          p.ps[1],
          p.w,
          p.h,
          map == null ? 0 : map.size());
    }
    if (map == null || map.isEmpty()) {
      return Set.of();
    }
    return map.values().stream()
        .flatMap(List::stream)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /** Bundles all metadata needed for a reslice request. */
  private record PlaneParams( // NOSONAR private DTO, never compared
      Vector3d ipp, Vector3d row, Vector3d col, double[] ps, int w, int h) {}

  private PlaneParams resolvePlaneParams() {
    double[] ipp = TagD.getTagValue(image, Tag.ImagePositionPatient, double[].class);
    if (ipp == null || ipp.length != 3) return null;
    Vector3d row = ImageOrientation.getRowImagePosition(image);
    Vector3d col = ImageOrientation.getColumnImagePosition(image);
    if (row == null || col == null) return null;
    double[] ps = TagD.getTagValue(image, Tag.PixelSpacing, double[].class);
    if (ps == null || ps.length < 2) ps = DEFAULT_PIXEL_SPACING;
    int[] dims = readDimensions();
    if (dims == null) return null;
    return new PlaneParams(new Vector3d(ipp), row, col, ps, dims[0], dims[1]);
  }

  private int[] readDimensions() {
    PlanarImage planar = image.getImage();
    if (planar == null) {
      return null;
    }
    try {
      int w = planar.width();
      int h = planar.height();
      return (w > 0 && h > 0) ? new int[] {w, h} : null;
    } finally {
      ImageConversion.releasePlanarImage(planar);
      image.removeImageFromCache();
    }
  }
}
