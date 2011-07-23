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
import org.weasis.core.api.gui.ImageOperation;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.util.ImageToolkit;

public class FlipOperation extends AbstractOperation {
    private static final Logger LOGGER = LoggerFactory.getLogger(FlipOperation.class);

    public static final String name = Messages.getString("FlipOperation.title"); //$NON-NLS-1$

    public String getOperationName() {
        return name;
    }

    public RenderedImage getRenderedImage(RenderedImage source, ImageOperation imageOperation) {
        Boolean flip = (Boolean) imageOperation.getActionValue(ActionW.FLIP.cmd());
        if (flip == null) {
            result = source;
            LOGGER.warn("Cannot apply \"{}\" because a parameter is null", name); //$NON-NLS-1$
        } else if (flip) {
            // use Transpose operation
            ParameterBlock param = new ParameterBlock();
            param.addSource(source);
            param.add(TransposeDescriptor.FLIP_HORIZONTAL);
            result = JAI.create("transpose", param, ImageToolkit.NOCACHE_HINT); //$NON-NLS-1$
        } else {
            result = source;
        }
        return result;
    }

}
