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

import java.awt.image.DataBuffer;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;

import javax.media.jai.JAI;
import javax.media.jai.PlanarImage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.Messages;
import org.weasis.core.api.image.util.ImageToolkit;

public class AutoLevelsOp extends AbstractOp {
    private static final Logger LOGGER = LoggerFactory.getLogger(AutoLevelsOp.class);

    public static final String OP_NAME = Messages.getString("AutoLevelsOp.auto_ct"); //$NON-NLS-1$

    /**
     * Set whether auto levels is applied to the image (Required parameter).
     *
     * Boolean value.
     */
    public static final String P_AUTO_LEVEL = "auto.level"; //$NON-NLS-1$

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
        RenderedImage source = (RenderedImage) params.get(Param.INPUT_IMG);
        RenderedImage result = source;
        Boolean auto = (Boolean) params.get(P_AUTO_LEVEL);

        if (auto != null && auto) {
            ParameterBlock pb = new ParameterBlock();
            pb.addSource(source);
            PlanarImage dst = JAI.create("extrema", pb, ImageToolkit.NOCACHE_HINT); //$NON-NLS-1$
            double[][] extrema = (double[][]) dst.getProperty("extrema"); //$NON-NLS-1$

            int numBands = dst.getSampleModel().getNumBands();
            double[] slopes = new double[numBands];
            double[] yInts = new double[numBands];
            // find the overall min, max (all bands)
            for (int i = 0; i < numBands; i++) {
                double range = extrema[1][i] - extrema[0][i];
                if (range < 1.0) {
                    range = 1.0;
                }
                slopes[i] = 255.0D / range;
                yInts[i] = 255.0D - slopes[i] * extrema[1][i];
            }

            // Rescaling from xxx to byte range
            pb = new ParameterBlock();
            pb.addSource(source);
            pb.add(slopes);
            pb.add(yInts);
            dst = JAI.create("rescale", pb, null); //$NON-NLS-1$

            // Produce a byte image
            pb = new ParameterBlock();
            pb.addSource(dst);
            pb.add(DataBuffer.TYPE_BYTE);
            result = JAI.create("format", pb, null); //$NON-NLS-1$
        }

        params.put(Param.OUTPUT_IMG, result);
    }

}
