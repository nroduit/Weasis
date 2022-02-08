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

import org.weasis.core.api.Messages;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.op.ByteLut;
import org.weasis.core.api.image.op.ByteLutCollection;
import org.weasis.core.util.LangUtil;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageProcessor;

public class PseudoColorOp extends AbstractOp {

  public static final String OP_NAME = Messages.getString("PseudoColorOperation.title");

  /**
   * Set the lookup table (Required parameter).
   *
   * <p>org.weasis.core.api.image.op.ByteLut value.
   */
  public static final String P_LUT = ActionW.LUT.cmd();

  /**
   * Whether the LUT must be inverted (Optional parameter).
   *
   * <p>Boolean value. Default value is false.
   */
  public static final String P_LUT_INVERSE = ActionW.INVERT_LUT.cmd();

  public PseudoColorOp() {
    setName(OP_NAME);
  }

  public PseudoColorOp(PseudoColorOp op) {
    super(op);
  }

  @Override
  public PseudoColorOp copy() {
    return new PseudoColorOp(this);
  }

  @Override
  public void process() throws Exception {
    PlanarImage source = (PlanarImage) params.get(Param.INPUT_IMG);
    PlanarImage result = source;
    ByteLut lutTable = (ByteLut) params.get(P_LUT);

    if (lutTable != null) {
      boolean invert = LangUtil.getNULLtoFalse((Boolean) params.get(P_LUT_INVERSE));
      byte[][] lut = lutTable.getLutTable();
      if (lut == null) {
        if (invert) {
          result = ImageProcessor.invertLUT(source.toImageCV());
        }
      } else {
        if (invert) {
          lut = ByteLutCollection.invert(lut);
        }
        result = ImageProcessor.applyLUT(source.toMat(), lut);
        // result = new LookupTableCV(lut).lookup(source);
      }
    }

    params.put(Param.OUTPUT_IMG, result);
  }
}
