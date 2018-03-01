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
package org.weasis.openjpeg;

import java.io.IOException;
import java.io.InputStream;

import javax.imageio.IIOException;
import javax.imageio.ImageReadParam;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.image.jni.ImageParameters;
import org.weasis.image.jni.InputStreamAdapter;
import org.weasis.image.jni.NativeCodec;
import org.weasis.image.jni.NativeImage;
import org.weasis.image.jni.NativeImageReader;
import org.weasis.image.jni.StreamSegment;
import org.weasis.openjpeg.internal.OpenJpegCodec;

import com.sun.media.imageioimpl.common.ExtendImageParam;

class NativeJ2kImageReader extends NativeImageReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(NativeJ2kImageReader.class);

    NativeJ2kImageReader(ImageReaderSpi originatingProvider) {
        super(originatingProvider);
    }

    @Override
    protected NativeCodec getCodec() {
        return new OpenJpegCodec();
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
            if (error != null) {
                throw new IIOException("Native J2K codec error: " + error);
            }

            long stop = System.currentTimeMillis();
            LOGGER.debug("Reading image time (native J2K codec): {} ms", stop - start); //$NON-NLS-1$

            decoder.dispose();

        } catch (Exception e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.error("Native J2K codec error", e); //$NON-NLS-1$
            }
            throw new IIOException("Native J2K codec error", e);
        }

        LOGGER.debug("Parameters => {}", mlImage.getImageParameters().toString());
        return mlImage;
    }

    @Override
    protected boolean skipImage(int index) throws IOException {
        if (input == null) {
            throw new IllegalStateException("input cannot be null");
        }
        InputStream stream;
        if (input instanceof ImageInputStream) {
            stream = new InputStreamAdapter((ImageInputStream) input);
        } else {
            throw new IllegalArgumentException("input is not an ImageInputStream!");
        }
        // FIXME skip stream!
        boolean retval = nativeDecode(stream, null, index) != null;

        if (retval) {
            long pos = ((ImageInputStream) input).getStreamPosition();
            if (pos > highMark) {
                highMark = pos;
            }
        }

        return retval;
    }

    @Override
    public boolean isImageTiled(int imageIndex) throws IOException {
        int w = getWidth(imageIndex);
        int tw = getTileWidth(imageIndex);
        if (tw > 0 && ((w + tw - 1) / tw) > 1) {
            return true;
        }

        int h = getHeight(imageIndex);
        int th = getTileHeight(imageIndex);
        if (th > 0 && ((h + th - 1) / th) > 1) {
            return true;
        }

        return false;
    }

    @Override
    public int getHeight(int imageIndex) throws IOException {
        return getInfoImage(imageIndex, null).getHeight();
    }

    @Override
    public int getTileGridXOffset(int imageIndex) throws IOException {
        ImageParameters info = getInfoImage(imageIndex, null);
        return info.getTileGridXOffset();
    }

    @Override
    public int getTileGridYOffset(int imageIndex) throws IOException {
        ImageParameters info = getInfoImage(imageIndex, null);
        return info.getTileGridYOffset();
    }

    @Override
    public int getTileWidth(int imageIndex) throws IOException {
        ImageParameters info = getInfoImage(imageIndex, null);
        return info.getTileWidth();
    }

    @Override
    public int getTileHeight(int imageIndex) throws IOException {
        ImageParameters info = getInfoImage(imageIndex, null);
        return info.getTileHeight();
    }

    public boolean getAbortRequest() {
        return abortRequested();
    }

}
