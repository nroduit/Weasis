/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.api.image;

import java.awt.Rectangle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.Messages;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageProcessor;

public class CropOp extends AbstractOp {
    private static final Logger LOGGER = LoggerFactory.getLogger(CropOp.class);

    public static final String OP_NAME = Messages.getString("CropOperation.name"); //$NON-NLS-1$

    /**
     * Set the area to crop (Required parameter).
     *
     * java.awt.Rectangle value.
     */
    public static final String P_AREA = "area"; //$NON-NLS-1$

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
            area = area.intersection(new Rectangle(0, 0, source.width(), source.height()));
            if (area.width > 1 && area.height > 1) {
                result = ImageProcessor.crop(source.toMat(), area);
            }
        }
        params.put(Param.OUTPUT_IMG, result);
    }

}
