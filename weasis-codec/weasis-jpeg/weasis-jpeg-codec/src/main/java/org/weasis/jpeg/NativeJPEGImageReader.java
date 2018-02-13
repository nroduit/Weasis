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
package org.weasis.jpeg;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.IIOException;
import javax.imageio.ImageReadParam;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.image.jni.InputStreamAdapter;
import org.weasis.image.jni.NativeCodec;
import org.weasis.image.jni.NativeImage;
import org.weasis.image.jni.NativeImageReader;
import org.weasis.image.jni.StreamSegment;
import org.weasis.jpeg.internal.JpegCodec;

import com.sun.media.imageioimpl.common.ExtendImageParam;
import com.sun.media.imageioimpl.plugins.jpeg.CLibJPEGMetadata;

class NativeJPEGImageReader extends NativeImageReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(NativeJPEGImageReader.class);

    private IIOMetadata imageMetadata = null;
    private int imageMetadataIndex = -1;

    NativeJPEGImageReader(ImageReaderSpi originatingProvider) {
        super(originatingProvider);
    }

    @Override
    protected NativeCodec getCodec() {
        return new JpegCodec();
    }

    @Override
    protected final synchronized NativeImage nativeDecode(InputStream stream, ImageReadParam param, int imageIndex)
        throws IOException {

        ImageInputStream iis = null;
        if (stream instanceof InputStreamAdapter) {
            iis = ((InputStreamAdapter) stream).getWrappedStream();
        }

        NativeImage mlImage = nativeImages.get(imageIndex);
        try {
            NativeCodec decoder = getCodec();

            if (mlImage == null) {
                mlImage = decoder.buildImage(iis);
            }
            if (param instanceof ExtendImageParam) {
                Boolean signed = ((ExtendImageParam) param).getSignedData();
                if (signed != null) {
                    mlImage.getImageParameters().setSignedData(signed);
                }
            }

            StreamSegment.adaptParametersFromStream(iis, mlImage, param);

            long start = System.currentTimeMillis();
            String error = decoder.decompress(mlImage, param);
            // TODO Get the ICC profile data.
            if (error != null) {
                throw new IIOException("Native JPEG codec error: " + error);
            }

            long stop = System.currentTimeMillis();
            LOGGER.debug("Reading image time (native codec): {} ms", (stop - start)); //$NON-NLS-1$

            // Free native resources.
            decoder.dispose();

        } catch (Throwable t) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.error("Native JPEG codec error", t); //$NON-NLS-1$
            }
            throw new IIOException("Native JPEG codec error", t);
        }

        LOGGER.debug("Parameters => {}", mlImage.getImageParameters().toString());
        return mlImage;
    }

    @Override
    protected boolean skipImage(int index) throws IOException {
        boolean retval = false;

        if (input == null) {
            throw new IllegalStateException("input cannot be null");
        }
        InputStream stream = null;
        if (input instanceof ImageInputStream) {
            stream = new InputStreamAdapter((ImageInputStream) input);
        } else {
            throw new IllegalArgumentException("input is not an ImageInputStream!");
        }

        retval = nativeDecode(stream, null, index) != null;

        if (retval) {
            long pos = ((ImageInputStream) input).getStreamPosition();
            if (pos > highMark) {
                highMark = pos;
            }
        }

        return retval;
    }

    @Override
    public synchronized IIOMetadata getImageMetadata(int imageIndex) throws IOException {
        if (input == null) {
            throw new IllegalStateException("input cannot be null");
        }

        if (imageMetadata == null || imageIndex != imageMetadataIndex) {
            seekToImage(imageIndex);

            ImageInputStream stream = (ImageInputStream) input;
            long pos = stream.getStreamPosition();

            try {
                imageMetadata = new CLibJPEGMetadata(stream);
                imageMetadataIndex = imageIndex;
            } catch (IIOException e) {
                throw e;
            } finally {
                stream.seek(pos);
            }
        }

        return imageMetadata;
    }

    @Override
    public boolean readerSupportsThumbnails() {
        return true;
    }

    @Override
    public int getNumThumbnails(int imageIndex) throws IOException {
        CLibJPEGMetadata metadata = (CLibJPEGMetadata) getImageMetadata(imageIndex);
        return metadata.getNumThumbnails();
    }

    @Override
    public BufferedImage readThumbnail(int imageIndex, int thumbnailIndex) throws IOException {
        CLibJPEGMetadata metadata = (CLibJPEGMetadata) getImageMetadata(imageIndex);
        return metadata.getThumbnail(thumbnailIndex);
    }

    @Override
    protected void resetLocal() {
        imageMetadata = null;
        imageMetadataIndex = -1;
        super.resetLocal();
    }

}
