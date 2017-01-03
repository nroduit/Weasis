/*
 * $RCSfile: RasterAccessor.java,v $
 *
 * Copyright (c) 2005 Sun Microsystems, Inc. All rights reserved.
 *
 * Use is subject to license terms.
 *
 * $Revision: 1.1 $
 * $Date: 2005/02/11 04:57:18 $
 * $State: Exp $
 */
package org.weasis.core.api.image.util;

import java.awt.Rectangle;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.ComponentSampleModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferShort;
import java.awt.image.DataBufferUShort;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;


/**
 * An adapter class for presenting non-binary image data in a <code>ComponentSampleModel</code> format and binary image
 * data in a zero-offset byte array format even when the original data are not so stored. <code>RasterAccessor</code> is
 * meant to make the common (<code>ComponentSampleModel</code>) case fast and other formats possible without forcing the
 * <code>OpImage</code> writer to cover more than one case per non-binary data type.
 *
 * <p>
 * When constructing a <code>RasterAccessor</code> with a source(s) that has an IndexColorModel and a destination that
 * has a non-<code>IndexColorModel</code>, <code>RasterAccessor</code> will perform expansion of the source pixels. If
 * the source(s) and the destination have an IndexColorModel, then <code>RasterAccessor</code> will assume that the
 * operation can correctly process an IndexColorModel source and will not expand the source pixels (colormap indices)
 * into color components. Refer to {@link JAI#KEY_REPLACE_INDEX_COLOR_MODEL} for a mechanism by which the destination
 * image's <code>ColorModel</code> is set to a non-<code>IndexColorModel</code> to cause <code>RasterAccessor</code> to
 * expand the source's <code>IndexColorModel</code>.
 * 
 * <p>
 * Binary data are handled as a special case. In general image data are considered to be binary when the image has a
 * single-banded <code>MultiPixelPackedSampleModel</code> with one bit per pixel. This may be verified by invoking the
 * <code>isBinary()</code> method. For this case the methods <code>getBinaryDataArray()</code> and
 * <code>copyBinaryDataToRaster()</code> should be used to access and set, respectively, the binary data in packed form.
 * If the binary data are to be accessed in expanded form, i.e., as bytes, then the usual byte methods
 * <code>getByteDataArray()</code>, <code>getByteDataArrays()</code>, and <code>copyDataToRaster()</code> should be
 * used.
 *
 */
public class RasterAccessor {

    /**
     * Value indicating how far COPY_MASK info is shifted to avoid interfering with the data type info.
     */
    private static final int COPY_MASK_SHIFT = 7;

    /* Value indicating how many bits the COPY_MASK is */
    private static final int COPY_MASK_SIZE = 2;

    /** The bits of a FormatTag associated with how dataArrays are obtained. */
    public static final int COPY_MASK = ((1 << COPY_MASK_SIZE) - 1) << COPY_MASK_SHIFT;

    /** Flag indicating data is raster's data. */
    public static final int UNCOPIED = 0x0 << COPY_MASK_SHIFT;

    /** Flag indicating data is a copy of the raster's data. */
    public static final int COPIED = 0x1 << COPY_MASK_SHIFT;

    /**
     * Value indicating how far EXPANSION_MASK info is shifted to avoid interfering with the data type info.
     */
    private static final int EXPANSION_MASK_SHIFT = COPY_MASK_SHIFT + COPY_MASK_SIZE;

    /** Value indicating how many bits the EXPANSION_MASK is */
    private static final int EXPANSION_MASK_SIZE = 2;

    /** The bits of a FormatTag associated with how ColorModels are used. */
    public static final int EXPANSION_MASK = ((1 << EXPANSION_MASK_SIZE) - 1) << EXPANSION_MASK_SHIFT;

    /** Flag indicating ColorModel data should be used only in copied case */
    public static final int DEFAULTEXPANSION = 0x0 << EXPANSION_MASK_SHIFT;

    /** Flag indicating ColorModel data should be interpreted. */
    public static final int EXPANDED = 0x1 << EXPANSION_MASK_SHIFT;

    /** Flag indicating ColorModel info should be ignored */
    public static final int UNEXPANDED = 0x02 << EXPANSION_MASK_SHIFT;

    /** The bits of a FormatTagID associated with pixel datatype. */
    public static final int DATATYPE_MASK = (0x1 << COPY_MASK_SHIFT) - 1;

    /** FormatTagID indicating data in byte arrays and uncopied. */
    public static final int TAG_BYTE_UNCOPIED = DataBuffer.TYPE_BYTE | UNCOPIED;

    /** FormatTagID indicating data in unsigned short arrays and uncopied. */
    public static final int TAG_USHORT_UNCOPIED = DataBuffer.TYPE_USHORT | UNCOPIED;

    /** FormatTagID indicating data in short arrays and uncopied. */
    public static final int TAG_SHORT_UNCOPIED = DataBuffer.TYPE_SHORT | UNCOPIED;

    /** FormatTagID indicating data in int arrays and uncopied. */
    public static final int TAG_INT_UNCOPIED = DataBuffer.TYPE_INT | UNCOPIED;

    /** FormatTagID indicating data in float arrays and uncopied. */
    public static final int TAG_FLOAT_UNCOPIED = DataBuffer.TYPE_FLOAT | UNCOPIED;

    /** FormatTagID indicating data in double arrays and uncopied. */
    public static final int TAG_DOUBLE_UNCOPIED = DataBuffer.TYPE_DOUBLE | UNCOPIED;

    /** FormatTagID indicating data in int arrays and copied. */
    public static final int TAG_INT_COPIED = DataBuffer.TYPE_INT | COPIED;

    /** FormatTagID indicating data in float arrays and copied. */
    public static final int TAG_FLOAT_COPIED = DataBuffer.TYPE_FLOAT | COPIED;

    /** FormatTagID indicating data in double arrays and copied. */
    public static final int TAG_DOUBLE_COPIED = DataBuffer.TYPE_DOUBLE | COPIED;

    /** FormatTagID indicating data in byte arrays and expanded. */
    public static final int TAG_BYTE_EXPANDED = DataBuffer.TYPE_BYTE | EXPANDED;

