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

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import javax.imageio.IIOException;
import javax.imageio.ImageReader;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.spi.ServiceRegistry;
import javax.imageio.stream.ImageInputStream;

import com.sun.media.imageioimpl.common.ImageUtil;

public class NativeJLSImageReaderSpi extends ImageReaderSpi {

    static final String[] NAMES = { "jpeg-ls", "JPEG-LS" };
    static final String[] SUFFIXES = { "jls" };
    static final String[] MIMES = { "image/jpeg-ls" };

    private boolean registered = false;

    public NativeJLSImageReaderSpi() {
        super("Weasis Team", "1.0", NAMES, SUFFIXES, MIMES, NativeJLSImageReader.class.getName(),
            new Class[] { ImageInputStream.class }, new String[] { NativeJLSImageWriterSpi.class.getName() }, false, // supportsStandardStreamMetadataFormat
            null, // nativeStreamMetadataFormatName
            null, // nativeStreamMetadataFormatClassName
            null, // extraStreamMetadataFormatNames
            null, // extraStreamMetadataFormatClassNames
            false, // supportsStandardImageMetadataFormat
            null, null, null, null);
    }

    @Override
    public void onRegistration(ServiceRegistry registry, Class category) {
        if (registered) {
            return;
        }
        registered = true;

        List list = ImageUtil.getJDKImageReaderWriterSPI(registry, "JPEG-LS", true);

        for (int i = 0; i < list.size(); i++) {
            // Set this codec to higher priority
            registry.setOrdering(category, this, list.get(i));
        }
    }

    @Override
    public String getDescription(Locale locale) {
        return "Natively-accelerated JPEG-LS Image Reader (CharLS based)";
    }

    @Override
    public boolean canDecodeInput(Object source) throws IOException {
        if (!(source instanceof ImageInputStream)) {
            return false;
        }
        ImageInputStream iis = (ImageInputStream) source;
        iis.mark();
        int byte1 = iis.read();
        int byte2 = iis.read();
        int byte3 = iis.read();
        int byte4 = iis.read();
        iis.reset();
        // Magic numbers for JPEG (general jpeg marker): 0xFFD8
        // Start of Frame, also known as SOF55, indicates a JPEG-LS file
        if ((byte1 == 0xFF) && (byte2 == 0xD8) && (byte3 == 0xFF) && (byte4 == 0xF7)) {
            return true;
        }
        return false;
    }

    @Override
    public ImageReader createReaderInstance(Object extension) throws IIOException {
        return new NativeJLSImageReader(this);
    }
}
