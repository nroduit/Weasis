/*
 * $RCSfile: RawRenderedImage.java,v $
 *
 *
 * Copyright (c) 2005 Sun Microsystems, Inc. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * - Redistribution of source code must retain the above copyright
 *   notice, this  list of conditions and the following disclaimer.
 *
 * - Redistribution in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in
 *   the documentation and/or other materials provided with the
 *   distribution.
 *
 * Neither the name of Sun Microsystems, Inc. or the names of
 * contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any
 * kind. ALL EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND
 * WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE HEREBY
 * EXCLUDED. SUN MIDROSYSTEMS, INC. ("SUN") AND ITS LICENSORS SHALL
 * NOT BE LIABLE FOR ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF
 * USING, MODIFYING OR DISTRIBUTING THIS SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE FOR
 * ANY LOST REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL,
 * CONSEQUENTIAL, INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND
 * REGARDLESS OF THE THEORY OF LIABILITY, ARISING OUT OF THE USE OF OR
 * INABILITY TO USE THIS SOFTWARE, EVEN IF SUN HAS BEEN ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGES.
 *
 * You acknowledge that this software is not designed or intended for
 * use in the design, construction, operation or maintenance of any
 * nuclear facility.
 *
 * $Revision: 1.2 $
 * $Date: 2006-04-21 23:19:13 $
 * $State: Exp $
 */
package org.weasis.dicom.codec;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.ComponentSampleModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferDouble;
import java.awt.image.DataBufferFloat;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferShort;
import java.awt.image.DataBufferUShort;
import java.awt.image.MultiPixelPackedSampleModel;
import java.awt.image.PixelInterleavedSampleModel;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.SinglePixelPackedSampleModel;
import java.awt.image.WritableRaster;
import java.io.IOException;

import javax.imageio.ImageReadParam;
import javax.imageio.ImageTypeSpecifier;

import com.sun.media.imageio.stream.RawImageInputStream;
import com.sun.media.imageioimpl.common.ImageUtil;
import com.sun.media.imageioimpl.common.SimpleRenderedImage;

public class RawRenderedImage extends SimpleRenderedImage {
    /** The sample model for the original image. */
    private SampleModel originalSampleModel;

    private Raster currentTile;
    private Point currentTileGrid;

    /** The input stream we read from */
    private RawImageInputStream iis = null;

    /**
     * Caches the <code>RawImageReader</code> which creates this object. This variable is used to monitor the abortion.
     */
    private RawImageReader reader;

    /**
     * The <code>ImageReadParam</code> to create this <code>renderedImage</code>.
     */
    private ImageReadParam param = null;

    // The image index in the stream
    private int imageIndex;

    /** The destination bounds. */
    private Rectangle destinationRegion;
    private Rectangle originalRegion;
    private Point sourceOrigin;
    private Dimension originalDimension;
    private int maxXTile, maxYTile;

    /** The subsampling parameters. */
    private int scaleX, scaleY, xOffset, yOffset;
    private int[] destinationBands = null;
    private int[] sourceBands = null;
    private int nComp;

    /**
     * Coordinate transform is not needed from the source (image stream) to the destination.
     */
    private boolean noTransform = true;

    /** The raster for medialib tiles to share. */
    private WritableRaster rasForATile;

    private BufferedImage destImage;

    /** The position of the first sample of this image in the stream. */
    private final long position;

    /** cache the size of the data for each tile in the stream. */
    private long tileDataSize;

    /** The orginal number tiles in X direction. */
    private int originalNumXTiles;

    public RawRenderedImage(RawImageInputStream iis, RawImageReader reader, ImageReadParam param, int imageIndex)
        throws IOException {
        this.iis = iis;
        this.reader = reader;
        this.param = param;
        this.imageIndex = imageIndex;
        this.position = iis.getImageOffset(imageIndex);
        this.originalDimension = iis.getImageDimension(imageIndex);

        ImageTypeSpecifier type = iis.getImageType();
        sampleModel = originalSampleModel = type.getSampleModel();
        colorModel = type.getColorModel();

        // If the destination band is set used it
        sourceBands = (param == null) ? null : param.getSourceBands();

        if (sourceBands == null) {
            nComp = originalSampleModel.getNumBands();
            sourceBands = new int[nComp];
            for (int i = 0; i < nComp; i++) {
                sourceBands[i] = i;
            }
        } else {
            sampleModel = originalSampleModel.createSubsetSampleModel(sourceBands);
            colorModel = ImageUtil.createColorModel(null, sampleModel);
        }

        nComp = sourceBands.length;

        destinationBands = (param == null) ? null : param.getDestinationBands();
        if (destinationBands == null) {
            destinationBands = new int[nComp];
            for (int i = 0; i < nComp; i++) {
                destinationBands[i] = i;
            }
        }

        Dimension dim = iis.getImageDimension(imageIndex);
        this.width = dim.width;
        this.height = dim.height;

        Rectangle sourceRegion = new Rectangle(0, 0, this.width, this.height);

        originalRegion = (Rectangle) sourceRegion.clone();

        destinationRegion = (Rectangle) sourceRegion.clone();

        if (param != null) {
            RawImageReader.computeRegionsWrapper(param, this.width, this.height, param.getDestination(), sourceRegion,
                destinationRegion);
            scaleX = param.getSourceXSubsampling();
            scaleY = param.getSourceYSubsampling();
            xOffset = param.getSubsamplingXOffset();
            yOffset = param.getSubsamplingYOffset();
        }

        sourceOrigin = new Point(sourceRegion.x, sourceRegion.y);
        if (!destinationRegion.equals(sourceRegion)) {
            noTransform = false;
        }

        this.tileDataSize = ImageUtil.getTileSize(originalSampleModel);

        // Only supporting PixelInterleavedSampleModel and BandedSampleModel for tiled rendering
        if (originalSampleModel instanceof PixelInterleavedSampleModel
            || originalSampleModel instanceof BandedSampleModel) {
            this.tileWidth = originalSampleModel.getWidth();
            this.tileHeight = originalSampleModel.getHeight();
        } else {
            this.tileWidth = dim.width;
            this.tileHeight = dim.height;
        }

        this.tileGridXOffset = destinationRegion.x;
        this.tileGridYOffset = destinationRegion.y;
        this.originalNumXTiles = getNumXTiles();

        this.width = destinationRegion.width;
        this.height = destinationRegion.height;
        this.minX = destinationRegion.x;
        this.minY = destinationRegion.y;

        sampleModel = sampleModel.createCompatibleSampleModel(tileWidth, tileHeight);

        maxXTile = originalDimension.width / tileWidth;
        maxYTile = originalDimension.height / tileHeight;
    }

