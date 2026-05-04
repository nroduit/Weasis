/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr;

import java.awt.Dimension;
import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.util.DicomUtils;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.joml.Vector4d;
import org.opencv.core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.seg.SegMaskOrientation;
import org.weasis.dicom.codec.seg.SegSpecialElement;
import org.weasis.dicom.codec.seg.SegmentationVolume;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageConversion;
import org.weasis.opencv.seg.RegionAttributes;

/**
 * Builds a {@link SegmentationVolume} from a {@link SegSpecialElement} and its associated {@link
 * DicomSeries} of binary mask frames.
 *
 * <p>The builder creates the segmentation volume in the <b>image volume's</b> coordinate space
 * (same origin, axes, pixel spacing, and grid dimensions) so that the same {@code
 * combinedTransform} from {@link MprAxis#getRealVolumeTransformation} can be used for both the
 * image volume reslicing and the segmentation overlay reslicing.
 *
 * <p>Each binary mask frame is splatted into the volume using the same trilinear weighted-splat
 * pipeline as {@link Volume#copyFromAnyOrientationWithRectification} (see {@link SplatContext}).
 * For every non-zero source pixel, value {@code 1.0} is distributed onto the 8 surrounding voxel
 * corners weighted by trilinear coefficients in the image-volume voxel grid. After all frames of a
 * given segment have been accumulated, {@link SplatContext#normalizeBinary} converts each voxel
 * whose averaged contribution is at least the {@value #BINARY_THRESHOLD} majority threshold into a
 * label via {@link SegmentationVolume#addLabel}. This eliminates the patient-orientation artefacts
 * that the previous nearest-neighbor implementation produced when SEG frames were not axis-aligned
 * with the image volume.
 */
public final class SegVolumeBuilder {

  private static final Logger LOGGER = LoggerFactory.getLogger(SegVolumeBuilder.class);

  /** Majority-vote threshold used by {@link SplatContext#normalizeBinary}. */
  private static final float BINARY_THRESHOLD = 0.5f;

  private SegVolumeBuilder() {}

  /**
   * Returns a usable canonical {@link SegmentationVolume} for {@code segElement} when one can be
   * obtained without blocking the EDT. Off the EDT this falls through to {@link
   * SegSpecialElement#getOrBuildSegmentationVolume()} which may build it synchronously; on the EDT
   * we only peek at the existing soft-referenced cache so painting / interaction is never stalled
   * by a heavy canonical build.
   */
  private static SegmentationVolume tryGetCanonicalVolume(SegSpecialElement segElement) {
    if (EventQueue.isDispatchThread()) {
      return segElement.peekCanonicalSegmentationVolume();
    }
    return segElement.getOrBuildSegmentationVolume();
  }

