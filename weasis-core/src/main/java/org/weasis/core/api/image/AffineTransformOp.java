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
import java.util.Arrays;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.weasis.core.api.Messages;
import org.weasis.core.util.MathUtil;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageProcessor;

public class AffineTransformOp extends AbstractOp {

  public static final String OP_NAME = Messages.getString("AffineTransformOp.affine_op");

  public static final double[] identityMatrix = new double[] {1.0, 0.0, 0.0, 0.0, 1.0, 0.0};
  /**
   * Set an affine transformation (Required parameter).
   *
   * <p>Double array (length of 6).
   */
  public static final String P_AFFINE_MATRIX = "affine.matrix";

  /**
   * Set the interpolation type (Optional parameter).
   *
   * <p>Integer value. Default value is bilinear interpolation. See javax.media.jai.Interpolation.
   */
  public static final String P_INTERPOLATION = "interpolation"; // NON-NLS

  public static final String P_DST_BOUNDS = "dest.bounds";

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
    PlanarImage source = (PlanarImage) params.get(Param.INPUT_IMG);
    PlanarImage result = source;
    double[] matrix = (double[]) params.get(P_AFFINE_MATRIX);
    Rectangle2D bound = (Rectangle2D) params.get(P_DST_BOUNDS);

    if (bound != null
        && matrix != null
        && (!Arrays.equals(identityMatrix, matrix)
            || MathUtil.isDifferent(source.width(), bound.getWidth())
            || MathUtil.isDifferent(source.height(), bound.getHeight()))) {
      if (bound.getWidth() > 0 && bound.getHeight() > 0) {
        Mat mat = new Mat(2, 3, CvType.CV_64FC1);
        mat.put(0, 0, matrix);
        ZoomOp.Interpolation interpolation = (ZoomOp.Interpolation) params.get(P_INTERPOLATION);
        Integer inter = null;
        if (interpolation != null) {
          inter = interpolation.getOpencvValue();
        }
        result =
            ImageProcessor.warpAffine(
                source.toMat(), mat, new Size(bound.getWidth(), bound.getHeight()), inter);
      } else {
        result = null;
      }
    }

    params.put(Param.OUTPUT_IMG, result);
  }
}