    @Override
    public synchronized Raster getTile(int tileX, int tileY) {
        if (currentTile != null && currentTileGrid.x == tileX && currentTileGrid.y == tileY) {
            return currentTile;
        }

        int originalNumYTiles = getNumYTiles();
        if (tileX >= originalNumXTiles || tileY >= originalNumYTiles) {
            throw new IllegalArgumentException();
        }

        try {
            // Has more than one tile
            boolean tiled = width != tileWidth || height != tileHeight;

            long pStream;
            int lineStep = 0;
            int sampleSize = (DataBuffer.getDataTypeSize(originalSampleModel.getDataType()) + 7) / 8;
            int bands = originalSampleModel.getNumBands();
            int pps = 1; // Pixel per sample
            int lineLength = tileWidth;
            int nbLine = tileHeight;

            if (tiled) {
                int tileRowOffset = (originalNumXTiles * tileWidth) - width;
                int tileColumnOffset = (originalNumYTiles * tileHeight) - height;

                if (originalSampleModel instanceof BandedSampleModel) {
                    // Position in stream (divide by bands when data model is band oriented)
                    pStream = position + tileY * originalNumXTiles * tileDataSize / bands;
                } else {
                    // For pixel interleaved, adapt the pixel size with the number of bands.
                    pps = bands;
                    sampleSize *= bands;
                    pStream = position + tileY * originalNumXTiles * tileDataSize;
                }
                // Remove partial tile size from previous rows.
                pStream -= tileY * tileRowOffset * tileHeight * sampleSize;
                // Add current raw line offset
                pStream += tileX * tileWidth * sampleSize;

                if (tileY == (originalNumYTiles - 1)) {
                    nbLine = tileHeight - tileColumnOffset;
                }
                if (tileX == (originalNumXTiles - 1)) {
                    lineLength = tileWidth - tileRowOffset;
                }
                lineStep = (width - lineLength) * sampleSize;
            } else {
                pStream = position;
            }

            iis.seek(pStream);

            int x = tileXToX(tileX);
            int y = tileYToY(tileY);
            currentTile = Raster.createWritableRaster(sampleModel, new Point(x, y));

            if (noTransform) {
                switch (sampleModel.getDataType()) {
                    case DataBuffer.TYPE_BYTE:
                        byte[][] buf = ((DataBufferByte) currentTile.getDataBuffer()).getBankData();
                        for (int i = 0; i < buf.length; i++) {
                            if (tiled) {
                                if (i > 0) {
                                    iis.seek(pStream + i * (width * height * sampleSize));
                                }
                                iis.readFully(buf[i], 0, lineLength * pps);
                                for (int j = 1; j < nbLine; j++) {
                                    iis.skipBytes(lineStep);
                                    iis.readFully(buf[i], tileWidth * j * pps, lineLength * pps);
                                }
                            } else {
                                iis.readFully(buf[i], 0, buf[i].length);
                            }
                        }
                        break;

                    case DataBuffer.TYPE_SHORT:
                        short[][] sbuf = ((DataBufferShort) currentTile.getDataBuffer()).getBankData();
                        for (int i = 0; i < sbuf.length; i++) {
                            if (tiled) {
                                // Handle seek and skipBytes in byte and readFully in short
                                if (i > 0) {
                                    iis.seek(pStream + i * (width * height * sampleSize));
                                }
                                iis.readFully(sbuf[i], 0, lineLength * pps);
                                for (int j = 1; j < nbLine; j++) {
                                    iis.skipBytes(lineStep);
                                    iis.readFully(sbuf[i], tileWidth * j * pps, lineLength * pps);
                                }
                            } else {
                                iis.readFully(sbuf[i], 0, sbuf[i].length);
                            }
                        }
                        break;

                    case DataBuffer.TYPE_USHORT:
                        short[][] usbuf = ((DataBufferUShort) currentTile.getDataBuffer()).getBankData();
                        for (int i = 0; i < usbuf.length; i++) {
                            if (tiled) {
                                if (i > 0) {
                                    iis.seek(pStream + i * (width * height * sampleSize));
                                }
                                iis.readFully(usbuf[i], 0, lineLength * pps);
                                for (int j = 1; j < nbLine; j++) {
                                    iis.skipBytes(lineStep);
                                    iis.readFully(usbuf[i], tileWidth * j * pps, lineLength * pps);
                                }
                            } else {
                                iis.readFully(usbuf[i], 0, usbuf[i].length);
                            }
                        }
                        break;
                    case DataBuffer.TYPE_INT:
                        int[][] ibuf = ((DataBufferInt) currentTile.getDataBuffer()).getBankData();
                        for (int i = 0; i < ibuf.length; i++) {
                            if (tiled) {
                                if (i > 0) {
                                    iis.seek(pStream + i * (width * height * sampleSize));
                                }
                                iis.readFully(ibuf[i], 0, lineLength * pps);
                                for (int j = 1; j < nbLine; j++) {
                                    iis.skipBytes(lineStep);
                                    iis.readFully(ibuf[i], tileWidth * j * pps, lineLength * pps);
                                }
                            } else {
                                iis.readFully(ibuf[i], 0, ibuf[i].length);
                            }
                        }
                        break;
                    case DataBuffer.TYPE_FLOAT:
                        float[][] fbuf = ((DataBufferFloat) currentTile.getDataBuffer()).getBankData();
                        for (int i = 0; i < fbuf.length; i++) {
                            if (tiled) {
                                if (i > 0) {
                                    iis.seek(pStream + i * (width * height * sampleSize));
                                }
                                iis.readFully(fbuf[i], 0, lineLength * pps);
                                for (int j = 1; j < nbLine; j++) {
                                    iis.skipBytes(lineStep);
                                    iis.readFully(fbuf[i], tileWidth * j * pps, lineLength * pps);
                                }
                            } else {
                                iis.readFully(fbuf[i], 0, fbuf[i].length);
                            }
                        }
                        break;
                    case DataBuffer.TYPE_DOUBLE:
                        double[][] dbuf = ((DataBufferDouble) currentTile.getDataBuffer()).getBankData();
                        for (int i = 0; i < dbuf.length; i++) {
                            if (tiled) {
                                if (i > 0) {
                                    iis.seek(pStream + i * (width * height * sampleSize));
                                }
                                iis.readFully(dbuf[i], 0, lineLength * pps);
                                for (int j = 1; j < nbLine; j++) {
                                    iis.skipBytes(lineStep);
                                    iis.readFully(dbuf[i], tileWidth * j * pps, lineLength * pps);
                                }
                            } else {
                                iis.readFully(dbuf[i], 0, dbuf[i].length);
                            }
                        }
                        break;
                }
            } else {
                currentTile = readSubsampledRaster((WritableRaster) currentTile);
            }
        } catch (IOException e) {
            // Issue when the stream is closed, need to throw Error because RuntimeException is caught
            // TODO Change JAI lib to propagate the RuntimeException
            throw new Error(e);
        }

        if (currentTileGrid == null) {
            currentTileGrid = new Point(tileX, tileY);
        } else {
            currentTileGrid.x = tileX;
            currentTileGrid.y = tileY;
        }

        return currentTile;
    }

