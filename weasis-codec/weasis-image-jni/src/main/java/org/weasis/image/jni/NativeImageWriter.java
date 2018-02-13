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
package org.weasis.image.jni;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentSampleModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferShort;
import java.awt.image.DataBufferUShort;
import java.awt.image.DirectColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.MultiPixelPackedSampleModel;
import java.awt.image.PackedColorModel;
import java.awt.image.PixelInterleavedSampleModel;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;

import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageWriterSpi;

public abstract class NativeImageWriter extends ImageWriter {

    protected static final Object getDataBufferData(DataBuffer db) {
        Object data;

        int dType = db.getDataType();
        switch (dType) {
            case DataBuffer.TYPE_BYTE:
                data = ((DataBufferByte) db).getData();
                break;
            case DataBuffer.TYPE_USHORT:
                data = ((DataBufferUShort) db).getData();
                break;
            case DataBuffer.TYPE_SHORT:
                data = ((DataBufferShort) db).getData();
                break;
            default:
                throw new IllegalArgumentException("Unsupported data type: " + dType);
        }

        return data;
    }

    /**
     * Returns a contiguous <code>Raster</code> of data over the specified <code>Rectangle</code>. If the region is a
     * sub-region of a single tile, then a child of that tile will be returned. If the region overlaps more than one
     * tile and has 8 bits per sample, then a pixel interleaved Raster having band offsets 0,1,... will be returned.
     * Otherwise the Raster returned by <code>im.copyData(null)</code> will be returned.
     */
    protected static final Raster getContiguousData(RenderedImage im, Rectangle region) {
        if (im == null) {
            throw new IllegalArgumentException("im == null");
        } else if (region == null) {
            throw new IllegalArgumentException("region == null");
        }

        Raster raster;
        if (im.getNumXTiles() == 1 && im.getNumYTiles() == 1) {
            // Image is not tiled so just get a reference to the tile.
            raster = im.getTile(im.getMinTileX(), im.getMinTileY());

            // Ensure result has requested coverage.
            Rectangle bounds = raster.getBounds();
            if (!bounds.equals(region)) {
                raster = raster.createChild(region.x, region.y, region.width, region.height, region.x, region.y, null);
            }
        } else {
            // Image is tiled.

            // Create an interleaved raster for copying for 8-bit case.
            // This ensures that for RGB data the band offsets are {0,1,2}.
            SampleModel sampleModel = im.getSampleModel();
            WritableRaster target =
                sampleModel.getSampleSize(0) == 8 ? Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE, im.getWidth(),
                    im.getHeight(), sampleModel.getNumBands(), new Point(im.getMinX(), im.getMinY())) : null;

            // Copy the data.
            raster = im.copyData(target);
        }

