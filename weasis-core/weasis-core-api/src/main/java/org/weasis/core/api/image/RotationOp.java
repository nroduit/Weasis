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

import java.util.Optional;

import org.opencv.core.Core;
import org.weasis.core.api.Messages;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageProcessor;

public class RotationOp extends AbstractOp {

    public static final String OP_NAME = Messages.getString("RotationOperation.title"); //$NON-NLS-1$

    /**
     * Set the clockwise angle value in degree (Required parameter).
     *
     * Integer value.
     */
    public static final String P_ROTATE = "rotate"; //$NON-NLS-1$

    public RotationOp() {
        setName(OP_NAME);
    }

    public RotationOp(RotationOp op) {
        super(op);
    }

    @Override
    public RotationOp copy() {
        return new RotationOp(this);
    }

    @Override
    public void process() throws Exception {
        PlanarImage source = (PlanarImage) params.get(Param.INPUT_IMG);
        PlanarImage result = source;
        Integer rotationAngle = Optional.ofNullable((Integer) params.get(P_ROTATE)).orElse(0);
        rotationAngle = rotationAngle % 360;

        if (rotationAngle != 0) {
            // optimize rotation by right angles
            Integer rotOp = null;
            if (rotationAngle == 90) {
                rotOp = Core.ROTATE_90_CLOCKWISE;
            } else if (rotationAngle == 180) {
                rotOp = Core.ROTATE_180;
            } else if (rotationAngle == 270) {
                rotOp = Core.ROTATE_90_COUNTERCLOCKWISE;
            }
            
            if (rotOp != null) {
                result = ImageProcessor.getRotatedImage(source.toMat(), rotOp);
            } else {
                result = ImageProcessor.getRotatedImage(source.toMat(), rotationAngle, source.width() / 2.0,
                    source.height() / 2.0);
            }
        }
        params.put(Param.OUTPUT_IMG, result);
    }

}
