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

import java.awt.Rectangle;

import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageProcessor;

public class MaskOp extends AbstractOp {

    public static final String OP_NAME = "Mask"; //$NON-NLS-1$

    /**
     * Set whether the shutter is applied (Required parameter).
     *
     * Boolean value.
     */
    public static final String P_SHOW = "show"; //$NON-NLS-1$
    public static final String P_SHAPE = "shape"; //$NON-NLS-1$
    public static final String P_ALPHA = "img.alpha"; //$NON-NLS-1$

    public MaskOp() {
        setName(OP_NAME);
    }

    public MaskOp(MaskOp op) {
        super(op);
    }

    @Override
    public MaskOp copy() {
        return new MaskOp(this);
    }

    @Override
    public void process() throws Exception {
        PlanarImage source = (PlanarImage) params.get(Param.INPUT_IMG);
        PlanarImage result = source;

        Boolean mask = (Boolean) params.get(P_SHOW);
        Rectangle area = (Rectangle) params.get(P_SHAPE);

        if (mask != null && mask && area != null
            && !area.equals(new Rectangle(0, 0, source.width(), source.height()))) {
            Double alpha = (Double) params.get(P_ALPHA);
            result = ImageProcessor.applyCropMask(source.toMat(), area, alpha == null ? 0.7 : alpha);
        }
        params.put(Param.OUTPUT_IMG, result);
    }
}
