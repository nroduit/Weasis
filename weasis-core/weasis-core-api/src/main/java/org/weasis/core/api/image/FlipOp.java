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

import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;

import javax.media.jai.JAI;
import javax.media.jai.operator.TransposeDescriptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.Messages;
import org.weasis.core.api.image.util.ImageToolkit;

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

    @Override
    public void process() throws Exception {
        RenderedImage source = (RenderedImage) params.get(INPUT_IMG);
        RenderedImage result = source;
        Boolean flip = (Boolean) params.get(P_FLIP);

        if (flip == null) {
            LOGGER.warn("Cannot apply \"{}\" because a parameter is null", OP_NAME); //$NON-NLS-1$
        } else if (flip) {
            // use Transpose operation
            ParameterBlock param = new ParameterBlock();
            param.addSource(source);
            param.add(TransposeDescriptor.FLIP_HORIZONTAL);
            result = JAI.create("transpose", param, ImageToolkit.NOCACHE_HINT); //$NON-NLS-1$
        }

        params.put(OUTPUT_IMG, result);
    }
}
