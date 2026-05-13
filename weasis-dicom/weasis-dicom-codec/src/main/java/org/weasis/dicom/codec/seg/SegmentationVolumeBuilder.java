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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.util.DicomUtils;
import org.joml.Vector3d;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.geometry.VectorUtils;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageConversion;
import org.weasis.opencv.seg.RegionAttributes;

/**
 * Builds a <b>canonical</b> {@link SegmentationVolume} from a {@link SegSpecialElement}, using the
 * segmentation's <em>own</em> spatial grid (no reference to any image volume).
 *
 * <p>The volume axes are derived from the first frame's row/column direction cosines and their
 * cross product (sign-normalized to LPS+); the slice extent is computed from the projection of all
 * frames' Image Position Patient onto that normal; the in-plane spacing is taken from the SEG's own
 * PixelSpacing and the slice spacing from consecutive frame projections.
 *
 * <p>The resulting volume is suitable for reslicing along any plane (used by the 2D overlay
 * fallback when image and segmentation orientations diverge, by MPR overlays, and — after a grid
 * resample — by Volume Rendering).
 */
public final class SegmentationVolumeBuilder {

  private static final Logger LOGGER = LoggerFactory.getLogger(SegmentationVolumeBuilder.class);

  /** Tolerance in mm for matching positions / spacings when classifying frames as axis-aligned. */
  private static final double TOLERANCE_MM = 0.05;

  private SegmentationVolumeBuilder() {}

  /**
   * Throws a {@link CancellationException} when the current thread has been interrupted (e.g.
   * because the SwingWorker hosting the build was cancelled from the explorer's loading panel).
   * Clears the interrupt flag so the surrounding async wrapper sees a clean cancellation.
   */
  private static void checkInterrupted() {
    if (Thread.interrupted()) {
      throw new CancellationException("Segmentation volume build was cancelled");
    }
  }