    /**
     * FormatTagID corresponding to the binary case. This occurs when the image has a
     * <code>MultiPixelPackedSampleModel</code> with a single band and one bit per pixel.
     */
    private static final int TAG_BINARY = DataBuffer.TYPE_BYTE | COPIED | UNEXPANDED;

    /** The raster that is the source of pixel data. */
    protected Raster raster;

    /** The width of the rectangle this RasterAccessor addresses. */
    protected int rectWidth;

    /** The height of the rectangle this RasterAccessor addresses. */
    protected int rectHeight;

    /**
     * The x coordinate of upper-left corner of the rectangle this RasterAccessor addresses.
     */
    protected int rectX;

    /**
     * The y coordinate of upper-left corner of the rectangle this RasterAccessor addresses.
     */
    protected int rectY;

    /** Tag indicating the data type of the data and whether it's copied */
    protected int formatTagID;

    /**
     * The image data for the binary case. The data will be packed as eight bits per byte with no bit offset, i.e., the
     * first bit in each image line will be the left-most bit of the first byte of the line. The line stride in bytes
     * will be <code>(int)((rectWidth+7)/8)</code>. The length of the array will be <code>rectHeight</code> multiplied
     * by the line stride.
     *
     * @since JAI 1.1
     */
    protected byte binaryDataArray[] = null;

    /**
     * The image data in a two-dimensional byte array. This value will be non-null only if getDataType() returns
     * DataBuffer.TYPE_BYTE. byteDataArrays.length will equal numBands. Note that often the numBands subArrays will all
     * point to the same place in memory.
     *
     * <p>
     * For the case of binary data this variable will not be initialized until <code>getByteDataArrays()</code> or
     * <code>getByteDataArray(int b)</code> is invoked.
     */
    protected byte byteDataArrays[][] = null;

    /**
     * The image data in a two-dimensional short array. This value will be non-null only if getDataType() returns
     * DataBuffer.TYPE_USHORT or DataBuffer.TYPE_SHORT. shortDataArrays.length will equal numBands. Note that often the
     * numBands subArrays will all point to the same place in memory.
     */
    protected short shortDataArrays[][] = null;

    /**
     * The image data in a two-dimensional int array. This value will be non-null only if getDataType() returns
     * DataBuffer.TYPE_INT. intDataArrays.length will equal numBands. Note that often the numBands subArrays will all
     * point to the same place in memory.
     */
    protected int intDataArrays[][] = null;

    /**
     * The image data in a two-dimensional float array. This value will be non-null only if getDataType() returns
     * DataBuffer.TYPE_FLOAT. floatDataArrays.length will equal numBands. Note that often the numBand subArrays will all
     * point to the same place in memory.
     */
    protected float floatDataArrays[][] = null;

    /**
     * The image data in a two-dimensional double array. This value will be non-null only if getDataType() returns
     * DataBuffer.TYPE_DOUBLE. doubleDataArrays.length will equal numBands. Note that often the numBand subArrays will
     * all point to the same place in memory.
     */
    protected double doubleDataArrays[][] = null;

    /**
     * The bandOffset + subRasterOffset + DataBufferOffset into each of the numBand data arrays
     */
    protected int bandDataOffsets[];

    /** Offset from a pixel's offset to a band of that pixel */
    protected int bandOffsets[];

    /** The number of bands per pixel in the data array. */
    protected int numBands;

    /** The scanline stride of the image data in each data array */
    protected int scanlineStride;

    /** The pixel stride of the image data in each data array */
    protected int pixelStride;

    /**
     * Finds the appropriate tags for the constructor, based on the SampleModel and ColorModel of all the source and
     * destination.
     *
     * @param srcs
     *            The operations sources; may be <code>null</code> which is taken to be equivalent to zero sources.
     * @param dst
     *            The operation destination.
     * @return An array containing <code>RasterFormatTag</code>s for the sources in the first src.length elements and a
     *         <code>RasterFormatTag</code> for the destination in the last element.
     * @throws NullPointerException
     *             if <code>dst</code> is <code>null</code>.
     */
    public static RasterFormatTag[] findCompatibleTags(RenderedImage srcs[], RenderedImage dst) {
        int tagIDs[];
        if (srcs != null) {
            tagIDs = new int[srcs.length + 1];
        } else {
            tagIDs = new int[1];
        }
        SampleModel dstSampleModel = dst.getSampleModel();
        int dstDataType = dstSampleModel.getTransferType();

        int defaultDataType = dstDataType;
        boolean binaryDst = ImageToolkit.isBinary(dstSampleModel);
        if (binaryDst) {
            defaultDataType = DataBuffer.TYPE_BYTE;
        } else if ((dstDataType == DataBuffer.TYPE_BYTE) || (dstDataType == DataBuffer.TYPE_USHORT)
            || (dstDataType == DataBuffer.TYPE_SHORT)) {
            defaultDataType = DataBuffer.TYPE_INT;
        }

        // use highest precision datatype of all srcs & dst
        if (srcs != null) {
            int numSources = srcs.length;
            int i;
            for (i = 0; i < numSources; i++) {
                SampleModel srcSampleModel = srcs[i].getSampleModel();
                int srcDataType = srcSampleModel.getTransferType();
                if (!(binaryDst && ImageToolkit.isBinary(srcSampleModel)) && srcDataType > defaultDataType) {
                    defaultDataType = srcDataType;
                }
            }
        }

        // Set the tag. For binary data at this point this should
        // equal DataBuffer.TYPE_BYTE | COPIED.
        int tagID = defaultDataType | COPIED;

        if (dstSampleModel instanceof ComponentSampleModel) {
            if (srcs != null) {
                int numSources = srcs.length;
                int i;
                for (i = 0; i < numSources; i++) {
                    SampleModel srcSampleModel = srcs[i].getSampleModel();
                    int srcDataType = srcSampleModel.getTransferType();
                    if (!(srcSampleModel instanceof ComponentSampleModel) || (srcDataType != dstDataType)) {
                        break;
                    }
                }
                if (i == numSources) {
                    tagID = dstDataType | UNCOPIED;
                }
            } else {
                tagID = dstDataType | UNCOPIED;
            }
        }

        // If the source has an IndexColorModel but the dest does not,
        // perform expansion of the source pixels. If both have an
        // IndexColorModel, assume the operation knows what it is doing.
        RasterFormatTag rft[] = new RasterFormatTag[tagIDs.length];
        if (srcs != null) {
            for (int i = 0; i < srcs.length; i++) {
                // dst can't be EXPANDED
                if ((srcs[i].getColorModel() instanceof IndexColorModel)) {
                    if (dst.getColorModel() instanceof IndexColorModel) {
                        tagIDs[i] = tagID | UNEXPANDED;
                    } else {
                        tagIDs[i] = tagID | EXPANDED;
                    }
                } else if (srcs[i].getColorModel() instanceof ComponentColorModel
                    || (binaryDst && ImageToolkit.isBinary(srcs[i].getSampleModel()))) {
                    tagIDs[i] = tagID | UNEXPANDED;
                } else {
                    tagIDs[i] = tagID | DEFAULTEXPANSION;
                }
            }
            tagIDs[srcs.length] = tagID | UNEXPANDED;

            for (int i = 0; i < srcs.length; i++) {
                rft[i] = new RasterFormatTag(srcs[i].getSampleModel(), tagIDs[i]);
            }
            // get the dest
            rft[srcs.length] = new RasterFormatTag(dstSampleModel, tagIDs[srcs.length]);
        } else { // no sources, dest only
            rft[0] = new RasterFormatTag(dstSampleModel, tagID | UNEXPANDED);
        }

        return rft;
    }

