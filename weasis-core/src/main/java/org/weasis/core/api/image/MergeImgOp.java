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

import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageProcessor;

public class MergeImgOp extends AbstractOp {

  public static final String OP_NAME = "merge.img";

  /**
   * The second image for merging operation (Required parameter). Note: calling clearIOCache will
   * remove the parameter value.
   *
   * <p>java.awt.image.RenderedImage value.
   */
  public static final String INPUT_IMG2 = "op.input.img.2";

  /**
   * Opacity of the fist image. If null the value is 1.0 (no transparency).
   *
   * <p>Double value from 0.0 to 1.0 where 0.0 is fully transparent and 1.0 is fully opaque.
   */
  public static final String P_OPACITY_1 = "opacity1"; // NON-NLS

  /**
   * Opacity of the second image. If null the value is 1.0 (no transparency).
   *
   * <p>Double value from 0.0 to 1.0 where 0.0 is fully transparent and 1.0 is fully opaque.
   */
  public static final String P_OPACITY_2 = "opacity2"; // NON-NLS

  public MergeImgOp() {
    setName(OP_NAME);
  }

  public MergeImgOp(MergeImgOp op) {
    super(op);
  }

  @Override
  public MergeImgOp copy() {
    return new MergeImgOp(this);
  }

  @Override
  public void process() throws Exception {
    PlanarImage source = (PlanarImage) params.get(Param.INPUT_IMG);
    PlanarImage source2 = (PlanarImage) params.get(INPUT_IMG2);
    PlanarImage result = source;

    if (source2 != null) {
      Double opacity1 = (Double) params.get(P_OPACITY_1);
      Double opacity2 = (Double) params.get(P_OPACITY_2);
      if (opacity1 == null) opacity1 = 1.0;
      if (opacity2 == null) opacity2 = 1.0;
      result = ImageProcessor.mergeImages(source.toMat(), source2.toMat(), opacity1, opacity2);
    }
    params.put(Param.OUTPUT_IMG, result);
  }
}