  /**
   * Builds a {@link SegmentationVolume} from the given segmentation element, using the image
   * volume's coordinate space.
   *
   * @param segElement the DICOM SEG special element containing segment metadata
   * @param segSeries the DicomSeries holding the binary mask frames of the SEG object
   * @param imageVolume the image volume whose coordinate space the SegmentationVolume will share
   * @return the populated SegmentationVolume, or null if the segmentation cannot be built
   */
  public static SegmentationVolume build(
      SegSpecialElement segElement, DicomSeries segSeries, Volume<?, ?> imageVolume) {
    if (segElement == null || segSeries == null || imageVolume == null) {
      return null;
    }

    Attributes dicom = segElement.getMediaReader().getDicomObject();
    if (dicom == null) {
      return null;
    }

    // Use the image volume's coordinate space for the seg volume
    Vector3i volSize = imageVolume.getSize();
    Vector3d volOrigin = imageVolume.getVolumeOrigin();
    Vector3d volAxisX = imageVolume.getVolumeAxisX();
    Vector3d volAxisY = imageVolume.getVolumeAxisY();
    Vector3d volAxisZ = imageVolume.getVolumeAxisZ();
    Vector3d pixelSpacing = new Vector3d(imageVolume.getPixelRatio());

    // The image volume's voxel grid is filled by Volume.setValue() using a slightly different
    // convention than the textbook  voxel = (LPS - origin) · axes / spacing  formula:
    //   - For rectified non-axial volumes, an extra Z-flip (size.z - vz) is applied;
    //   - The translation actually used is  firstTLHC - volumeOrigin  (NOT volumeOrigin itself);
    //   - The volume axes are LPS-canonical (1,0,0)/(0,1,0)/(0,0,-1) regardless of the original
    //     acquisition plane.
    // To put a SEG voxel on top of the *same* image voxel for an identical patient-space (LPS)
    // point, we MUST go through the image volume's own lpsToVoxel() mapping rather than building
    // a transform from volOrigin/axes/spacing — those three are correct as a description of the
    // grid but disagree with where setValue() actually wrote the image pixels for sagittal /
    // coronal acquisitions.
    OriginalStack stack = imageVolume.getStack();
    boolean useLpsMapping = stack != null && !imageVolume.isBasic();
    if (!useLpsMapping) {
      LOGGER.warn(
          "SegVolumeBuilder: image volume is basic (no rectification); SEG overlay falls back "
              + "to the legacy axes-projection mapping which only works when SEG and image share "
              + "the same acquisition plane.");
    }

    Map<Integer, ? extends RegionAttributes> segAttrs = segElement.getSegAttributes();

    SegmentationVolume segVolume =
        new SegmentationVolume(
            volSize.x,
            volSize.y,
            volSize.z,
            pixelSpacing,
            volOrigin,
            volAxisX,
            volAxisY,
            volAxisZ,
            segAttrs);

    // Fast path: if the SEG already has a canonical (native-grid) volume cached on the
    // SegSpecialElement, resample it onto the image-volume grid voxel-by-voxel instead of
    // re-decoding every per-frame mask. This typically runs in a fraction of the time of the
    // splat path because (a) all the mask decoding work is skipped, (b) the canonical volume
    // already merged overlapping segments and (c) the inner loop is a pure arithmetic LPS
    // lookup. Falls back to the per-frame splat below when no canonical volume is available
    // (e.g. its async build was aborted, the SoftReference was reclaimed, or the SEG is
    // fractional).
    if (useLpsMapping && !segElement.isFractionalSeg()) {
      SegmentationVolume canonical = tryGetCanonicalVolume(segElement);
      if (canonical != null && !canonical.isEmpty()) {
        long stamped = canonical.resampleInto(segVolume, imageVolume::voxelToLps);
        if (stamped > 0) {
          LOGGER.info(
              "Built image-aligned SegmentationVolume by resampling the canonical volume "
                  + "({} stamped voxels, storage: {})",
              stamped,
              segVolume.isShortMode() ? "short" : "byte");
          return segVolume;
        }
        // Empty resample (e.g. canonical and image volumes have no spatial overlap): the
        // segVolume is still pristine (no addLabel was called by resampleInto), so we can fall
        // through to the per-frame splat path with the same instance.
        LOGGER.debug(
            "Canonical→image-aligned resample produced no labels; falling back to per-frame splat");
      }
    }

    // Get shared functional groups for fallback spatial metadata
    Attributes sharedFG = dicom.getNestedDataset(Tag.SharedFunctionalGroupsSequence);

    // SEG's declared (Columns, Rows) and SOP UID — needed to detect and fix the highdicom
    // packed-bit binary SEG transposition issue (decoded mask delivered with swapped dims).
    int segCols = dicom.getInt(Tag.Columns, 0);
    int segRows = dicom.getInt(Tag.Rows, 0);
    String segUid = dicom.getString(Tag.SOPInstanceUID);

    Sequence perFrameSeq = dicom.getSequence(Tag.PerFrameFunctionalGroupsSequence);
    if (perFrameSeq == null || perFrameSeq.isEmpty()) {
      LOGGER.warn("No PerFrameFunctionalGroupsSequence found in SEG object");
      segVolume.removeData();
      return null;
    }

    // Group frames by segment number while collecting their spatial metadata once.
    Map<Integer, List<FrameRef>> framesBySegment = new LinkedHashMap<>();
    int frameIndex = 0;
    int totalFrames = perFrameSeq.size();
    for (Attributes frame : perFrameSeq) {
      int idx = frameIndex++;
      Attributes segIdSeq = frame.getNestedDataset(Tag.SegmentIdentificationSequence);
      if (segIdSeq == null) continue;
      Integer segmentNumber =
          DicomUtils.getIntegerFromDicomElement(segIdSeq, Tag.ReferencedSegmentNumber, null);
      if (segmentNumber == null || !segAttrs.containsKey(segmentNumber)) continue;

      FrameSpatialInfo spatial = getFrameSpatialInfo(frame, sharedFG, dicom);
      if (spatial == null) {
        LOGGER.debug("Cannot determine spatial info for SEG frame {}", idx + 1);
        continue;
      }
      framesBySegment
          .computeIfAbsent(segmentNumber, _ -> new ArrayList<>())
          .add(new FrameRef(idx, spatial));
    }

    if (framesBySegment.isEmpty()) {
      LOGGER.warn("No usable SEG frames found");
      segVolume.removeData();
      return null;
    }

    long totalVoxels = (long) volSize.x * volSize.y * volSize.z;
    int stampedFrames = 0;

    // Process one segment at a time, sharing accumulators across all of its frames so that
    // overlapping trilinear contributions are averaged before binarisation.
    for (var entry : framesBySegment.entrySet()) {
      final int segmentNumber = entry.getKey();
      List<FrameRef> frames = entry.getValue();

      try (SplatContext sharedCtx = SplatContext.create(true, totalVoxels)) {
        boolean weighted = sharedCtx.isWeighted();
        for (FrameRef ref : frames) {
          DicomImageElement maskElement = segSeries.getMedia(ref.frameIndex, null, null);
          if (maskElement == null) continue;

          PlanarImage rawMask = maskElement.getImage();
          if (rawMask == null || rawMask.width() <= 0 || rawMask.height() <= 0) {
            if (rawMask != null) ImageConversion.releasePlanarImage(rawMask);
            maskElement.removeImageFromCache();
            continue;
          }
          // Re-align decoded mask to the SEG's declared (Columns, Rows). Required for some
          // highdicom packed-bit binary SEGs whose non-square frames are delivered transposed
          // by the dcm4che image reader; without this the splat / nearest-neighbor stamping
          // below would write each frame rotated by 90° in the volume.
          PlanarImage maskImage = SegMaskOrientation.normalize(rawMask, segCols, segRows, segUid);
          if (maskImage == null) {
            ImageConversion.releasePlanarImage(rawMask);
            maskElement.removeImageFromCache();
            continue;
          }
          boolean transposed = maskImage != rawMask;

          Matrix4d transform =
              useLpsMapping
                  ? buildFrameToVoxelTransformViaLps(ref.spatial, imageVolume)
                  : buildFrameToVoxelTransform(
                      ref.spatial, volOrigin, volAxisX, volAxisY, volAxisZ, pixelSpacing);

          if (weighted) {
            SplatContext sliceCtx =
                sharedCtx.withTransformAndDim(
                    transform, new Dimension(maskImage.width(), maskImage.height()));
            splatBinaryFrame(maskImage, sliceCtx, volSize);
          } else {
            // Fallback when accumulators could not be allocated: nearest-neighbor stamping.
            stampNearestNeighbor(maskImage, transform, volSize, segVolume, segmentNumber);
          }
          stampedFrames++;

          if (transposed) {
            maskImage.release();
          }
          ImageConversion.releasePlanarImage(rawMask);
          maskElement.removeImageFromCache();
        }

        if (weighted) {
          sharedCtx.normalizeBinary(
              volSize, BINARY_THRESHOLD, (x, y, z) -> segVolume.addLabel(x, y, z, segmentNumber));
        }
      }
    }

    if (stampedFrames == 0) {
      LOGGER.warn("No segmentation frames could be stamped into the volume");
      segVolume.removeData();
      return null;
    }

    LOGGER.info(
        "Built SegmentationVolume with {} stamped frames out of {} total frames (storage: {})",
        stampedFrames,
        totalFrames,
        segVolume.isShortMode() ? "short" : "byte");
    return segVolume;
  }

