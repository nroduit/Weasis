/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.api.image.util;

import java.awt.Rectangle;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferShort;
import java.awt.image.DataBufferUShort;
import java.awt.image.IndexColorModel;
import java.awt.image.MultiPixelPackedSampleModel;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.util.Hashtable;

import org.weasis.core.api.gui.util.MathUtil;
import org.weasis.core.api.image.cv.ImageProcessor;
import org.weasis.core.api.media.data.ImageElement;

/**
 * An image manipulation toolkit.
 *
 */
public class ImageToolkit {

    private ImageToolkit() {
    }

    public static ColorModel getDefaultColorModel(int dataType, int numBands) {
        if (dataType < DataBuffer.TYPE_BYTE || dataType > DataBuffer.TYPE_DOUBLE || numBands < 1 || numBands > 4) {
            return null;
        }

        ColorSpace cs =
            numBands <= 2 ? ColorSpace.getInstance(ColorSpace.CS_GRAY) : ColorSpace.getInstance(ColorSpace.CS_sRGB);

        boolean useAlpha = (numBands == 2) || (numBands == 4);
        int transparency = useAlpha ? Transparency.TRANSLUCENT : Transparency.OPAQUE;

        return RasterFactory.createComponentColorModel(dataType, cs, useAlpha, false, transparency);

    }

    public static boolean isBinary(SampleModel sm) {
        return sm instanceof MultiPixelPackedSampleModel && ((MultiPixelPackedSampleModel) sm).getPixelBitStride() == 1
            && sm.getNumBands() == 1;
    }

    /**
     * Fix the issue in ComponentColorModel when signed short DataBuffer is less than 16 bit (only 16 bits is
     * supported).
     * 
     * @param source
     * @param shiftBit
     */
    public static void fixSignedShortDataBuffer(RenderedImage source, int shiftBit) {
        if (source != null && source.getSampleModel().getDataType() == DataBuffer.TYPE_SHORT) {
            Raster raster = source.getData();
            if (raster.getDataBuffer() instanceof DataBufferShort) {
                int limit = (1 << shiftBit) / 2;
                short[] s = ((DataBufferShort) raster.getDataBuffer()).getData();
                for (int i = 0; i < s.length; i++) {
                    if (s[i] >= limit) {
                        s[i] = (short) (s[i] - (1 << shiftBit));
                    }
                }
            }
        }
    }

    /**
     * Bug fix: CLibImageReader and J2KImageReaderCodecLib (imageio libs) do not handle negative values for short data.
     * They convert signed short to unsigned short.
     * 
     * @param source
     * @return 
     */
    public static RenderedImage fixSignedShortDataBuffer(RenderedImage source) {
        if (source != null && source.getSampleModel().getDataType() == DataBuffer.TYPE_USHORT) {
            Raster raster = source.getData();
            if (raster.getDataBuffer() instanceof DataBufferUShort) {
                short[] s = ((DataBufferUShort) raster.getDataBuffer()).getData();
                DataBufferShort db = new DataBufferShort(s, s.length);
                ColorModel cm = source.getColorModel();
                WritableRaster wr = Raster.createWritableRaster(source.getSampleModel(), db, null);
                return new BufferedImage(cm, wr , cm.isAlphaPremultiplied(), null);
            }
        }
        return source;
    }

    public static Rectangle getBounds(RenderedImage img) {
        return new Rectangle(img.getMinX(), img.getMinY(), img.getWidth(), img.getHeight());
    }

    public static BufferedImage convertRenderedImage(RenderedImage img) {
        if (img == null) {
            return null;
        }
        if (img instanceof BufferedImage) {
            return (BufferedImage) img;
        }
        ColorModel cm = img.getColorModel();
        int width = img.getWidth();
        int height = img.getHeight();
        WritableRaster raster = cm.createCompatibleWritableRaster(width, height);
        boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
        Hashtable<String, Object> properties = new Hashtable<>();
        String[] keys = img.getPropertyNames();
        if (keys != null) {
            for (int i = 0; i < keys.length; i++) {
                properties.put(keys[i], img.getProperty(keys[i]));
            }
        }
        BufferedImage result = new BufferedImage(cm, raster, isAlphaPremultiplied, properties);
        img.copyData(raster);
        return result;
    }

