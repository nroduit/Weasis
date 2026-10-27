/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.subtraction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.dcm4che3.data.Tag;
import org.joml.Vector3d;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;
import org.weasis.dicom.codec.utils.DicomMediaUtils;

/**
 * Decides whether a mask series can be subtracted from a series without resampling, and pairs their
 * slices. The direct path needs the same modality, the same study, one frame of reference, one
 * matrix, one pixel spacing, one orientation, and a mask slice at the position of every slice of
 * the series: the subtraction is then one difference per pixel. Anything else is refused with the
 * reason, so the reader learns why the pair does not qualify.
 */
public final class SubtractionCompatibility {

  /** Tolerance on direction cosines and pixel spacing, in their own units. */
  static final double TOLERANCE = 1e-3;

  /** The verdict: the slice pairs of the direct path, or why the pair is refused. */
  public sealed interface Result permits Pairing, Refusal {}

  /**
   * The slices of the series, each with the mask slice at its position.
   *
   * @param sameFrameOfReference false when both operands lack a Frame of Reference UID, which the
   *     direct path accepts with a warning
   */
  public record Pairing(List<SlicePair> slices, boolean sameFrameOfReference) implements Result {}

  public record Refusal(String reason) implements Result {}

  public record SlicePair(DicomImageElement image, DicomImageElement mask) {}

  private SubtractionCompatibility() {}

  public static boolean isCompatible(
      MediaSeries<DicomImageElement> series, MediaSeries<DicomImageElement> mask) {
    return pair(series, mask) instanceof Pairing;
  }

  /** Pairs the slices of the series with those of the mask, or says why it cannot. */
  public static Result pair(
      MediaSeries<DicomImageElement> series, MediaSeries<DicomImageElement> mask) {
    if (series == null || mask == null) {
      return new Refusal("Both a series and a mask are needed");
    }
    if (series == mask) {
      return new Refusal("A series cannot be subtracted from itself");
    }
    List<DicomImageElement> images = sortedSlices(series);
    List<DicomImageElement> masks = sortedSlices(mask);
    if (images.isEmpty() || masks.isEmpty()) {
      return new Refusal("An operand has no image");
    }
    String modality = modality(series, images.getFirst());
    String maskModality = modality(mask, masks.getFirst());
    if (modality == null || !modality.equals(maskModality)) {
      return new Refusal("Different modalities: " + modality + " and " + maskModality);
    }
    String study = text(images.getFirst(), Tag.StudyInstanceUID);
    if (study == null || !study.equals(text(masks.getFirst(), Tag.StudyInstanceUID))) {
      return new Refusal("The operands belong to different studies");
    }
    String frame = frameOfReference(series, images.getFirst());
    String maskFrame = frameOfReference(mask, masks.getFirst());
    boolean sameFrame = frame != null && frame.equals(maskFrame);
    if (!sameFrame && frame != null && maskFrame != null) {
      return new Refusal("The operands have different frames of reference");
    }
    if (!sameMatrix(images.getFirst(), masks.getFirst())) {
      return new Refusal("The operands have different matrices (rows and columns)");
    }
    GeometryOfSlice geometry = images.getFirst().getSliceGeometry();
    GeometryOfSlice maskGeometry = masks.getFirst().getSliceGeometry();
    if (geometry == null || maskGeometry == null) {
      return pairByIndex(images, masks, geometry == null && maskGeometry == null, sameFrame);
    }
    if (!within(geometry.getVoxelSpacing(), maskGeometry.getVoxelSpacing())) {
      return new Refusal("The operands have different pixel spacings");
    }
    if (!within(geometry.getRow(), maskGeometry.getRow())
        || !within(geometry.getColumn(), maskGeometry.getColumn())) {
      return new Refusal("The operands have different orientations");
    }
    return pairByPosition(images, masks, sameFrame);
  }

  /** Two-dimensional images without geometry pair by order when the counts match. */
  private static Result pairByIndex(
      List<DicomImageElement> images,
      List<DicomImageElement> masks,
      boolean bothPlanar,
      boolean sameFrame) {
    if (!bothPlanar) {
      return new Refusal("Only one operand has a geometry (position and orientation)");
    }
    if (images.size() != masks.size()) {
      return new Refusal(
          "Without geometry the operands need the same number of images: "
              + images.size()
              + " and "
              + masks.size());
    }
    List<SlicePair> pairs = new ArrayList<>(images.size());
    for (int i = 0; i < images.size(); i++) {
      pairs.add(new SlicePair(images.get(i), masks.get(i)));
    }
    return new Pairing(acquisitionOrder(pairs), sameFrame);
  }

