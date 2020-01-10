/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.imageio.codec;

import java.net.URI;
import java.util.Hashtable;

import javax.imageio.ImageIO;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Deactivate;
import org.weasis.core.api.image.cv.ImageCVIO;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.MediaReader;

import com.sun.media.imageioimpl.common.ImageioUtil;
import com.sun.media.imageioimpl.stream.ChannelImageInputStreamSpi;
import com.sun.media.imageioimpl.stream.ChannelImageOutputStreamSpi;

@org.osgi.service.component.annotations.Component(service = Codec.class, immediate = false)
public class ImageioCodec implements Codec {

    @Override
    public String[] getReaderMIMETypes() {
        return ImageIO.getReaderMIMETypes();
    }

    @Override
    public String[] getReaderExtensions() {
        return ImageIO.getReaderFileSuffixes();
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
        return "Sun java imageio"; //$NON-NLS-1$
    }

    @Override
    public String[] getWriterExtensions() {
        return ImageIO.getWriterFileSuffixes();
    }

    @Override
    public String[] getWriterMIMETypes() {
        return ImageIO.getWriterMIMETypes();
    }

    // ================================================================================
    // OSGI service implementation
    // ================================================================================

    @Activate
    protected void activate(ComponentContext context) {
        // Do not use cache. Images must be download locally before reading them.
        ImageIO.setUseCache(false);

        // SPI Issue Resolution
        // Register imageio SPI with the classloader of this bundle
        // and unregister imageio SPI if imageio.jar is also in the jre/lib/ext folder

        Class<?>[] jaiCodecs = { ChannelImageInputStreamSpi.class, ChannelImageOutputStreamSpi.class };

        for (Class<?> c : jaiCodecs) {
            ImageioUtil.registerServiceProvider(c);
        }
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {
        Class<?>[] jaiCodecs = { ChannelImageInputStreamSpi.class, ChannelImageOutputStreamSpi.class };

        for (Class<?> c : jaiCodecs) {
            ImageioUtil.unRegisterServiceProvider(c);
        }
    }

}