    /**
     * Returns the most efficient FormatTagID that is compatible with the destination SampleModel and all source
     * SampleModels. Since there is no <code>ColorModel</code> associated with a <code>SampleModel</code>, this method
     * does not expand the data buffer as it has no access to the Raster's ColorModel.
     */
    public static int findCompatibleTag(SampleModel[] srcSampleModels, SampleModel dstSampleModel) {
        int dstDataType = dstSampleModel.getTransferType();

        int tag = dstDataType | COPIED;
        if (ImageToolkit.isBinary(dstSampleModel)) {
            tag = DataBuffer.TYPE_BYTE | COPIED;
        } else if (dstDataType == DataBuffer.TYPE_BYTE || dstDataType == DataBuffer.TYPE_USHORT
            || dstDataType == DataBuffer.TYPE_SHORT) {
            tag = TAG_INT_COPIED;
        }

        if (dstSampleModel instanceof ComponentSampleModel) {
            if (srcSampleModels != null) {
                int numSources = srcSampleModels.length;
                int i;
                for (i = 0; i < numSources; i++) {
                    int srcDataType = srcSampleModels[i].getTransferType();

                    if (!(srcSampleModels[i] instanceof ComponentSampleModel) || srcDataType != dstDataType) {
                        break;
                    }
                }
                if (i == numSources) {
                    tag = dstDataType | UNCOPIED;
                }
            } else {
                tag = dstDataType | UNCOPIED;
            }
        }
        return tag | UNEXPANDED; // only called when colormodel not around
                                 // so never expand
    }

