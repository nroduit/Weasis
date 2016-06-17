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
import org.weasis.core.api.image.op.ShutterDescriptor;
import org.weasis.core.api.image.util.ImageFiler;

public class MaskOp extends AbstractOp {
    private static final Logger LOGGER = LoggerFactory.getLogger(MaskOp.class);

    public static final String OP_NAME = "Mask";

    /**
     * Set whether the shutter is applied (Required parameter).
     *
     * Boolean value.
     */
    public static final String P_SHOW = "show"; //$NON-NLS-1$
    public static final String P_SHAPE = "shape"; //$NON-NLS-1$
    public static final String P_RGB_COLOR = "rgb.color"; //$NON-NLS-1$
    public static final String P_GRAY_TRANSPARENCY = "img.transparency"; //$NON-NLS-1$

    public MaskOp() {
        setName(OP_NAME);
    }

    public MaskOp(MaskOp op) {
        super(op);
    }

    @Override
    public MaskOp copy() {
        return new MaskOp(this);
    }

    @Override
    public void process() throws Exception {
        RenderedImage source = (RenderedImage) params.get(Param.INPUT_IMG);
        RenderedImage result = source;

        Boolean mask = (Boolean) params.get(P_SHOW);
        Area area = (Area) params.get(P_SHAPE);

        if (mask == null) {
            LOGGER.warn("Cannot apply \"{}\" because a parameter is null", OP_NAME); //$NON-NLS-1$
        } else if (mask && area != null) {
            Integer transparency = (Integer) params.get(P_GRAY_TRANSPARENCY);
            Byte[] color = getMaskColor();
            if (transparency == null && isBlack(color)) {
                result = ShutterDescriptor.create(source, new ROIShape(area), getMaskColor(), null);
            } else {
                RenderedImage sourceUP;
                if (transparency != null) {
                    sourceUP = MergeImgOp.combineTwoImages(source,
                        ImageFiler.getEmptyImage(Color.GRAY, source.getWidth(), source.getHeight()), transparency);
                } else {
                    sourceUP = ImageFiler.getEmptyImage(color, source.getWidth(), source.getHeight());
                }
                result = MergeImgOp.combineTwoImages(source, sourceUP, getAsImage(area, source));
            }
        }

        params.put(Param.OUTPUT_IMG, result);
    }

    private boolean isBlack(Byte[] color) {
        for (Byte i : color) {
            if (i != 0) {
                return false;
            }
        }
        return true;
    }

    private Byte[] getMaskColor() {
        Color color = (Color) params.get(P_RGB_COLOR);
        if (color == null) {
            return new Byte[] { 0 };
        } else {
            return new Byte[] { (byte) color.getRed(), (byte) color.getGreen(), (byte) color.getBlue() };
        }
    }

    private PlanarImage getAsImage(Area shape, RenderedImage source) {
        SampleModel sm =
            new MultiPixelPackedSampleModel(DataBuffer.TYPE_BYTE, source.getWidth(), source.getHeight(), 1);
        TiledImage ti = new TiledImage(source.getMinX(), source.getMinY(), source.getWidth(), source.getHeight(),
            source.getTileGridXOffset(), source.getTileGridYOffset(), sm, PlanarImage.createColorModel(sm));
        Graphics2D g2d = ti.createGraphics();
        // Write the Shape into the TiledImageGraphics.
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.fill(shape);
        g2d.dispose();
        return ti;
    }
}
