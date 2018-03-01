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
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.WritableRaster;
import java.util.Map;

import javax.media.jai.ColormapOpImage;
import javax.media.jai.ImageLayout;
import javax.media.jai.RasterAccessor;
import javax.media.jai.RasterFormatTag;

import com.sun.media.jai.util.ImageUtil;

final class RectifyUShortToShortDataOpImage extends ColormapOpImage {

    /**
     * Constructor.
     *
     * @param source
     *            The source image.
     * @param layout
     *            The destination image layout.
     */
    public RectifyUShortToShortDataOpImage(RenderedImage source, Map config, ImageLayout layout) {
        super(source, layout, config, true);

        // Set flag to permit in-place operation.
        permitInPlaceOperation();

        // Initialize the colormap if necessary.
        initializeColormapOperation();
    }

    /**
     * Transform the colormap according to the rescaling parameters. Should never go through this function.
     */
    @Override
    protected void transformColormap(byte[][] colormap) {
        for (int b = 0; b < 3; b++) {
            byte[] map = colormap[b];
            int mapSize = map.length;
            for (int i = 0; i < mapSize; i++) {
                map[i] = ImageUtil.clampRoundByte((map[i] & 0xFF));
            }
        }
    }

    /**
     * Operation to correct signed images (9-15 bits) read by imageio codecs.
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
        RasterFormatTag srcTag = new RasterFormatTag(getSampleModel(), DataBuffer.TYPE_USHORT | 1024);
        RasterFormatTag dstTag = new RasterFormatTag(dest.getSampleModel(), DataBuffer.TYPE_SHORT | 1024);

        Rectangle srcRect = mapDestRect(destRect, 0);

        RasterAccessor dst = new RasterAccessor(dest, destRect, dstTag, getColorModel());
        RasterAccessor src = new RasterAccessor(sources[0], srcRect, srcTag, getSourceImage(0).getColorModel());

        switch (dst.getDataType()) {
            case DataBuffer.TYPE_SHORT:
                computeRectShort(src, dst);
                break;
        }
        /* Do not clamp dst data. */
        dst.copyDataToRaster();
    }

    private void computeRectShort(RasterAccessor src, RasterAccessor dst) {
        int dstWidth = dst.getWidth();
        int dstHeight = dst.getHeight();
        int dstBands = dst.getNumBands();

        int dstLineStride = dst.getScanlineStride();
        int dstPixelStride = dst.getPixelStride();
        int[] dstBandOffsets = dst.getBandOffsets();
        short[][] dstData = dst.getShortDataArrays();

        int srcLineStride = src.getScanlineStride();
        int srcPixelStride = src.getPixelStride();
        int[] srcBandOffsets = src.getBandOffsets();
        short[][] srcData = src.getShortDataArrays();

        for (int b = 0; b < dstBands; b++) {
            short[] d = dstData[b];
            short[] s = srcData[b];
            if (s.length == d.length) {
                System.arraycopy(s, 0, d, 0, d.length);
            } else {
                int dstLineOffset = dstBandOffsets[b];
                int srcLineOffset = srcBandOffsets[b];

                for (int h = 0; h < dstHeight; h++) {
                    int dstPixelOffset = dstLineOffset;
                    int srcPixelOffset = srcLineOffset;

                    dstLineOffset += dstLineStride;
                    srcLineOffset += srcLineStride;

                    for (int w = 0; w < dstWidth; w++) {
                        d[dstPixelOffset] = s[srcPixelOffset];
                        dstPixelOffset += dstPixelStride;
                        srcPixelOffset += srcPixelStride;
                    }
                }
            }
        }
    }
}
