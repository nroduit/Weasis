/*
 * $RCSfile: RawImageReader.java,v $
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
 * $Revision: 1.1 $
 * $Date: 2005-02-11 05:01:42 $
 * $State: Exp $
 */
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
     * Wrapper for the protected method <code>computeRegions</code>. So it can be access from the classes which are not
     * in <code>ImageReader</code> hierachy.
     */
    public static void computeRegionsWrapper(ImageReadParam param, int srcWidth, int srcHeight, BufferedImage image,
        Rectangle srcRegion, Rectangle destRegion) {
        computeRegions(param, srcWidth, srcHeight, image, srcRegion, destRegion);
    }

    /**
     * Constructs <code>RawImageReader</code> from the provided <code>ImageReaderSpi</code>.
     */
    public RawImageReader(ImageReaderSpi originator) {
        super(originator);
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
                Raster.createWritableRaster(
                    sampleModel.createCompatibleSampleModel(image.getMinX() + image.getWidth(),
                        image.getMinY() + image.getHeight()), new Point(0, 0));

            bi =
                new BufferedImage(colorModel, raster, colorModel != null ? colorModel.isAlphaPremultiplied() : false,
                    new Hashtable());
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
        return bi.getData();
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