  /**
   * Builds the affine transform that maps a SEG frame pixel {@code (mx, my, 0, 1)} to its
   * floating-point voxel coordinate {@code (vx, vy, vz, 1)} in the image volume's grid by
   * <b>composing</b> the SEG-pixel→LPS map with the image volume's own {@link Volume#lpsToVoxel}
   * mapping. This guarantees the SEG voxel ends up at the same image voxel as the underlying MR /
   * CT pixel for the same patient-space LPS point — even when the image acquisition plane and the
   * SEG acquisition plane are perpendicular (e.g. axial highdicom SEG over a sagittal MR series).
   *
   * <p>Both legs of the composition are affine, so the result is a single 4×4 matrix that the
   * splatting / nearest-neighbor stamping loops can apply per source pixel without any further
   * branching.
   *
   * <p>SEG-pixel→LPS: {@code LPS = framePos + mx · rowDir · colSpacing + my · colDir · rowSpacing}.
   *
   * <p>LPS→voxel: see {@link Volume#lpsToVoxel}. For rectified non-axial volumes this is {@code
   * (vx, vy, size.z − vz)} with {@code vi = (LPS_i − translation_i) / pixelRatio_i} and {@code
   * translation = firstTLHC − volumeOrigin}; for rectified axial it is {@code (vx, vy, −vz)}.
   */
  private static Matrix4d buildFrameToVoxelTransformViaLps(
      FrameSpatialInfo spatial, Volume<?, ?> imageVolume) {
    // DICOM PixelSpacing = [row spacing, column spacing] = [along colDir, along rowDir]
    double colSpacing = spatial.pixelSpacing[1]; // mm per column step (along rowDir)
    double rowSpacing = spatial.pixelSpacing[0]; // mm per row step (along colDir)

    // Sample three LPS points (origin, +mx, +my) and convert each through the image volume's
    // lpsToVoxel(); the resulting voxel-space points define a unique affine map (mx, my) → voxel.
    Vector3d lps0 = new Vector3d(spatial.position);
    Vector3d lpsRow =
        new Vector3d(spatial.rowDir).mul(colSpacing).add(spatial.position); // pixel (1, 0)
    Vector3d lpsCol =
        new Vector3d(spatial.colDir).mul(rowSpacing).add(spatial.position); // pixel (0, 1)

    Vector3d v0 = imageVolume.lpsToVoxel(lps0, new Vector3d());
    Vector3d vRow = imageVolume.lpsToVoxel(lpsRow, new Vector3d());
    Vector3d vCol = imageVolume.lpsToVoxel(lpsCol, new Vector3d());

    // Per-mx contribution = vRow - v0, per-my contribution = vCol - v0, translation = v0.
    double m00 = vRow.x - v0.x;
    double m01 = vRow.y - v0.y;
    double m02 = vRow.z - v0.z;

    double m10 = vCol.x - v0.x;
    double m11 = vCol.y - v0.y;
    double m12 = vCol.z - v0.z;

    Matrix4d m = new Matrix4d();
    m.set(0, 0, m00);
    m.set(0, 1, m01);
    m.set(0, 2, m02);
    m.set(1, 0, m10);
    m.set(1, 1, m11);
    m.set(1, 2, m12);
    m.set(2, 0, 0.0);
    m.set(2, 1, 0.0);
    m.set(2, 2, 0.0);
    m.set(3, 0, v0.x);
    m.set(3, 1, v0.y);
    m.set(3, 2, v0.z);
    return m;
  }

