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
import java.util.Optional;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.imgproc.Imgproc;
import org.weasis.core.api.Messages;
import org.weasis.core.util.MathUtil;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageProcessor;

public class RotationOp extends AbstractOp {

  public static final String OP_NAME = Messages.getString("RotationOperation.title");

  /**
   * Set the clockwise angle value in degree (Required parameter).
   *
   * <p>Integer value.
   */
  public static final String P_ROTATE = "rotate"; // NON-NLS

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
    PlanarImage source = (PlanarImage) params.get(Param.INPUT_IMG);
    PlanarImage result = source;
    int rotationAngle = Optional.ofNullable((Integer) params.get(P_ROTATE)).orElse(0);
    rotationAngle = rotationAngle % 360;

    if (rotationAngle != 0) {
      // optimize rotation by right angles
      Integer rotOp = null;
      if (rotationAngle == 90) {
        rotOp = Core.ROTATE_90_CLOCKWISE;
      } else if (rotationAngle == 180) {
        rotOp = Core.ROTATE_180;
      } else if (rotationAngle == 270) {
        rotOp = Core.ROTATE_90_COUNTERCLOCKWISE;
      }

      if (rotOp == null) {
        result =
            getRotatedImage(
                source.toMat(), rotationAngle, source.width() / 2.0, source.height() / 2.0);
      } else {
        result = ImageProcessor.getRotatedImage(source.toMat(), rotOp);
      }
    }
    params.put(Param.OUTPUT_IMG, result);
  }

  public static ImageCV getRotatedImage(Mat source, double angle, double centerx, double centery) {
    if (MathUtil.isEqualToZero(angle)) {
      return ImageCV.toImageCV(source);
    }
    Mat srcImg = Objects.requireNonNull(source);
    Point ptCenter = new Point(centerx, centery);
    Mat rot = Imgproc.getRotationMatrix2D(ptCenter, -angle, 1.0);
    ImageCV dstImg = new ImageCV();
    // determine bounding rectangle
    Rect bbox = new RotatedRect(ptCenter, srcImg.size(), -angle).boundingRect();
    double[] matrix = new double[rot.cols() * rot.rows()];
    // adjust transformation matrix
    rot.get(0, 0, matrix);
    matrix[2] += bbox.width / 2.0 - centerx;
    matrix[rot.cols() + 2] += bbox.height / 2.0 - centery;
    rot.put(0, 0, matrix);
    Imgproc.warpAffine(srcImg, dstImg, rot, bbox.size());
    return dstImg;
  }
}
