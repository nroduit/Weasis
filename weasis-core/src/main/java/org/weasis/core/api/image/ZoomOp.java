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
import org.weasis.core.Messages;
import org.weasis.core.util.MathUtil;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageTransformer;

/** Operation for scaling images with configurable zoom factors and interpolation methods. */
public class ZoomOp extends AbstractOp {

  public static final String OP_NAME = Messages.getString("ZoomOperation.title");

  public static final String P_RATIO_X = "ratio.x";
  public static final String P_RATIO_Y = "ratio.y";
  public static final String P_INTERPOLATION = "interpolation";
  private static final double DEFAULT_ZOOM_FACTOR = 1.0;
  private static final double DOWNSCALE_THRESHOLD = 0.1;

  /** Interpolation methods for image scaling operations. */
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

    public String getTitle() {
      return title;
    }

    @Override
    public String toString() {
      return title;
    }

    public static Interpolation fromPosition(int position) {
      Interpolation[] values = values();
      return position >= 0 && position < values.length ? values[position] : BILINEAR;
    }
  }

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
    PlanarImage source = getSourceImage();
    Double zoomX = (Double) params.get(P_RATIO_X);
    Double zoomY = (Double) params.get(P_RATIO_Y);

    PlanarImage result =
        (zoomX == null || zoomY == null || !requiresScaling(zoomX, zoomY))
            ? source
            : scaleImage(source, zoomX, zoomY);

    params.put(Param.OUTPUT_IMG, result);
  }

  private boolean requiresScaling(double zoomX, double zoomY) {
    return MathUtil.isDifferent(zoomX, DEFAULT_ZOOM_FACTOR)
        || MathUtil.isDifferent(zoomY, DEFAULT_ZOOM_FACTOR);
  }

  /** The length of the scaled image, as computed by this operation. */
  public static int scaledLength(int length, double zoom) {
    return (int) (Math.abs(zoom) * length);
  }

  private PlanarImage scaleImage(PlanarImage source, double zoomX, double zoomY) throws Exception {
    int newWidth = scaledLength(source.width(), zoomX);
    int newHeight = scaledLength(source.height(), zoomY);
    Integer interpolation = selectInterpolation(zoomX, zoomY);

    return ImageTransformer.scale(
        source.toMat(), new Dimension(newWidth, newHeight), interpolation);
  }

  private Integer selectInterpolation(double zoomX, double zoomY) {
    if (Math.abs(zoomX) < DOWNSCALE_THRESHOLD || Math.abs(zoomY) < DOWNSCALE_THRESHOLD) {
      return Imgproc.INTER_AREA;
    }

    Interpolation interpolation = (Interpolation) params.get(P_INTERPOLATION);
    return interpolation != null ? interpolation.getOpencvValue() : null;
  }
}
