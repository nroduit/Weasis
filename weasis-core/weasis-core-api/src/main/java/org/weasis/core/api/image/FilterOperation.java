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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.Messages;
import org.weasis.core.api.gui.ImageOperation;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.util.KernelData;

public class FilterOperation extends AbstractOperation {
    private static final Logger LOGGER = LoggerFactory.getLogger(FilterOperation.class);

    public final static String name = Messages.getString("FilterOperation.title"); //$NON-NLS-1$

    public String getOperationName() {
        return name;
    }

    public RenderedImage getRenderedImage(RenderedImage source, ImageOperation imageOperation) {
        KernelData kernel = (KernelData) imageOperation.getActionValue(ActionW.FILTER.cmd());
        if (kernel == null) {
            result = source;
            LOGGER.warn("Cannot apply \"{}\" because a parameter is null", name); //$NON-NLS-1$
        } else if (kernel.equals(KernelData.NONE)) {
            result = source;
        } else {
            ParameterBlock paramBlock = new ParameterBlock();
            paramBlock.addSource(source);
            paramBlock.add(kernel.getKernelJAI());
            result = JAI.create("convolve", paramBlock, null); //$NON-NLS-1$
        }
        return result;
    }

}