    /**
     * Constructs a RasterAccessor object out of a Raster, Rectangle and formatTagID returned from
     * RasterFormat.findCompatibleTag().
     *
     * <p>
     * The <code>RasterFormatTag</code> must agree with the raster's <code>SampleModel</code> and
     * <code>ColorModel</code>. It is best to obtain the correct tag using the <code>findCompatibleTags</code> static
     * method.
     *
     * @param raster
     *            The raster to be accessed
     * @param rect
     *            A <code>Rectangle</code> from the raster to be accessed
     * @param rft
     *            The <code>RasterFormatTag</code> associated with the Raster
     * @param theColorModel
     *            The <code>ColorModel</code> for color components
     *
     * @throws ClassCastException
     *             if the data type of <code>RasterFormatTag</code> does not agree with the actual data type of the
     *             <code>Raster</code>.
     * @throws IllegalArgumentException
     *             if <code>raster</code>, <code>rect</code>, or <code>rft</code> is <code>null</code>.
     * @throws IllegalArgumentException
     *             if the <code>Rectangle</code> is not contained within <code>Raster</code>'s bounds.
     */
    public RasterAccessor(Raster raster, Rectangle rect, RasterFormatTag rft, ColorModel theColorModel) {

        if (raster == null || rect == null || rft == null) {
            throw new IllegalArgumentException("Parameters cannot be null");
        }

        // If requesting a region that lies outside the bounds,
        // throw an exception.
        if (!raster.getBounds().contains(rect)) {
            throw new IllegalArgumentException("Region outside the bounds!");
        }

        this.raster = raster;
        this.rectX = rect.x;
        this.rectY = rect.y;
        this.rectWidth = rect.width;
        this.rectHeight = rect.height;
        this.formatTagID = rft.getFormatTagID();
        if ((formatTagID & COPY_MASK) == UNCOPIED) {

            this.numBands = rft.getNumBands();
            this.pixelStride = rft.getPixelStride();

            ComponentSampleModel csm = (ComponentSampleModel) raster.getSampleModel();
            this.scanlineStride = csm.getScanlineStride();

            int bankIndices[] = null;

            // if the rft isPixelSequential we can rely on it's
            // version of bandOffsets and bankIndicies. If it's
            // not the SampleModel passed in might not completely
            // match the one that was passed to the the
            // RasterFormatTag constructor so we have to get them
            // from the passed in Raster/SampleModel
            if (rft.isPixelSequential()) {
                this.bandOffsets = rft.getBandOffsets();
                bankIndices = rft.getBankIndices();
            } else {
                this.bandOffsets = csm.getBandOffsets();
                bankIndices = csm.getBankIndices();
            }

            this.bandDataOffsets = new int[numBands];

            int dataBufferOffsets[] = raster.getDataBuffer().getOffsets();

            int subRasterOffset = (rectY - raster.getSampleModelTranslateY()) * scanlineStride
                + (rectX - raster.getSampleModelTranslateX()) * pixelStride;

            if (dataBufferOffsets.length == 1) {
                int theDataBufferOffset = dataBufferOffsets[0];
                for (int i = 0; i < numBands; i++) {
                    bandDataOffsets[i] = bandOffsets[i] + theDataBufferOffset + subRasterOffset;
                }
            } else if (dataBufferOffsets.length == bandDataOffsets.length) {
                for (int i = 0; i < numBands; i++) {
                    bandDataOffsets[i] = bandOffsets[i] + dataBufferOffsets[i] + subRasterOffset;
                }
            } else {
                throw new RuntimeException("Band offset number is different!");
            }

            switch (formatTagID & DATATYPE_MASK) {
                case DataBuffer.TYPE_BYTE:
                    DataBufferByte dbb = (DataBufferByte) raster.getDataBuffer();
                    byteDataArrays = new byte[numBands][];
                    for (int i = 0; i < numBands; i++) {
                        byteDataArrays[i] = dbb.getData(bankIndices[i]);
                    }
                    break;

                case DataBuffer.TYPE_USHORT:
                    DataBufferUShort dbus = (DataBufferUShort) raster.getDataBuffer();
                    shortDataArrays = new short[numBands][];
                    for (int i = 0; i < numBands; i++) {
                        shortDataArrays[i] = dbus.getData(bankIndices[i]);
                    }
                    break;

                case DataBuffer.TYPE_SHORT:
                    DataBufferShort dbs = (DataBufferShort) raster.getDataBuffer();
                    shortDataArrays = new short[numBands][];
                    for (int i = 0; i < numBands; i++) {
                        shortDataArrays[i] = dbs.getData(bankIndices[i]);
                    }
                    break;

                case DataBuffer.TYPE_INT:
                    DataBufferInt dbi = (DataBufferInt) raster.getDataBuffer();
                    intDataArrays = new int[numBands][];
                    for (int i = 0; i < numBands; i++) {
                        intDataArrays[i] = dbi.getData(bankIndices[i]);
                    }
                    break;

                case DataBuffer.TYPE_FLOAT:
                    DataBuffer dbf = raster.getDataBuffer();
                    floatDataArrays = new float[numBands][];
                    for (int i = 0; i < numBands; i++) {
                        floatDataArrays[i] = DataBufferUtils.getDataFloat(dbf, bankIndices[i]);
                    }
                    break;

                case DataBuffer.TYPE_DOUBLE:
                    DataBuffer dbd = raster.getDataBuffer();
                    doubleDataArrays = new double[numBands][];
                    for (int i = 0; i < numBands; i++) {
                        doubleDataArrays[i] = DataBufferUtils.getDataDouble(dbd, bankIndices[i]);
                    }
                    break;
            }
            // only do this if not copied and expanded
            if ((formatTagID & EXPANSION_MASK) == EXPANDED && theColorModel instanceof IndexColorModel) {
                IndexColorModel icm = (IndexColorModel) theColorModel;

                int newNumBands = icm.getNumComponents();

                int mapSize = icm.getMapSize();
                int newBandDataOffsets[] = new int[newNumBands];
                int newScanlineStride = rectWidth * newNumBands;
                int newPixelStride = newNumBands;
                byte ctable[][] = new byte[newNumBands][mapSize];

                icm.getReds(ctable[0]);
                icm.getGreens(ctable[1]);
                icm.getBlues(ctable[2]);
                byte rtable[] = ctable[0];
                byte gtable[] = ctable[1];
                byte btable[] = ctable[2];

                byte atable[] = null;
                if (newNumBands == 4) {
                    icm.getAlphas(ctable[3]);
                    atable = ctable[3];
                }

                for (int i = 0; i < newNumBands; i++) {
                    newBandDataOffsets[i] = i;
                }

                switch (formatTagID & DATATYPE_MASK) {
                    case DataBuffer.TYPE_BYTE: {
                        byte newBArray[] = new byte[rectWidth * rectHeight * newNumBands];
                        byte byteDataArray[] = byteDataArrays[0];
                        int scanlineOffset = bandDataOffsets[0];
                        int newScanlineOffset = 0;
                        for (int j = 0; j < rectHeight; j++) {
                            int pixelOffset = scanlineOffset;
                            int newPixelOffset = newScanlineOffset;
                            for (int i = 0; i < rectWidth; i++) {
                                int index = byteDataArray[pixelOffset] & 0xff;
                                for (int k = 0; k < newNumBands; k++) {
                                    newBArray[newPixelOffset + k] = ctable[k][index];
                                }
                                pixelOffset += pixelStride;
                                newPixelOffset += newPixelStride;
                            }
                            scanlineOffset += scanlineStride;
                            newScanlineOffset += newScanlineStride;
                        }
                        byteDataArrays = new byte[newNumBands][];
                        for (int i = 0; i < newNumBands; i++) {
                            byteDataArrays[i] = newBArray;
                        }
                    }
                        break;

                    case DataBuffer.TYPE_USHORT: {
                        short newIArray[] = new short[rectWidth * rectHeight * newNumBands];
                        short shortDataArray[] = shortDataArrays[0];
                        int scanlineOffset = bandDataOffsets[0];
                        int newScanlineOffset = 0;
                        for (int j = 0; j < rectHeight; j++) {
                            int pixelOffset = scanlineOffset;
                            int newPixelOffset = newScanlineOffset;
                            for (int i = 0; i < rectWidth; i++) {
                                int index = (shortDataArray[pixelOffset] & 0xffff);
                                for (int k = 0; k < newNumBands; k++) {
                                    newIArray[newPixelOffset + k] = (short) (ctable[k][index] & 0xff);
                                }
                                pixelOffset += pixelStride;
                                newPixelOffset += newPixelStride;
                            }
                            scanlineOffset += scanlineStride;
                            newScanlineOffset += newScanlineStride;
                        }

                        shortDataArrays = new short[newNumBands][];
                        for (int i = 0; i < newNumBands; i++) {
                            shortDataArrays[i] = newIArray;
                        }
                    }
                        break;

                    case DataBuffer.TYPE_SHORT: {
                        short newIArray[] = new short[rectWidth * rectHeight * newNumBands];
                        short shortDataArray[] = shortDataArrays[0];
                        int scanlineOffset = bandDataOffsets[0];
                        int newScanlineOffset = 0;
                        for (int j = 0; j < rectHeight; j++) {
                            int pixelOffset = scanlineOffset;
                            int newPixelOffset = newScanlineOffset;
                            for (int i = 0; i < rectWidth; i++) {
                                int index = shortDataArray[pixelOffset];
                                for (int k = 0; k < newNumBands; k++) {
                                    newIArray[newPixelOffset + k] = (short) (ctable[k][index] & 0xff);
                                }
                                pixelOffset += pixelStride;
                                newPixelOffset += newPixelStride;
                            }
                            scanlineOffset += scanlineStride;
                            newScanlineOffset += newScanlineStride;
                        }

                        shortDataArrays = new short[newNumBands][];
                        for (int i = 0; i < newNumBands; i++) {
                            shortDataArrays[i] = newIArray;
                        }
                    }
                        break;

                    case DataBuffer.TYPE_INT: {
                        int newIArray[] = new int[rectWidth * rectHeight * newNumBands];
                        int intDataArray[] = intDataArrays[0];
                        int scanlineOffset = bandDataOffsets[0];
                        int newScanlineOffset = 0;
                        for (int j = 0; j < rectHeight; j++) {
                            int pixelOffset = scanlineOffset;
                            int newPixelOffset = newScanlineOffset;
                            for (int i = 0; i < rectWidth; i++) {
                                int index = intDataArray[pixelOffset];
                                for (int k = 0; k < newNumBands; k++) {
                                    newIArray[newPixelOffset + k] = (ctable[k][index] & 0xff);
                                }
                                pixelOffset += pixelStride;
                                newPixelOffset += newPixelStride;
                            }
                            scanlineOffset += scanlineStride;
                            newScanlineOffset += newScanlineStride;
                        }

                        intDataArrays = new int[newNumBands][];
                        for (int i = 0; i < newNumBands; i++) {
                            intDataArrays[i] = newIArray;
                        }
                    }
                        break;

                    case DataBuffer.TYPE_FLOAT: {
                        float newFArray[] = new float[rectWidth * rectHeight * newNumBands];
                        float floatDataArray[] = floatDataArrays[0];
                        int scanlineOffset = bandDataOffsets[0];
                        int newScanlineOffset = 0;
                        for (int j = 0; j < rectHeight; j++) {
                            int pixelOffset = scanlineOffset;
                            int newPixelOffset = newScanlineOffset;
                            for (int i = 0; i < rectWidth; i++) {
                                int index = (int) floatDataArray[pixelOffset];
                                for (int k = 0; k < newNumBands; k++) {
                                    newFArray[newPixelOffset + k] = (ctable[k][index] & 0xff);
                                }
                                pixelOffset += pixelStride;
                                newPixelOffset += newPixelStride;
                            }
                            scanlineOffset += scanlineStride;
                            newScanlineOffset += newScanlineStride;
                        }
                        floatDataArrays = new float[newNumBands][];
                        for (int i = 0; i < newNumBands; i++) {
                            floatDataArrays[i] = newFArray;
                        }
                    }
                        break;

                    case DataBuffer.TYPE_DOUBLE: {
                        double newDArray[] = new double[rectWidth * rectHeight * newNumBands];
                        double doubleDataArray[] = doubleDataArrays[0];
                        int scanlineOffset = bandDataOffsets[0];
                        int newScanlineOffset = 0;
                        for (int j = 0; j < rectHeight; j++) {
                            int pixelOffset = scanlineOffset;
                            int newPixelOffset = newScanlineOffset;
                            for (int i = 0; i < rectWidth; i++) {
                                int index = (int) doubleDataArray[pixelOffset];
                                for (int k = 0; k < newNumBands; k++) {
                                    newDArray[newPixelOffset + k] = (ctable[k][index] & 0xff);
                                }
                                pixelOffset += pixelStride;
                                newPixelOffset += newPixelStride;
                            }
                            scanlineOffset += scanlineStride;
                            newScanlineOffset += newScanlineStride;
                        }
                        doubleDataArrays = new double[newNumBands][];
                        for (int i = 0; i < newNumBands; i++) {
                            doubleDataArrays[i] = newDArray;
                        }
                    }
                        break;
                }
                this.numBands = newNumBands;
                this.pixelStride = newPixelStride;
                this.scanlineStride = newScanlineStride;
                this.bandDataOffsets = newBandDataOffsets;
                this.bandOffsets = newBandDataOffsets;
            }
        } else if ((formatTagID & COPY_MASK) == COPIED && (formatTagID & EXPANSION_MASK) != UNEXPANDED
            && theColorModel != null) {
            this.numBands = theColorModel instanceof IndexColorModel ? theColorModel.getNumComponents()
                : raster.getSampleModel().getNumBands();
            this.pixelStride = this.numBands;
            this.scanlineStride = rectWidth * numBands;
            this.bandOffsets = new int[numBands];

            for (int i = 0; i < numBands; i++) {
                bandOffsets[i] = i;
            }
            this.bandDataOffsets = bandOffsets;

            Object odata = null;
            int offset = 0;

            int[] components = new int[theColorModel.getNumComponents()];

            switch (formatTagID & DATATYPE_MASK) {

                case DataBuffer.TYPE_INT:
                    int idata[] = new int[rectWidth * rectHeight * numBands];
                    intDataArrays = new int[numBands][];
                    for (int i = 0; i < numBands; i++) {
                        intDataArrays[i] = idata;
                    }

                    odata = raster.getDataElements(rectX, rectY, null);
                    offset = 0;

                    // Workaround for bug in BytePackedRaster
                    byte[] bdata = null;
                    if (raster instanceof sun.awt.image.BytePackedRaster) {
                        bdata = (byte[]) odata;
                    }

                    for (int j = rectY; j < rectY + rectHeight; j++) {
                        for (int i = rectX; i < rectX + rectWidth; i++) {
                            if (bdata != null) {
                                bdata[0] = (byte) raster.getSample(i, j, 0);
                            } else {
                                raster.getDataElements(i, j, odata);
                            }

                            theColorModel.getComponents(odata, components, 0);

                            idata[offset] = components[0];
                            idata[offset + 1] = components[1];
                            idata[offset + 2] = components[2];
                            if (numBands > 3) {
                                idata[offset + 3] = components[3];
                            }

                            offset += pixelStride;
                        }
                    }
                    break;

                case DataBuffer.TYPE_FLOAT:
                    float fdata[] = new float[rectWidth * rectHeight * numBands];
                    floatDataArrays = new float[numBands][];
                    for (int i = 0; i < numBands; i++) {
                        floatDataArrays[i] = fdata;
                    }
                    odata = null;
                    offset = 0;
                    for (int j = rectY; j < rectY + rectHeight; j++) {
                        for (int i = rectX; i < rectX + rectWidth; i++) {
                            odata = raster.getDataElements(i, j, odata);

                            theColorModel.getComponents(odata, components, 0);

                            fdata[offset] = components[0];
                            fdata[offset + 1] = components[1];
                            fdata[offset + 2] = components[2];
                            if (numBands > 3) {
                                fdata[offset + 3] = components[3];
                            }
                            offset += pixelStride;
                        }
                    }
                    break;

                case DataBuffer.TYPE_DOUBLE:
                    double ddata[] = new double[rectWidth * rectHeight * numBands];
                    doubleDataArrays = new double[numBands][];
                    for (int i = 0; i < numBands; i++) {
                        doubleDataArrays[i] = ddata;
                    }
                    odata = null;
                    offset = 0;
                    for (int j = rectY; j < rectY + rectHeight; j++) {
                        for (int i = rectX; i < rectX + rectWidth; i++) {
                            odata = raster.getDataElements(i, j, odata);

                            theColorModel.getComponents(odata, components, 0);

                            ddata[offset] = components[0];
                            ddata[offset + 1] = components[1];
                            ddata[offset + 2] = components[2];
                            if (numBands > 3) {
                                ddata[offset + 3] = components[3];
                            }
                            offset += pixelStride;
                        }
                    }
                    break;
            }
        } else {
            // if ((formatTagID & COPY_MASK) == COPIED &&
            // (formatTagID & EXPANSION_MASK) == UNEXPANDED) {
            // this has become a catchall case. Specifically for
            // Rasters with null colormodels. So we take out the
            // if as the boolean clause will get way complicated
            // otherwise.
            this.numBands = rft.getNumBands();
            this.pixelStride = this.numBands;
            this.scanlineStride = rectWidth * numBands;
            this.bandDataOffsets = rft.getBandOffsets();
            this.bandOffsets = this.bandDataOffsets;

            switch (formatTagID & DATATYPE_MASK) {
                case DataBuffer.TYPE_INT:
                    int idata[] = raster.getPixels(rectX, rectY, rectWidth, rectHeight, (int[]) null);
                    intDataArrays = new int[numBands][];
                    for (int i = 0; i < numBands; i++) {
                        intDataArrays[i] = idata;
                    }
                    break;

                case DataBuffer.TYPE_FLOAT:
                    float fdata[] = raster.getPixels(rectX, rectY, rectWidth, rectHeight, (float[]) null);
                    floatDataArrays = new float[numBands][];
                    for (int i = 0; i < numBands; i++) {
                        floatDataArrays[i] = fdata;
                    }
                    break;

                case DataBuffer.TYPE_DOUBLE:
                    double ddata[] = raster.getPixels(rectX, rectY, rectWidth, rectHeight, (double[]) null);
                    doubleDataArrays = new double[numBands][];
                    for (int i = 0; i < numBands; i++) {
                        doubleDataArrays[i] = ddata;
                    }
                    break;
            }
        }
    }

