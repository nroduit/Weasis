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

import java.awt.Rectangle;
import org.weasis.core.Messages;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageTransformer;

/**
 * Image crop operation.
 *
 * <p>Applies a rectangular crop area to the source image. If no crop area is defined or it covers
 * the entire image, the source is returned unchanged.
 */
public final class CropOp extends AbstractOp {

  public static final String OP_NAME = Messages.getString("CropOperation.name");

  /** Parameter key for crop area (Rectangle, required). */
  public static final String P_AREA = "area"; // NON-NLS

  public CropOp() {
    setName(OP_NAME);
  }

  public CropOp(CropOp op) {
    super(op);
  }

  @Override
  public CropOp copy() {
    return new CropOp(this);
  }

  // The input itself or a new image
  @Override
  protected boolean ownsOutput() {
    return true;
  }

  @Override
  public void process() throws Exception {
    PlanarImage source = getSourceImage();
    Rectangle cropArea = (Rectangle) params.get(P_AREA);

    PlanarImage result =
        needsCrop(cropArea, source) ? ImageTransformer.crop(source.toMat(), cropArea) : source;
    params.put(Param.OUTPUT_IMG, result);
  }

  private boolean needsCrop(Rectangle cropArea, PlanarImage source) {
    if (cropArea == null || cropArea.isEmpty()) {
      return false;
    }

    Rectangle imageArea = new Rectangle(0, 0, source.width(), source.height());
    return !cropArea.equals(imageArea);
  }
}
