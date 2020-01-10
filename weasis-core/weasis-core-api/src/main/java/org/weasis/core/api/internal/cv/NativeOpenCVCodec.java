/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.api.internal.cv;

import java.net.URI;
import java.util.Hashtable;

import org.opencv.osgi.OpenCVNativeLoader;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.image.cv.ImageCVIO;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.MediaReader;

@org.osgi.service.component.annotations.Component(service = Codec.class, immediate = false)
public class NativeOpenCVCodec implements Codec {
    private static final Logger LOGGER = LoggerFactory.getLogger(NativeOpenCVCodec.class);

    private static final String[] readerMIMETypes = { "image/bmp", "image/x-bmp", "image/x-windows-bmp", "image/jpeg", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        "image/pjpeg", "image/png", "image/x-portable-bitmap", "image/x-portable-graymap", "image/x-portable-greymap", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
        "image/x-portable-pixmap", "image/x-portable-anymap", "application/x-portable-anymap", "image/cmu-raster", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        "application/x-cmu-raster", "image/x-cmu-raster", "image/tiff", "image/x-tiff", "image/hdr", "image/jp2", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
        "image/jp2k", "image/j2k", "image/j2c" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    private static final String[] readerFileSuffixes = { "bm", "bmp", "dib", "jpeg", "jpg", "jpe", "png", "x-png", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$
        "pbm", "pgm", "ppm", "pxm", "pnm", "ras", "rast", "tiff", "tif", "hdr", "jp2", "jp2k", "j2k", "j2c" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$ //$NON-NLS-11$ //$NON-NLS-12$ //$NON-NLS-13$ //$NON-NLS-14$

    private static final String[] writerMIMETypes = {};
    private static final String[] writerFileSuffixes = {};

    @Override
    public String[] getReaderMIMETypes() {
        return readerMIMETypes;
    }

    @Override
    public String[] getReaderExtensions() {
        return readerFileSuffixes;
    }

    @Override
    public boolean isMimeTypeSupported(String mimeType) {
        if (mimeType != null) {
            for (String mime : getReaderMIMETypes()) {
                if (mimeType.equals(mime)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public MediaReader getMediaIO(URI media, String mimeType, Hashtable<String, Object> properties) {
        if (isMimeTypeSupported(mimeType)) {
            return new ImageCVIO(media, mimeType, this);
        }
        return null;
    }

    @Override
    public String getCodecName() {
        return "OpenCV imgcodecs"; //$NON-NLS-1$
    }

    @Override
    public String[] getWriterExtensions() {
        return writerFileSuffixes;
    }

    @Override
    public String[] getWriterMIMETypes() {
        return writerMIMETypes;
    }

    // ================================================================================
    // OSGI service implementation
    // ================================================================================

    @Activate
    protected void activate(ComponentContext context) {
        // Load the native OpenCV library
        OpenCVNativeLoader loader = new OpenCVNativeLoader();
        loader.init();
        LOGGER.info("Native OpenCV is activated"); //$NON-NLS-1$
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {

    }

}
