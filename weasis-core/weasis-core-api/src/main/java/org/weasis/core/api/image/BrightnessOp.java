/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.api.image;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageProcessor;

public class BrightnessOp extends AbstractOp {
    private static final Logger LOGGER = LoggerFactory.getLogger(BrightnessOp.class);

    public static final String OP_NAME = "rescale"; //$NON-NLS-1$

    public static final String P_BRIGTNESS_VALUE = "rescale.brightness"; //$NON-NLS-1$
    public static final String P_CONTRAST_VALUE = "rescale.contrast"; //$NON-NLS-1$

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
        Double brigtness = (Double) params.get(P_BRIGTNESS_VALUE);

        if (contrast != null && brigtness != null) {
            result = ImageProcessor.rescaleToByte(source.toImageCV(), contrast / 100.0, brigtness);
        }

        params.put(Param.OUTPUT_IMG, result);
    }

}
