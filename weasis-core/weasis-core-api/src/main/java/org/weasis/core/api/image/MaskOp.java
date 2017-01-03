/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.api.image;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.geom.Area;
import java.awt.image.RenderedImage;

import org.weasis.core.api.image.cv.ImageProcessor;

public class MaskOp extends AbstractOp {

    public static final String OP_NAME = "Mask"; //$NON-NLS-1$

    /**
     * Set whether the shutter is applied (Required parameter).
     *
     * Boolean value.
     */
    public static final String P_SHOW = "show"; //$NON-NLS-1$
    public static final String P_SHAPE = "shape"; //$NON-NLS-1$
    public static final String P_RGB_COLOR = "rgb.color"; //$NON-NLS-1$
    public static final String P_GRAY_TRANSPARENCY = "img.transparency"; //$NON-NLS-1$

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
        RenderedImage source = (RenderedImage) params.get(Param.INPUT_IMG);
        RenderedImage result = source;

        Boolean mask = (Boolean) params.get(P_SHOW);
        Area area = (Area) params.get(P_SHAPE);

        if (mask != null && mask && area != null
            && !area.equals(new Area(new Rectangle(0, 0, source.getWidth(), source.getHeight())))) {
            Integer transparency = (Integer) params.get(P_GRAY_TRANSPARENCY);
            Color color = getMaskColor();
            result = ImageProcessor.applyShutter(source, area, color);
        }
        params.put(Param.OUTPUT_IMG, result);
    }

    private Color getMaskColor() {
        Color color = (Color) params.get(P_RGB_COLOR);
        return color == null ? Color.BLACK : color;
    }
}
