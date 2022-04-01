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
import org.weasis.core.api.Messages;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageProcessor;

public class CropOp extends AbstractOp {

  public static final String OP_NAME = Messages.getString("CropOperation.name");

  /**
   * Set the area to crop (Required parameter).
   *
   * <p>java.awt.Rectangle value.
   */
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

  @Override
  public void process() throws Exception {
    PlanarImage source = (PlanarImage) params.get(Param.INPUT_IMG);
    PlanarImage result = source;
    Rectangle area = (Rectangle) params.get(P_AREA);

    if (area != null) {
      result = ImageProcessor.crop(source.toMat(), area);
    }
    params.put(Param.OUTPUT_IMG, result);
  }
}
