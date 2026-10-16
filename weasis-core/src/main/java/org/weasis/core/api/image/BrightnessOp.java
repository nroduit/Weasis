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
import org.weasis.opencv.op.ImageTransformer;

/**
 * Image operation for adjusting brightness and contrast.
 *
 * <p>This operation applies rescaling transformations to modify the brightness and contrast of an
 * image. Default values (contrast=1.0, brightness=0.0) result in no transformation.
 */
public class BrightnessOp extends AbstractOp {

  public static final String OP_NAME = "rescale"; // NON-NLS

  public static final String P_BRIGHTNESS_VALUE = "rescale.brightness";
  public static final String P_CONTRAST_VALUE = "rescale.contrast";

  private static final double CONTRAST_SCALE_FACTOR = 100.0;
  private static final double DEFAULT_CONTRAST = 1.0;
  private static final double DEFAULT_BRIGHTNESS = 0.0;

  public BrightnessOp() {
    setName(OP_NAME);
  }

  public BrightnessOp(BrightnessOp op) {
    super(op);
  }

  @Override
  public BrightnessOp copy() {
    return new BrightnessOp(this);
  }

  // The input itself or a new image
  @Override
  protected boolean ownsOutput() {
    return true;
  }

  @Override
  public void process() throws Exception {
    PlanarImage source = getSourceImage();
    double contrast = getParam(P_CONTRAST_VALUE, Double.class, DEFAULT_CONTRAST);
    double brightness = getParam(P_BRIGHTNESS_VALUE, Double.class, DEFAULT_BRIGHTNESS);

    PlanarImage result =
        (contrast == DEFAULT_CONTRAST && brightness == DEFAULT_BRIGHTNESS)
            ? source
            : ImageTransformer.rescaleToByte(
                source.toImageCV(), contrast / CONTRAST_SCALE_FACTOR, brightness);
    params.put(Param.OUTPUT_IMG, result);
  }
}
