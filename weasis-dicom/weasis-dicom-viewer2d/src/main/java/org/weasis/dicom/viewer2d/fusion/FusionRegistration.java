/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.fusion;

import java.awt.geom.Point2D;
import java.util.Objects;
import org.joml.Vector3d;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.geometry.Geometry;
import org.opencv.imgproc.Imgproc;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;
import org.weasis.dicom.codec.geometry.ImageOrientation;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;

/**
 * Computes the spatial registration (affine transform) needed to align a PET image onto a CT/MR
 * image using their DICOM spatial coordinates.
 *
 * <p>The registration uses:
 *
 * <ul>
 *   <li>{@code ImagePositionPatient} (TLHC offset) for translation
 *   <li>{@code ImageOrientationPatient} (axis alignment) for rotation
 *   <li>{@code PixelSpacing} ratio for scale
 * </ul>
 *
 * <p>The preferred OpenCV method is {@code Imgproc.warpAffine()} which applies scale, translation,
 * and rotation in a single interpolation step, minimizing quality loss compared to sequential
 * {@code resize()} + manual pixel shifting.
 *
 * <p>For non-coplanar series (different orientations), a full affine decomposition is used. For the
 * common case of same-orientation series (e.g., both axial), a simpler scale + translation is
 * applied.
 */
public final class FusionRegistration {

  private static final int AFFINE_ROWS = 2;
  private static final int AFFINE_COLS = 3;

  private FusionRegistration() {}

  /**
   * Aligns a PET image to the CT/MR image pixel space using DICOM spatial coordinates.
   *
   * <p>This method handles three scenarios:
   *
   * <ol>
   *   <li><b>Same orientation, different spacing/position</b>: Scale + translate (most common for
   *       PET/CT)
   *   <li><b>Different orientation (oblique)</b>: Full affine transform with rotation
   *   <li><b>Missing geometry</b>: Falls back to simple resize to match CT dimensions
   * </ol>
   *
   * @param overlayImage the PET image element
   * @param overlaySource the PET pixel data (after W/L and LUT have been applied)
   * @param baseImage the reference CT/MR image element
   * @param baseSource the reference CT/MR pixel data (used only for target dimensions)
   * @return the aligned PET image in the CT pixel coordinate system
   */
  public static PlanarImage alignOverlayToBase(
      DicomImageElement overlayImage,
      PlanarImage overlaySource,
      DicomImageElement baseImage,
      PlanarImage baseSource) {
    Objects.requireNonNull(overlaySource, "Overlay source cannot be null");
    Objects.requireNonNull(baseSource, "Base source cannot be null");

    if (overlayImage == null || baseImage == null) {
      return resizeFallback(overlaySource, baseSource);
    }

    GeometryOfSlice overlayGeometry = overlayImage.getSliceGeometry();
    GeometryOfSlice baseGeometry = baseImage.getSliceGeometry();

    if (overlayGeometry == null || baseGeometry == null) {
      return resizeFallback(overlaySource, baseSource);
    }

    boolean sameOrientation = ImageOrientation.hasSameOrientation(overlayImage, baseImage);
    if (sameOrientation) {
      return alignSameOrientation(overlaySource, overlayGeometry, baseGeometry, baseSource);
    } else {
      return alignDifferentOrientation(overlaySource, overlayGeometry, baseGeometry, baseSource);
    }
  }

  /**
   * Aligns PET to CT when both have the same image orientation (e.g., both axial). This is the most
   * common scenario for PET/CT acquired on the same scanner.
   *
   * <p>Uses scale (PixelSpacing ratio) + translation (ImagePositionPatient difference projected
   * into CT pixel space).
   */
  private static PlanarImage alignSameOrientation(
      PlanarImage overlaySource,
      GeometryOfSlice overlayGeometry,
      GeometryOfSlice baseGeometry,
      PlanarImage baseTarget) {

    Vector3d overlaySpacing = overlayGeometry.getVoxelSpacing();
    Vector3d baseSpacing = baseGeometry.getVoxelSpacing();

    // Scale factors: ratio of physical pixel sizes
    double scaleX = overlaySpacing.x / baseSpacing.x;
    double scaleY = overlaySpacing.y / baseSpacing.y;

    // Compute translation: project PET TLHC into CT pixel coordinates
    Vector3d overlayTlhc = overlayGeometry.getTLHC();
    Point2D overlayInBasePixels = baseGeometry.getImagePosition(overlayTlhc);

    double tx = 0;
    double ty = 0;
    if (overlayInBasePixels != null) {
      tx = overlayInBasePixels.getX();
      ty = overlayInBasePixels.getY();
    }

    // Build 2x3 affine matrix: [scaleX, 0, tx; 0, scaleY, ty]
    Mat affineMatrix = buildAffineMatrix(scaleX, 0, tx, 0, scaleY, ty);

    return warpAffine(overlaySource, affineMatrix, baseTarget.width(), baseTarget.height());
  }

