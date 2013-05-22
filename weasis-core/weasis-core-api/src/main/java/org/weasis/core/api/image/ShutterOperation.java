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
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.image.DataBuffer;
import java.awt.image.MultiPixelPackedSampleModel;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;

import javax.media.jai.PlanarImage;
import javax.media.jai.ROIShape;
import javax.media.jai.TiledImage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.ImageOperation;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.op.ShutterDescriptor;
import org.weasis.core.api.image.util.ImageFiler;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;

public class ShutterOperation extends AbstractOperation {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShutterOperation.class);

    public static final String name = ActionW.IMAGE_SHUTTER.getTitle();

    @Override
    public String getOperationName() {
        return name;
    }

    @Override
    public RenderedImage getRenderedImage(RenderedImage source, ImageOperation imageOperation) {
        Boolean shutter = (Boolean) imageOperation.getActionValue(ActionW.IMAGE_SHUTTER.cmd());
        ImageElement image = imageOperation.getImage();
        Area area = null;
        if (shutter == null || image == null) {
            result = source;
            LOGGER.warn("Cannot apply \"{}\" because a parameter is null", name); //$NON-NLS-1$
        } else if (shutter && (area = (Area) imageOperation.getActionValue(TagW.ShutterFinalShape.getName())) != null) {
            Byte[] color = getShutterColor(imageOperation);
            if (isBlack(color)) {
                result = ShutterDescriptor.create(source, new ROIShape(area), getShutterColor(imageOperation), null);
            } else {
                result =
                    CombineTwoImagesOperation.combineTwoImages(source,
                        ImageFiler.getEmptyImage(color, source.getWidth(), source.getHeight()),
                        getAsImage(area, source));
            }
        } else {
            result = source;
        }

        return result;
    }

    private boolean isBlack(Byte[] color) {
        for (Byte i : color) {
            if (i != 0) {
                return false;
            }
        }
        return true;
    }

    private Byte[] getShutterColor(ImageOperation imageOperation) {
        Color color = (Color) imageOperation.getActionValue(TagW.ShutterRGBColor.getName());
        if (color == null) {
            /*
             * A single gray unsigned value used to replace those parts of the image occluded by the shutter, when
             * rendered on a monochrome display. The units are specified in P-Values, from a minimum of 0000H (black) up
             * to a maximum of FFFFH (white).
             */
            Integer val = (Integer) imageOperation.getActionValue(TagW.ShutterPSValue.getName());
            return val == null ? new Byte[] { 0 } : new Byte[] { (byte) (val >> 8) };
        } else {
            Byte[] bandValues = { (byte) color.getRed(), (byte) color.getGreen(), (byte) color.getBlue() };
            return bandValues;
        }
    }

    private PlanarImage getAsImage(Area shape, RenderedImage source) {
        SampleModel sm =
            new MultiPixelPackedSampleModel(DataBuffer.TYPE_BYTE, source.getWidth(), source.getHeight(), 1);
        TiledImage ti =
            new TiledImage(source.getMinX(), source.getMinY(), source.getWidth(), source.getHeight(),
                source.getTileGridXOffset(), source.getTileGridYOffset(), sm, PlanarImage.createColorModel(sm));
        Graphics2D g2d = ti.createGraphics();
        // Write the Shape into the TiledImageGraphics.
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.fill(shape);
        g2d.dispose();
        return ti;
    }
}
