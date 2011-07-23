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

import javax.media.jai.Interpolation;
import javax.media.jai.JAI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.Messages;
import org.weasis.core.api.gui.ImageOperation;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.util.ImageToolkit;
import org.weasis.core.api.media.data.ImageElement;

public class ZoomOperation extends AbstractOperation {
    private static final Logger LOGGER = LoggerFactory.getLogger(ZoomOperation.class);
    public static final String name = Messages.getString("ZoomOperation.title"); //$NON-NLS-1$
    public static final String[] INTERPOLATIONS =
        {
            Messages.getString("ZoomOperation.nearest"), Messages.getString("ZoomOperation.bilinear"), Messages.getString("ZoomOperation.bicubic"), Messages.getString("ZoomOperation.bicubic2") }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    public static final String INTERPOLATION_CMD = "zoomInterpolation"; //$NON-NLS-1$

    @Override
    public String getOperationName() {
        return name;
    }

    @Override
    public RenderedImage getRenderedImage(RenderedImage source, ImageOperation imageOperation) {
        Double zoomFactor = (Double) imageOperation.getActionValue(ActionW.ZOOM.cmd());
        ImageElement image = imageOperation.getImage();
        if (zoomFactor == null || image == null) {
            result = source;
            LOGGER.warn("Cannot apply \"{}\" because a parameter is null", name); //$NON-NLS-1$
        } else if (zoomFactor == 1.0 && image.getRescaleX() == 1.0 && image.getRescaleY() == 1.0) {
            result = source;
        } else {
            float val = (float) Math.abs(zoomFactor);
            ParameterBlock pb = new ParameterBlock();
            pb.addSource(source);
            pb.add(val * (float) image.getRescaleX());
            pb.add(val * (float) image.getRescaleY());
            pb.add(0.0f);
            pb.add(0.0f);
            pb.add(getInterpolation(imageOperation));

            result = JAI.create("scale", pb, ImageToolkit.NOCACHE_HINT); //$NON-NLS-1$
        }
        return result;
    }

    public static Interpolation getInterpolation(ImageOperation imageOperation) {
        Integer interpolation = (Integer) imageOperation.getActionValue(INTERPOLATION_CMD);
        if (interpolation == null || interpolation < 0 || interpolation > 3) {
            interpolation = 1;
        }
        return Interpolation.getInstance(interpolation);
    }
}