        return raster;
    }

    /**
     * Subsamples and sub-bands the input <code>Raster</code> over a sub-region and stores the result in a
     * <code>WritableRaster</code>.
     *
     * @param src
     *            The source <code>Raster</code>
     * @param sourceBands
     *            The source bands to use; may be <code>null</code>
     * @param subsampleX
     *            The subsampling factor along the horizontal axis.
     * @param subsampleY
     *            The subsampling factor along the vertical axis. in which case all bands will be used.
     * @param dst
     *            The destination <code>WritableRaster</code>.
     * @throws IllegalArgumentException
     *             if <code>source</code> is <code>null</code> or empty, <code>dst</code> is <code>null</code>,
     *             <code>sourceBands.length</code> exceeds the number of bands in <code>source</code>, or
     *             <code>sourcBands</code> contains an element which is negative or greater than or equal to the number
     *             of bands in <code>source</code>.
     */
    private static void reformat(Raster source, int[] sourceBands, int subsampleX, int subsampleY, WritableRaster dst) {
        // Check for nulls.
        if (source == null) {
            throw new IllegalArgumentException("source == null!");
        } else if (dst == null) {
            throw new IllegalArgumentException("dst == null!");
        }

        // Validate the source bounds. XXX is this needed?
        Rectangle sourceBounds = source.getBounds();
        if (sourceBounds.isEmpty()) {
            throw new IllegalArgumentException("source.getBounds().isEmpty()!");
        }

        // Check sub-banding.
        boolean isSubBanding = false;
        int numSourceBands = source.getSampleModel().getNumBands();
        if (sourceBands != null) {
            if (sourceBands.length > numSourceBands) {
                throw new IllegalArgumentException("sourceBands.length > numSourceBands!");
            }

            boolean isRamp = sourceBands.length == numSourceBands;
            for (int i = 0; i < sourceBands.length; i++) {
                if (sourceBands[i] < 0 || sourceBands[i] >= numSourceBands) {
                    throw new IllegalArgumentException("sourceBands[i] < 0 || sourceBands[i] >= numSourceBands!");
                } else if (sourceBands[i] != i) {
                    isRamp = false;
                }
            }

            isSubBanding = !isRamp;
        }

        // Allocate buffer for a single source row.
        int sourceWidth = sourceBounds.width;
        int[] pixels = new int[sourceWidth * numSourceBands];

        // Initialize variables used in loop.
        int sourceX = sourceBounds.x;
        int sourceY = sourceBounds.y;
        int numBands = sourceBands != null ? sourceBands.length : numSourceBands;
        int dstWidth = dst.getWidth();
        int dstYMax = dst.getHeight() - 1;
        int copyFromIncrement = numSourceBands * subsampleX;

        // Loop over source rows, subsample each, and store in destination.
        for (int dstY = 0; dstY <= dstYMax; dstY++) {
            // Read one row.
            source.getPixels(sourceX, sourceY, sourceWidth, 1, pixels);

            // Copy within the same buffer by left shifting.
            if (isSubBanding) {
                int copyFrom = 0;
                int copyTo = 0;
                for (int i = 0; i < dstWidth; i++) {
                    for (int j = 0; j < numBands; j++) {
                        pixels[copyTo++] = pixels[copyFrom + sourceBands[j]];
                    }
                    copyFrom += copyFromIncrement;
                }
            } else {
                int copyFrom = copyFromIncrement;
                int copyTo = numSourceBands;
                // Start from index 1 as no need to copy the first pixel.
                for (int i = 1; i < dstWidth; i++) {
                    int k = copyFrom;
                    for (int j = 0; j < numSourceBands; j++) {
                        pixels[copyTo++] = pixels[k++];
                    }
                    copyFrom += copyFromIncrement;
                }
            }

            // Set the destionation row.
            dst.setPixels(0, dstY, dstWidth, 1, pixels);

            // Increment the source row.
            sourceY += subsampleY;
        }
    }

    protected NativeImageWriter(ImageWriterSpi originatingProvider) {
        super(originatingProvider);
    }

    @Override
    public IIOMetadata convertImageMetadata(IIOMetadata inData, ImageTypeSpecifier imageType, ImageWriteParam param) {
        return null;
    }

    @Override
    public IIOMetadata convertStreamMetadata(IIOMetadata inData, ImageWriteParam param) {
        return null;
    }

    @Override
    public IIOMetadata getDefaultImageMetadata(ImageTypeSpecifier imageType, ImageWriteParam param) {
        return null;
    }

    @Override
    public IIOMetadata getDefaultStreamMetadata(ImageWriteParam param) {
        return null;
    }

    protected static final Rectangle getSourceRegion(ImageWriteParam param, int sourceMinX, int sourceMinY,
        int srcWidth, int srcHeight) {
        Rectangle sourceRegion = new Rectangle(sourceMinX, sourceMinY, srcWidth, srcHeight);
        if (param != null) {
            Rectangle region = param.getSourceRegion();
            if (region != null) {
                sourceRegion = sourceRegion.intersection(region);
            }

            int subsampleXOffset = param.getSubsamplingXOffset();
            int subsampleYOffset = param.getSubsamplingYOffset();
            sourceRegion.x += subsampleXOffset;
            sourceRegion.y += subsampleYOffset;
            sourceRegion.width -= subsampleXOffset;
            sourceRegion.height -= subsampleYOffset;
        }

        return sourceRegion;
    }

    protected void formatInputDataBuffer(NativeImage nImage, RenderedImage image, ImageWriteParam param,
        boolean allowBilevel, int[] supportedFormats) {
        if (supportedFormats == null) {
            throw new IllegalArgumentException("supportedFormats == null!");
        }

        // Determine the source region.
        Rectangle sourceRegion =
            getSourceRegion(param, image.getMinX(), image.getMinY(), image.getWidth(), image.getHeight());

        if (sourceRegion.isEmpty()) {
            throw new IllegalArgumentException("sourceRegion.isEmpty()");
        }

        // Check whether reformatting is necessary to conform to mediaLib
        // image format (packed bilevel if allowed or ((G|I)|(RGB))[A]).

        boolean reformatData = false;
        boolean isBilevel = false;

        SampleModel sampleModel = image.getSampleModel();
        int numSourceBands = sampleModel.getNumBands();
        int[] sourceBands = param != null ? param.getSourceBands() : null;

        // Check for non-nominal sub-banding.
        int numBands;
        if (sourceBands != null) {
            numBands = sourceBands.length;
            if (numBands != numSourceBands) {
                // The number of bands must be the same.
                reformatData = true;
            } else {
                // The band order must not change.
                for (int i = 0; i < numSourceBands; i++) {
                    if (sourceBands[i] != i) {
                        reformatData = true;
                        break;
                    }
                }
            }
        } else {
            numBands = numSourceBands;
        }

        int cs_format = numBands == 1 ? ImageParameters.CM_GRAY
            : numBands == 3 ? ImageParameters.CM_S_RGB : ImageParameters.CM_S_RGBA;

        // If sub-banding does not dictate reformatting, check subsampling..
        if (!reformatData && param != null
            && (param.getSourceXSubsampling() != 1 || param.getSourceXSubsampling() != 1)) {
            reformatData = true;
        }

        // If sub-banding does not dictate reformatting check SampleModel.
        if (!reformatData) {
            if (allowBilevel && sampleModel.getNumBands() == 1 && sampleModel.getSampleSize(0) == 1
                && sampleModel instanceof MultiPixelPackedSampleModel
                && sampleModel.getDataType() == DataBuffer.TYPE_BYTE) {
                // Need continguous packed bits.
                MultiPixelPackedSampleModel mppsm = (MultiPixelPackedSampleModel) sampleModel;
                if (mppsm.getPixelBitStride() == 1) {
                    isBilevel = true;
                } else {
                    reformatData = true;
                }
            } else {
                // TODO get format
                // cs_format = getFormat(sampleModel, image.getColorModel());

                // Set the data reformatting flag.
                reformatData = true;
                int len = supportedFormats.length;
                for (int i = 0; i < len; i++) {
                    if (cs_format == supportedFormats[i]) {
                        reformatData = false;
                        break;
                    }
                }
            }
        }

        // Variable for the eventual destination data.
        Raster raster = null;

        if (reformatData) {
            // Determine the maximum bit depth.
            int[] sampleSize = sampleModel.getSampleSize();
            int bitDepthMax = sampleSize[0];
            for (int i = 1; i < numSourceBands; i++) {
                bitDepthMax = Math.max(bitDepthMax, sampleSize[i]);
            }

            // Set the data type as a function of bit depth.
            int dataType;
            if (bitDepthMax <= 8) {
                dataType = DataBuffer.TYPE_BYTE;
            } else if (bitDepthMax <= 16) {
                dataType = DataBuffer.TYPE_USHORT;
            } else {
                throw new IllegalArgumentException("Unsupported data type, pixel depth: " + bitDepthMax);

            }

            // Determine the width and height.
            int width;
            int height;
            if (param != null) {
                int subsampleX = param.getSourceXSubsampling();
                int subsampleY = param.getSourceYSubsampling();
                width = (sourceRegion.width + subsampleX - 1) / subsampleX;
                height = (sourceRegion.height + subsampleY - 1) / subsampleY;
            } else {
                width = sourceRegion.width;
                height = sourceRegion.height;
            }

            // Load a ramp for band offsets.
            int[] newBandOffsets = new int[numBands];
            for (int i = 0; i < numBands; i++) {
                newBandOffsets[i] = i;
            }

            // Create a new SampleModel.
            SampleModel newSampleModel;
            if (allowBilevel && sampleModel.getNumBands() == 1 && bitDepthMax == 1) {
                newSampleModel = new MultiPixelPackedSampleModel(dataType, width, height, 1);
                isBilevel = true;
            } else {
                newSampleModel = new PixelInterleavedSampleModel(dataType, width, height, newBandOffsets.length,
                    width * numSourceBands, newBandOffsets);
            }

            // Create a new Raster at (0,0).
            WritableRaster newRaster = Raster.createWritableRaster(newSampleModel, null);

            // Populate the new Raster.
            if (param != null && (param.getSourceXSubsampling() != 1 || param.getSourceXSubsampling() != 1)) {
                // Subsampling, possibly with sub-banding.
                reformat(getContiguousData(image, sourceRegion), sourceBands, param.getSourceXSubsampling(),
                    param.getSourceYSubsampling(), newRaster);
            } else if (sourceBands == null && image.getSampleModel().getClass().isInstance(newSampleModel)
                && newSampleModel.getTransferType() == image.getSampleModel().getTransferType()) {
                // Neither subsampling nor sub-banding.
                WritableRaster translatedChild =
                    newRaster.createWritableTranslatedChild(sourceRegion.x, sourceRegion.y);
                // Use copyData() to avoid potentially cobbling the entire
                // source region into an extra Raster via getData().
                image.copyData(translatedChild);
            } else {
                // Cannot use copyData() so use getData() to retrieve and
                // possibly sub-band the source data and use setRect().
                WritableRaster translatedChild =
                    newRaster.createWritableTranslatedChild(sourceRegion.x, sourceRegion.y);
                Raster sourceRaster = getContiguousData(image, sourceRegion);
                if (sourceBands != null) {
                    // Copy only the requested bands.
                    sourceRaster = sourceRaster.createChild(sourceRegion.x, sourceRegion.y, sourceRegion.width,
                        sourceRegion.height, sourceRegion.x, sourceRegion.y, sourceBands);
                }

                // Get the region from the image and set it into the Raster.
                translatedChild.setRect(sourceRaster);
            }
            raster = newRaster;
            sampleModel = newRaster.getSampleModel();
        } else {
            raster = getContiguousData(image, sourceRegion).createTranslatedChild(0, 0);
            sampleModel = raster.getSampleModel();

            // TODO get format
            // cs_format = getFormat(sampleModel, image.getColorModel());
        }

        // Create the input data buffer
        if (isBilevel) {
            // Bilevel image
            MultiPixelPackedSampleModel mppsm = ((MultiPixelPackedSampleModel) sampleModel);
            int stride = mppsm.getScanlineStride();

            // Determine the offset to the start of the data.
            int offset = raster.getDataBuffer().getOffset() - raster.getSampleModelTranslateY() * stride
                - raster.getSampleModelTranslateX() / 8 + mppsm.getOffset(0, 0);

            // Get a reference to the internal data array.
            Object bitData = getDataBufferData(raster.getDataBuffer());
            int dataLength = raster.getDataBuffer().getSize();

            ImageParameters params = nImage.getImageParameters();
            params.setDataType(ImageParameters.TYPE_BIT);
            params.setSamplesPerPixel(sampleModel.getNumBands());
            params.setBitsPerSample(sampleModel.getSampleSize(0));
            params.setWidth(raster.getWidth());
            params.setHeight(raster.getHeight());
            params.setBytesPerLine((stride * params.getBitsPerSample() + 7) / 8);

            nImage.fillInputBuffer(bitData, offset, dataLength);
        } else {
            ComponentSampleModel csm = (ComponentSampleModel) sampleModel;

            // Get the internal data array.
            Object data = getDataBufferData(raster.getDataBuffer());
            int dataLength = raster.getDataBuffer().getSize();
            int stride = csm.getScanlineStride();

            int[] bandOffsets = csm.getBandOffsets();
            int minBandOffset = bandOffsets[0];
            for (int i = 1; i < bandOffsets.length; i++) {
                if (bandOffsets[i] < minBandOffset) {
                    minBandOffset = bandOffsets[i];
                }
            }
            // Determine the offset to the start of the data
            int offset = (raster.getMinY() - raster.getSampleModelTranslateY()) * stride
                + (raster.getMinX() - raster.getSampleModelTranslateX()) * numSourceBands + minBandOffset;

            ImageParameters params = nImage.getImageParameters();
            params.setDataType(sampleModel.getDataType());
            params.setSamplesPerPixel(sampleModel.getNumBands());
            params.setBitsPerSample(sampleModel.getSampleSize(0));
            params.setWidth(raster.getWidth());
            params.setHeight(raster.getHeight());
            params.setBytesPerLine((stride * params.getBitsPerSample() + 7) / 8);

            nImage.fillInputBuffer(data, offset, dataLength);
        }
    }

    protected abstract NativeCodec getCodec();

    /**
     * Convert an IndexColorModel-based image to 3-band component RGB.
     *
     * @param image
     *            The source image.
     */
    protected static BufferedImage convertTo3BandRGB(RenderedImage image) {
        if (image == null) {
            throw new IllegalArgumentException("image cannot be null");
        }

        ColorModel cm = image.getColorModel();
        if (!(cm instanceof IndexColorModel)) {
            throw new IllegalArgumentException("color model in not IndexColorModel!");
        }

        Raster src;
        if (image.getNumXTiles() == 1 && image.getNumYTiles() == 1) {
            // Image is not tiled so just get a reference to the tile.
            src = image.getTile(image.getMinTileX(), image.getMinTileY());

            if (src.getWidth() != image.getWidth() || src.getHeight() != image.getHeight()) {
                src = src.createChild(src.getMinX(), src.getMinY(), image.getWidth(), image.getHeight(), src.getMinX(),
                    src.getMinY(), null);
            }
        } else {
            // Image is tiled so need to get a contiguous raster.
            src = image.getData();
        }

        // This is probably not the most efficient approach given that
        // the mediaLibImage will eventually need to be in component form.
        BufferedImage dst = ((IndexColorModel) cm).convertToIntDiscrete(src, false);

        if (dst.getSampleModel().getNumBands() == 4) {
            //
            // Without copying data create a BufferedImage which has
            // only the RGB bands, not the alpha band.
            //
            WritableRaster rgbaRas = dst.getRaster();
            WritableRaster rgbRas =
                rgbaRas.createWritableChild(0, 0, dst.getWidth(), dst.getHeight(), 0, 0, new int[] { 0, 1, 2 });
            PackedColorModel pcm = (PackedColorModel) dst.getColorModel();
            int bits = pcm.getComponentSize(0) + pcm.getComponentSize(1) + pcm.getComponentSize(2);
            DirectColorModel dcm = new DirectColorModel(bits, pcm.getMask(0), pcm.getMask(1), pcm.getMask(2));
            dst = new BufferedImage(dcm, rgbRas, false, null);
        }

        return dst;
    }

}
