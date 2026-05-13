/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import java.awt.*;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import org.dcm4che3.data.Tag;
import org.joml.Vector3d;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.geometry.ImageOrientation;
import org.weasis.dicom.codec.seg.LazyContourLoader;
import org.weasis.dicom.codec.seg.SegmentationVolume;
import org.weasis.dicom.codec.seg.VolumeSliceContourLoader;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.opencv.seg.RegionAttributes;

public interface SpecialElementRegion {

  /** Tolerance in mm for matching slice positions in the positionMap (parallel planes). */
  double POSITION_TOLERANCE = 0.07;

  /**
   * Larger tolerance in mm used when the image plane is only nearly parallel to the segmentation
   * plane (oblique mismatch). Roughly accounts for the position drift introduced by a small angular
   * deviation across the image extent.
   */
  double POSITION_TOLERANCE_OBLIQUE = 2.5;

  /**
   * Cosine threshold above which two unit normals are considered to describe the same plane (very
   * strict — sub-degree). Used to decide whether positionMap keys are directly comparable.
   */
  double ORIENTATION_COSINE_STRICT = 0.9999;

  /**
   * Cosine threshold above which two unit normals are considered nearly parallel. Below this value
   * the planes are too different and the positionMap lookup is skipped (fall back to refMap, or —
   * in phase 2 — to a resampled segmentation volume).
   */
  double ORIENTATION_COSINE_OBLIQUE = 0.95;

  boolean isVisible();

  void setVisible(boolean visible);

  /**
   * Returns {@code true} when the region's contour data is fully built and safe to query. The
   * default implementation returns {@code true}; segmentations that build their contours
   * asynchronously must override this to return {@code false} during the build phase so 2D/MPR/3D
   * consumers do not race the loader thread.
   */
  default boolean isReady() {
    return true;
  }

  float getOpacity();

  void setOpacity(float opacity);

  Map<String, Map<String, Set<LazyContourLoader>>> getRefMap();

  NavigableMap<Double, Set<LazyContourLoader>> getPositionMap();

  Map<Integer, ? extends RegionAttributes> getSegAttributes();

  /**
   * Returns the sign-normalized unit normal of the plane along which {@link #getPositionMap()} keys
   * are computed (i.e. {@code key = referenceNormal · ImagePositionPatient}). Returns {@code null}
   * when the implementation does not expose a reference orientation; callers will then fall back to
   * the legacy {@link TagW#SlicePosition} lookup, which only works when image and segmentation
   * share the same dominant axis.
   */
  default Vector3d getReferenceNormal() {
    return null;
  }

  /**
   * Returns the segmentation's native slice spacing in mm along {@link #getReferenceNormal()}, or
   * {@code 0} when unknown. Used by {@link #lookupByPosition} to widen the matching tolerance when
   * the queried image's slice grid is coarser than (or simply offset from) the segmentation grid:
   * an MR slice can sit anywhere within a SEG slab, so the tolerance must be at least half the SEG
   * slice spacing for the corresponding SEG frames to be returned.
   */
  default double getSliceSpacing() {
    return 0;
  }

  /**
   * Returns the in-plane row direction of the SEG mask (DICOM IOP[0..2]), or {@code null} when the
   * implementation does not expose one. Combined with {@link #getMaskColumnDirection}, {@link
   * #getMaskPixelSpacing}, {@link #getMaskWidth} and {@link #getMaskHeight}, this is used by {@link
   * #lookupByPosition} to verify that the SEG mask grid matches the queried image grid
   * pixel-for-pixel before returning a position-based contour loader (whose contour points are in
   * the SEG mask's own pixel coordinates).
   */
  default Vector3d getMaskRowDirection() {
    return null;
  }

  /** In-plane column direction of the SEG mask (DICOM IOP[3..5]), or {@code null}. */
  default Vector3d getMaskColumnDirection() {
    return null;
  }

  /** SEG mask DICOM PixelSpacing as {@code [rowSpacing, colSpacing]} in mm, or {@code null}. */
  default double[] getMaskPixelSpacing() {
    return null;
  }