  /**
   * Builds a canonical segmentation volume from a SEG element and its mask series.
   *
   * @param segElement the DICOM SEG special element containing segment metadata
   * @param segSeries the mask frames series
   * @return the populated volume, or {@code null} if the segmentation cannot be built
   */
  public static SegmentationVolume buildCanonical(
      SegSpecialElement segElement, DicomSeries segSeries) {
    if (segElement == null || segSeries == null) {
      return null;
    }
    DicomMediaIO mediaIO = segElement.getMediaReader();
    if (mediaIO == null) {
      return null;
    }
    Attributes dicom = mediaIO.getDicomObject();
    if (dicom == null) {
      return null;
    }

    Attributes sharedFG = dicom.getNestedDataset(Tag.SharedFunctionalGroupsSequence);
    Sequence perFrameSeq = dicom.getSequence(Tag.PerFrameFunctionalGroupsSequence);
    if (perFrameSeq == null || perFrameSeq.isEmpty()) {
      LOGGER.debug("SEG has no PerFrameFunctionalGroupsSequence; cannot build canonical volume");
      return null;
    }

    // First pass: collect spatial info for every frame.
    List<FrameSpatialInfo> frames = new ArrayList<>(perFrameSeq.size());
    for (Attributes frame : perFrameSeq) {
      checkInterrupted();
      frames.add(extractSpatialInfo(frame, sharedFG, dicom)); // null entries kept for indexing
    }

    FrameSpatialInfo reference = frames.stream().filter(Objects::nonNull).findFirst().orElse(null);
    if (reference == null) {
      LOGGER.debug("No SEG frame exposes complete spatial metadata");
      return null;
    }

    // Canonical axes: keep the SEG frame's own row/column directions (do NOT sign-normalize the
    // normal here). Sign-normalising and re-deriving Y as (Z × X) silently flips axisY (and
    // sometimes axisX) relative to the SEG frames' actual colDir/rowDir for many sagittal,
    // coronal and oblique SEGs. The fast axis-aligned stamping path (isAxisAligned +
    // stampAxialMask) would then write each mask frame mirrored along Y/X, producing flipped
    // overlays when the volume is resliced for the 2D viewer.
    //
    // Keeping axisX = rowDir, axisY = colDir, axisZ = normalize(rowDir × colDir) ensures every
    // axis-aligned frame stamps in the correct orientation, and reslicing on an arbitrary image
    // plane (sampleImagePlane) still works because the resulting basis is orthonormal.
    Vector3d axisX = new Vector3d(reference.rowDir);
    Vector3d axisY = new Vector3d(reference.colDir);
    Vector3d axisZ = VectorUtils.computeNormalOfSurface(axisX, axisY).normalize();

    // Compute slice projections along Z to determine the volume's Z extent and spacing.
    double minProj = Double.POSITIVE_INFINITY;
    double maxProj = Double.NEGATIVE_INFINITY;
    double[] projections = new double[frames.size()];
    Arrays.fill(projections, Double.NaN);
    int validCount = 0;
    for (int i = 0; i < frames.size(); i++) {
      FrameSpatialInfo f = frames.get(i);
      if (f == null) continue;
      double p = axisZ.dot(f.position);
      projections[i] = p;
      if (p < minProj) minProj = p;
      if (p > maxProj) maxProj = p;
      validCount++;
    }
    if (validCount == 0) {
      return null;
    }

    // Estimate slice spacing as the median of consecutive sorted projection deltas.
    double sliceSpacing = estimateSliceSpacing(projections, reference);

    // SEG in-plane spacing: DICOM PixelSpacing = [row spacing (Y), column spacing (X)].
    double colSpacing = reference.pixelSpacing[1];
    double rowSpacing = reference.pixelSpacing[0];

    // Volume dimensions: take the authoritative in-plane size from the SEG's own DICOM
    // attributes (Columns/Rows), not from the decoded PlanarImage. The decoded image can be
    // delivered transposed for some highdicom-style packed-bit binary SEGs, which silently
    // rotates the volume by 90° (invisible on square masks, visible on non-square ones).
    // Frames whose decoded dims do not match (Columns, Rows) are realigned through
    // SegMaskOrientation.normalize() before stamping, so the fast axis-aligned path is always
    // safe to use here.
    int sizeX = dicom.getInt(Tag.Columns, 0);
    int sizeY = dicom.getInt(Tag.Rows, 0);
    String segUid = dicom.getString(Tag.SOPInstanceUID);
    if (sizeX <= 0 || sizeY <= 0) {
      // Fallback to the first decoded mask only when the top-level Columns/Rows are missing.
      DicomImageElement first = segSeries.getMedia(0, null, null);
      if (first != null) {
        if (sizeX <= 0) sizeX = safeWidth(first);
        if (sizeY <= 0) sizeY = safeHeight(first);
      }
    }
    if (sizeX <= 0 || sizeY <= 0) {
      LOGGER.debug("Cannot determine SEG mask dimensions");
      return null;
    }
    int sizeZ = Math.max(1, (int) Math.round((maxProj - minProj) / sliceSpacing) + 1);

    // Volume origin: LPS position of voxel (0, 0, 0). We anchor X/Y at the reference frame's
    // origin (since all frames share the same in-plane grid by assumption) and shift Z so that
    // voxel (0,0,0) corresponds to minProj along axisZ.
    Vector3d origin = new Vector3d(reference.position);
    double refProj = axisZ.dot(reference.position);
    origin.fma(refProj - minProj, new Vector3d(axisZ).negate());

    Map<Integer, ? extends RegionAttributes> segAttrs = segElement.getSegAttributes();
    // DICOM (0062,0001) SegmentationType: BINARY / FRACTIONAL / LABELMAP.
    // For LABELMAP, each frame stores all segments at that slice with pixel value =
    // SegmentNumber, and no SegmentIdentificationSequence is present per frame.
    boolean isLabelMap = "LABELMAP".equalsIgnoreCase(dicom.getString(Tag.SegmentationType));
    // DICOM (0062,0013) SegmentsOverlap: YES / NO / UNDEFINED.
    // When the SEG explicitly declares NO, segments are guaranteed not to overlap, so we can
    // lock the volume into the compact exclusive (short) mode and skip the per-voxel
    // read+compare overlap check during stamping. LABELMAP also implies no overlap.
    boolean noOverlap = isLabelMap || "NO".equalsIgnoreCase(dicom.getString(Tag.SegmentsOverlap));
    SegmentationVolume volume =
        new SegmentationVolume(
            sizeX,
            sizeY,
            sizeZ,
            new Vector3d(colSpacing, rowSpacing, sliceSpacing),
            origin,
            axisX,
            axisY,
            axisZ,
            segAttrs,
            noOverlap);

    // Second pass: stamp each mask frame into the canonical grid.
    StampStats stats;
    try {
      stats =
          stampAllFrames(
              volume,
              segSeries,
              perFrameSeq,
              frames,
              sizeX,
              sizeY,
              sizeZ,
              origin,
              axisX,
              axisY,
              axisZ,
              colSpacing,
              rowSpacing,
              sliceSpacing,
              minProj,
              isLabelMap,
              segAttrs,
              segUid);
    } catch (CancellationException ce) {
      // Build was interrupted (user clicked the cancel button on the loading panel). Release
      // the partially-stamped volume and propagate the cancellation up to the async wrapper so
      // the future completes with CancellationException without finishing the heavy loop.
      LOGGER.debug("Canonical SEG volume build cancelled");
      volume.removeData();
      throw ce;
    }

    if (stats.total == 0) {
      LOGGER.debug("No SEG frame could be stamped into the canonical volume");
      volume.removeData();
      return null;
    }
    LOGGER.info(
        "Built canonical SegmentationVolume {}x{}x{} (storage: {}, stamped: {} fast + {} transform)",
        sizeX,
        sizeY,
        sizeZ,
        volume.isShortMode() ? "short" : "byte",
        stats.fast,
        stats.transform);
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(
          "  first decoded mask: {}x{}, axisX={}, axisY={}, axisZ={}, ps=[{}, {}, {}], origin={}",
          stats.firstW,
          stats.firstH,
          axisX,
          axisY,
          axisZ,
          colSpacing,
          rowSpacing,
          sliceSpacing,
          origin);
    }
    return volume;
  }

  /** Aggregated counts and first-frame size collected during the stamping loop. */
  private static final class StampStats {
    int total;
    int fast;
    int transform;
    int firstW = -1;
    int firstH = -1;
  }

  private static StampStats stampAllFrames(
      SegmentationVolume volume,
      DicomSeries segSeries,
      Sequence perFrameSeq,
      List<FrameSpatialInfo> frames,
      int sizeX,
      int sizeY,
      int sizeZ,
      Vector3d origin,
      Vector3d axisX,
      Vector3d axisY,
      Vector3d axisZ,
      double colSpacing,
      double rowSpacing,
      double sliceSpacing,
      double minProj,
      boolean isLabelMap,
      Map<Integer, ? extends RegionAttributes> segAttrs,
      String segUid) {
    StampStats stats = new StampStats();
    for (int i = 0; i < frames.size(); i++) {
      // Per-frame interrupt check: each iteration can decode a multi-MB DICOM mask and stamp
      // it into the volume, so this is the natural cancellation point. Throwing
      // CancellationException short-circuits the loop and lets the caller release the partially
      // built volume.
      checkInterrupted();
      FrameSpatialInfo info = frames.get(i);
      if (info == null) continue;
      Attributes frame = perFrameSeq.get(i);
      List<Integer> declaredSegs = isLabelMap ? null : resolveSegmentNumber(frame, segAttrs);
      if (!isLabelMap && declaredSegs == null) continue;

      DicomImageElement maskElement = segSeries.getMedia(i, null, null);
      if (maskElement == null) continue;
      stampFrame(
          volume,
          maskElement,
          info,
          declaredSegs,
          isLabelMap,
          segAttrs,
          sizeX,
          sizeY,
          sizeZ,
          origin,
          axisX,
          axisY,
          axisZ,
          colSpacing,
          rowSpacing,
          sliceSpacing,
          minProj,
          segUid,
          stats);
    }
    return stats;
  }

  /** Resolves the segment number for a non-LABELMAP frame; null when missing or unknown. */
  private static List<Integer> resolveSegmentNumber(
      Attributes frame, Map<Integer, ? extends RegionAttributes> segAttrs) {
    Attributes segIdSeq = frame.getNestedDataset(Tag.SegmentIdentificationSequence);
    if (segIdSeq == null) return null;
    Integer segNum =
        DicomUtils.getIntegerFromDicomElement(segIdSeq, Tag.ReferencedSegmentNumber, null);
    return (segNum != null && segAttrs.containsKey(segNum)) ? List.of(segNum) : null;
  }

  private static void stampFrame(
      SegmentationVolume volume,
      DicomImageElement maskElement,
      FrameSpatialInfo info,
      List<Integer> declaredSegs,
      boolean isLabelMap,
      Map<Integer, ? extends RegionAttributes> segAttrs,
      int sizeX,
      int sizeY,
      int sizeZ,
      Vector3d origin,
      Vector3d axisX,
      Vector3d axisY,
      Vector3d axisZ,
      double colSpacing,
      double rowSpacing,
      double sliceSpacing,
      double minProj,
      String segUid,
      StampStats stats) {
    PlanarImage rawMask = maskElement.getImage();
    if (rawMask == null || rawMask.width() <= 0 || rawMask.height() <= 0) {
      if (rawMask != null) ImageConversion.releasePlanarImage(rawMask);
      maskElement.removeImageFromCache();
      return;
    }
    // Re-align decoded mask to the SEG's declared (Columns, Rows) — handles the highdicom
    // packed-bit binary SEG case where dcm4che delivers a non-square frame transposed.
    PlanarImage maskImage = SegMaskOrientation.normalize(rawMask, sizeX, sizeY, segUid);
    if (maskImage == null) {
      ImageConversion.releasePlanarImage(rawMask);
      maskElement.removeImageFromCache();
      return;
    }
    boolean transposed = maskImage != rawMask;

    try {
      if (stats.firstW < 0) {
        stats.firstW = maskImage.width();
        stats.firstH = maskImage.height();
      }
      boolean dimsMatch = maskImage.width() == sizeX && maskImage.height() == sizeY;
      boolean axisAligned =
          dimsMatch
              && isAxisAligned(
                  info, origin, axisX, axisY, colSpacing, rowSpacing, sizeX, sizeY, maskImage);

      List<Integer> resolvedSegs =
          isLabelMap ? collectLabelMapSegments(maskImage, segAttrs) : declaredSegs;
      if (resolvedSegs.isEmpty()) return;

      for (Integer segNum : resolvedSegs) {
        PlanarImage stampMask = isLabelMap ? extractLabelMask(maskImage, segNum) : maskImage;
        try {
          if (axisAligned) {
            int sliceZ = (int) Math.round((axisZ.dot(info.position) - minProj) / sliceSpacing);
            if (sliceZ >= 0 && sliceZ < sizeZ) {
              volume.stampAxialMask(stampMask, sliceZ, segNum);
              stats.total++;
              stats.fast++;
            }
          } else {
            // Non-axis-aligned (oblique IOP / spacing mismatch / off-grid). Use per-pixel
            // transform.
            volume.stampMaskWithTransform(
                stampMask, info.position, info.rowDir, info.colDir, info.pixelSpacing, segNum);
            stats.total++;
            stats.transform++;
          }
        } finally {
          if (isLabelMap) {
            stampMask.release();
          }
        }
      }
    } finally {
      if (transposed) {
        maskImage.release();
      }
      ImageConversion.releasePlanarImage(rawMask);
      maskElement.removeImageFromCache();
    }
  }

  private static int safeWidth(DicomImageElement el) {
    return safeDim(el, true);
  }

  private static int safeHeight(DicomImageElement el) {
    return safeDim(el, false);
  }

  private static int safeDim(DicomImageElement el, boolean width) {
    PlanarImage img = el.getImage();
    int v = img == null ? 0 : (width ? img.width() : img.height());
    if (img != null) ImageConversion.releasePlanarImage(img);
    el.removeImageFromCache();
    return v;
  }

  /** Median of consecutive sorted projection deltas; falls back to slice thickness or 1 mm. */
  private static double estimateSliceSpacing(double[] projections, FrameSpatialInfo reference) {
    double[] sorted = Arrays.stream(projections).filter(d -> !Double.isNaN(d)).sorted().toArray();
    if (sorted.length >= 2) {
      double[] deltas = new double[sorted.length - 1];
      int n = 0;
      for (int i = 1; i < sorted.length; i++) {
        double d = Math.abs(sorted[i] - sorted[i - 1]);
        if (d > 1e-6) deltas[n++] = d;
      }
      if (n > 0) {
        double[] trimmed = Arrays.copyOf(deltas, n);
        Arrays.sort(trimmed);
        return trimmed[trimmed.length / 2];
      }
    }
    return reference.sliceThickness > 0 ? reference.sliceThickness : 1.0;
  }

  private static boolean isAxisAligned(
      FrameSpatialInfo info,
      Vector3d origin,
      Vector3d axisX,
      Vector3d axisY,
      double colSpacing,
      double rowSpacing,
      int sizeX,
      int sizeY,
      PlanarImage mask) {
    if (mask.width() != sizeX || mask.height() != sizeY) return false;
    // Require POSITIVE alignment (not just |dot|=1): an antiparallel rowDir/colDir frame would
    // otherwise be stamped mirrored by stampAxialMask, since that path writes mask pixel (mx,my)
    // straight to voxel (mx,my,sliceZ) without any axis-sign correction. Letting such frames fall
    // through to stampMaskWithTransform keeps the geometry correct.
    if (info.rowDir.dot(axisX) < 1.0 - 0.001) return false;
    if (info.colDir.dot(axisY) < 1.0 - 0.001) return false;
    if (Math.abs(info.pixelSpacing[1] - colSpacing) > TOLERANCE_MM) return false;
    if (Math.abs(info.pixelSpacing[0] - rowSpacing) > TOLERANCE_MM) return false;
    Vector3d offset = new Vector3d(info.position).sub(origin);
    if (Math.abs(offset.dot(axisX)) > TOLERANCE_MM) return false;
    return Math.abs(offset.dot(axisY)) <= TOLERANCE_MM;
  }

  // ---- Spatial info extraction ----

  private static FrameSpatialInfo extractSpatialInfo(
      Attributes frame, Attributes sharedFG, Attributes dicom) {
    double[] ipp = nested(frame, Tag.PlanePositionSequence, Tag.ImagePositionPatient);
    if (ipp == null) ipp = nested(sharedFG, Tag.PlanePositionSequence, Tag.ImagePositionPatient);
    if (ipp == null)
      ipp = DicomUtils.getDoubleArrayFromDicomElement(dicom, Tag.ImagePositionPatient, null);
    if (ipp == null || ipp.length != 3) return null;

    double[] iop = nested(frame, Tag.PlaneOrientationSequence, Tag.ImageOrientationPatient);
    if (iop == null)
      iop = nested(sharedFG, Tag.PlaneOrientationSequence, Tag.ImageOrientationPatient);
    if (iop == null)
      iop = DicomUtils.getDoubleArrayFromDicomElement(dicom, Tag.ImageOrientationPatient, null);
    if (iop == null || iop.length != 6) return null;

    double[] ps = nested(frame, Tag.PixelMeasuresSequence, Tag.PixelSpacing);
    if (ps == null) ps = nested(sharedFG, Tag.PixelMeasuresSequence, Tag.PixelSpacing);
    if (ps == null) ps = DicomUtils.getDoubleArrayFromDicomElement(dicom, Tag.PixelSpacing, null);
    if (ps == null || ps.length < 2) ps = new double[] {1.0, 1.0};

    Double thickness = null;
    Attributes pms = frame == null ? null : frame.getNestedDataset(Tag.PixelMeasuresSequence);
    if (pms == null && sharedFG != null) pms = sharedFG.getNestedDataset(Tag.PixelMeasuresSequence);
    if (pms != null) {
      thickness = DicomUtils.getDoubleFromDicomElement(pms, Tag.SliceThickness, null);
    }

    return new FrameSpatialInfo(
        new Vector3d(ipp),
        new Vector3d(iop[0], iop[1], iop[2]),
        new Vector3d(iop[3], iop[4], iop[5]),
        ps,
        thickness == null ? 0.0 : thickness);
  }

  private static double[] nested(Attributes attrs, int seqTag, int valueTag) {
    if (attrs == null) return null;
    Attributes nested = attrs.getNestedDataset(seqTag);
    if (nested == null) return null;
    return DicomUtils.getDoubleArrayFromDicomElement(nested, valueTag, null);
  }

  /**
   * Sorted distinct non-zero pixel values in {@code maskImage} that match a known segment number.
   */
  private static List<Integer> collectLabelMapSegments(
      PlanarImage maskImage, Map<Integer, ? extends RegionAttributes> segAttrs) {
    int[] labels = LabelMapScanner.sortedDistinctLabels(maskImage.toMat(), segAttrs::containsKey);
    return labels.length == 0 ? List.of() : Arrays.stream(labels).boxed().toList();
  }

  /** CV_8UC1 binary mask (255/0) thresholded on equality to {@code segNum}. Caller owns it. */
  private static PlanarImage extractLabelMask(PlanarImage labelMap, int segNum) {
    Mat src = labelMap.toMat();
    ImageCV out = new ImageCV();
    Core.compare(src, new Scalar(segNum), out, Core.CMP_EQ);
    return out;
  }

  /** Holds the spatial metadata extracted from a single DICOM SEG frame. */
  private record FrameSpatialInfo( // NOSONAR private DTO, never compared
      Vector3d position,
      Vector3d rowDir,
      Vector3d colDir,
      double[] pixelSpacing,
      double sliceThickness) {}
}
