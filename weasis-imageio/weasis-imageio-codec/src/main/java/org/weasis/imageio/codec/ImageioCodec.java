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
package org.weasis.imageio.codec;

import java.net.URI;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import javax.imageio.ImageIO;

import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.MediaReader;

@org.apache.felix.scr.annotations.Component(immediate = false)
@org.apache.felix.scr.annotations.Service
@org.apache.felix.scr.annotations.Property(name = "service.name", value = "Imageio Codec (additionnal java packages)")
public class ImageioCodec implements Codec {

    @Override
    public String[] getReaderMIMETypes() {
        List<String> list = new ArrayList<>();
        for (String s : ImageIO.getReaderMIMETypes()) {
            list.add(s);
        }
        list.add("image/x-ms-bmp"); //$NON-NLS-1$
        return list.toArray(new String[list.size()]);
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