  /**
   * Aligns PET to CT when orientations differ (e.g., oblique PET on axial CT). Computes a full
   * affine transform by mapping 3 corners of the PET image into CT pixel space.
   */
  private static PlanarImage alignDifferentOrientation(
      PlanarImage overlaySource,
      GeometryOfSlice overlayGeometry,
      GeometryOfSlice baseGeometry,
      PlanarImage baseTarget) {

    Vector3d overlaySpacing = overlayGeometry.getVoxelSpacing();
    Vector3d overlayRow = overlayGeometry.getRow();
    Vector3d overlayCol = overlayGeometry.getColumn();
    Vector3d overlayTlhc = overlayGeometry.getTLHC();

    // Define 3 corners of the PET image in patient coordinates
    // Corner 0: top-left (TLHC)
    Vector3d corner0 = new Vector3d(overlayTlhc);

    // Corner 1: top-right (move along PET row by image width)
    double overlayWidthMm = overlaySource.width() * overlaySpacing.x;
    Vector3d corner1 =
        new Vector3d(
            overlayTlhc.x + overlayRow.x * overlayWidthMm,
            overlayTlhc.y + overlayRow.y * overlayWidthMm,
            overlayTlhc.z + overlayRow.z * overlayWidthMm);

    // Corner 2: bottom-left (move along PET column by image height)
    double overlayHeightMm = overlaySource.height() * overlaySpacing.y;
    Vector3d corner2 =
        new Vector3d(
            overlayTlhc.x + overlayCol.x * overlayHeightMm,
            overlayTlhc.y + overlayCol.y * overlayHeightMm,
            overlayTlhc.z + overlayCol.z * overlayHeightMm);

    // Project these 3D corners into CT pixel coordinates
    Point2D base0 = baseGeometry.getImagePosition(corner0);
    Point2D base1 = baseGeometry.getImagePosition(corner1);
    Point2D base2 = baseGeometry.getImagePosition(corner2);

    if (base0 == null || base1 == null || base2 == null) {
      return resizeFallback(overlaySource, baseTarget);
    }

    // Source points in PET pixel space: TLHC, top-right, bottom-left
    MatOfPoint2f srcPoints =
        new MatOfPoint2f(
            new Point(0, 0),
            new Point(overlaySource.width(), 0),
            new Point(0, overlaySource.height()));

    // Destination points in CT pixel space
    MatOfPoint2f dstPoints =
        new MatOfPoint2f(
            new Point(base0.getX(), base0.getY()),
            new Point(base1.getX(), base1.getY()),
            new Point(base2.getX(), base2.getY()));

    Mat affineMatrix = Geometry.getAffineTransform(srcPoints, dstPoints);

    return warpAffine(overlaySource, affineMatrix, baseTarget.width(), baseTarget.height());
  }

  private static PlanarImage resizeFallback(PlanarImage overlaySource, PlanarImage baseTarget) {
    if (overlaySource.width() == baseTarget.width()
        && overlaySource.height() == baseTarget.height()) {
      return overlaySource;
    }
    ImageCV result = new ImageCV();
    Imgproc.resize(
        overlaySource.toMat(),
        result,
        new Size(baseTarget.width(), baseTarget.height()),
        0,
        0,
        Imgproc.INTER_LINEAR);
    return result;
  }

  private static PlanarImage warpAffine(Mat source, Mat affineMatrix, int dstWidth, int dstHeight) {
    ImageCV result = new ImageCV();
    Imgproc.warpAffine(
        source,
        result,
        affineMatrix,
        new Size(dstWidth, dstHeight),
        Imgproc.INTER_LINEAR,
        org.opencv.core.Core.BORDER_CONSTANT);
    return result;
  }

  /** Convenience overload accepting PlanarImage. */
  private static PlanarImage warpAffine(
      PlanarImage source, Mat affineMatrix, int dstWidth, int dstHeight) {
    return warpAffine(source.toMat(), affineMatrix, dstWidth, dstHeight);
  }

  /** Builds a 2x3 affine matrix from individual components. */
  private static Mat buildAffineMatrix(
      double a, double b, double tx, double c, double d, double ty) {
    Mat mat = new Mat(AFFINE_ROWS, AFFINE_COLS, CvType.CV_64FC1);
    mat.put(0, 0, a, b, tx, c, d, ty);
    return mat;
  }
}
