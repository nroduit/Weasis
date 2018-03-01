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

import javax.media.jai.ImageLayout;
import javax.media.jai.PackedImageData;
import javax.media.jai.PixelAccessor;
import javax.media.jai.PointOpImage;
import javax.media.jai.UnpackedImageData;

/**
 * The Class ThresholdToBinOpImage.
 *
 * @author Nicolas Roduit
 */
/**
 * SampleOpImage is an extension of PointOpImage that takes two integer parameters and one source and performs a
 * modified threshold operation on the given source.
 */
public class ThresholdToBinOpImage extends PointOpImage {

    private double min;
    private double max;
    private boolean inverse = false;
    /**
     * Lookup table for ORing bytes of output.
     */
    private static byte[] byteTable = new byte[] { (byte) 0x80, (byte) 0x40, (byte) 0x20, (byte) 0x10, (byte) 0x08,
        (byte) 0x04, (byte) 0x02, (byte) 0x01, };

    /**
     * Constructs an SampleOpImage. The image dimensions are copied from the source image. The tile grid layout,
     * SampleModel, and ColorModel may optionally be specified by an ImageLayout object.
     *
     * @param source
     *            a RenderedImage.
     * @param layout
     *            an ImageLayout optionally containing the tile grid layout, SampleModel, and ColorModel, or null.
     */
    public ThresholdToBinOpImage(RenderedImage source, Map map, ImageLayout layout, double min, double max) {
        super(source, layout, map, true);
        this.min = min;
        this.max = max;
        // cas limite, si l'inverse est sélectionné et que les valeurs min et max sont égales, alors la sélection se
        // fait comme
        // si l'inverse n'était pas sélectionné, donc seuille une intensité unique.
        if (min > max) {
            inverse = true;
        }
    }

    @Override
    protected void computeRect(Raster[] sources, WritableRaster dest, Rectangle destRect) {
        // Depending on the base dataType of the RasterAccessors,
        // either the byteLoop or intLoop method is called. The two
        // functions are virtually the same, except for the data type
        // of the underlying arrays.
        switch (sources[0].getSampleModel().getDataType()) {
            case DataBuffer.TYPE_BYTE:
                byteLoop(sources[0], dest, destRect);
                break;
            case DataBuffer.TYPE_SHORT:
                shortLoop(sources[0], dest, destRect);
                break;
            case DataBuffer.TYPE_USHORT:
                unsignedShortLoop(sources[0], dest, destRect);
                break;
            case DataBuffer.TYPE_INT:
                intLoop(sources[0], dest, destRect);
                break;
            case DataBuffer.TYPE_FLOAT:
                floatLoop(sources[0], dest, destRect);
                break;
            case DataBuffer.TYPE_DOUBLE:
                doubleLoop(sources[0], dest, destRect);
                break;
            default:
                String className = this.getClass().getName();
                throw new RuntimeException(className + " does not implement computeRect" //$NON-NLS-1$
                    + " for int/short/float/double data"); //$NON-NLS-1$
        }
    }

    private void byteLoop(Raster source, WritableRaster dest, Rectangle destRect) {
        Rectangle srcRect = mapDestRect(destRect, 0); // should be identical to destRect
        PixelAccessor pa = new PixelAccessor(dest.getSampleModel(), null);
        PackedImageData pid = pa.getPackedPixels(dest, destRect, true, false);
        int offset = pid.offset;
        PixelAccessor srcPa = new PixelAccessor(source.getSampleModel(), null);
        UnpackedImageData srcImD = srcPa.getPixels(source, srcRect, DataBuffer.TYPE_BYTE, false);
        int srcOffset = srcImD.bandOffsets[0];
        byte[] srcData = ((byte[][]) srcImD.data)[0];
        int pixelStride = srcImD.pixelStride;
        int ind0 = pid.bitOffset;
        for (int h = 0; h < destRect.height; h++) {
            for (int b = ind0, s = srcOffset; b < ind0 + destRect.width; b++, s += pixelStride) {
                int pixel = srcData[s] & 0xFF;
                if (inverse) {
                    if (pixel > min || pixel < max) {
                        pid.data[offset + (b >> 3)] |= byteTable[b % 8];
                    }
                } else {
                    if (pixel >= min && pixel <= max) {
                        pid.data[offset + (b >> 3)] |= byteTable[b % 8];
                    }
                }
            }
            offset += pid.lineStride;
            srcOffset += srcImD.lineStride;
        }
        pa.setPackedPixels(pid);
    }