    /**
     * Returns the x coordinate of the upper-left corner of the RasterAccessor's accessible area.
     */
    public int getX() {
        return rectX;
    }

    /**
     * Returns the y coordinate of the upper-left corner of the RasterAccessor's accessible area.
     */
    public int getY() {
        return rectY;
    }

    /**
     * Returns the width of the RasterAccessor's accessible area.
     */
    public int getWidth() {
        return rectWidth;
    }

    /**
     * Returns the height of the RasterAccessor's accessible area.
     */
    public int getHeight() {
        return rectHeight;
    }

    /** Returns the numBands of the presented area. */
    public int getNumBands() {
        return numBands;
    }

    /**
     * Whether the <code>RasterAccessor</code> represents binary data. This occurs when the <code>Raster</code> has a
     * <code>MultiPixelPackedSampleModel</code> with a single band and one bit per pixel.
     *
     * @since JAI 1.1
     */
    public boolean isBinary() {
        return (formatTagID & TAG_BINARY) == TAG_BINARY && ImageToolkit.isBinary(raster.getSampleModel());
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
     *
     * @since JAI 1.1
     */
    public byte[] getBinaryDataArray() {
        if (binaryDataArray == null && isBinary()) {
            binaryDataArray = ImageToolkit.getPackedBinaryData(raster, new Rectangle(rectX, rectY, rectWidth, rectHeight));
        }
        return binaryDataArray;
    }

    /**
     * Returns the image data as a byte array. Non-null only if getDataType = DataBuffer.TYPE_BYTE.
     *
     * <p>
     * For the case of binary data the corresponding instance variable <code>byteDataArrays</code> will not be
     * initialized until this method or <code>getByteDataArray(int b)</code> is invoked. The binary data will be
     * returned as bytes with value 0 or 1.
     */
    public byte[][] getByteDataArrays() {
        if (byteDataArrays == null && isBinary()) {
            byte[] bdata = ImageToolkit.getUnpackedBinaryData(raster, new Rectangle(rectX, rectY, rectWidth, rectHeight));
            byteDataArrays = new byte[][] { bdata };
        }
        return byteDataArrays;
    }

    /**
     * Returns the image data as a byte array for a specific band. Non-null only if getDataType = DataBuffer.TYPE_BYTE.
     */
    public byte[] getByteDataArray(int b) {
        byte[][] bda = getByteDataArrays();
        return (bda == null ? null : bda[b]);
    }

    /**
     * Returns the image data as a short array. Non-null only if getDataType = DataBuffer.TYPE_USHORT or
     * DataBuffer.TYPE_SHORT.
     */
    public short[][] getShortDataArrays() {
        return shortDataArrays;
    }

    /**
     * Returns the image data as a short array for a specific band. Non-null only if getDataType =
     * DataBuffer.TYPE_USHORT or DataBuffer.TYPE_SHORT.
     */
    public short[] getShortDataArray(int b) {
        return (shortDataArrays == null ? null : shortDataArrays[b]);
    }

    /**
     * Returns the image data as an int array. Non-null only if getDataType = DataBuffer.TYPE_INT.
     */
    public int[][] getIntDataArrays() {
        return intDataArrays;
    }

    /**
     * Returns the image data as an int array for a specific band. Non-null only if getDataType = DataBuffer.TYPE_INT.
     */
    public int[] getIntDataArray(int b) {
        return (intDataArrays == null ? null : intDataArrays[b]);
    }

    /**
     * Returns the image data as a float array. Non-null only if getDataType = DataBuffer.TYPE_FLOAT.
     */
    public float[][] getFloatDataArrays() {
        return floatDataArrays;
    }

    /**
     * Returns the image data as a float array for a specific band. Non-null only if getDataType =
     * DataBuffer.TYPE_FLOAT.
     */
    public float[] getFloatDataArray(int b) {
        return (floatDataArrays == null ? null : floatDataArrays[b]);
    }

    /**
     * Returns the image data as a double array. Non-null only if getDataType = DataBuffer.TYPE_DOUBLE
     */
    public double[][] getDoubleDataArrays() {
        return doubleDataArrays;
    }

    /**
     * Returns the image data as a double array for a specific band. Non-null only if getDataType =
     * DataBuffer.TYPE_DOUBLE
     */
    public double[] getDoubleDataArray(int b) {
        return (doubleDataArrays == null ? null : doubleDataArrays[b]);
    }

    /**
     * Returns the image data as an Object for a specific band.
     *
     * @param b
     *            The index of the image band of interest.
     */
    public Object getDataArray(int b) {
        Object dataArray = null;
        switch (getDataType()) {
            case DataBuffer.TYPE_BYTE:
                dataArray = getByteDataArray(b);
                break;

            case DataBuffer.TYPE_SHORT:
            case DataBuffer.TYPE_USHORT:
                dataArray = getShortDataArray(b);
                break;

            case DataBuffer.TYPE_INT:
                dataArray = getIntDataArray(b);
                break;

            case DataBuffer.TYPE_FLOAT:
                dataArray = getFloatDataArray(b);
                break;

            case DataBuffer.TYPE_DOUBLE:
                dataArray = getDoubleDataArray(b);
                break;

            default:
                dataArray = null;
        }

        return dataArray;
    }

    /** Returns the bandDataOffsets into the dataArrays. */
    public int[] getBandOffsets() {
        return bandDataOffsets;
    }

    /**
     * Returns the offset of all band's samples from any pixel offset.
     */
    public int[] getOffsetsForBands() {
        return bandOffsets;
    }

    /**
     * Returns the offset of a specific band's first sample into the DataBuffer including the DataBuffer's offset.
     */
    public int getBandOffset(int b) {
        return bandDataOffsets[b];
    }

    /**
     * Returns the offset of a specified band's sample from any pixel offset.
     */
    public int getOffsetForBand(int b) {
        return bandOffsets[b];
    }

    /**
     * Returns the scanlineStride for the image data.
     *
     * <p>
     * For binary data this stride is applies to the arrays returned by <code>getByteDataArray()</code> and
     * <code>getByteDataArrays()</code> if the data are accessed as bytes; it does not apply to the array returned by
     * <code>getBinaryDataArray()</code> when the data are accessed as bits packed into bytes.
     */
    public int getScanlineStride() {
        return scanlineStride;
    }

    /** Returns the pixelStride for the image data. */
    public int getPixelStride() {
        return pixelStride;
    }

    /**
     * Returns the data type of the RasterAccessor object. Note that this datatype is not necessarily the same data type
     * as the underlying raster.
     */
    public int getDataType() {
        return formatTagID & DATATYPE_MASK;
    }

    /**
     * Returns true if the RasterAccessors's data is copied from it's raster.
     */
    public boolean isDataCopy() {
        return ((formatTagID & COPY_MASK) == COPIED);
    }

    /**
     * For the case of binary data (<code>isBinary()</code> returns <code>true</code>), copy the binary data back into
     * the <code>Raster</code> of the <code>RasterAccessor</code>. If this method is invoked in the non-binary case it
     * does nothing. Any bit offset in the original <code>SampleModel</code> will be accounted for.
     *
     * @since JAI 1.1
     */
    // Note: ALL branches of this method have been tested. (bpb 10 May 2000)
    public void copyBinaryDataToRaster() {
        if (binaryDataArray == null || !isBinary()) {
            return;
        }

        ImageToolkit.setPackedBinaryData(binaryDataArray, (WritableRaster) raster,
            new Rectangle(rectX, rectY, rectWidth, rectHeight));
    }

    /**
     * Copies data back into the RasterAccessor's raster. Note that the data is cast from the intermediate data format
     * to the raster's format. If clamping is needed, the call clampDataArrays() method needs to be called before
     * calling the copyDataToRaster() method. Note: the raster is expected to be writable - typically a destination
     * raster - otherwise, a run-time exception will occur.
     *
     * <p>
     * If the data are binary, then the target bit will be set if and only if the corresponding byte is non-zero.
     */
    public void copyDataToRaster() {
        if (isDataCopy()) {

            // Writeback should only be necessary on destRasters which
            // should be writable so this cast should succeed.
            WritableRaster wr = (WritableRaster) raster;
            switch (getDataType()) {
                case DataBuffer.TYPE_BYTE:
                    // Note: ALL branches of this case have been tested.
                    // (bpb 10 May 2000)
                    if (!isBinary()) {
                        // If this exception occurs then there is a logic
                        // error within this accessor since the only case
                        // wherein byte data should be COPIED is when the
                        // data set is binary.
                        throw new RuntimeException("Not a binary rRasterAccessor");
                    }

                    // This case only occurs for binary src and dst.

                    ImageToolkit.setUnpackedBinaryData(byteDataArrays[0], wr,
                        new Rectangle(rectX, rectY, rectWidth, rectHeight));
                    break;
                case DataBuffer.TYPE_INT:
                    wr.setPixels(rectX, rectY, rectWidth, rectHeight, intDataArrays[0]);
                    break;

                case DataBuffer.TYPE_FLOAT:
                    wr.setPixels(rectX, rectY, rectWidth, rectHeight, floatDataArrays[0]);
                    break;

                case DataBuffer.TYPE_DOUBLE:
                    wr.setPixels(rectX, rectY, rectWidth, rectHeight, doubleDataArrays[0]);
                    break;
            }
        }
    }

    /**
     * Indicates if the RasterAccessor has a larger dynamic range than the underlying Raster. Except in special cases,
     * where the op knows something special, this call will determine whether or not clampDataArrays() needs to be
     * called.
     */
    public boolean needsClamping() {
        int bits[] = raster.getSampleModel().getSampleSize();

        // Do we even need a clamp? We do if there's any band
        // of the source image stored in that's less than 32 bits
        // and is stored in a byte, short or int format. (The automatic
        // casts between floats/doubles and 32-bit ints in setPixel()
        // do what we want.)

        for (int i = 0; i < bits.length; i++) {
            if (bits[i] < 32) {
                return true;
            }
        }
        return false;
    }

    /**
     * Clamps data array values to a range that the underlying raster can deal with. For example, if the underlying
     * raster stores data as bytes, but the samples are unpacked into integer arrays by the RasterAccessor for an
     * operation, the operation will need to call clampDataArrays() so that the data in the int arrays is restricted to
     * the range 0..255 before a setPixels() call is made on the underlying raster. Note that some operations (for
     * example, lookup) can guarantee that their results don't need clamping so they can call
     * RasterAccessor.copyDataToRaster() without first calling this function.
     */
    public void clampDataArrays() {
        int bits[] = raster.getSampleModel().getSampleSize();

        // Do we even need a clamp? We do if there's any band
        // of the source image stored in that's less than 32 bits
        // and is stored in a byte, short or int format. (The automatic
        // casts between floats/doubles and 32-bit ints in setPixel()
        // do what we want.)

        boolean needClamp = false;
        boolean uniformBitSize = true;
        int bitSize = bits[0];
        for (int i = 0; i < bits.length; i++) {
            if (bits[i] < 32) {
                needClamp = true;
            }
            if (bits[i] != bitSize) {
                uniformBitSize = false;
            }
        }
        if (!needClamp) {
            return;
        }

        int dataType = raster.getDataBuffer().getDataType();
        double hiVals[] = new double[bits.length];
        double loVals[] = new double[bits.length];

        if (dataType == DataBuffer.TYPE_USHORT && uniformBitSize && bits[0] == 16) {
            for (int i = 0; i < bits.length; i++) {
                hiVals[i] = 0xFFFF;
                loVals[i] = 0;
            }
        } else if (dataType == DataBuffer.TYPE_SHORT && uniformBitSize && bits[0] == 16) {
            for (int i = 0; i < bits.length; i++) {
                hiVals[i] = Short.MAX_VALUE;
                loVals[i] = Short.MIN_VALUE;
            }
        } else if (dataType == DataBuffer.TYPE_INT && uniformBitSize && bits[0] == 32) {
            for (int i = 0; i < bits.length; i++) {
                hiVals[i] = Integer.MAX_VALUE;
                loVals[i] = Integer.MIN_VALUE;
            }
        } else {
            for (int i = 0; i < bits.length; i++) {
                hiVals[i] = (1 << bits[i]) - 1;
                loVals[i] = 0;
            }
        }
        clampDataArray(hiVals, loVals);
    }

    private void clampDataArray(double hiVals[], double loVals[]) {
        switch (getDataType()) {
            case DataBuffer.TYPE_INT:
                clampIntArrays(toIntArray(hiVals), toIntArray(loVals));
                break;

            case DataBuffer.TYPE_FLOAT:
                clampFloatArrays(toFloatArray(hiVals), toFloatArray(loVals));
                break;

            case DataBuffer.TYPE_DOUBLE:
                clampDoubleArrays(hiVals, loVals);
                break;
        }
    }

    private int[] toIntArray(double vals[]) {
        int returnVals[] = new int[vals.length];
        for (int i = 0; i < vals.length; i++) {
            returnVals[i] = (int) vals[i];
        }
        return returnVals;
    }

    private float[] toFloatArray(double vals[]) {
        float returnVals[] = new float[vals.length];
        for (int i = 0; i < vals.length; i++) {
            returnVals[i] = (float) vals[i];
        }
        return returnVals;
    }

    private void clampIntArrays(int hiVals[], int loVals[]) {
        int width = rectWidth;
        int height = rectHeight;
        for (int k = 0; k < numBands; k++) {
            int data[] = intDataArrays[k];
            int scanlineOffset = bandDataOffsets[k];
            int hiVal = hiVals[k];
            int loVal = loVals[k];
            for (int j = 0; j < height; j++) {
                int pixelOffset = scanlineOffset;
                for (int i = 0; i < width; i++) {
                    int tmp = data[pixelOffset];
                    if (tmp < loVal) {
                        data[pixelOffset] = loVal;
                    } else if (tmp > hiVal) {
                        data[pixelOffset] = hiVal;
                    }
                    pixelOffset += pixelStride;
                }
                scanlineOffset += scanlineStride;
            }
        }
    }

    private void clampFloatArrays(float hiVals[], float loVals[]) {
        int width = rectWidth;
        int height = rectHeight;
        for (int k = 0; k < numBands; k++) {
            float data[] = floatDataArrays[k];
            int scanlineOffset = bandDataOffsets[k];
            float hiVal = hiVals[k];
            float loVal = loVals[k];
            for (int j = 0; j < height; j++) {
                int pixelOffset = scanlineOffset;
                for (int i = 0; i < width; i++) {
                    float tmp = data[pixelOffset];
                    if (tmp < loVal) {
                        data[pixelOffset] = loVal;
                    } else if (tmp > hiVal) {
                        data[pixelOffset] = hiVal;
                    }
                    pixelOffset += pixelStride;
                }
                scanlineOffset += scanlineStride;
            }
        }
    }

    private void clampDoubleArrays(double hiVals[], double loVals[]) {
        int width = rectWidth;
        int height = rectHeight;
        for (int k = 0; k < numBands; k++) {
            double data[] = doubleDataArrays[k];
            int scanlineOffset = bandDataOffsets[k];
            double hiVal = hiVals[k];
            double loVal = loVals[k];
            for (int j = 0; j < height; j++) {
                int pixelOffset = scanlineOffset;
                for (int i = 0; i < width; i++) {
                    double tmp = data[pixelOffset];
                    if (tmp < loVal) {
                        data[pixelOffset] = loVal;
                    } else if (tmp > hiVal) {
                        data[pixelOffset] = hiVal;
                    }
                    pixelOffset += pixelStride;
                }
                scanlineOffset += scanlineStride;
            }
        }
    }
}