  /** SEG mask width (Columns), or {@code 0} when unknown. */
  default int getMaskWidth() {
    return 0;
  }

  /** SEG mask height (Rows), or {@code 0} when unknown. */
  default int getMaskHeight() {
    return 0;
  }

  /**
   * Returns a shared, lazily-built 3D segmentation volume keyed on the segmentation's own native
   * grid. Implementations that expose a volume here enable orientation-independent 2D overlays
   * (used as a fallback when {@link #getPositionMap()} keys are not comparable to the queried
   * image), MPR overlays and Volume Rendering. The default implementation returns {@code null}.
   */
  default SegmentationVolume getOrBuildSegmentationVolume() {
    return null;
  }

  /**
   * Returns {@code true} when an asynchronous build of the canonical segmentation volume is
   * currently in progress. While a build is in flight, {@link #getOrBuildSegmentationVolume()}
   * returns {@code null} (so the EDT is never blocked) and {@link #getContours} returns {@code
   * null} too — the views will repaint when the build completes (the implementation fires an {@code
   * UPDATE} event) and the next call will hit the cached volume. The default returns {@code false}.
   */
  default boolean isSegmentationVolumeBuilding() {
    return false;
  }

  /**
   * Returns {@code true} when the segmentation carries fractional / probabilistic values that the
   * canonical 3D volume cannot represent without loss (the volume builder collapses fractional
   * frames to binary stamps). When {@code true}, {@link #getContours} keeps the per-frame
   * "graphics" path as the primary source so that {@code FractionalOverlay} can render the alpha
   * mask, and only uses the volume reslice as a last-resort fallback. The default returns {@code
   * false}.
   */
  default boolean isFractionalSeg() {
    return false;
  }

  default boolean containsSopInstanceUIDReference(DicomImageElement img) {
    if (!isReady()) {
      return false;
    }
    if (img != null) {
      String seriesUID = TagD.getTagValue(img, Tag.SeriesInstanceUID, String.class);
      if (seriesUID != null) {
        // Prefer spatial position-based matching (positionMap) over SOP UID-based (refMap).
        NavigableMap<Double, Set<LazyContourLoader>> positionMap = getPositionMap();
        if (!positionMap.isEmpty()) {
          Series<?> series = img.getMediaReader().getMediaSeries();
          String frameOfRef = TagD.getTagValue(series, Tag.FrameOfReferenceUID, String.class);
          if (frameOfRef != null && this instanceof DicomElement group) {
            String frameOfRef2 =
                TagD.getTagValue(
                    group.getMediaReader().getMediaSeries(), Tag.FrameOfReferenceUID, String.class);
            if (Objects.equals(frameOfRef, frameOfRef2)) {
              // Same patient frame; additionally verify the orientations are at least nearly
              // parallel when a reference normal is known, otherwise the segmentation cannot be
              // overlaid as 2D contours on this image.
              Vector3d segNormal = getReferenceNormal();
              if (segNormal == null) {
                return true;
              }
              Vector3d imgNormal = DicomMediaUtils.computeImageNormal(img);
              if (imgNormal == null) {
                return true;
              }
              return Math.abs(imgNormal.dot(segNormal)) >= ORIENTATION_COSINE_OBLIQUE
                  || getOrBuildSegmentationVolume() != null;
            }
          }
        }

        // Fall back to SOP UID-based matching from the Derivation Image Sequence
        String sopInstanceUID = TagD.getTagValue(img, Tag.SOPInstanceUID, String.class);
        Map<String, Set<LazyContourLoader>> map = getRefMap().get(seriesUID);
        if (map != null && sopInstanceUID != null) {
          return map.containsKey(sopInstanceUID);
        }
      }
    }
    return false;
  }