    public void readAsRaster(WritableRaster raster) throws java.io.IOException {
        readSubsampledRaster(raster);
    }

    private Raster readSubsampledRaster(WritableRaster raster) throws IOException {
        if (raster == null) {
            raster = Raster.createWritableRaster(
                sampleModel.createCompatibleSampleModel(destinationRegion.x + destinationRegion.width,
                    destinationRegion.y + destinationRegion.height),
                new Point(destinationRegion.x, destinationRegion.y));
        }

        int numBands = sourceBands.length;
        int dataType = sampleModel.getDataType();
        int sampleSizeBit = DataBuffer.getDataTypeSize(dataType);
        int sampleSizeByte = (sampleSizeBit + 7) / 8;

        Rectangle destRect = raster.getBounds().intersection(destinationRegion);

        int offx = destinationRegion.x;
        int offy = destinationRegion.y;

        int sourceSX = (destRect.x - offx) * scaleX + sourceOrigin.x;
        int sourceSY = (destRect.y - offy) * scaleY + sourceOrigin.y;
        int sourceEX = (destRect.width - 1) * scaleX + sourceSX;
        int sourceEY = (destRect.height - 1) * scaleY + sourceSY;
        int startXTile = sourceSX / tileWidth;
        int startYTile = sourceSY / tileHeight;
        int endXTile = sourceEX / tileWidth;
        int endYTile = sourceEY / tileHeight;

        startXTile = clip(startXTile, 0, maxXTile);
        startYTile = clip(startYTile, 0, maxYTile);
        endXTile = clip(endXTile, 0, maxXTile);
        endYTile = clip(endYTile, 0, maxYTile);

        int totalXTiles = getNumXTiles();
        int totalYTiles = getNumYTiles();
        int totalTiles = totalXTiles * totalYTiles;

        // The line buffer for the source
        byte[] pixbuf = null; // byte buffer for the decoded pixels.
        short[] spixbuf = null; // byte buffer for the decoded pixels.
        int[] ipixbuf = null; // byte buffer for the decoded pixels.
        float[] fpixbuf = null; // byte buffer for the decoded pixels.
        double[] dpixbuf = null; // byte buffer for the decoded pixels.

        // A flag to show the ComponentSampleModel has a single data bank
        boolean singleBank = true;
        int pixelStride = 0;
        int scanlineStride = 0;
        int bandStride = 0;
        int[] bandOffsets = null;
        int[] bankIndices = null;

        if (originalSampleModel instanceof ComponentSampleModel) {
            ComponentSampleModel csm = (ComponentSampleModel) originalSampleModel;
            bankIndices = csm.getBankIndices();
            int maxBank = 0;
            for (int i = 0; i < bankIndices.length; i++) {
                if (maxBank > bankIndices[i]) {
                    maxBank = bankIndices[i];
                }
            }

            if (maxBank > 0) {
                singleBank = false;
            }
            pixelStride = csm.getPixelStride();

            scanlineStride = csm.getScanlineStride();
            bandOffsets = csm.getBandOffsets();
            for (int i = 0; i < bandOffsets.length; i++) {
                if (bandStride < bandOffsets[i]) {
                    bandStride = bandOffsets[i];
                }
            }
        } else if (originalSampleModel instanceof MultiPixelPackedSampleModel) {
            scanlineStride = ((MultiPixelPackedSampleModel) originalSampleModel).getScanlineStride();
        } else if (originalSampleModel instanceof SinglePixelPackedSampleModel) {
            pixelStride = 1;
            scanlineStride = ((SinglePixelPackedSampleModel) originalSampleModel).getScanlineStride();
        }

        // The dstination buffer for the raster
        byte[] destPixbuf = null; // byte buffer for the decoded pixels.
        short[] destSPixbuf = null; // byte buffer for the decoded pixels.
        int[] destIPixbuf = null; // byte buffer for the decoded pixels.
        float[] destFPixbuf = null; // byte buffer for the decoded pixels.
        double[] destDPixbuf = null; // byte buffer for the decoded pixels.
        int[] destBandOffsets = null;
        int destPixelStride = 0;
        int destScanlineStride = 0;
        int destSX = 0; // The first pixel for the destionation

        if (raster.getSampleModel() instanceof ComponentSampleModel) {
            ComponentSampleModel csm = (ComponentSampleModel) raster.getSampleModel();
            bankIndices = csm.getBankIndices();
            destBandOffsets = csm.getBandOffsets();
            destPixelStride = csm.getPixelStride();
            destScanlineStride = csm.getScanlineStride();
            destSX = csm.getOffset(raster.getMinX() - raster.getSampleModelTranslateX(),
                raster.getMinY() - raster.getSampleModelTranslateY()) - destBandOffsets[0];

            switch (dataType) {
                case DataBuffer.TYPE_BYTE:
                    destPixbuf = ((DataBufferByte) raster.getDataBuffer()).getData();
                    break;
                case DataBuffer.TYPE_SHORT:
                    destSPixbuf = ((DataBufferShort) raster.getDataBuffer()).getData();
                    break;

                case DataBuffer.TYPE_USHORT:
                    destSPixbuf = ((DataBufferUShort) raster.getDataBuffer()).getData();
                    break;

                case DataBuffer.TYPE_INT:
                    destIPixbuf = ((DataBufferInt) raster.getDataBuffer()).getData();
                    break;

                case DataBuffer.TYPE_FLOAT:
                    destFPixbuf = ((DataBufferFloat) raster.getDataBuffer()).getData();
                    break;

                case DataBuffer.TYPE_DOUBLE:
                    destDPixbuf = ((DataBufferDouble) raster.getDataBuffer()).getData();
                    break;
            }
        } else if (raster.getSampleModel() instanceof SinglePixelPackedSampleModel) {
            numBands = 1;
            bankIndices = new int[] { 0 };
            destBandOffsets = new int[numBands];
            for (int i = 0; i < numBands; i++) {
                destBandOffsets[i] = 0;
            }
            destPixelStride = 1;
            destScanlineStride = ((SinglePixelPackedSampleModel) raster.getSampleModel()).getScanlineStride();
        }

        // Start the data delivery to the cached consumers tile by tile
        for (int y = startYTile; y <= endYTile; y++) {
            if (reader.getAbortRequest()) {
                break;
            }

            // Loop on horizontal tiles
            for (int x = startXTile; x <= endXTile; x++) {
                if (reader.getAbortRequest()) {
                    break;
                }

                long tilePosition = position + (y * originalNumXTiles + x) * tileDataSize;
                iis.seek(tilePosition);
                float percentage = (x - startXTile + y * totalXTiles) / totalXTiles;

                int startX = x * tileWidth;
                int startY = y * tileHeight;

                int cTileHeight = tileHeight;
                int cTileWidth = tileWidth;

                if (startY + cTileHeight >= originalDimension.height) {
                    cTileHeight = originalDimension.height - startY;
                }

                if (startX + cTileWidth >= originalDimension.width) {
                    cTileWidth = originalDimension.width - startX;
                }

                int tx = startX;
                int ty = startY;

                // If source start position calculated by taking subsampling
                // into account is after the tile's start X position, adjust
                // the start position accordingly
                if (sourceSX > startX) {
                    cTileWidth += startX - sourceSX;
                    tx = sourceSX;
                    startX = sourceSX;
                }

                if (sourceSY > startY) {
                    cTileHeight += startY - sourceSY;
                    ty = sourceSY;
                    startY = sourceSY;
                }

                // If source end position calculated by taking subsampling
                // into account is prior to the tile's end X position, adjust
                // the tile width to read accordingly
                if (sourceEX < startX + cTileWidth - 1) {
                    cTileWidth += sourceEX - startX - cTileWidth + 1;
                }

                if (sourceEY < startY + cTileHeight - 1) {
                    cTileHeight += sourceEY - startY - cTileHeight + 1;
                }

                // The start X in the destination
                int x1 = (startX + scaleX - 1 - sourceOrigin.x) / scaleX;
                int x2 = (startX + scaleX - 1 + cTileWidth - sourceOrigin.x) / scaleX;
                int lineLength = x2 - x1;
                x2 = (x2 - 1) * scaleX + sourceOrigin.x;

                int y1 = (startY + scaleY - 1 - sourceOrigin.y) / scaleY;
                startX = x1 * scaleX + sourceOrigin.x;
                startY = y1 * scaleY + sourceOrigin.y;

                // offx is destination.x
                x1 += offx;
                y1 += offy;

                tx -= x * tileWidth;
                ty -= y * tileHeight;

                if (sampleModel instanceof MultiPixelPackedSampleModel) {
                    MultiPixelPackedSampleModel mppsm = (MultiPixelPackedSampleModel) originalSampleModel;

                    iis.skipBytes(mppsm.getOffset(tx, ty) * sampleSizeByte);

                    int readBytes = (mppsm.getOffset(x2, 0) - mppsm.getOffset(startX, 0) + 1) * sampleSizeByte;

                    int skipLength = (scanlineStride * scaleY - readBytes) * sampleSizeByte;
                    readBytes *= sampleSizeByte;

                    if (pixbuf == null || pixbuf.length < readBytes) {
                        pixbuf = new byte[readBytes];
                    }

                    int bitoff = mppsm.getBitOffset(tx);

                    for (int l = 0, m = y1; l < cTileHeight; l += scaleY, m++) {
                        if (reader.getAbortRequest()) {
                            break;
                        }
                        iis.readFully(pixbuf, 0, readBytes);
                        if (scaleX == 1) {

                            if (bitoff != 0) {
                                int mask1 = (255 << bitoff) & 255;
                                int mask2 = ~mask1 & 255;
                                int shift = 8 - bitoff;

                                int n = 0;
                                for (; n < readBytes - 1; n++) {
                                    pixbuf[n] =
                                        (byte) (((pixbuf[n] & mask2) << shift) | (pixbuf[n + 1] & mask1) >> bitoff);
                                }
                                pixbuf[n] = (byte) ((pixbuf[n] & mask2) << shift);
                            }
                        } else {

                            int bit = 7;
                            int pos = 0;
                            int mask = 128;

                            for (int n = 0, n1 = startX & 7; n < lineLength; n++, n1 += scaleX) {
                                pixbuf[pos] = (byte) ((pixbuf[pos] & ~(1 << bit))
                                    | (((pixbuf[n1 >> 3] >> (7 - (n1 & 7))) & 1) << bit));
                                bit--;
                                if (bit == -1) {
                                    bit = 7;
                                    pos++;
                                }
                            }
                        }

                        ImageUtil.setPackedBinaryData(pixbuf, raster, new Rectangle(x1, m, lineLength, 1));
                        iis.skipBytes(skipLength);
                        if (destImage != null) {
                            reader.processImageUpdateWrapper(destImage, x1, m, cTileWidth, 1, 1, 1, destinationBands);
                        }

                        reader.processImageProgressWrapper(percentage + (l - startY + 1.0F) / cTileHeight / totalTiles);
                    }
                } else {

                    int readLength, skipLength;
                    if (pixelStride < scanlineStride) {
                        readLength = cTileWidth * pixelStride;
                        skipLength = (scanlineStride * scaleY - readLength) * sampleSizeByte;
                    } else {
                        readLength = cTileHeight * scanlineStride;
                        skipLength = (pixelStride * scaleX - readLength) * sampleSizeByte;
                    }

                    // Allocate buffer for all the types
                    switch (sampleModel.getDataType()) {
                        case DataBuffer.TYPE_BYTE:
                            if (pixbuf == null || pixbuf.length < readLength) {
                                pixbuf = new byte[readLength];
                            }
                            break;

                        case DataBuffer.TYPE_SHORT:
                        case DataBuffer.TYPE_USHORT:
                            if (spixbuf == null || spixbuf.length < readLength) {
                                spixbuf = new short[readLength];
                            }
                            break;

                        case DataBuffer.TYPE_INT:
                            if (ipixbuf == null || ipixbuf.length < readLength) {
                                ipixbuf = new int[readLength];
                            }
                            break;

                        case DataBuffer.TYPE_FLOAT:
                            if (fpixbuf == null || fpixbuf.length < readLength) {
                                fpixbuf = new float[readLength];
                            }
                            break;

                        case DataBuffer.TYPE_DOUBLE:
                            if (dpixbuf == null || dpixbuf.length < readLength) {
                                dpixbuf = new double[readLength];
                            }
                            break;
                    }

                    if (sampleModel instanceof PixelInterleavedSampleModel) {
                        iis.skipBytes((tx * pixelStride + ty * scanlineStride) * sampleSizeByte);

                        // variables for ther loop
                        int outerFirst, outerSecond, outerStep, outerBound;
                        int innerStep, innerStep1, outerStep1;
                        if (pixelStride < scanlineStride) {
                            outerFirst = 0;
                            outerSecond = y1;
                            outerStep = scaleY;
                            outerBound = cTileHeight;
                            innerStep = scaleX * pixelStride;
                            innerStep1 = destPixelStride;
                            outerStep1 = destScanlineStride;
                        } else {
                            outerFirst = 0;
                            outerSecond = x1;
                            outerStep = scaleX;
                            outerBound = cTileWidth;
                            innerStep = scaleY * scanlineStride;
                            innerStep1 = destScanlineStride;
                            outerStep1 = destPixelStride;
                        }

                        int destPos = destSX + (y1 - raster.getSampleModelTranslateY()) * destScanlineStride
                            + (x1 - raster.getSampleModelTranslateX()) * destPixelStride;

                        for (int l = outerFirst, m = outerSecond; l < outerBound; l += outerStep, m++) {
                            if (reader.getAbortRequest()) {
                                break;
                            }

                            switch (dataType) {
                                case DataBuffer.TYPE_BYTE:
                                    if (innerStep == numBands && innerStep1 == numBands) {
                                        iis.readFully(destPixbuf, destPos, readLength);
                                    } else {
                                        iis.readFully(pixbuf, 0, readLength);
                                    }
                                    break;
                                case DataBuffer.TYPE_SHORT:
                                case DataBuffer.TYPE_USHORT:
                                    if (innerStep == numBands && innerStep1 == numBands) {
                                        iis.readFully(destSPixbuf, destPos, readLength);
                                    } else {
                                        iis.readFully(spixbuf, 0, readLength);
                                    }
                                    break;
                                case DataBuffer.TYPE_INT:
                                    if (innerStep == numBands && innerStep1 == numBands) {
                                        iis.readFully(destIPixbuf, destPos, readLength);
                                    } else {
                                        iis.readFully(ipixbuf, 0, readLength);
                                    }
                                    break;
                                case DataBuffer.TYPE_FLOAT:
                                    if (innerStep == numBands && innerStep1 == numBands) {
                                        iis.readFully(destFPixbuf, destPos, readLength);
                                    } else {
                                        iis.readFully(fpixbuf, 0, readLength);
                                    }
                                    break;
                                case DataBuffer.TYPE_DOUBLE:
                                    if (innerStep == numBands && innerStep1 == numBands) {
                                        iis.readFully(destDPixbuf, destPos, readLength);
                                    } else {
                                        iis.readFully(dpixbuf, 0, readLength);
                                    }
                                    break;
                            }

                            if (innerStep != numBands || innerStep1 != numBands) {
                                for (int b = 0; b < numBands; b++) {
                                    int destBandOffset = destBandOffsets[destinationBands[b]];
                                    destPos += destBandOffset;

                                    int sourceBandOffset = bandOffsets[sourceBands[b]];

                                    switch (dataType) {
                                        case DataBuffer.TYPE_BYTE:
                                            for (int m1 = 0, n = destPos; m1 < readLength; m1 += innerStep, n +=
                                                innerStep1) {
                                                destPixbuf[n] = pixbuf[m1 + sourceBandOffset];
                                            }
                                            break;
                                        case DataBuffer.TYPE_SHORT:
                                        case DataBuffer.TYPE_USHORT:
                                            for (int m1 = 0, n = destPos; m1 < readLength; m1 += innerStep, n +=
                                                innerStep1) {
                                                destSPixbuf[n] = spixbuf[m1 + sourceBandOffset];
                                            }
                                            break;
                                        case DataBuffer.TYPE_INT:
                                            for (int m1 = 0, n = destPos; m1 < readLength; m1 += innerStep, n +=
                                                innerStep1) {
                                                destIPixbuf[n] = ipixbuf[m1 + sourceBandOffset];
                                            }
                                            break;
                                        case DataBuffer.TYPE_FLOAT:
                                            for (int m1 = 0, n = destPos; m1 < readLength; m1 += innerStep, n +=
                                                innerStep1) {
                                                destFPixbuf[n] = fpixbuf[m1 + sourceBandOffset];
                                            }
                                            break;
                                        case DataBuffer.TYPE_DOUBLE:
                                            for (int m1 = 0, n = destPos; m1 < readLength; m1 += innerStep, n +=
                                                innerStep1) {
                                                destDPixbuf[n] = dpixbuf[m1 + sourceBandOffset];
                                            }
                                            break;
                                    }
                                    destPos -= destBandOffset;
                                }
                            }

                            iis.skipBytes(skipLength);
                            destPos += outerStep1;

                            if (destImage != null) {
                                if (pixelStride < scanlineStride) {
                                    reader.processImageUpdateWrapper(destImage, x1, m, outerBound, 1, 1, 1,
                                        destinationBands);
                                } else {
                                    reader.processImageUpdateWrapper(destImage, m, y1, 1, outerBound, 1, 1,
                                        destinationBands);
                                }
                            }

                            reader.processImageProgressWrapper(percentage + (l + 1.0F) / outerBound / totalTiles);
                        }
                    } else if (sampleModel instanceof BandedSampleModel
                        || sampleModel instanceof SinglePixelPackedSampleModel || bandStride == 0) {
                        boolean isBanded = sampleModel instanceof BandedSampleModel;

                        int bandSize = (int) ImageUtil.getBandSize(originalSampleModel);

                        for (int b = 0; b < numBands; b++) {
                            iis.seek(tilePosition + bandSize * sourceBands[b] * sampleSizeByte);
                            int destBandOffset = destBandOffsets[destinationBands[b]];

                            iis.skipBytes((ty * scanlineStride + tx * pixelStride) * sampleSizeByte);

                            // variables for ther loop
                            int outerFirst, outerSecond, outerStep, outerBound;
                            int innerStep, innerStep1, outerStep1;
                            if (pixelStride < scanlineStride) {
                                outerFirst = 0;
                                outerSecond = y1;
                                outerStep = scaleY;
                                outerBound = cTileHeight;
                                innerStep = scaleX * pixelStride;
                                innerStep1 = destPixelStride;
                                outerStep1 = destScanlineStride;
                            } else {
                                outerFirst = 0;
                                outerSecond = x1;
                                outerStep = scaleX;
                                outerBound = cTileWidth;
                                innerStep = scaleY * scanlineStride;
                                innerStep1 = destScanlineStride;
                                outerStep1 = destPixelStride;
                            }

                            int destPos = destSX + (y1 - raster.getSampleModelTranslateY()) * destScanlineStride
                                + (x1 - raster.getSampleModelTranslateX()) * destPixelStride + destBandOffset;

                            int bank = bankIndices[destinationBands[b]];

                            switch (dataType) {
                                case DataBuffer.TYPE_BYTE:
                                    destPixbuf = ((DataBufferByte) raster.getDataBuffer()).getData(bank);
                                    break;
                                case DataBuffer.TYPE_SHORT:
                                    destSPixbuf = ((DataBufferShort) raster.getDataBuffer()).getData(bank);
                                    break;

                                case DataBuffer.TYPE_USHORT:
                                    destSPixbuf = ((DataBufferUShort) raster.getDataBuffer()).getData(bank);
                                    break;

                                case DataBuffer.TYPE_INT:
                                    destIPixbuf = ((DataBufferInt) raster.getDataBuffer()).getData(bank);
                                    break;

                                case DataBuffer.TYPE_FLOAT:
                                    destFPixbuf = ((DataBufferFloat) raster.getDataBuffer()).getData(bank);
                                    break;

                                case DataBuffer.TYPE_DOUBLE:
                                    destDPixbuf = ((DataBufferDouble) raster.getDataBuffer()).getData(bank);
                                    break;
                            }

                            for (int l = outerFirst, m = outerSecond; l < outerBound; l += outerStep, m++) {
                                if (reader.getAbortRequest()) {
                                    break;
                                }

                                switch (dataType) {
                                    case DataBuffer.TYPE_BYTE:
                                        if (innerStep == 1 && innerStep1 == 1) {
                                            iis.readFully(destPixbuf, destPos, readLength);
                                        } else {
                                            iis.readFully(pixbuf, 0, readLength);
                                            for (int m1 = 0, n = destPos; m1 < readLength; m1 += innerStep, n +=
                                                innerStep1) {
                                                destPixbuf[n] = pixbuf[m1];
                                            }
                                        }
                                        break;
                                    case DataBuffer.TYPE_SHORT:
                                    case DataBuffer.TYPE_USHORT:
                                        if (innerStep == 1 && innerStep1 == 1) {
                                            iis.readFully(destSPixbuf, destPos, readLength);
                                        } else {
                                            iis.readFully(spixbuf, 0, readLength);
                                            for (int m1 = 0, n = destPos; m1 < readLength; m1 += innerStep, n +=
                                                innerStep1) {
                                                destSPixbuf[n] = spixbuf[m1];
                                            }
                                        }
                                        break;
                                    case DataBuffer.TYPE_INT:
                                        if (innerStep == 1 && innerStep1 == 1) {
                                            iis.readFully(destIPixbuf, destPos, readLength);
                                        } else {
                                            iis.readFully(ipixbuf, 0, readLength);
                                            for (int m1 = 0, n = destPos; m1 < readLength; m1 += innerStep, n +=
                                                innerStep1) {
                                                destIPixbuf[n] = ipixbuf[m1];
                                            }
                                        }
                                        break;
                                    case DataBuffer.TYPE_FLOAT:
                                        if (innerStep == 1 && innerStep1 == 1) {
                                            iis.readFully(destFPixbuf, destPos, readLength);
                                        } else {
                                            iis.readFully(fpixbuf, 0, readLength);
                                            for (int m1 = 0, n = destPos; m1 < readLength; m1 += innerStep, n +=
                                                innerStep1) {
                                                destFPixbuf[n] = fpixbuf[m1];
                                            }
                                        }
                                        break;
                                    case DataBuffer.TYPE_DOUBLE:
                                        if (innerStep == 1 && innerStep1 == 1) {
                                            iis.readFully(destDPixbuf, destPos, readLength);
                                        } else {
                                            iis.readFully(dpixbuf, 0, readLength);
                                            for (int m1 = 0, n = destPos; m1 < readLength; m1 += innerStep, n +=
                                                innerStep1) {
                                                destDPixbuf[n] = dpixbuf[m1];
                                            }
                                        }
                                        break;
                                }

                                iis.skipBytes(skipLength);
                                destPos += outerStep1;

                                if (destImage != null) {
                                    int[] destBands = new int[] { destinationBands[b] };
                                    if (pixelStride < scanlineStride) {
                                        reader.processImageUpdateWrapper(destImage, x1, m, outerBound, 1, 1, 1,
                                            destBands);
                                    } else {
                                        reader.processImageUpdateWrapper(destImage, m, y1, 1, outerBound, 1, 1,
                                            destBands);
                                    }
                                }

                                reader.processImageProgressWrapper(
                                    (percentage + (l + 1.0F) / outerBound / numBands / totalTiles) * 100.0F);
                            }
                        }
                    } else if (sampleModel instanceof ComponentSampleModel) {
                        // for the other case, may slow
                        // Allocate buffer for all the types
                        int bufferSize = (int) tileDataSize;

                        switch (sampleModel.getDataType()) {
                            case DataBuffer.TYPE_BYTE:
                                if (pixbuf == null || pixbuf.length < tileDataSize) {
                                    pixbuf = new byte[(int) tileDataSize];
                                }
                                iis.readFully(pixbuf, 0, (int) tileDataSize);
                                break;

                            case DataBuffer.TYPE_SHORT:
                            case DataBuffer.TYPE_USHORT:
                                bufferSize /= 2;
                                if (spixbuf == null || spixbuf.length < bufferSize) {
                                    spixbuf = new short[bufferSize];
                                }
                                iis.readFully(spixbuf, 0, bufferSize);
                                break;

                            case DataBuffer.TYPE_INT:
                                bufferSize /= 4;
                                if (ipixbuf == null || ipixbuf.length < bufferSize) {
                                    ipixbuf = new int[bufferSize];
                                }
                                iis.readFully(ipixbuf, 0, bufferSize);
                                break;

                            case DataBuffer.TYPE_FLOAT:
                                bufferSize /= 4;
                                if (fpixbuf == null || fpixbuf.length < bufferSize) {
                                    fpixbuf = new float[bufferSize];
                                }
                                iis.readFully(fpixbuf, 0, bufferSize);
                                break;

                            case DataBuffer.TYPE_DOUBLE:
                                bufferSize /= 8;
                                if (dpixbuf == null || dpixbuf.length < bufferSize) {
                                    dpixbuf = new double[bufferSize];
                                }
                                iis.readFully(dpixbuf, 0, bufferSize);
                                break;
                        }

                        for (int b = 0; b < numBands; b++) {
                            int destBandOffset = destBandOffsets[destinationBands[b]];

                            int destPos = ((ComponentSampleModel) raster.getSampleModel()).getOffset(
                                x1 - raster.getSampleModelTranslateX(), y1 - raster.getSampleModelTranslateY(),
                                destinationBands[b]);

                            int bank = bankIndices[destinationBands[b]];

                            switch (dataType) {
                                case DataBuffer.TYPE_BYTE:
                                    destPixbuf = ((DataBufferByte) raster.getDataBuffer()).getData(bank);
                                    break;
                                case DataBuffer.TYPE_SHORT:
                                    destSPixbuf = ((DataBufferShort) raster.getDataBuffer()).getData(bank);
                                    break;

                                case DataBuffer.TYPE_USHORT:
                                    destSPixbuf = ((DataBufferUShort) raster.getDataBuffer()).getData(bank);
                                    break;

                                case DataBuffer.TYPE_INT:
                                    destIPixbuf = ((DataBufferInt) raster.getDataBuffer()).getData(bank);
                                    break;

                                case DataBuffer.TYPE_FLOAT:
                                    destFPixbuf = ((DataBufferFloat) raster.getDataBuffer()).getData(bank);
                                    break;

                                case DataBuffer.TYPE_DOUBLE:
                                    destDPixbuf = ((DataBufferDouble) raster.getDataBuffer()).getData(bank);
                                    break;
                            }

                            int srcPos = ((ComponentSampleModel) originalSampleModel).getOffset(tx, ty, sourceBands[b]);
                            int skipX = scaleX * pixelStride;
                            ;
                            for (int l = 0, m = y1; l < cTileHeight; l += scaleY, m++) {
                                if (reader.getAbortRequest()) {
                                    break;
                                }

                                switch (dataType) {
                                    case DataBuffer.TYPE_BYTE:
                                        for (int n = 0, m1 = srcPos, m2 = destPos; n < lineLength; n++, m1 +=
                                            skipX, m2 += destPixelStride) {
                                            destPixbuf[m2] = pixbuf[m1];
                                        }
                                        break;
                                    case DataBuffer.TYPE_SHORT:
                                    case DataBuffer.TYPE_USHORT:
                                        for (int n = 0, m1 = srcPos, m2 = destPos; n < lineLength; n++, m1 +=
                                            skipX, m2 += destPixelStride) {
                                            destSPixbuf[m2] = spixbuf[m1];
                                        }
                                        break;
                                    case DataBuffer.TYPE_INT:
                                        for (int n = 0, m1 = srcPos, m2 = destPos; n < lineLength; n++, m1 +=
                                            skipX, m2 += destPixelStride) {
                                            destIPixbuf[m2] = ipixbuf[m1];
                                        }
                                        break;
                                    case DataBuffer.TYPE_FLOAT:
                                        for (int n = 0, m1 = srcPos, m2 = destPos; n < lineLength; n++, m1 +=
                                            skipX, m2 += destPixelStride) {
                                            destFPixbuf[m2] = fpixbuf[m1];
                                        }
                                        break;
                                    case DataBuffer.TYPE_DOUBLE:
                                        for (int n = 0, m1 = srcPos, m2 = destPos; n < lineLength; n++, m1 +=
                                            skipX, m2 += destPixelStride) {
                                            destDPixbuf[m2] = dpixbuf[m1];
                                        }
                                        break;
                                }

                                destPos += destScanlineStride;
                                srcPos += scanlineStride * scaleY;

                                if (destImage != null) {
                                    int[] destBands = new int[] { destinationBands[b] };
                                    reader.processImageUpdateWrapper(destImage, x1, m, cTileHeight, 1, 1, 1, destBands);
                                }

                                reader.processImageProgressWrapper(
                                    percentage + (l + 1.0F) / cTileHeight / numBands / totalTiles);
                            }
                        }
                    } else {
                        throw new IllegalArgumentException();
                    }
                }
            } // End loop on horizontal tiles
        } // End loop on vertical tiles

        return raster;
    }

    public void setDestImage(BufferedImage image) {
        destImage = image;
    }

    public void clearDestImage() {
        destImage = null;
    }

    private int getTileNum(int x, int y) {
        int num = (y - getMinTileY()) * getNumXTiles() + x - getMinTileX();

        if (num < 0 || num >= getNumXTiles() * getNumYTiles()) {
            throw new IllegalArgumentException();
        }

        return num;
    }

    private int clip(int value, int min, int max) {
        if (value < min) {
            value = min;
        }
        if (value > max) {
            value = max;
        }
        return value;
    }
}
