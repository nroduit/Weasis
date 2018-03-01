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
import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;

import javax.media.jai.JAI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.Messages;
import org.weasis.core.api.gui.util.MathUtil;
import org.weasis.core.api.util.LangUtil;

public class CropOp extends AbstractOp {
    private static final Logger LOGGER = LoggerFactory.getLogger(CropOp.class);

    public static final String OP_NAME = Messages.getString("CropOperation.name"); //$NON-NLS-1$

    /**
     * Set the area to crop (Required parameter).
     *
     * java.awt.Rectangle value.
     */
    public static final String P_AREA = "area"; //$NON-NLS-1$

    /**
     * Whether or not the image origin is shift after cropping.
     *
     * Boolean value. Default value is false (keep the original image referential).
     */
    public static final String P_SHIFT_TO_ORIGIN = "shift.origin"; //$NON-NLS-1$

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
        RenderedImage source = (RenderedImage) params.get(Param.INPUT_IMG);
        RenderedImage result = source;
        Rectangle area = (Rectangle) params.get(P_AREA);

        if (area != null) {
            area = area
                .intersection(new Rectangle(source.getMinX(), source.getMinY(), source.getWidth(), source.getHeight()));
            if (area.width > 1 && area.height > 1) {
                ParameterBlock pb = new ParameterBlock();
                pb.addSource(source);
                pb.add((float) area.x).add((float) area.y);
                pb.add((float) area.width).add((float) area.height);
                result = JAI.create("crop", pb, null); //$NON-NLS-1$

                if (LangUtil.getNULLtoFalse((Boolean) params.get(P_SHIFT_TO_ORIGIN))) {
                    float diffw = (float) source.getMinX() - result.getMinX();
                    float diffh = (float) source.getMinY() - result.getMinY();
                    if (MathUtil.isDifferentFromZero(diffw) || MathUtil.isDifferentFromZero(diffh)) {
                        pb = new ParameterBlock();
                        pb.addSource(result);
                        pb.add(diffw);
                        pb.add(diffh);
                        result = JAI.create("translate", pb, null); //$NON-NLS-1$
                    }
                }
            }
        }
        params.put(Param.OUTPUT_IMG, result);
    }

}