    private void shortLoop(Raster source, WritableRaster dest, Rectangle destRect) {
        Rectangle srcRect = mapDestRect(destRect, 0); // should be identical to destRect
        PixelAccessor pa = new PixelAccessor(dest.getSampleModel(), null);
        PackedImageData pid = pa.getPackedPixels(dest, destRect, true, false);
        int offset = pid.offset;
        PixelAccessor srcPa = new PixelAccessor(source.getSampleModel(), null);
        UnpackedImageData srcImD = srcPa.getPixels(source, srcRect, DataBuffer.TYPE_SHORT, false);
        int srcOffset = srcImD.bandOffsets[0];
        short[] srcData = ((short[][]) srcImD.data)[0];
        int pixelStride = srcImD.pixelStride;
        int ind0 = pid.bitOffset;
        for (int h = 0; h < destRect.height; h++) {
            for (int b = ind0, s = srcOffset; b < ind0 + destRect.width; b++, s += pixelStride) {
                int pixel = srcData[s];
                if (inverse) {
                    if (pixel > min || pixel < max) {
                        pid.data[offset + (b >> 3)] |= byteTable[b % 8];
                    }
                } else {
                    if (pixel >= min && pixel <= max) {
                        pid.data[offset + (b >> 3)] |= byteTable[b % 8];
                    }
                }
            }
            offset += pid.lineStride;
            srcOffset += srcImD.lineStride;
        }
        pa.setPackedPixels(pid);
    }

    private void unsignedShortLoop(Raster source, WritableRaster dest, Rectangle destRect) {
        Rectangle srcRect = mapDestRect(destRect, 0); // should be identical to destRect
        PixelAccessor pa = new PixelAccessor(dest.getSampleModel(), null);
        PackedImageData pid = pa.getPackedPixels(dest, destRect, true, false);
        int offset = pid.offset;
        PixelAccessor srcPa = new PixelAccessor(source.getSampleModel(), null);
        UnpackedImageData srcImD = srcPa.getPixels(source, srcRect, DataBuffer.TYPE_USHORT, false);
        int srcOffset = srcImD.bandOffsets[0];
        short[] srcData = ((short[][]) srcImD.data)[0];
        int pixelStride = srcImD.pixelStride;
        int ind0 = pid.bitOffset;
        for (int h = 0; h < destRect.height; h++) {
            for (int b = ind0, s = srcOffset; b < ind0 + destRect.width; b++, s += pixelStride) {
                int pixel = srcData[s] & 0xffff;
                if (inverse) {
                    if (pixel > min || pixel < max) {
                        pid.data[offset + (b >> 3)] |= byteTable[b % 8];
                    }
                } else {
                    if (pixel >= min && pixel <= max) {
                        pid.data[offset + (b >> 3)] |= byteTable[b % 8];
                    }
                }
            }
            offset += pid.lineStride;
            srcOffset += srcImD.lineStride;
        }
        pa.setPackedPixels(pid);
    }

