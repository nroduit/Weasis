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

import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;

import javax.media.jai.Interpolation;
import javax.media.jai.JAI;

import org.weasis.core.api.Messages;
import org.weasis.core.api.gui.util.MathUtil;
import org.weasis.core.api.image.util.ImageToolkit;

public class ZoomOp extends AbstractOp {

    public static final String OP_NAME = Messages.getString("ZoomOperation.title"); //$NON-NLS-1$

    public static final String[] INTERPOLATIONS =
        { Messages.getString("ZoomOperation.nearest"), Messages.getString("ZoomOperation.bilinear"), //$NON-NLS-1$ //$NON-NLS-2$
            Messages.getString("ZoomOperation.bicubic"), Messages.getString("ZoomOperation.bicubic2") }; //$NON-NLS-1$ //$NON-NLS-2$

    /**
     * Set a zoom factor in x-axis (Required parameter).
     *
     * Double value.
     */
    public static final String P_RATIO_X = "ratio.x"; //$NON-NLS-1$

    /**
     * Set a zoom factor in y-axis (Required parameter).
     *
     * Double value.
     */
    public static final String P_RATIO_Y = "ratio.y"; //$NON-NLS-1$

    /**
     * Set the interpolation type (Optional parameter).
     *
     * Integer value. Default value is bilinear interpolation. See javax.media.jai.Interpolation.
     */
    public static final String P_INTERPOLATION = "interpolation"; //$NON-NLS-1$

    public ZoomOp() {
        setName(OP_NAME);
    }

    public ZoomOp(ZoomOp op) {
        super(op);
    }

    @Override
    public ZoomOp copy() {
        return new ZoomOp(this);
    }

    @Override
    public void process() throws Exception {
        RenderedImage source = (RenderedImage) params.get(Param.INPUT_IMG);
        RenderedImage result = source;
        Double zoomFactorX = (Double) params.get(P_RATIO_X);
        Double zoomFactorY = (Double) params.get(P_RATIO_Y);

        if (zoomFactorX != null && zoomFactorY != null
            && (MathUtil.isDifferent(zoomFactorX, 1.0) || MathUtil.isDifferent(zoomFactorY, 1.0))) {
            ParameterBlock pb = new ParameterBlock();
            pb.addSource(source);
            pb.add(Math.abs(zoomFactorX.floatValue()));
            pb.add(Math.abs(zoomFactorY.floatValue()));
            pb.add(0.0f);
            pb.add(0.0f);
            pb.add(getInterpolation());

            result = JAI.create("scale", pb, ImageToolkit.NOCACHE_HINT); //$NON-NLS-1$
        }

        params.put(Param.OUTPUT_IMG, result);
    }

    public Interpolation getInterpolation() {
        Integer interpolation = (Integer) params.get(P_INTERPOLATION);
        if (interpolation == null || interpolation < 0 || interpolation > 3) {
            interpolation = 1;
        }
        return Interpolation.getInstance(interpolation);
    }
}
