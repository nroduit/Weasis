/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.opencv.op;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.ComponentSampleModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferDouble;
import java.awt.image.DataBufferFloat;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferShort;
import java.awt.image.DataBufferUShort;
import java.awt.image.MultiPixelPackedSampleModel;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;

public class ImageConversion {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImageConversion.class);

    /**
     * Converts/writes a Mat into a BufferedImage.
     * 
     * @param matrix
     * 
     * @return BufferedImage
     */
    public static BufferedImage toBufferedImage(Mat matrix) {
        if (matrix == null) {
            return null;
        }
        
        int cols = matrix.cols();
        int rows = matrix.rows();
        int type = matrix.type();
        int elemSize = CvType.ELEM_SIZE(type);
        int channels = CvType.channels(type);
        int bpp = (elemSize * 8) / channels;

        ColorSpace cs;
        WritableRaster raster;
        ComponentColorModel colorModel;
        int dataType = convertToDataType(type);

        switch (channels) {
            case 1:
                cs = ColorSpace.getInstance(ColorSpace.CS_GRAY);
                colorModel = new ComponentColorModel(cs, new int[] { bpp }, false, true, Transparency.OPAQUE, dataType);
                raster = colorModel.createCompatibleWritableRaster(cols, rows);
                break;
            case 3:
                cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
                colorModel = new ComponentColorModel(cs, new int[] { bpp, bpp, bpp }, false, false, Transparency.OPAQUE,
                    dataType);
                raster = Raster.createInterleavedRaster(dataType, cols, rows, cols * channels, channels,
                    new int[] { 2, 1, 0 }, null);
                break;
            default:
                throw new UnsupportedOperationException(
                    "No implementation to handle " + channels + " channels");
        }

        DataBuffer buf = raster.getDataBuffer();

        if (buf instanceof DataBufferByte) {
            matrix.get(0, 0, ((DataBufferByte) buf).getData());
        } else if (buf instanceof DataBufferUShort) {
            matrix.get(0, 0, ((DataBufferUShort) buf).getData());
        } else if (buf instanceof DataBufferShort) {
            matrix.get(0, 0, ((DataBufferShort) buf).getData());
        } else if (buf instanceof DataBufferInt) {
            matrix.get(0, 0, ((DataBufferInt) buf).getData());
        } else if (buf instanceof DataBufferFloat) {
            matrix.get(0, 0, ((DataBufferFloat) buf).getData());
        } else if (buf instanceof DataBufferDouble) {
            matrix.get(0, 0, ((DataBufferDouble) buf).getData());
        }
        return new BufferedImage(colorModel, raster, false, null);

    }

    public static BufferedImage toBufferedImage(PlanarImage matrix) {
        if (matrix == null) {
            return null;
        }
        return toBufferedImage(matrix.toMat());
    }
    
    public static void releaseMat(Mat mat) {
        if (mat != null) {
            mat.release();
        }
    }
    
    public static void releasePlanarImage(PlanarImage img) {
        if (img != null) {
            img.release();
        }
    }


    public static int convertToDataType(int cvType) {
        switch (CvType.depth(cvType)) {
            case CvType.CV_8U:
            case CvType.CV_8S:
                return DataBuffer.TYPE_BYTE;
            case CvType.CV_16U:
                return DataBuffer.TYPE_USHORT;
            case CvType.CV_16S:
                return DataBuffer.TYPE_SHORT;
            case CvType.CV_32S:
                return DataBuffer.TYPE_INT;
            case CvType.CV_32F:
                return DataBuffer.TYPE_FLOAT;
            case CvType.CV_64F:
                return DataBuffer.TYPE_DOUBLE;
            default:
                throw new java.lang.UnsupportedOperationException("Unsupported CvType value: " + cvType);
        }
    }

    public static ImageCV toMat(RenderedImage img) {
        return toMat(img, null);
    }

    public static ImageCV toMat(RenderedImage img, Rectangle region) {
        return toMat(img, region, true);
    }

    public static ImageCV toMat(RenderedImage img, Rectangle region, boolean toBGR) {
        Raster raster = region == null ? img.getData() : img.getData(region);
        DataBuffer buf = raster.getDataBuffer();
        int[] samples = raster.getSampleModel().getSampleSize();
        int[] offsets;
        if (raster.getSampleModel() instanceof ComponentSampleModel) {
            offsets = ((ComponentSampleModel) raster.getSampleModel()).getBandOffsets();
        } else {
            offsets = new int[samples.length];
            for (int i = 0; i < offsets.length; i++) {
                offsets[i] = i;
            }
        }

        if (isBinary(raster.getSampleModel())) {
            // Sonar false positive: not mandatory to close ImageCV (can be done with finalize())
            ImageCV mat = new ImageCV(raster.getHeight(), raster.getWidth(), CvType.CV_8UC1);  //NOSONAR
            mat.put(0, 0, getUnpackedBinaryData(raster, raster.getBounds()));
            return mat;
        }

        if (buf instanceof DataBufferByte) {
            if (Arrays.equals(offsets, new int[] { 0, 0, 0 })) {

                Mat b = new Mat(raster.getHeight(), raster.getWidth(), CvType.CV_8UC1);
                b.put(0, 0, ((DataBufferByte) buf).getData(2));
                Mat g = new Mat(raster.getHeight(), raster.getWidth(), CvType.CV_8UC1);
                g.put(0, 0, ((DataBufferByte) buf).getData(1));
                ImageCV r = new ImageCV(raster.getHeight(), raster.getWidth(), CvType.CV_8UC1);
                r.put(0, 0, ((DataBufferByte) buf).getData(0));
                List<Mat> mv = toBGR ? Arrays.asList(b, g, r) : Arrays.asList(r, g, b);
                ImageCV dstImg = new ImageCV(raster.getHeight(), raster.getWidth(), CvType.CV_8UC3);
                Core.merge(mv, dstImg);
                return dstImg;
            }

            ImageCV mat = new ImageCV(raster.getHeight(), raster.getWidth(), CvType.CV_8UC(samples.length));
            mat.put(0, 0, ((DataBufferByte) buf).getData());
            if (toBGR && Arrays.equals(offsets, new int[] { 0, 1, 2 })) {
                ImageCV dstImg = new ImageCV();
                Imgproc.cvtColor(mat, dstImg, Imgproc.COLOR_RGB2BGR);
                return dstImg;
            } else if (!toBGR && Arrays.equals(offsets, new int[] { 2, 1, 0 })) {
                ImageCV dstImg = new ImageCV();
                Imgproc.cvtColor(mat, dstImg, Imgproc.COLOR_BGR2RGB);
                return dstImg;
            }
            return mat;
        } else if (buf instanceof DataBufferUShort) {
            ImageCV mat = new ImageCV(raster.getHeight(), raster.getWidth(), CvType.CV_16UC(samples.length));
            mat.put(0, 0, ((DataBufferUShort) buf).getData());
            return mat;
        } else if (buf instanceof DataBufferShort) {
            ImageCV mat = new ImageCV(raster.getHeight(), raster.getWidth(), CvType.CV_16SC(samples.length));
            mat.put(0, 0, ((DataBufferShort) buf).getData());
            return mat;
        } else if (buf instanceof DataBufferInt) {
            ImageCV mat = new ImageCV(raster.getHeight(), raster.getWidth(), CvType.CV_32SC(samples.length));
            mat.put(0, 0, ((DataBufferInt) buf).getData());
            return mat;
        } else if (buf instanceof DataBufferFloat) {
            ImageCV mat = new ImageCV(raster.getHeight(), raster.getWidth(), CvType.CV_32FC(samples.length));
            mat.put(0, 0, ((DataBufferFloat) buf).getData());
            return mat;
        } else if (buf instanceof DataBufferDouble) {
            ImageCV mat = new ImageCV(raster.getHeight(), raster.getWidth(), CvType.CV_64FC(samples.length));
            mat.put(0, 0, ((DataBufferDouble) buf).getData());
            return mat;
        }

        return null;
    }

    public static Rectangle getBounds(PlanarImage img) {
        return new Rectangle(0, 0, img.width(), img.height());
    }

    public static BufferedImage convertTo(RenderedImage src, int imageType) {
        BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), imageType);
        Graphics2D big = dst.createGraphics();
        try {
            big.drawRenderedImage(src, AffineTransform.getTranslateInstance(0.0, 0.0));
        } finally {
            big.dispose();
        }
        return dst;
    }

    public static boolean isBinary(SampleModel sm) {
        return sm instanceof MultiPixelPackedSampleModel && ((MultiPixelPackedSampleModel) sm).getPixelBitStride() == 1
            && sm.getNumBands() == 1;
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
                return new BufferedImage(cm, wr, cm.isAlphaPremultiplied(), null);
            }
        }
        return source;
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
}