    private void intLoop(Raster source, WritableRaster dest, Rectangle destRect) {
        Rectangle srcRect = mapDestRect(destRect, 0); // should be identical to destRect
        PixelAccessor pa = new PixelAccessor(dest.getSampleModel(), null);
        PackedImageData pid = pa.getPackedPixels(dest, destRect, true, false);
        int offset = pid.offset;
        PixelAccessor srcPa = new PixelAccessor(source.getSampleModel(), null);
        UnpackedImageData srcImD = srcPa.getPixels(source, srcRect, DataBuffer.TYPE_INT, false);
        int srcOffset = srcImD.bandOffsets[0];
        int[] srcData = ((int[][]) srcImD.data)[0];
        int pixelStride = srcImD.pixelStride;
        int ind0 = pid.bitOffset;
        for (int h = 0; h < destRect.height; h++) {
            for (int b = ind0, s = srcOffset; b < ind0 + destRect.width; b++, s += pixelStride) {
                int pixel = srcData[s];
                if (inverse) {
                    if (pixel > min || pixel < max) {
                        pid.data[offset + (b >> 3)] |= byteTable[b % 8];
                    }
                } else {
                    if (pixel >= min && pixel <= max) {
                        pid.data[offset + (b >> 3)] |= byteTable[b % 8];
                    }
                }
            }
            offset += pid.lineStride;
            srcOffset += srcImD.lineStride;
        }
        pa.setPackedPixels(pid);
    }

    private void floatLoop(Raster source, WritableRaster dest, Rectangle destRect) {
        Rectangle srcRect = mapDestRect(destRect, 0); // should be identical to destRect
        PixelAccessor pa = new PixelAccessor(dest.getSampleModel(), null);
        PackedImageData pid = pa.getPackedPixels(dest, destRect, true, false);
        int offset = pid.offset;
        PixelAccessor srcPa = new PixelAccessor(source.getSampleModel(), null);
        UnpackedImageData srcImD = srcPa.getPixels(source, srcRect, DataBuffer.TYPE_FLOAT, false);
        int srcOffset = srcImD.bandOffsets[0];
        float[] srcData = ((float[][]) srcImD.data)[0];
        int pixelStride = srcImD.pixelStride;
        int ind0 = pid.bitOffset;
        for (int h = 0; h < destRect.height; h++) {
            for (int b = ind0, s = srcOffset; b < ind0 + destRect.width; b++, s += pixelStride) {
                float pixel = srcData[s];
                if (inverse) {
                    if (pixel > min || pixel < max) {
                        pid.data[offset + (b >> 3)] |= byteTable[b % 8];
                    }
                } else {
                    if (pixel >= min && pixel <= max) {
                        pid.data[offset + (b >> 3)] |= byteTable[b % 8];
                    }
                }
            }
            offset += pid.lineStride;
            srcOffset += srcImD.lineStride;
        }
        pa.setPackedPixels(pid);
    }

    private void doubleLoop(Raster source, WritableRaster dest, Rectangle destRect) {
        Rectangle srcRect = mapDestRect(destRect, 0); // should be identical to destRect
        PixelAccessor pa = new PixelAccessor(dest.getSampleModel(), null);
        PackedImageData pid = pa.getPackedPixels(dest, destRect, true, false);
        int offset = pid.offset;
        PixelAccessor srcPa = new PixelAccessor(source.getSampleModel(), null);
        UnpackedImageData srcImD = srcPa.getPixels(source, srcRect, DataBuffer.TYPE_DOUBLE, false);
        int srcOffset = srcImD.bandOffsets[0];
        double[] srcData = ((double[][]) srcImD.data)[0];
        int pixelStride = srcImD.pixelStride;
        int ind0 = pid.bitOffset;
        for (int h = 0; h < destRect.height; h++) {
            for (int b = ind0, s = srcOffset; b < ind0 + destRect.width; b++, s += pixelStride) {
                double pixel = srcData[s];
                if (inverse) {
                    if (pixel > min || pixel < max) {
                        pid.data[offset + (b >> 3)] |= byteTable[b % 8];
                    }
                } else {
                    if (pixel >= min && pixel <= max) {
                        pid.data[offset + (b >> 3)] |= byteTable[b % 8];
                    }
                }
            }
            offset += pid.lineStride;
            srcOffset += srcImD.lineStride;
        }
        pa.setPackedPixels(pid);
    }
}
