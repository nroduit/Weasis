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
import org.weasis.core.api.Messages;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageProcessor;

public class FlipOp extends AbstractOp {
    private static final Logger LOGGER = LoggerFactory.getLogger(FlipOp.class);

    public static final String OP_NAME = Messages.getString("FlipOperation.title"); //$NON-NLS-1$

    /**
     * Set whether the image is flip horizontally (Required parameter).
     *
     * Boolean value.
     */
    public static final String P_FLIP = "flip"; //$NON-NLS-1$

    public FlipOp() {
        setName(OP_NAME);
    }

    public FlipOp(FlipOp op) {
        super(op);
    }

    @Override
    public FlipOp copy() {
        return new FlipOp(this);
    }

    @Override
    public void process() throws Exception {
        PlanarImage source = (PlanarImage) params.get(Param.INPUT_IMG);
        PlanarImage result = source;
        Boolean flip = (Boolean) params.get(P_FLIP);

        if (flip != null && flip) {
            result = ImageProcessor.flip(source.toMat(), 1); // 1) means flipping around y-axis
        }

        params.put(Param.OUTPUT_IMG, result);
    }
}