    /**
     * For the case of binary data (<code>isBinary()</code> returns <code>true</code>), return the binary data as a
     * packed byte array. The data will be packed as eight bits per byte with no bit offset, i.e., the first bit in each
     * image line will be the left-most of the first byte of the line. The line stride in bytes will be
     * <code>(int)((getWidth()+7)/8)</code>. The length of the returned array will be the line stride multiplied by
     * <code>getHeight()</code>
     *
     * @return the binary data as a packed array of bytes with zero offset of <code>null</code> if the data are not
     *         binary.
     * @throws IllegalArgumentException
     *             if <code>isBinary()</code> returns <code>false</code> with the <code>SampleModel</code> of the
     *             supplied <code>Raster</code> as argument.
     */
    public static byte[] getPackedBinaryData(Raster raster, Rectangle rect) {
        SampleModel sm = raster.getSampleModel();
        if (!isBinary(sm)) {
            throw new IllegalArgumentException("Not a binary raster!");
        }

        int rectX = rect.x;
        int rectY = rect.y;
        int rectWidth = rect.width;
        int rectHeight = rect.height;

        DataBuffer dataBuffer = raster.getDataBuffer();

        int dx = rectX - raster.getSampleModelTranslateX();
        int dy = rectY - raster.getSampleModelTranslateY();

        MultiPixelPackedSampleModel mpp = (MultiPixelPackedSampleModel) sm;
        int lineStride = mpp.getScanlineStride();
        int eltOffset = dataBuffer.getOffset() + mpp.getOffset(dx, dy);
        int bitOffset = mpp.getBitOffset(dx);

        int numBytesPerRow = (rectWidth + 7) / 8;
        if (dataBuffer instanceof DataBufferByte && eltOffset == 0 && bitOffset == 0 && numBytesPerRow == lineStride
            && ((DataBufferByte) dataBuffer).getData().length == numBytesPerRow * rectHeight) {
            return ((DataBufferByte) dataBuffer).getData();
        }

        byte[] binaryDataArray = new byte[numBytesPerRow * rectHeight];

        int b = 0;

        if (bitOffset == 0) {
            if (dataBuffer instanceof DataBufferByte) {
                byte[] data = ((DataBufferByte) dataBuffer).getData();
                int stride = numBytesPerRow;
                int offset = 0;
                for (int y = 0; y < rectHeight; y++) {
                    System.arraycopy(data, eltOffset, binaryDataArray, offset, stride);
                    offset += stride;
                    eltOffset += lineStride;
                }
            } else if (dataBuffer instanceof DataBufferShort || dataBuffer instanceof DataBufferUShort) {
                short[] data = dataBuffer instanceof DataBufferShort ? ((DataBufferShort) dataBuffer).getData()
                    : ((DataBufferUShort) dataBuffer).getData();

                for (int y = 0; y < rectHeight; y++) {
                    int xRemaining = rectWidth;
                    int i = eltOffset;
                    while (xRemaining > 8) {
                        short datum = data[i++];
                        binaryDataArray[b++] = (byte) ((datum >>> 8) & 0xFF);
                        binaryDataArray[b++] = (byte) (datum & 0xFF);
                        xRemaining -= 16;
                    }
                    if (xRemaining > 0) {
                        binaryDataArray[b++] = (byte) ((data[i] >>> 8) & 0XFF);
                    }
                    eltOffset += lineStride;
                }
            } else if (dataBuffer instanceof DataBufferInt) {
                int[] data = ((DataBufferInt) dataBuffer).getData();

                for (int y = 0; y < rectHeight; y++) {
                    int xRemaining = rectWidth;
                    int i = eltOffset;
                    while (xRemaining > 24) {
                        int datum = data[i++];
                        binaryDataArray[b++] = (byte) ((datum >>> 24) & 0xFF);
                        binaryDataArray[b++] = (byte) ((datum >>> 16) & 0xFF);
                        binaryDataArray[b++] = (byte) ((datum >>> 8) & 0xFF);
                        binaryDataArray[b++] = (byte) (datum & 0xFF);
                        xRemaining -= 32;
                    }
                    int shift = 24;
                    while (xRemaining > 0) {
                        binaryDataArray[b++] = (byte) ((data[i] >>> shift) & 0xFF);
                        shift -= 8;
                        xRemaining -= 8;
                    }
                    eltOffset += lineStride;
                }
            }
        } else { // bitOffset != 0
            if (dataBuffer instanceof DataBufferByte) {
                byte[] data = ((DataBufferByte) dataBuffer).getData();

                if ((bitOffset & 7) == 0) {
                    int stride = numBytesPerRow;
                    int offset = 0;
                    for (int y = 0; y < rectHeight; y++) {
                        System.arraycopy(data, eltOffset, binaryDataArray, offset, stride);
                        offset += stride;
                        eltOffset += lineStride;
                    }
                } else { // bitOffset % 8 != 0
                    int leftShift = bitOffset & 7;
                    int rightShift = 8 - leftShift;
                    for (int y = 0; y < rectHeight; y++) {
                        int i = eltOffset;
                        int xRemaining = rectWidth;
                        while (xRemaining > 0) {
                            if (xRemaining > rightShift) {
                                binaryDataArray[b++] =
                                    (byte) (((data[i++] & 0xFF) << leftShift) | ((data[i] & 0xFF) >>> rightShift));
                            } else {
                                binaryDataArray[b++] = (byte) ((data[i] & 0xFF) << leftShift);
                            }
                            xRemaining -= 8;
                        }
                        eltOffset += lineStride;
                    }
                }
            } else if (dataBuffer instanceof DataBufferShort || dataBuffer instanceof DataBufferUShort) {
                short[] data = dataBuffer instanceof DataBufferShort ? ((DataBufferShort) dataBuffer).getData()
                    : ((DataBufferUShort) dataBuffer).getData();

                for (int y = 0; y < rectHeight; y++) {
                    int bOffset = bitOffset;
                    for (int x = 0; x < rectWidth; x += 8, bOffset += 8) {
                        int i = eltOffset + bOffset / 16;
                        int mod = bOffset % 16;
                        int left = data[i] & 0xFFFF;
                        if (mod <= 8) {
                            binaryDataArray[b++] = (byte) (left >>> (8 - mod));
                        } else {
                            int delta = mod - 8;
                            int right = data[i + 1] & 0xFFFF;
                            binaryDataArray[b++] = (byte) ((left << delta) | (right >>> (16 - delta)));
                        }
                    }
                    eltOffset += lineStride;
                }
            } else if (dataBuffer instanceof DataBufferInt) {
                int[] data = ((DataBufferInt) dataBuffer).getData();

                for (int y = 0; y < rectHeight; y++) {
                    int bOffset = bitOffset;
                    for (int x = 0; x < rectWidth; x += 8, bOffset += 8) {
                        int i = eltOffset + bOffset / 32;
                        int mod = bOffset % 32;
                        int left = data[i];
                        if (mod <= 24) {
                            binaryDataArray[b++] = (byte) (left >>> (24 - mod));
                        } else {
                            int delta = mod - 24;
                            int right = data[i + 1];
                            binaryDataArray[b++] = (byte) ((left << delta) | (right >>> (32 - delta)));
                        }
                    }
                    eltOffset += lineStride;
                }
            }
        }

        return binaryDataArray;
    }

