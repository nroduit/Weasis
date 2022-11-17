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

import java.awt.image.DataBuffer;
import org.opencv.core.Core.MinMaxLocResult;
import org.weasis.core.api.Messages;
import org.weasis.core.util.LangUtil;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageConversion;
import org.weasis.opencv.op.ImageProcessor;

public class AutoLevelsOp extends AbstractOp {

  public static final String OP_NAME = Messages.getString("AutoLevelsOp.auto_ct");
  /**
   * Set whether auto levels is applied to the image (Required parameter).
   *
   * <p>Boolean value.
   */
  public static final String P_AUTO_LEVEL = "auto.level";

  public AutoLevelsOp() {
    setName(OP_NAME);
  }

  public AutoLevelsOp(AutoLevelsOp op) {
    super(op);
  }

  @Override
  public AutoLevelsOp copy() {
    return new AutoLevelsOp(this);
  }

  @Override
  public void process() throws Exception {
    PlanarImage source = (PlanarImage) params.get(Param.INPUT_IMG);
    PlanarImage result = source;
    Boolean auto = (Boolean) params.get(P_AUTO_LEVEL);

    if (LangUtil.getNULLtoFalse(auto)) {
      MinMaxLocResult val = ImageProcessor.findMinMaxValues(source.toMat());
      if (val != null) {
        int datatype = ImageConversion.convertToDataType(source.type());
        double range = val.maxVal - val.minVal;
        if (range < 1.0 && datatype == DataBuffer.TYPE_INT) {
          range = 1.0;
        }
        double slope = 255.0 / range;
        double yint = 255.0 - slope * val.maxVal;
        result = ImageProcessor.rescaleToByte(source.toImageCV(), slope, yint);
      }
    }
    params.put(Param.OUTPUT_IMG, result);
  }
}
