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
package org.opencv.internal;

import java.net.URI;
import java.util.Hashtable;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Deactivate;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.MediaReader;

@org.osgi.service.component.annotations.Component(service = Codec.class, immediate = false)
public class NativeOpenCVCodec implements Codec {
    private static final String[] readerMIMETypes = { "image/bmp", "image/x-bmp", "image/x-windows-bmp", "image/jpeg",
        "image/pjpeg", "image/png", "image/x-portable-bitmap", "image/x-portable-graymap", "image/x-portable-greymap",
        "image/x-portable-pixmap", "image/x-portable-anymap", "application/x-portable-anymap", "image/cmu-raster",
        "application/x-cmu-raster", "image/x-cmu-raster", "image/tiff", "image/x-tiff", "image/hdr" };
    private static final String[] readerFileSuffixes = { "bm", "bmp", "dib", "jpeg", "jpg", "jpe", "png", "x-png",
        "pbm", "pgm", "ppm", "pxm", "pnm", "ras", "rast", "tiff", "tif", "hdr" };
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
        System.loadLibrary("opencv_java");
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {

    }

}