    /**
     * Returns the binary data unpacked into an array of bytes. The line stride will be the width of the
     * <code>Raster</code>.
     *
     * @throws IllegalArgumentException
     *             if <code>isBinary()</code> returns <code>false</code> with the <code>SampleModel</code> of the
     *             supplied <code>Raster</code> as argument.
     */
    public static byte[] getUnpackedBinaryData(Raster raster, Rectangle rect) {
        SampleModel sm = raster.getSampleModel();
        if (!isBinary(sm)) {
            throw new IllegalArgumentException("Not a binary raster!");
        }

        int rectX = rect.x;
        int rectY = rect.y;
        int rectWidth = rect.width;
        int rectHeight = rect.height;

        DataBuffer dataBuffer = raster.getDataBuffer();

        int dx = rectX - raster.getSampleModelTranslateX();
        int dy = rectY - raster.getSampleModelTranslateY();

        MultiPixelPackedSampleModel mpp = (MultiPixelPackedSampleModel) sm;
        int lineStride = mpp.getScanlineStride();
        int eltOffset = dataBuffer.getOffset() + mpp.getOffset(dx, dy);
        int bitOffset = mpp.getBitOffset(dx);

        byte[] bdata = new byte[rectWidth * rectHeight];
        int maxY = rectY + rectHeight;
        int maxX = rectX + rectWidth;
        int k = 0;

        if (dataBuffer instanceof DataBufferByte) {
            byte[] data = ((DataBufferByte) dataBuffer).getData();
            for (int y = rectY; y < maxY; y++) {
                int bOffset = eltOffset * 8 + bitOffset;
                for (int x = rectX; x < maxX; x++) {
                    byte b = data[bOffset / 8];
                    bdata[k++] = (byte) ((b >>> (7 - bOffset & 7)) & 0x0000001);
                    bOffset++;
                }
                eltOffset += lineStride;
            }
        } else if (dataBuffer instanceof DataBufferShort || dataBuffer instanceof DataBufferUShort) {
            short[] data = dataBuffer instanceof DataBufferShort ? ((DataBufferShort) dataBuffer).getData()
                : ((DataBufferUShort) dataBuffer).getData();
            for (int y = rectY; y < maxY; y++) {
                int bOffset = eltOffset * 16 + bitOffset;
                for (int x = rectX; x < maxX; x++) {
                    short s = data[bOffset / 16];
                    bdata[k++] = (byte) ((s >>> (15 - bOffset % 16)) & 0x0000001);
                    bOffset++;
                }
                eltOffset += lineStride;
            }
        } else if (dataBuffer instanceof DataBufferInt) {
            int[] data = ((DataBufferInt) dataBuffer).getData();
            for (int y = rectY; y < maxY; y++) {
                int bOffset = eltOffset * 32 + bitOffset;
                for (int x = rectX; x < maxX; x++) {
                    int i = data[bOffset / 32];
                    bdata[k++] = (byte) ((i >>> (31 - bOffset % 32)) & 0x0000001);
                    bOffset++;
                }
                eltOffset += lineStride;
            }
        }

        return bdata;
    }

