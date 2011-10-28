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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.Messages;
import org.weasis.core.api.gui.ImageOperation;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.util.ImageToolkit;
import org.weasis.core.api.media.data.ImageElement;

public class WindowLevelOperation extends AbstractOperation {
    private static final Logger LOGGER = LoggerFactory.getLogger(WindowLevelOperation.class);

    public static final String name = Messages.getString("WindowLevelOperation.title"); //$NON-NLS-1$

    @Override
    public RenderedImage getRenderedImage(RenderedImage source, ImageOperation imageOperation) {
        ImageElement image = imageOperation.getImage();
        Float window = (Float) imageOperation.getActionValue(ActionW.WINDOW.cmd());
        Float level = (Float) imageOperation.getActionValue(ActionW.LEVEL.cmd());
        if (image == null || window == null || level == null) {
            result = source;
            LOGGER.warn("Cannot apply \"{}\" because a parameter is null", name); //$NON-NLS-1$
        } else {
            Boolean padding = (Boolean) imageOperation.getActionValue(ActionW.IMAGE_PIX_PADDING.cmd());
            result =
                ImageToolkit.getDefaultRenderedImage(image, source, image.getPixelWindow(window),
                    image.getPixelLevel(level), padding == null ? true : padding);
        }
        return result;
    }

    @Override
    public String getOperationName() {
        return name;
    }

}
