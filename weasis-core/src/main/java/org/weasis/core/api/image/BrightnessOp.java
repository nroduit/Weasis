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

public class BrightnessOp extends AbstractOp {

  public static final String OP_NAME = "rescale"; // NON-NLS

  public static final String P_BRIGHTNESS_VALUE = "rescale.brightness";
  public static final String P_CONTRAST_VALUE = "rescale.contrast";

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

  @Override
  public void process() throws Exception {
    PlanarImage source = (PlanarImage) params.get(Param.INPUT_IMG);
    PlanarImage result = source;

    Double contrast = (Double) params.get(P_CONTRAST_VALUE);
    Double brightness = (Double) params.get(P_BRIGHTNESS_VALUE);

    if (contrast != null && brightness != null) {
      result = ImageProcessor.rescaleToByte(source.toImageCV(), contrast / 100.0, brightness);
    }

    params.put(Param.OUTPUT_IMG, result);
  }
}