    /**
     * Sets the supplied <code>Raster</code>'s data from an array of packed binary data of the form returned by
     * <code>getPackedBinaryData()</code>.
     *
     * @throws IllegalArgumentException
     *             if <code>isBinary()</code> returns <code>false</code> with the <code>SampleModel</code> of the
     *             supplied <code>Raster</code> as argument.
     */
    public static void setPackedBinaryData(byte[] binaryDataArray, WritableRaster raster, Rectangle rect) {
        SampleModel sm = raster.getSampleModel();
        if (!isBinary(sm)) {
            throw new IllegalArgumentException("Not a binary raster!");
        }

        int rectX = rect.x;
        int rectY = rect.y;
        int rectWidth = rect.width;
        int rectHeight = rect.height;

        DataBuffer dataBuffer = raster.getDataBuffer();

        int dx = rectX - raster.getSampleModelTranslateX();
        int dy = rectY - raster.getSampleModelTranslateY();

        MultiPixelPackedSampleModel mpp = (MultiPixelPackedSampleModel) sm;
        int lineStride = mpp.getScanlineStride();
        int eltOffset = dataBuffer.getOffset() + mpp.getOffset(dx, dy);
        int bitOffset = mpp.getBitOffset(dx);

        int b = 0;

        if (bitOffset == 0) {
            if (dataBuffer instanceof DataBufferByte) {
                byte[] data = ((DataBufferByte) dataBuffer).getData();
                if (data == binaryDataArray) {
                    // Optimal case: simply return.
                    return;
                }
                int stride = (rectWidth + 7) / 8;
                int offset = 0;
                for (int y = 0; y < rectHeight; y++) {
                    System.arraycopy(binaryDataArray, offset, data, eltOffset, stride);
                    offset += stride;
                    eltOffset += lineStride;
                }
            } else if (dataBuffer instanceof DataBufferShort || dataBuffer instanceof DataBufferUShort) {
                short[] data = dataBuffer instanceof DataBufferShort ? ((DataBufferShort) dataBuffer).getData()
                    : ((DataBufferUShort) dataBuffer).getData();

                for (int y = 0; y < rectHeight; y++) {
                    int xRemaining = rectWidth;
                    int i = eltOffset;
                    while (xRemaining > 8) {
                        data[i++] = (short) (((binaryDataArray[b++] & 0xFF) << 8) | (binaryDataArray[b++] & 0xFF));
                        xRemaining -= 16;
                    }
                    if (xRemaining > 0) {
                        data[i++] = (short) ((binaryDataArray[b++] & 0xFF) << 8);
                    }
                    eltOffset += lineStride;
                }
            } else if (dataBuffer instanceof DataBufferInt) {
                int[] data = ((DataBufferInt) dataBuffer).getData();

                for (int y = 0; y < rectHeight; y++) {
                    int xRemaining = rectWidth;
                    int i = eltOffset;
                    while (xRemaining > 24) {
                        data[i++] = ((binaryDataArray[b++] & 0xFF) << 24) | ((binaryDataArray[b++] & 0xFF) << 16)
                            | ((binaryDataArray[b++] & 0xFF) << 8) | (binaryDataArray[b++] & 0xFF);
                        xRemaining -= 32;
                    }
                    int shift = 24;
                    while (xRemaining > 0) {
                        data[i] |= (binaryDataArray[b++] & 0xFF) << shift;
                        shift -= 8;
                        xRemaining -= 8;
                    }
                    eltOffset += lineStride;
                }
            }
        } else { // bitOffset != 0
            int stride = (rectWidth + 7) / 8;
            int offset = 0;
            if (dataBuffer instanceof DataBufferByte) {
                byte[] data = ((DataBufferByte) dataBuffer).getData();

                if ((bitOffset & 7) == 0) {
                    for (int y = 0; y < rectHeight; y++) {
                        System.arraycopy(binaryDataArray, offset, data, eltOffset, stride);
                        offset += stride;
                        eltOffset += lineStride;
                    }
                } else { // bitOffset % 8 != 0
                    int rightShift = bitOffset & 7;
                    int leftShift = 8 - rightShift;
                    int leftShift8 = 8 + leftShift;
                    int mask = (byte) (255 << leftShift);
                    int mask1 = (byte) ~mask;

                    for (int y = 0; y < rectHeight; y++) {
                        int i = eltOffset;
                        int xRemaining = rectWidth;
                        while (xRemaining > 0) {
                            byte datum = binaryDataArray[b++];

                            if (xRemaining > leftShift8) {
                                // when all the bits in this BYTE will be set
                                // into the data buffer.
                                data[i] = (byte) ((data[i] & mask) | ((datum & 0xFF) >>> rightShift));
                                data[++i] = (byte) ((datum & 0xFF) << leftShift);
                            } else if (xRemaining > leftShift) {
                                // All the "leftShift" high bits will be set
                                // into the data buffer. But not all the
                                // "rightShift" low bits will be set.
                                data[i] = (byte) ((data[i] & mask) | ((datum & 0xFF) >>> rightShift));
                                i++;
                                data[i] = (byte) ((data[i] & mask1) | ((datum & 0xFF) << leftShift));
                            } else {
                                // Less than "leftShift" high bits will be set.
                                int remainMask = (1 << leftShift - xRemaining) - 1;
                                data[i] = (byte) ((data[i] & (mask | remainMask))
                                    | (datum & 0xFF) >>> rightShift & ~remainMask);
                            }
                            xRemaining -= 8;
                        }
                        eltOffset += lineStride;
                    }
                }
            } else if (dataBuffer instanceof DataBufferShort || dataBuffer instanceof DataBufferUShort) {
                short[] data = dataBuffer instanceof DataBufferShort ? ((DataBufferShort) dataBuffer).getData()
                    : ((DataBufferUShort) dataBuffer).getData();

                int rightShift = bitOffset & 7;
                int leftShift = 8 - rightShift;
                int leftShift16 = 16 + leftShift;
                int mask = (short) (~(255 << leftShift));
                int mask1 = (short) (65535 << leftShift);
                int mask2 = (short) ~mask1;

                for (int y = 0; y < rectHeight; y++) {
                    int bOffset = bitOffset;
                    int xRemaining = rectWidth;
                    for (int x = 0; x < rectWidth; x += 8, bOffset += 8, xRemaining -= 8) {
                        int i = eltOffset + (bOffset >> 4);
                        int mod = bOffset & 15;
                        int datum = binaryDataArray[b++] & 0xFF;
                        if (mod <= 8) {
                            // This BYTE is set into one SHORT
                            if (xRemaining < 8) {
                                // Mask the bits to be set.
                                datum &= 255 << 8 - xRemaining;
                            }
                            data[i] = (short) ((data[i] & mask) | (datum << leftShift));
                        } else if (xRemaining > leftShift16) {
                            // This BYTE will be set into two SHORTs
                            data[i] = (short) ((data[i] & mask1) | ((datum >>> rightShift) & 0xFFFF));
                            data[++i] = (short) ((datum << leftShift) & 0xFFFF);
                        } else if (xRemaining > leftShift) {
                            // This BYTE will be set into two SHORTs;
                            // But not all the low bits will be set into SHORT
                            data[i] = (short) ((data[i] & mask1) | ((datum >>> rightShift) & 0xFFFF));
                            i++;
                            data[i] = (short) ((data[i] & mask2) | ((datum << leftShift) & 0xFFFF));
                        } else {
                            // Only some of the high bits will be set into
                            // SHORTs
                            int remainMask = (1 << leftShift - xRemaining) - 1;
                            data[i] = (short) ((data[i] & (mask1 | remainMask))
                                | ((datum >>> rightShift) & 0xFFFF & ~remainMask));
                        }
                    }
                    eltOffset += lineStride;
                }
            } else if (dataBuffer instanceof DataBufferInt) {
                int[] data = ((DataBufferInt) dataBuffer).getData();
                int rightShift = bitOffset & 7;
                int leftShift = 8 - rightShift;
                int leftShift32 = 32 + leftShift;
                int mask = 0xFFFFFFFF << leftShift;
                int mask1 = ~mask;

                for (int y = 0; y < rectHeight; y++) {
                    int bOffset = bitOffset;
                    int xRemaining = rectWidth;
                    for (int x = 0; x < rectWidth; x += 8, bOffset += 8, xRemaining -= 8) {
                        int i = eltOffset + (bOffset >> 5);
                        int mod = bOffset & 31;
                        int datum = binaryDataArray[b++] & 0xFF;
                        if (mod <= 24) {
                            // This BYTE is set into one INT
                            int shift = 24 - mod;
                            if (xRemaining < 8) {
                                // Mask the bits to be set.
                                datum &= 255 << 8 - xRemaining;
                            }
                            data[i] = (data[i] & (~(255 << shift))) | (datum << shift);
                        } else if (xRemaining > leftShift32) {
                            // All the bits of this BYTE will be set into two INTs
                            data[i] = (data[i] & mask) | (datum >>> rightShift);
                            data[++i] = datum << leftShift;
                        } else if (xRemaining > leftShift) {
                            // This BYTE will be set into two INTs;
                            // But not all the low bits will be set into INT
                            data[i] = (data[i] & mask) | (datum >>> rightShift);
                            i++;
                            data[i] = (data[i] & mask1) | (datum << leftShift);
                        } else {
                            // Only some of the high bits will be set into INT
                            int remainMask = (1 << leftShift - xRemaining) - 1;
                            data[i] = (data[i] & (mask | remainMask)) | (datum >>> rightShift & ~remainMask);
                        }
                    }
                    eltOffset += lineStride;
                }
            }
        }
    }

