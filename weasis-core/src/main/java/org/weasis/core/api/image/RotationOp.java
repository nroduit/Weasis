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

import java.util.Objects;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.geometry.Geometry;
import org.opencv.imgproc.Imgproc;
import org.weasis.core.Messages;
import org.weasis.core.util.MathUtil;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageTransformer;

/**
 * Applies rotation transformation to images. Supports optimized right-angle rotations (90°, 180°,
 * 270°) and arbitrary angle rotations.
 */
public final class RotationOp extends AbstractOp {

  public static final String OP_NAME = Messages.getString("RotationOperation.title");
  public static final String P_ROTATE = "rotate";

  private static final int FULL_CIRCLE_DEGREES = 360;
  private static final int ROTATION_90 = 90;
  private static final int ROTATION_180 = 180;
  private static final int ROTATION_270 = 270;

  public RotationOp() {
    setName(OP_NAME);
  }

  public RotationOp(RotationOp op) {
    super(op);
  }

  @Override
  public RotationOp copy() {
    return new RotationOp(this);
  }

  @Override
  public void process() throws Exception {
    var source = getSourceImage();
    int angle = getNormalizedRotationAngle();
    var result = angle == 0 ? source : applyRotation(source, angle);
    params.put(Param.OUTPUT_IMG, result);
  }

  private int getNormalizedRotationAngle() {
    return switch (params.get(P_ROTATE)) {
      case Integer angle -> angle % FULL_CIRCLE_DEGREES;
      case null, default -> 0;
    };
  }

  private PlanarImage applyRotation(PlanarImage source, int angle) {
    return switch (angle) {
      case ROTATION_90 ->
          ImageTransformer.getRotatedImage(source.toMat(), Core.ROTATE_90_CLOCKWISE);
      case ROTATION_180 -> ImageTransformer.getRotatedImage(source.toMat(), Core.ROTATE_180);
      case ROTATION_270 ->
          ImageTransformer.getRotatedImage(source.toMat(), Core.ROTATE_90_COUNTERCLOCKWISE);
      default ->
          rotateArbitraryAngle(source.toMat(), angle, source.width() / 2.0, source.height() / 2.0);
    };
  }

  /**
   * Rotates an image by an arbitrary angle around a center point.
   *
   * @param source the source image matrix
   * @param angle rotation angle in degrees (clockwise)
   * @param centerX rotation center X coordinate
   * @param centerY rotation center Y coordinate
   * @return the rotated image
   */
  public static ImageCV rotateArbitraryAngle(
      Mat source, double angle, double centerX, double centerY) {
    if (MathUtil.isEqualToZero(angle)) {
      return ImageCV.fromMat(source);
    }
    var srcImg = Objects.requireNonNull(source, "Source image cannot be null");
    var center = new Point(centerX, centerY);
    var rotationMatrix = Geometry.getRotationMatrix2D(center, -angle, 1.0);
    var bbox = new RotatedRect(center, srcImg.size(), -angle).boundingRect();

    double[] matrix = transformMatrixWithOffset(rotationMatrix, bbox, center);
    rotationMatrix.put(0, 0, matrix);

    var dstImg = new ImageCV();
    Imgproc.warpAffine(srcImg, dstImg, rotationMatrix, bbox.size());
    return dstImg;
  }

  public static double[] transformMatrixWithOffset(
      Mat rot, Rect bbox, org.opencv.core.Point ptCenter) {
    double[] m = new double[rot.cols() * rot.rows()];
    rot.get(0, 0, m);
    m[2] += bbox.width / 2.0 - ptCenter.x;
    m[rot.cols() + 2] += bbox.height / 2.0 - ptCenter.y;
    return m;
  }
}