  /**
   * Builds the affine transform that maps a SEG frame pixel {@code (mx, my, 0, 1)} to its
   * floating-point voxel coordinate {@code (vx, vy, vz, 1)} in the image volume's grid.
   *
   * <p>For a SEG pixel, the LPS position is:
   *
   * <pre>
   * LPS = framePos + mx · rowDir · colSpacing + my · colDir · rowSpacing
   * </pre>
   *
   * <p>and the voxel index along each volume axis is:
   *
   * <pre>
   * v_i = ((LPS − volumeOrigin) · volumeAxis_i) / pixelSpacing_i
   * </pre>
   *
   * <p><b>Note:</b> this textbook formula only matches the image volume's actual writes when the
   * volume is in <em>basic</em> (non-rectified) mode and the SEG plane is parallel to the image
   * plane. Prefer {@link #buildFrameToVoxelTransformViaLps} for rectified volumes.
   */
  private static Matrix4d buildFrameToVoxelTransform(
      FrameSpatialInfo spatial,
      Vector3d volOrigin,
      Vector3d volAxisX,
      Vector3d volAxisY,
      Vector3d volAxisZ,
      Vector3d pixelSpacing) {
    // DICOM PixelSpacing is [row spacing, column spacing] = [along colDir, along rowDir]
    double colSpacing = spatial.pixelSpacing[1]; // mm per column step (along rowDir)
    double rowSpacing = spatial.pixelSpacing[0]; // mm per row step (along colDir)

    Vector3d delta = new Vector3d(spatial.position).sub(volOrigin);

    // Per-mx contribution (column 0 of the matrix in JOML's set(col, row, value) convention)
    double m00 = (spatial.rowDir.dot(volAxisX) * colSpacing) / pixelSpacing.x;
    double m01 = (spatial.rowDir.dot(volAxisY) * colSpacing) / pixelSpacing.y;
    double m02 = (spatial.rowDir.dot(volAxisZ) * colSpacing) / pixelSpacing.z;

    // Per-my contribution (column 1)
    double m10 = (spatial.colDir.dot(volAxisX) * rowSpacing) / pixelSpacing.x;
    double m11 = (spatial.colDir.dot(volAxisY) * rowSpacing) / pixelSpacing.y;
    double m12 = (spatial.colDir.dot(volAxisZ) * rowSpacing) / pixelSpacing.z;

    // Translation (column 3): voxel index of pixel (0, 0)
    double m30 = delta.dot(volAxisX) / pixelSpacing.x;
    double m31 = delta.dot(volAxisY) / pixelSpacing.y;
    double m32 = delta.dot(volAxisZ) / pixelSpacing.z;

    Matrix4d m = new Matrix4d();
    m.set(0, 0, m00);
    m.set(0, 1, m01);
    m.set(0, 2, m02);
    m.set(1, 0, m10);
    m.set(1, 1, m11);
    m.set(1, 2, m12);
    // Column 2 stays (0,0,0,0) — there is no Z component in the source pixel coordinate.
    m.set(2, 0, 0.0);
    m.set(2, 1, 0.0);
    m.set(2, 2, 0.0);
    m.set(3, 0, m30);
    m.set(3, 1, m31);
    m.set(3, 2, m32);
    return m;
  }