    /**
     * Copies data into the packed array of the <code>Raster</code> from an array of unpacked data of the form returned
     * by <code>getUnpackedBinaryData()</code>.
     *
     * <p>
     * If the data are binary, then the target bit will be set if and only if the corresponding byte is non-zero.
     *
     * @throws IllegalArgumentException
     *             if <code>isBinary()</code> returns <code>false</code> with the <code>SampleModel</code> of the
     *             supplied <code>Raster</code> as argument.
     */
    public static void setUnpackedBinaryData(byte[] bdata, WritableRaster raster, Rectangle rect) {
        SampleModel sm = raster.getSampleModel();
        if (!isBinary(sm)) {
            throw new IllegalArgumentException("Not a binary raster!");
        }

        int rectX = rect.x;
        int rectY = rect.y;
        int rectWidth = rect.width;
        int rectHeight = rect.height;

        DataBuffer dataBuffer = raster.getDataBuffer();

        int dx = rectX - raster.getSampleModelTranslateX();
        int dy = rectY - raster.getSampleModelTranslateY();

        MultiPixelPackedSampleModel mpp = (MultiPixelPackedSampleModel) sm;
        int lineStride = mpp.getScanlineStride();
        int eltOffset = dataBuffer.getOffset() + mpp.getOffset(dx, dy);
        int bitOffset = mpp.getBitOffset(dx);

        int k = 0;

        if (dataBuffer instanceof DataBufferByte) {
            byte[] data = ((DataBufferByte) dataBuffer).getData();
            for (int y = 0; y < rectHeight; y++) {
                int bOffset = eltOffset * 8 + bitOffset;
                for (int x = 0; x < rectWidth; x++) {
                    if (bdata[k++] != (byte) 0) {
                        data[bOffset / 8] |= (byte) (0x00000001 << (7 - bOffset & 7));
                    }
                    bOffset++;
                }
                eltOffset += lineStride;
            }
        } else if (dataBuffer instanceof DataBufferShort || dataBuffer instanceof DataBufferUShort) {
            short[] data = dataBuffer instanceof DataBufferShort ? ((DataBufferShort) dataBuffer).getData()
                : ((DataBufferUShort) dataBuffer).getData();
            for (int y = 0; y < rectHeight; y++) {
                int bOffset = eltOffset * 16 + bitOffset;
                for (int x = 0; x < rectWidth; x++) {
                    if (bdata[k++] != (byte) 0) {
                        data[bOffset / 16] |= (short) (0x00000001 << (15 - bOffset % 16));
                    }
                    bOffset++;
                }
                eltOffset += lineStride;
            }
        } else if (dataBuffer instanceof DataBufferInt) {
            int[] data = ((DataBufferInt) dataBuffer).getData();
            for (int y = 0; y < rectHeight; y++) {
                int bOffset = eltOffset * 32 + bitOffset;
                for (int x = 0; x < rectWidth; x++) {
                    if (bdata[k++] != (byte) 0) {
                        data[bOffset / 32] |= 0x00000001 << (31 - bOffset % 32);
                    }
                    bOffset++;
                }
                eltOffset += lineStride;
            }
        }
    }

