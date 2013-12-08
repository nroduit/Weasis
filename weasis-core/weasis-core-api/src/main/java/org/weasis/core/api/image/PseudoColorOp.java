/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.api.image;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;

import javax.media.jai.JAI;
import javax.media.jai.LookupTableJAI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.Messages;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.image.op.ByteLut;
import org.weasis.core.api.image.util.ImageToolkit;

public class PseudoColorOp extends AbstractOp {
    private static final Logger LOGGER = LoggerFactory.getLogger(PseudoColorOp.class);

    public static final String OP_NAME = Messages.getString("PseudoColorOperation.title"); //$NON-NLS-1$

    /**
     * Set the lookup table (Required parameter).
     * 
     * org.weasis.core.api.image.op.ByteLut value.
     */
    public static final String P_LUT = ActionW.LUT.cmd();

    /**
     * Whether the LUT must be inverted (Optional parameter).
     * 
     * Boolean value. Default value is false.
     */

    public static final String P_LUT_INVERSE = ActionW.INVERSELUT.cmd();

    public PseudoColorOp() {
        setName(OP_NAME);
    }

    @Override
    public void process() throws Exception {
        RenderedImage source = (RenderedImage) params.get(INPUT_IMG);
        RenderedImage result = source;
        ByteLut lutTable = (ByteLut) params.get(P_LUT);

        if (lutTable == null) {
            LOGGER.warn("Cannot apply \"{}\" because a parameter is null", OP_NAME); //$NON-NLS-1$
        } else {
            boolean invert = JMVUtils.getNULLtoFalse(params.get(P_LUT_INVERSE));
            byte[][] lut = invert ? lutTable.getInvertedLutTable() : lutTable.getLutTable();
            if (lut == null) {
                if (invert) {
                    ParameterBlock pb = new ParameterBlock();
                    pb.addSource(source);
                    result = JAI.create("invert", pb, ImageToolkit.NOCACHE_HINT); //$NON-NLS-1$
                }
            } else {
                // TODO check LUT type with sample data type.
                ParameterBlock pb = new ParameterBlock();
                pb.addSource(source);
                pb.add(new LookupTableJAI(lut));
                result = JAI.create("lookup", pb, ImageToolkit.NOCACHE_HINT); //$NON-NLS-1$
            }
        }

        params.put(OUTPUT_IMG, result);
    }

    public static BufferedImage getLUT(byte[][] lut) {
        BufferedImage image = new BufferedImage(20, 256, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        for (int k = 0; k < 256; k++) {
            g.setPaint(new Color(lut[0][k] & 0xff, lut[1][k] & 0xff, lut[2][k] & 0xff));
            g.fillRect(0, k, 20, 1);
        }
        return image;
    }

}
