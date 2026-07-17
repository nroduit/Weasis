/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.fusion;

import java.util.Set;
import org.dcm4che3.data.Tag;
import org.joml.Vector3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeries.MEDIA_POSITION;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.SortSeriesStack;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;

/**
 * Decides whether a candidate series can be fused (registration-free) onto a base series.
 *
 * <p>Fusion here is a pure geometric overlay driven by each image's DICOM spatial metadata, so it
 * is only valid under strict conditions:
 *
 * <ul>
 *   <li><b>Modality pairing</b> — a functional/parametric overlay ({@code PT}, {@code NM}) on an
 *       anatomical base ({@code CT}, {@code MR}). This is the clinical-usefulness gate.
 *   <li><b>Same study</b> — both series share a non-empty {@code StudyInstanceUID}. A defensive
 *       guard: callers already pair series within one study, but it keeps spatial overlap from
 *       matching unrelated volumes if the check is ever reused in a wider scope.
 *   <li><b>Valid volume geometry</b> — both are cross-sectional volumes (≥2 slices with Image
 *       Position/Orientation Patient and Pixel Spacing).
 *   <li><b>Same patient coordinate system</b> — either the {@code FrameOfReferenceUID} matches, or
 *       (when it differs, as commonly happens for separately reconstructed PET/CT) the two volumes
 *       overlap in patient space. The latter is what other viewers rely on to fuse same-session
 *       PET/CT whose recorded Frame of Reference UIDs differ.
 * </ul>
 */
public final class FusionCompatibility {

  private static final Logger LOGGER = LoggerFactory.getLogger(FusionCompatibility.class);

  /** Functional/parametric modalities suitable as a fusion overlay. */
  private static final Set<String> OVERLAY_MODALITIES = Set.of("PT", "NM"); // NON-NLS

  /** Anatomical modalities suitable as a fusion base. */
  private static final Set<String> ANATOMICAL_MODALITIES = Set.of("CT", "MR"); // NON-NLS

  private FusionCompatibility() {}

  /** Returns {@code true} if {@code candidate} can be fused onto {@code base}. */
  public static boolean isCompatible(
      MediaSeries<DicomImageElement> base, MediaSeries<DicomImageElement> candidate) {
    if (base == null || candidate == null || base == candidate) {
      return false;
    }
    // Clinical pairing: functional/parametric overlay on an anatomical base.
    String baseMod = modality(base);
    String candMod = modality(candidate);
    if (!ANATOMICAL_MODALITIES.contains(baseMod) || !OVERLAY_MODALITIES.contains(candMod)) {
      return false;
    }
    String baseStudy = studyInstanceUid(base);
    if (baseStudy == null || !baseStudy.equals(studyInstanceUid(candidate))) {
      LOGGER.debug(
          "Fusion rejected ({}/{}): different or missing StudyInstanceUID", baseMod, candMod);
      return false;
    }
    // Both must be cross-sectional volumes with valid geometry.
    double[] baseBox = boundingBoxLps(base);
    double[] candidateBox = boundingBoxLps(candidate);
    if (baseBox == null || candidateBox == null) {
      LOGGER.debug("Fusion rejected ({}/{}): a series lacks volumetric geometry", baseMod, candMod);
      return false;
    }
    // A matching FrameOfReferenceUID guarantees a shared patient coordinate system. When it differs
    // (common for separately reconstructed PET/CT) fall back to spatial overlap, which is what
    // other viewers use to fuse same-session data with mismatched Frame of Reference UIDs.
    String baseFor = frameOfReference(base);
    boolean sameFrame = baseFor != null && baseFor.equals(frameOfReference(candidate));
    if (sameFrame || overlaps(baseBox, candidateBox)) {
      return true;
    }
    LOGGER.debug(
        "Fusion rejected ({}/{}): different FrameOfReferenceUID and no spatial overlap",
        baseMod,
        candMod);
    return false;
  }

  private static String modality(MediaSeries<DicomImageElement> series) {
    return TagD.getTagValue(series, Tag.Modality, String.class);
  }

  private static String frameOfReference(MediaSeries<DicomImageElement> series) {
    return TagD.getTagValue(series, Tag.FrameOfReferenceUID, String.class);
  }

  private static String studyInstanceUid(MediaSeries<DicomImageElement> series) {
    DicomImageElement first =
        series.getMedia(MEDIA_POSITION.FIRST, null, SortSeriesStack.slicePosition);
    if (first == null) {
      return null;
    }
    return TagD.getTagValue(first, Tag.StudyInstanceUID, String.class);
  }

  /**
   * Axis-aligned bounding box of the series volume in patient (LPS) mm as {@code
   * {minX,minY,minZ,maxX,maxY,maxZ}}, or {@code null} when the series is not a volume with valid
   * geometry.
   */
  private static double[] boundingBoxLps(MediaSeries<DicomImageElement> series) {
    if (series.size(null) < 2) {
      return null;
    }
    DicomImageElement first =
        series.getMedia(MEDIA_POSITION.FIRST, null, SortSeriesStack.slicePosition);
    DicomImageElement last =
        series.getMedia(MEDIA_POSITION.LAST, null, SortSeriesStack.slicePosition);
    if (first == null || last == null) {
      return null;
    }
    GeometryOfSlice g1 = first.getSliceGeometry();
    GeometryOfSlice g2 = last.getSliceGeometry();
    if (g1 == null || g2 == null) {
      return null;
    }
    double[] box = {
      Double.MAX_VALUE,
      Double.MAX_VALUE,
      Double.MAX_VALUE,
      -Double.MAX_VALUE,
      -Double.MAX_VALUE,
      -Double.MAX_VALUE
    };
    accumulateCorners(g1, box);
    accumulateCorners(g2, box);
    return box;
  }

  /** Expands {@code box} to include the four in-plane corners of the given slice. */
  private static void accumulateCorners(GeometryOfSlice g, double[] box) {
    Vector3d tlhc = g.getTLHC();
    Vector3d row = g.getRow();
    Vector3d col = g.getColumn();
    Vector3d spacing = g.getVoxelSpacing();
    Vector3d dim = g.getDimensions();
    if (tlhc == null || row == null || col == null || spacing == null || dim == null) {
      return;
    }
    double widthMm = spacing.x * dim.y; // extent along the row direction (columns)
    double heightMm = spacing.y * dim.x; // extent along the column direction (rows)
    for (int a = 0; a <= 1; a++) {
      for (int b = 0; b <= 1; b++) {
        double x = tlhc.x + row.x * widthMm * a + col.x * heightMm * b;
        double y = tlhc.y + row.y * widthMm * a + col.y * heightMm * b;
        double z = tlhc.z + row.z * widthMm * a + col.z * heightMm * b;
        box[0] = Math.min(box[0], x);
        box[1] = Math.min(box[1], y);
        box[2] = Math.min(box[2], z);
        box[3] = Math.max(box[3], x);
        box[4] = Math.max(box[4], y);
        box[5] = Math.max(box[5], z);
      }
    }
  }

  private static boolean overlaps(double[] a, double[] b) {
    return a[0] <= b[3]
        && a[3] >= b[0]
        && a[1] <= b[4]
        && a[4] >= b[1]
        && a[2] <= b[5]
        && a[5] >= b[2];
  }
}
