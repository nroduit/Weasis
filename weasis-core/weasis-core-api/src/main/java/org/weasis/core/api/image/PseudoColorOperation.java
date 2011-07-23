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
import org.weasis.core.api.gui.ImageOperation;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.op.ByteLut;
import org.weasis.core.api.image.util.ImageToolkit;

public class PseudoColorOperation extends AbstractOperation {
    private static final Logger LOGGER = LoggerFactory.getLogger(PseudoColorOperation.class);

    public static final String name = Messages.getString("PseudoColorOperation.title"); //$NON-NLS-1$

    public String getOperationName() {
        return name;
    }

    public RenderedImage getRenderedImage(RenderedImage source, ImageOperation imageOperation) {
        Boolean invert = (Boolean) imageOperation.getActionValue(ActionW.INVERSELUT.cmd());
        ByteLut lutTable = (ByteLut) imageOperation.getActionValue(ActionW.LUT.cmd());
        if (invert == null) {
            invert = false;
        }
        if (lutTable == null) {
            result = source;
            LOGGER.warn("Cannot apply \"{}\" because a parameter is null", name); //$NON-NLS-1$
        } else {
            byte[][] lut = invert ? lutTable.getInvertedLutTable() : lutTable.getLutTable();
            if (lut == null) {
                if (invert) {
                    ParameterBlock pb = new ParameterBlock();
                    pb.addSource(source);
                    result = JAI.create("invert", pb, ImageToolkit.NOCACHE_HINT); //$NON-NLS-1$
                } else {
                    result = source;
                }
            } else {
                ParameterBlock pb = new ParameterBlock();
                pb.addSource(source);
                pb.add(new LookupTableJAI(lut));
                result = JAI.create("lookup", pb, ImageToolkit.NOCACHE_HINT); //$NON-NLS-1$
            }
        }
        return result;
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