  /**
   * Splats a binary SEG mask frame into the shared accumulator: every non-zero source pixel
   * contributes value {@code 1.0} weighted by the trilinear coefficients of the 8 surrounding voxel
   * corners.
   */
  private static void splatBinaryFrame(PlanarImage mask, SplatContext sliceCtx, Vector3i volSize) {
    Mat mat = mask.toMat();
    int rows = mask.height();
    int cols = mask.width();
    int elem = (int) mat.elemSize();
    int total = rows * cols * elem;
    byte[] buf = new byte[total];
    mat.get(0, 0, buf);

    Matrix4d transform = sliceCtx.transform();
    long sliceStride = (long) volSize.x * volSize.y;
    Vector4d p = new Vector4d();

    for (int my = 0; my < rows; my++) {
      int rowOffset = my * cols * elem;
      for (int mx = 0; mx < cols; mx++) {
        if (buf[rowOffset + mx * elem] == 0) {
          continue;
        }

        p.set(mx, my, 0.0, 1.0);
        transform.transform(p);

        int x0 = (int) Math.floor(p.x);
        int y0 = (int) Math.floor(p.y);
        int z0 = (int) Math.floor(p.z);

        float fx = (float) (p.x - x0);
        float fy = (float) (p.y - y0);
        float fz = (float) (p.z - z0);

        float w000 = (1 - fx) * (1 - fy) * (1 - fz);
        float w100 = fx * (1 - fy) * (1 - fz);
        float w010 = (1 - fx) * fy * (1 - fz);
        float w110 = fx * fy * (1 - fz);
        float w001 = (1 - fx) * (1 - fy) * fz;
        float w101 = fx * (1 - fy) * fz;
        float w011 = (1 - fx) * fy * fz;
        float w111 = fx * fy * fz;

        int x1 = x0 + 1;
        int y1 = y0 + 1;
        int z1 = z0 + 1;

        accumulateCorner(sliceCtx, volSize, sliceStride, x0, y0, z0, w000);
        accumulateCorner(sliceCtx, volSize, sliceStride, x1, y0, z0, w100);
        accumulateCorner(sliceCtx, volSize, sliceStride, x0, y1, z0, w010);
        accumulateCorner(sliceCtx, volSize, sliceStride, x1, y1, z0, w110);
        accumulateCorner(sliceCtx, volSize, sliceStride, x0, y0, z1, w001);
        accumulateCorner(sliceCtx, volSize, sliceStride, x1, y0, z1, w101);
        accumulateCorner(sliceCtx, volSize, sliceStride, x0, y1, z1, w011);
        accumulateCorner(sliceCtx, volSize, sliceStride, x1, y1, z1, w111);
      }
    }
  }

  private static void accumulateCorner(
      SplatContext ctx, Vector3i size, long sliceStride, int x, int y, int z, float w) {
    if (w < SplatContext.WEIGHT_EPSILON) return;
    if (x < 0 || x >= size.x || y < 0 || y >= size.y || z < 0 || z >= size.z) return;
    long idx = (long) z * sliceStride + (long) y * size.x + x;
    ctx.accumulate(idx, 1.0f, w);
  }

