/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image;

import java.awt.geom.Rectangle2D;
import java.util.List;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.weasis.core.Messages;
import org.weasis.core.util.MathUtil;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageTransformer;

/**
 * Performs affine transformation operations on images using OpenCV.
 *
 * <p>Supported parameters:
 *
 * <ul>
 *   <li>{@link #P_AFFINE_MATRIX} - The 2x3 transformation matrix (required)
 *   <li>{@link #P_INTERPOLATION} - Interpolation method (optional)
 *   <li>{@link #P_DST_BOUNDS} - Destination bounds (required)
 * </ul>
 */
public class AffineTransformOp extends AbstractOp {

  public static final String OP_NAME = Messages.getString("AffineTransformOp.affine_op");
  public static final List<Double> IDENTITY_MATRIX = List.of(1.0, 0.0, 0.0, 0.0, 1.0, 0.0);

  /** The affine transformation matrix as a list of 6 doubles (2x3 matrix). */
  public static final String P_AFFINE_MATRIX = "affine.matrix";

  /** The interpolation type for the transformation. */
  public static final String P_INTERPOLATION = "interpolation";

  /** The destination bounds as a Rectangle2D. */
  public static final String P_DST_BOUNDS = "dest.bounds";

  private static final int MATRIX_ROWS = 2;
  private static final int MATRIX_COLS = 3;
  private static final double SIZE_TOLERANCE = 1e-6;

  public AffineTransformOp() {
    setName(OP_NAME);
  }

  public AffineTransformOp(AffineTransformOp op) {
    super(op);
  }

  @Override
  public AffineTransformOp copy() {
    return new AffineTransformOp(this);
  }

  @Override
  public void process() throws Exception {
    PlanarImage source = getSourceImage();
    PlanarImage result = applyTransformIfNeeded(source);
    params.put(Param.OUTPUT_IMG, result);
  }

  private PlanarImage applyTransformIfNeeded(PlanarImage source) {
    var matrix = getAffineMatrix();
    var bounds = getDestinationBounds();

    if (isTransformNotRequired(source, matrix, bounds)) {
      return source;
    }

    return applyAffineTransform(source, matrix, bounds);
  }

  private boolean isTransformNotRequired(
      PlanarImage source, List<Double> matrix, Rectangle2D bounds) {
    if (bounds == null || matrix == null) {
      return true;
    }

    boolean isIdentity = IDENTITY_MATRIX.equals(matrix);
    boolean hasSameDimensions =
        MathUtil.isEqual(source.width(), bounds.getWidth())
            && MathUtil.isEqual(source.height(), bounds.getHeight());

    return isIdentity && hasSameDimensions;
  }

  private PlanarImage applyAffineTransform(
      PlanarImage source, List<Double> matrix, Rectangle2D bounds) {
    if (bounds.getWidth() <= 0 || bounds.getHeight() <= 0) {
      return null;
    }

    var transformMatrix = createTransformMatrix(matrix);
    var interpolation = getInterpolationValue();
    // Round up: OpenCV truncates the size and would drop the partial last column and row. The
    // tolerance keeps a computed whole size (e.g. 512.0000001) from gaining an empty column.
    var outputSize = new Size(ceilSize(bounds.getWidth()), ceilSize(bounds.getHeight()));

    return ImageTransformer.warpAffine(source.toMat(), transformMatrix, outputSize, interpolation);
  }

  static double ceilSize(double size) {
    return Math.ceil(size - SIZE_TOLERANCE);
  }

  private Mat createTransformMatrix(List<Double> matrix) {
    var mat = new Mat(MATRIX_ROWS, MATRIX_COLS, CvType.CV_64FC1);
    double[] matrixArray = matrix.stream().mapToDouble(Double::doubleValue).toArray();
    mat.put(0, 0, matrixArray);
    return mat;
  }

  @SuppressWarnings("unchecked")
  private List<Double> getAffineMatrix() {
    return (List<Double>) params.get(P_AFFINE_MATRIX);
  }

  private Rectangle2D getDestinationBounds() {
    return (Rectangle2D) params.get(P_DST_BOUNDS);
  }

  private Integer getInterpolationValue() {
    var interpolation = (ZoomOp.Interpolation) params.get(P_INTERPOLATION);
    return interpolation != null ? interpolation.getOpencvValue() : null;
  }
}
