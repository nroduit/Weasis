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

import java.awt.geom.Area;
import java.awt.image.RenderedImage;

import javax.media.jai.ROIShape;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.ImageOperation;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.op.ShutterDescriptor;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;

public class ShutterOperation extends AbstractOperation {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShutterOperation.class);

    public static final String name = ActionW.IMAGE_SCHUTTER.getTitle();

    @Override
    public String getOperationName() {
        return name;
    }

    @Override
    public RenderedImage getRenderedImage(RenderedImage source, ImageOperation imageOperation) {
        Boolean shutter = (Boolean) imageOperation.getActionValue(ActionW.IMAGE_SCHUTTER.cmd());
        ImageElement image = imageOperation.getImage();
        Area area = null;
        if (shutter == null || image == null) {
            result = source;
            LOGGER.warn("Cannot apply \"{}\" because a parameter is null", name); //$NON-NLS-1$
        } else if (shutter && (area = (Area) imageOperation.getActionValue(TagW.ShutterFinalShape.getName())) != null) {
            result = ShutterDescriptor.create(source, new ROIShape(area), getShutterColor(imageOperation), null);
            // result =
            // combineTwoImagesOperation.combineTwoImages(
            // source,
            // ImageFiler.getEmptyImage(getShutterColor(imageOperation, source), source.getWidth(),
            // source.getHeight()), new ROIShape(area).getAsImage());
        } else {
            result = source;
        }

        return result;
    }

    private int[] getShutterColor(ImageOperation imageOperation) {
        int[] color = (int[]) imageOperation.getActionValue(TagW.ShutterRGBColor.getName());
        if (color != null) {
            // TODO convert LAB to RGB, and inversion when LUT is inverted
            // CIELab.getInstance().toRGB(color);
        }

        if (color == null) {
            // A single gray unsigned value used to
            // replace those parts of the image occluded
            // by the shutter, when rendered on a
            // monochrome display. The units are
            // specified in P-Values, from a minimum of
            // 0000H (black) up to a maximum of FFFFH
            // (white).
            Integer val = (Integer) imageOperation.getActionValue(TagW.ShutterPSValue.getName());
            color = val == null ? new int[] { 0 } : new int[] { val >> 8 };
        }
        // color = new int[] { 1300 };
        // Boolean invLut = (Boolean) imageOperation.getActionValue(ActionW.INVERSELUT.cmd());
        // if (invLut != null && invLut) {
        // for (int i = 0; i < color.length; i++) {
        // color[i] = 255 - color[i];
        // }
        // }
        // Byte[] bandValues;
        // if (source.getSampleModel().getNumBands() == 3) {
        // if (color == null || color.length < 3) {
        // color = new int[3];
        // }
        // bandValues = new Byte[] { (byte) color[0], (byte) color[1], (byte) color[2] };
        // } else {
        // if (color == null || color.length < 1) {
        // color = new int[1];
        // }
        // bandValues = new Byte[] { (byte) color[0] };
        // }
        return color;
    }
}