  /** Every slice of the series takes the mask slice within half a slice spacing of it. */
  private static Result pairByPosition(
      List<DicomImageElement> images, List<DicomImageElement> masks, boolean sameFrame) {
    double[] maskPositions =
        masks.stream().mapToDouble(SubtractionCompatibility::position).toArray();
    double tolerance = halfSpacing(images);
    List<SlicePair> pairs = new ArrayList<>(images.size());
    for (DicomImageElement image : images) {
      double position = position(image);
      int index = nearest(maskPositions, position);
      if (Math.abs(maskPositions[index] - position) > tolerance) {
        return new Refusal(
            String.format(
                Locale.ROOT,
                "No mask slice within %.2f mm of the slice at %.2f mm",
                tolerance,
                position));
      }
      pairs.add(new SlicePair(image, masks.get(index)));
    }
    return new Pairing(acquisitionOrder(pairs), sameFrame);
  }

  /**
   * The pairs in the order the series was acquired (its instance numbers), so the derived series
   * stacks and scrolls like its source; the position order stays when a number is missing.
   */
  private static List<SlicePair> acquisitionOrder(List<SlicePair> pairs) {
    if (pairs.stream()
        .anyMatch(p -> TagD.getTagValue(p.image(), Tag.InstanceNumber, Integer.class) == null)) {
      return pairs;
    }
    List<SlicePair> ordered = new ArrayList<>(pairs);
    ordered.sort(
        Comparator.comparingInt(
            p -> TagD.getTagValue(p.image(), Tag.InstanceNumber, Integer.class)));
    return ordered;
  }

  /** Half the smallest distance between two slices, or half the thickness of a single slice. */
  private static double halfSpacing(List<DicomImageElement> images) {
    double smallest = Double.MAX_VALUE;
    for (int i = 1; i < images.size(); i++) {
      double gap = Math.abs(position(images.get(i)) - position(images.get(i - 1)));
      if (gap > TOLERANCE) {
        smallest = Math.min(smallest, gap);
      }
    }
    if (smallest == Double.MAX_VALUE) {
      GeometryOfSlice geometry = images.getFirst().getSliceGeometry();
      double thickness = geometry == null ? 0 : geometry.getSliceThickness();
      smallest = thickness > 0 ? thickness : 1.0;
    }
    return smallest / 2;
  }

  private static int nearest(double[] sorted, double value) {
    int index = Arrays.binarySearch(sorted, value);
    if (index >= 0) {
      return index;
    }
    int insertion = -index - 1;
    if (insertion == 0) {
      return 0;
    }
    if (insertion >= sorted.length) {
      return sorted.length - 1;
    }
    return value - sorted[insertion - 1] <= sorted[insertion] - value ? insertion - 1 : insertion;
  }

  /** The images ordered along the slice normal, their position computed when it is not yet. */
  static List<DicomImageElement> sortedSlices(MediaSeries<DicomImageElement> series) {
    List<DicomImageElement> images = new ArrayList<>(series.copyOfMedias(null, null));
    images.forEach(DicomMediaUtils::computeSlicePosition);
    images.sort(Comparator.comparingDouble(SubtractionCompatibility::position));
    return images;
  }

  static double position(DicomImageElement image) {
    Double position = (Double) image.getTagValue(TagW.SlicePosition);
    return position == null ? 0.0 : position;
  }

  private static boolean sameMatrix(DicomImageElement image, DicomImageElement mask) {
    Integer rows = TagD.getTagValue(image, Tag.Rows, Integer.class);
    Integer columns = TagD.getTagValue(image, Tag.Columns, Integer.class);
    return rows != null
        && columns != null
        && rows.equals(TagD.getTagValue(mask, Tag.Rows, Integer.class))
        && columns.equals(TagD.getTagValue(mask, Tag.Columns, Integer.class));
  }

  private static boolean within(Vector3d a, Vector3d b) {
    return a != null
        && b != null
        && Math.abs(a.x - b.x) <= TOLERANCE
        && Math.abs(a.y - b.y) <= TOLERANCE
        && Math.abs(a.z - b.z) <= TOLERANCE;
  }

  /** The codec keeps the Frame of Reference UID on the series; a derived image may carry it. */
  private static String frameOfReference(
      MediaSeries<DicomImageElement> series, DicomImageElement first) {
    String frame = TagD.getTagValue(series, Tag.FrameOfReferenceUID, String.class);
    return frame != null ? frame : text(first, Tag.FrameOfReferenceUID);
  }

  private static String modality(MediaSeries<DicomImageElement> series, DicomImageElement first) {
    String modality = TagD.getTagValue(series, Tag.Modality, String.class);
    return modality != null ? modality : text(first, Tag.Modality);
  }

  private static String text(DicomImageElement image, int tag) {
    return TagD.getTagValue(image, tag, String.class);
  }
}