  default Set<LazyContourLoader> getContours(DicomImageElement img) {
    if (!isReady()) {
      return null;
    }
    String seriesUID = TagD.getTagValue(img, Tag.SeriesInstanceUID, String.class);
    if (seriesUID != null) {
      boolean fractional = isFractionalSeg();

      // Prefer the 3D segmentation volume reslice path: it produces contours in the queried
      // image's pixel coordinates regardless of orientation, handles oblique / MPR reformats
      // and is dramatically faster than the per-frame "graphics" loaders for large SEGs (a
      // single second-pass canonical stamp is reused across every redraw via the SoftReference
      // cache in getOrBuildSegmentationVolume()).
      //
      // Fractional SEGs are an exception: the volume builder collapses fractional frames to
      // binary stamps, so for those we keep the per-frame path as the primary source and only
      // use the volume as a last-resort fallback.
      if (!fractional) {
        Set<LazyContourLoader> volumeContours = lookupByVolume(img);
        if (volumeContours != null) {
          return volumeContours;
        }
        // The volume is not yet built but a background build is in progress: do NOT fall back
        // to the position-map path
        if (isSegmentationVolumeBuilding()) {
          return null;
        }
      }

      // Fall back to spatial position-based matching (positionMap). This is the primary path
      // for fractional SEGs (preserves alpha) and the only path when no 3D volume could be
      // built (RT structures, single-frame SEGs without a usable canonical grid, ...).
      NavigableMap<Double, Set<LazyContourLoader>> positionMap = getPositionMap();
      if (!positionMap.isEmpty()) {
        Set<LazyContourLoader> result = lookupByPosition(img, positionMap);
        if (result != null) {
          return result;
        }
      }

      // Last-resort fallback for fractional SEGs whose positionMap is not comparable to the
      // queried image (oblique reslice through a fractional SEG): downgrade to a binary
      // volume reslice rather than show nothing.
      if (fractional) {
        Set<LazyContourLoader> volumeContours = lookupByVolume(img);
        if (volumeContours != null) {
          return volumeContours;
        }
      }

      // Fall back to SOP UID-based matching from the Derivation Image Sequence
      String sopInstanceUID = TagD.getTagValue(img, Tag.SOPInstanceUID, String.class);
      Map<String, Set<LazyContourLoader>> map = getRefMap().get(seriesUID);
      if (map != null && sopInstanceUID != null) {
        Set<LazyContourLoader> loader;
        int frames = img.getMediaReader().getMediaElementNumber();
        if (frames > 1 && img.getKey() instanceof Integer intVal) {
          loader = map.get(sopInstanceUID + "_" + intVal);
        } else {
          loader = map.get(sopInstanceUID);
        }
        return loader;
      }
    }
    return null;
  }

  /**
   * Builds contours for the given image by reslicing the cached 3D segmentation volume. Returns
   * {@code null} when no volume is available (default implementation, RT classes, or build
   * failure). The returned set wraps a {@link VolumeSliceContourLoader} that performs the actual
   * reslicing lazily on first {@code getLazyContours()} call.
   */
  private Set<LazyContourLoader> lookupByVolume(DicomImageElement img) {
    SegmentationVolume volume = getOrBuildSegmentationVolume();
    if (volume == null) {
      return null;
    }
    Set<LazyContourLoader> set = new LinkedHashSet<>();
    set.add(new VolumeSliceContourLoader(volume, img));
    return set;
  }