    /**
     * Convert index color mapped image content to a full 24-bit 16-million color RGB image.
     *
     * @param image
     *            the source image to convert.
     * @return a full RGB color image as RenderedOp.
     */
    public static RenderedImage convertIndexColorToRGBColor(RenderedImage image) {
        RenderedImage result = image;

        // If the source image is color mapped, convert it to 3-band RGB.
        // Note that GIF and PNG files fall into this category.
        if (image.getColorModel() instanceof IndexColorModel) {
            // Retrieve the IndexColorModel
            IndexColorModel icm = (IndexColorModel) image.getColorModel();

            // Cache the number of elements in each band of the colormap.
            int mapSize = icm.getMapSize();

            // Allocate an array for the lookup table data.
            byte[][] lutData = new byte[3][mapSize];

            // Load the lookup table data from the IndexColorModel.
            icm.getReds(lutData[0]);
            icm.getGreens(lutData[1]);
            icm.getBlues(lutData[2]);

            throw new IllegalAccessError();
            // // Create the lookup table object.
            // LookupTableJAI lut = new LookupTableJAI(lutData);
            //
            // // Replace the original image with the 3-band RGB image.
            // result = JAI.create("lookup", image, lut); //$NON-NLS-1$
        }

        return result;
    }

    /**
     * Apply window/level to the image source. Note: this method cannot be used with a DicomImageElement as image
     * parameter.
     *
     * @param image
     * @param source
     * @param window
     * @param level
     * @param pixelPadding
     * @return
     */
    public static RenderedImage getDefaultRenderedImage(ImageElement image, RenderedImage source, double window,
        double level, boolean pixelPadding) {
        if (image == null || source == null) {
            return null;
        }
        SampleModel sampleModel = source.getSampleModel();
        if (sampleModel == null) {
            return null;
        }
        int datatype = sampleModel.getDataType();
        if (datatype == DataBuffer.TYPE_BYTE && MathUtil.isEqual(window, 255.0)
            && (MathUtil.isEqual(level, 127.5) || MathUtil.isEqual(level, 127.0))) {
            return source;
        }

        double low = level - window / 2.0;
        double high = level + window / 2.0;
        // use a lookup table for rescaling
        double range = high - low;
        if (range < 1.0) {
            range = 1.0;
        }

        double slope = 255.0 / range;
        double yInt = 255.0 - slope * high;

        return ImageProcessor.rescaleToByte(source, slope, yInt);
    }

    public static RenderedImage getDefaultRenderedImage(ImageElement image, RenderedImage source,
        boolean pixelPadding) {
        return getDefaultRenderedImage(image, source, image.getDefaultWindow(pixelPadding),
            image.getDefaultLevel(pixelPadding), true);
    }
}
