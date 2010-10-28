/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.imageio.codec;

import java.net.URI;
import java.util.Hashtable;

import javax.imageio.ImageIO;

import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.MediaReader;

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
            for (String mime : ImageIO.getReaderMIMETypes()) {
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
            return new ImageElementIO(media, mimeType, this);
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

}
