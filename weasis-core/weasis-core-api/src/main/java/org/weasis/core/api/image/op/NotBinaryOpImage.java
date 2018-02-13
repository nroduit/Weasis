/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.api.image.op;

import java.awt.Rectangle;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.WritableRaster;
import java.util.Map;

import javax.media.jai.ImageLayout;
import javax.media.jai.PointOpImage;
import javax.media.jai.RasterAccessor;
import javax.media.jai.RasterFormatTag;

final class NotBinaryOpImage extends PointOpImage {

    /**
     * Constructs an <code>NotOpImage</code>.
     *
     * @param source
     *            The source image.
     * @param layout
     *            The destination image layout.
     */
    public NotBinaryOpImage(RenderedImage source, Map config, ImageLayout layout) {
        super(source, layout, config, true);
    }

    /**
     * Nots the pixel values of the source image within a specified rectangle.
     *
     * @param sources
     *            Cobbled sources, guaranteed to provide all the source data necessary for computing the rectangle.
     * @param dest
     *            The tile containing the rectangle to be computed.
     * @param destRect
     *            The rectangle within the tile to be computed.
     */
    @Override
    protected void computeRect(Raster[] sources, WritableRaster dest, Rectangle destRect) {
        // Retrieve format tags.
        RasterFormatTag[] formatTags = getFormatTags();

        RasterAccessor src = new RasterAccessor(sources[0], destRect, formatTags[0], getSourceImage(0).getColorModel());
        RasterAccessor dst = new RasterAccessor(dest, destRect, formatTags[1], getColorModel());

        byte[] srcBits = src.getBinaryDataArray();
        byte[] dstBits = dst.getBinaryDataArray();

        int length = dstBits.length;
        for (int i = 0; i < length; i++) {
            dstBits[i] = (byte) (~(srcBits[i]));
        }

        dst.copyBinaryDataToRaster();
    }
}