  /**
   * Looks up contour loaders for the given image in the position map, taking into account the
   * relative orientation of the image plane and the segmentation plane.
   *
   * <ul>
   *   <li>When a reference normal is exposed by the segmentation, the image's IPP is projected onto
   *       that normal so the lookup remains valid even if the image and segmentation use different
   *       row/column directions or sign conventions.
   *   <li>When the planes are essentially parallel ({@code |cos| ≥ ORIENTATION_COSINE_STRICT}), the
   *       strict {@link #POSITION_TOLERANCE} is used.
   *   <li>When the planes are only nearly parallel ({@code |cos| ≥ ORIENTATION_COSINE_OBLIQUE}),
   *       the larger {@link #POSITION_TOLERANCE_OBLIQUE} is used.
   *   <li>When the planes diverge further, the lookup is skipped (returns {@code null}); the caller
   *       will fall through to the SOP UID map, or — in phase 2 — resample a shared segmentation
   *       volume.
   *   <li>When no reference normal is available (e.g., NM segmentations matched via SliceLocation),
   *       the legacy {@link TagW#SlicePosition} key is used.
   * </ul>
   */
  private Set<LazyContourLoader> lookupByPosition(
      DicomImageElement img, NavigableMap<Double, Set<LazyContourLoader>> positionMap) {
    Vector3d segNormal = getReferenceNormal();
    if (segNormal != null) {
      Vector3d imgNormal = DicomMediaUtils.computeImageNormal(img);
      if (imgNormal != null) {
        double cos = Math.abs(imgNormal.dot(segNormal));
        if (cos < ORIENTATION_COSINE_OBLIQUE) {
          // Planes are too different; positionMap keys are not comparable.
          return null;
        }
        // The position map returns contour loaders that produce contour points in the SEG mask's
        // *own* pixel grid (Rows × Columns × PixelSpacing of the SEG frame). This is only safe to
        // overlay directly on the image when the SEG mask and the image share an identical
        // in-plane grid — i.e. same row/column directions (sign included), same pixel spacing
        // and same IPP in-plane anchor. When the SEG was resampled to its own canonical grid
        // (typical of highdicom multi-segment objects with empty Derivation Image Sequence) the
        // mask pixel coordinates do not match image pixel coordinates and would render rotated
        // / translated / mis-scaled. The {@link #getContours} caller already tried the volume
        // reslice path first (which produces contours in the queried image's pixel coordinates),
        // so by the time we get here we are either a fractional SEG (preserve alpha) or no
        // volume could be built — only accept strictly compatible grids.
        if (!isInPlaneGridCompatible(img)) {
          return null;
        }
        Double key = projectOnNormal(img, segNormal);
        if (key != null) {
          double baseTol =
              cos >= ORIENTATION_COSINE_STRICT ? POSITION_TOLERANCE : POSITION_TOLERANCE_OBLIQUE;
          // Widen the tolerance to encompass:
          //  - half the SEG's own slab thickness (so an image slice sitting between two SEG
          //    frames still picks up the closest one, and a multi-segment SEG returns all
          //    segments alive on the same canonical Z),
          //  - half the queried image's slab thickness (so a thicker image returns every SEG
          //    frame inside its slab, instead of only the one closest to its centre).
          double segSpacing = getSliceSpacing();
          double imgThickness = imageSliceThickness(img);
          double tol = Math.max(baseTol, Math.max(segSpacing * 0.5, imgThickness * 0.5));
          // Add a small epsilon to absorb floating-point drift between sign-conventions.
          return findByTolerance(positionMap, key, tol + 1e-4);
        }
      }
    }
    // Legacy fallback: rely on the cached SlicePosition tag (works only when image and
    // segmentation share the same dominant axis and sign convention).
    Double loc = (Double) img.getTagValue(TagW.SlicePosition);
    if (loc == null) {
      // Fallback to SliceLocation when SlicePosition is unavailable (e.g., NM images)
      loc = TagD.getTagValue(img, Tag.SliceLocation, Double.class);
    }
    if (loc != null) {
      return findByTolerance(positionMap, loc, POSITION_TOLERANCE);
    }
    return null;
  }

  /** Projects the image's IPP onto the given (sign-normalized) normal. */
  private static Double projectOnNormal(DicomImageElement img, Vector3d normal) {
    double[] ipp = TagD.getTagValue(img, Tag.ImagePositionPatient, double[].class);
    if (ipp == null || ipp.length != 3) {
      return null;
    }
    return normal.dot(new Vector3d(ipp));
  }

