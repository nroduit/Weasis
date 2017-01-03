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
package org.weasis.dicom.codec;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;

import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageReaderSpi;

import com.sun.media.imageio.stream.RawImageInputStream;

/**
 * This class is the Java Image IO plugin reader for Raw images. It may subsample the image, clip the image, select
 * sub-bands, and shift the decoded image origin if the proper decoding parameter are set in the provided
 * <code>PNMImageReadParam</code>.
 */
public class RawImageReader extends ImageReader {
    /** The input stream where reads from */
    private RawImageInputStream iis = null;

    /**
     * Constructs <code>RawImageReader</code> from the provided <code>ImageReaderSpi</code>.
     */
    public RawImageReader(ImageReaderSpi originator) {
        super(originator);
    }

    /**
     * Wrapper for the protected method <code>computeRegions</code>. So it can be access from the classes which are not
     * in <code>ImageReader</code> hierachy.
     */
    public static void computeRegionsWrapper(ImageReadParam param, int srcWidth, int srcHeight, BufferedImage image,
        Rectangle srcRegion, Rectangle destRegion) {
        computeRegions(param, srcWidth, srcHeight, image, srcRegion, destRegion);
    }

    /**
     * Overrides the method defined in the superclass.
     *
     * @throw ClassCastException If the provided <code>input</code> is not an instance of
     *        <code>RawImageInputImage</code>
     */
    @Override
    public void setInput(Object input, boolean seekForwardOnly, boolean ignoreMetadata) {
        super.setInput(input, seekForwardOnly, ignoreMetadata);
        iis = (RawImageInputStream) input; // Always works
    }

    /** Overrides the method defined in the superclass. */
    @Override
    public int getNumImages(boolean allowSearch) throws IOException {
        return iis.getNumImages();
    }

    @Override
    public int getWidth(int imageIndex) throws IOException {
        checkIndex(imageIndex);
        return iis.getImageDimension(imageIndex).width;
    }

    @Override
    public int getHeight(int imageIndex) throws IOException {
        checkIndex(imageIndex);

        return iis.getImageDimension(imageIndex).height;
    }

    @Override
    public int getTileWidth(int imageIndex) throws IOException {
        checkIndex(imageIndex);
        return iis.getImageType().getSampleModel().getWidth();
    }

    @Override
    public int getTileHeight(int imageIndex) throws IOException {
        checkIndex(imageIndex);
        return iis.getImageType().getSampleModel().getHeight();
    }

    private void checkIndex(int imageIndex) throws IOException {
        if (imageIndex < 0 || imageIndex >= getNumImages(true)) {
            throw new IndexOutOfBoundsException();
        }
    }

    @Override
    public Iterator getImageTypes(int imageIndex) throws IOException {
        checkIndex(imageIndex);
        ArrayList list = new ArrayList(1);
        list.add(iis.getImageType());
        return list.iterator();
    }

    @Override
    public ImageReadParam getDefaultReadParam() {
        return new ImageReadParam();
    }

    @Override
    public IIOMetadata getImageMetadata(int imageIndex) throws IOException {
        return null;
    }

    @Override
    public IIOMetadata getStreamMetadata() throws IOException {
        return null;
    }

    @Override
    public boolean isRandomAccessEasy(int imageIndex) throws IOException {
        checkIndex(imageIndex);
        return true;
    }

    @Override
    public BufferedImage read(int imageIndex, ImageReadParam param) throws IOException {
        if (param == null) {
            param = getDefaultReadParam();
        }
        checkIndex(imageIndex);
        clearAbortRequest();
        processImageStarted(imageIndex);

        BufferedImage bi = param.getDestination();
        RawRenderedImage image = new RawRenderedImage(iis, this, param, imageIndex);
        Point offset = param.getDestinationOffset();
        WritableRaster raster;

        if (bi == null) {
            ColorModel colorModel = image.getColorModel();
            SampleModel sampleModel = image.getSampleModel();

            // If the destination type is specified, use the color model of it.
            ImageTypeSpecifier type = param.getDestinationType();
            if (type != null) {
                colorModel = type.getColorModel();
            }

            raster =
                Raster.createWritableRaster(sampleModel.createCompatibleSampleModel(image.getMinX() + image.getWidth(),
                    image.getMinY() + image.getHeight()), new Point(0, 0));

            bi = new BufferedImage(colorModel, raster, colorModel.isAlphaPremultiplied(), null);
        } else {
            raster = bi.getWritableTile(0, 0);
        }

        image.setDestImage(bi);

        image.readAsRaster(raster);
        image.clearDestImage();

        if (abortRequested()) {
            processReadAborted();
        } else {
            processImageComplete();
        }
        return bi;
    }

    @Override
    public RenderedImage readAsRenderedImage(int imageIndex, ImageReadParam param) throws java.io.IOException {
        if (param == null) {
            param = getDefaultReadParam();
        }

        checkIndex(imageIndex);
        clearAbortRequest();
        processImageStarted(0);

        RenderedImage image = new RawRenderedImage(iis, this, param, imageIndex);

        if (abortRequested()) {
            processReadAborted();
        } else {
            processImageComplete();
        }
        return image;
    }

    @Override
    public Raster readRaster(int imageIndex, ImageReadParam param) throws IOException {
        BufferedImage bi = read(imageIndex, param);
        return bi.getRaster();
    }

    @Override
    public boolean canReadRaster() {
        return true;
    }

    @Override
    public void reset() {
        super.reset();
        iis = null;
    }

    /**
     * Wrapper for the protected method <code>processImageUpdate</code> So it can be access from the classes which are
     * not in <code>ImageReader</code> hierachy.
     */
    public void processImageUpdateWrapper(BufferedImage theImage, int minX, int minY, int width, int height,
        int periodX, int periodY, int[] bands) {
        processImageUpdate(theImage, minX, minY, width, height, periodX, periodY, bands);
    }

    /**
     * Wrapper for the protected method <code>processImageProgress</code> So it can be access from the classes which are
     * not in <code>ImageReader</code> hierachy.
     */
    public void processImageProgressWrapper(float percentageDone) {
        processImageProgress(percentageDone);
    }

    /**
     * This method wraps the protected method <code>abortRequested</code> to allow the abortions be monitored by
     * <code>J2KReadState</code>.
     */
    public boolean getAbortRequest() {
        return abortRequested();
    }
}