  /**
   * Nearest-neighbor stamping fallback used when {@link SplatContext} could not allocate
   * accumulators. Produces the same artefacts the previous implementation did, but only kicks in
   * under heavy memory pressure.
   */
  private static void stampNearestNeighbor(
      PlanarImage mask,
      Matrix4d transform,
      Vector3i volSize,
      SegmentationVolume segVolume,
      int segmentNumber) {
    Mat mat = mask.toMat();
    int rows = mask.height();
    int cols = mask.width();
    int elem = (int) mat.elemSize();
    byte[] buf = new byte[rows * cols * elem];
    mat.get(0, 0, buf);

    Vector4d p = new Vector4d();
    for (int my = 0; my < rows; my++) {
      int rowOffset = my * cols * elem;
      for (int mx = 0; mx < cols; mx++) {
        if (buf[rowOffset + mx * elem] == 0) continue;
        p.set(mx, my, 0.0, 1.0);
        transform.transform(p);
        int ix = (int) Math.round(p.x);
        int iy = (int) Math.round(p.y);
        int iz = (int) Math.round(p.z);
        if (ix < 0 || ix >= volSize.x || iy < 0 || iy >= volSize.y || iz < 0 || iz >= volSize.z) {
          continue;
        }
        segVolume.addLabel(ix, iy, iz, segmentNumber);
      }
    }
  }

  /**
   * Extracts the spatial metadata (IPP, IOP, pixel spacing) for a single frame, with fallback to
   * shared functional groups and top-level DICOM attributes.
   */
  private static FrameSpatialInfo getFrameSpatialInfo(
      Attributes frame, Attributes sharedFG, Attributes dicom) {

    // Get Image Position Patient (IPP)
    double[] ipp = getNestedDoubleArray(frame, Tag.PlanePositionSequence, Tag.ImagePositionPatient);
    if (ipp == null && sharedFG != null) {
      ipp = getNestedDoubleArray(sharedFG, Tag.PlanePositionSequence, Tag.ImagePositionPatient);
    }
    if (ipp == null) {
      ipp = DicomUtils.getDoubleArrayFromDicomElement(dicom, Tag.ImagePositionPatient, null);
    }
    if (ipp == null || ipp.length != 3) {
      return null;
    }

    // Get Image Orientation Patient (IOP)
    double[] iop =
        getNestedDoubleArray(frame, Tag.PlaneOrientationSequence, Tag.ImageOrientationPatient);
    if (iop == null && sharedFG != null) {
      iop =
          getNestedDoubleArray(sharedFG, Tag.PlaneOrientationSequence, Tag.ImageOrientationPatient);
    }
    if (iop == null) {
      iop = DicomUtils.getDoubleArrayFromDicomElement(dicom, Tag.ImageOrientationPatient, null);
    }
    if (iop == null || iop.length != 6) {
      return null;
    }

    // Get Pixel Spacing
    double[] ps = getNestedDoubleArray(frame, Tag.PixelMeasuresSequence, Tag.PixelSpacing);
    if (ps == null && sharedFG != null) {
      ps = getNestedDoubleArray(sharedFG, Tag.PixelMeasuresSequence, Tag.PixelSpacing);
    }
    if (ps == null) {
      ps = DicomUtils.getDoubleArrayFromDicomElement(dicom, Tag.PixelSpacing, null);
    }
    if (ps == null || ps.length < 2) {
      ps = new double[] {1.0, 1.0};
    }

    return new FrameSpatialInfo(
        new Vector3d(ipp),
        new Vector3d(iop[0], iop[1], iop[2]),
        new Vector3d(iop[3], iop[4], iop[5]),
        ps);
  }

  private static double[] getNestedDoubleArray(Attributes attrs, int seqTag, int valueTag) {
    Attributes nested = attrs.getNestedDataset(seqTag);
    if (nested != null) {
      return DicomUtils.getDoubleArrayFromDicomElement(nested, valueTag, null);
    }
    return null;
  }

  /** Holds the spatial metadata extracted from a single DICOM SEG frame. */
  private record FrameSpatialInfo(
      Vector3d position, Vector3d rowDir, Vector3d colDir, double[] pixelSpacing) {}

  /** Pairs a frame's index in the SEG series with its parsed spatial metadata. */
  private record FrameRef(int frameIndex, FrameSpatialInfo spatial) {}
}
