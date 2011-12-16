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
import javax.media.jai.LookupTableJAI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.Messages;
import org.weasis.core.api.gui.ImageOperation;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.media.data.ImageElement;

public class WindowLevelOperation extends AbstractOperation {
    private static final Logger LOGGER = LoggerFactory.getLogger(WindowLevelOperation.class);

    public static final String name = Messages.getString("WindowLevelOperation.title"); //$NON-NLS-1$

    @Override
    public RenderedImage getRenderedImage(RenderedImage source, ImageOperation imageOperation) {
        ImageElement image = imageOperation.getImage();

        Float level = (Float) imageOperation.getActionValue(ActionW.LEVEL.cmd());
        Float window = (Float) imageOperation.getActionValue(ActionW.WINDOW.cmd());
        LutShape lutShape = (LutShape) imageOperation.getActionValue(ActionW.LUT_SHAPE.cmd());

        if (image == null || source == null) {
            LOGGER.warn("Cannot apply \"{}\" ", name);
        } else if (image instanceof DicomImageElement) {
            LookupTableJAI lookup = ((DicomImageElement) image).getVOILookup(window, level, lutShape);

            if (lookup != null) {
                ParameterBlock pb = new ParameterBlock();
                pb.addSource(source);
                pb.add(lookup);
                result = JAI.create("lookup", pb, null); // Will add tiles in cache tile memory
            }
        }
        return result;
    }

    @Override
    public String getOperationName() {
        return name;
    }

}
