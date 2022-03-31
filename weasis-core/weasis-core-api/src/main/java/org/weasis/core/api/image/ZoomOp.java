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

import java.awt.Dimension;
import org.opencv.imgproc.Imgproc;
import org.weasis.core.api.Messages;
import org.weasis.core.util.MathUtil;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageProcessor;

public class ZoomOp extends AbstractOp {

  public static final String OP_NAME = Messages.getString("ZoomOperation.title");

  public enum Interpolation {
    NEAREST_NEIGHBOUR(Messages.getString("ZoomOperation.nearest"), Imgproc.INTER_NEAREST),
    BILINEAR(Messages.getString("ZoomOperation.bilinear"), Imgproc.INTER_LINEAR),
    BICUBIC(Messages.getString("ZoomOperation.bicubic"), Imgproc.INTER_CUBIC),
    LANCZOS(Messages.getString("ZoomOperation.lanczos"), Imgproc.INTER_LANCZOS4);

    private final String title;
    private final int opencvValue;

    Interpolation(String title, int opencvValue) {
      this.title = title;
      this.opencvValue = opencvValue;
    }

    public int getOpencvValue() {
      return opencvValue;
    }

    @Override
    public String toString() {
      return title;
    }

    public String getTitle() {
      return title;
    }

    public static Interpolation getInterpolation(int position) {
      for (Interpolation v : Interpolation.values()) {
        if (v.ordinal() == position) {
          return v;
        }
      }
      return BILINEAR;
    }
  }

  /**
   * Set a zoom factor in x-axis (Required parameter).
   *
   * <p>Double value.
   */
  public static final String P_RATIO_X = "ratio.x";

  /**
   * Set a zoom factor in y-axis (Required parameter).
   *
   * <p>Double value.
   */
  public static final String P_RATIO_Y = "ratio.y";

  /**
   * Set the interpolation type (Optional parameter).
   *
   * <p>Integer value. Default value is bilinear interpolation. See javax.media.jai.Interpolation.
   */
  public static final String P_INTERPOLATION = "interpolation"; // NON-NLS

  public ZoomOp() {
    setName(OP_NAME);
  }

  public ZoomOp(ZoomOp op) {
    super(op);
  }

  @Override
  public ZoomOp copy() {
    return new ZoomOp(this);
  }

  @Override
  public void process() throws Exception {
    PlanarImage source = (PlanarImage) params.get(Param.INPUT_IMG);
    PlanarImage result = source;
    Double zoomFactorX = (Double) params.get(P_RATIO_X);
    Double zoomFactorY = (Double) params.get(P_RATIO_Y);

    if (zoomFactorX != null
        && zoomFactorY != null
        && (MathUtil.isDifferent(zoomFactorX, 1.0) || MathUtil.isDifferent(zoomFactorY, 1.0))) {
      Dimension dim =
          new Dimension(
              (int) (Math.abs(zoomFactorX) * source.width()),
              (int) (Math.abs(zoomFactorY) * source.height()));
      ZoomOp.Interpolation interpolation = (ZoomOp.Interpolation) params.get(P_INTERPOLATION);
      Integer inter = null;
      if (Math.abs(zoomFactorX) < 0.1 || Math.abs(zoomFactorY) < 0.1) {
        inter = Imgproc.INTER_AREA;
      } else if (interpolation != null) {
        inter = interpolation.getOpencvValue();
      }
      result = ImageProcessor.scale(source.toMat(), dim, inter);
    }

    params.put(Param.OUTPUT_IMG, result);
  }
}