  /**
   * Returns {@code true} when the SEG mask in-plane grid (row/col directions, pixel spacing, mask
   * dimensions) matches the queried image's in-plane grid pixel-for-pixel, so that the SEG mask
   * pixel coordinates returned by the position-map loaders can be drawn directly on the image
   * canvas. Returns {@code true} (legacy behaviour) when the SEG implementation does not expose its
   * mask grid via {@link #getMaskRowDirection} (e.g. RT structures).
   */
  private boolean isInPlaneGridCompatible(DicomImageElement img) {
    Vector3d segRow = getMaskRowDirection();
    Vector3d segCol = getMaskColumnDirection();
    if (segRow == null || segCol == null) {
      return true; // unknown SEG grid; preserve legacy behaviour
    }
    Vector3d imgRow = ImageOrientation.getRowImagePosition(img);
    Vector3d imgCol = ImageOrientation.getColumnImagePosition(img);
    if (imgRow == null || imgCol == null) {
      return true;
    }
    // Require strictly identical (sign included) row/col directions.
    if (segRow.dot(imgRow) < ORIENTATION_COSINE_STRICT) return false;
    if (segCol.dot(imgCol) < ORIENTATION_COSINE_STRICT) return false;

    double[] segPs = getMaskPixelSpacing();
    double[] imgPs = TagD.getTagValue(img, Tag.PixelSpacing, double[].class);
    if (segPs != null && imgPs != null && segPs.length >= 2 && imgPs.length >= 2) {
      if (Math.abs(segPs[0] - imgPs[0]) > 1e-3) return false;
      if (Math.abs(segPs[1] - imgPs[1]) > 1e-3) return false;
    }

    // Mask dimensions must match the displayed image to ensure (mx, my) ≡ (u, v).
    org.weasis.opencv.data.PlanarImage planar = img.getImage();
    try {
      if (planar != null) {
        int segW = getMaskWidth();
        int segH = getMaskHeight();
        if (segW > 0 && planar.width() != segW) return false;
        if (segH > 0 && planar.height() != segH) return false;
      }
    } finally {
      if (planar != null) {
        org.weasis.opencv.op.ImageConversion.releasePlanarImage(planar);
        img.removeImageFromCache();
      }
    }
    return true;
  }

  /**
   * Returns the queried image's slab thickness in mm: SpacingBetweenSlices when present, else
   * SliceThickness, else {@code 0}. Used to widen the position-map tolerance when the image is
   * thicker than the SEG slab.
   */
  private static double imageSliceThickness(DicomImageElement img) {
    Double v = TagD.getTagValue(img, Tag.SpacingBetweenSlices, Double.class);
    if (v == null || v <= 0) {
      v = TagD.getTagValue(img, Tag.SliceThickness, Double.class);
    }
    return v == null || v <= 0 ? 0 : v;
  }

  default void updateOpacityInSegAttributes(float opacity) {
    int opacityValue = (int) (opacity * 255f);
    getSegAttributes()
        .values()
        .forEach(
            c -> {
              Color color = c.getColor();
              color = new Color(color.getRed(), color.getGreen(), color.getBlue(), opacityValue);
              c.setColor(color);
            });
  }

  /**
   * Finds all loaders whose position key is within the given tolerance of the target value, merging
   * all matching sets into a single result.
   *
   * @param map the position map (NavigableMap keyed by slice position)
   * @param target the target slice position
   * @param tolerance the tolerance in mm
   * @return the merged set of loaders, or null if no match was found
   */
  static Set<LazyContourLoader> findByTolerance(
      NavigableMap<Double, Set<LazyContourLoader>> map, double target, double tolerance) {
    NavigableMap<Double, Set<LazyContourLoader>> sub =
        map.subMap(target - tolerance, true, target + tolerance, true);
    if (sub.isEmpty()) {
      return null;
    }
    if (sub.size() == 1) {
      return sub.firstEntry().getValue();
    }
    // Merge multiple matching sets (rare: only when several positions fall within tolerance)
    Set<LazyContourLoader> merged = new LinkedHashSet<>();
    for (Set<LazyContourLoader> s : sub.values()) {
      merged.addAll(s);
    }
    return merged;
  }

  /**
   * Returns an immutable empty NavigableMap, for implementations that do not use position-based
   * matching.
   */
  static NavigableMap<Double, Set<LazyContourLoader>> emptyPositionMap() {
    return Collections.emptyNavigableMap();
  }
}
