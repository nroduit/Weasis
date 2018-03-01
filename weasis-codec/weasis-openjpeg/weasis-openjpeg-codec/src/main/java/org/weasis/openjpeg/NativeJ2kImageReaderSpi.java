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
import java.util.List;
import java.util.Locale;

import javax.imageio.IIOException;
import javax.imageio.ImageReader;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.spi.ServiceRegistry;
import javax.imageio.stream.ImageInputStream;

import com.sun.media.imageioimpl.common.ImageUtil;

public class NativeJ2kImageReaderSpi extends ImageReaderSpi {

    static final String[] SUFFIXES = { "jp2", "jp2k", "j2k", "j2c" };
    static final String[] NAMES = { "JP2KSimpleBox", "jpeg2000", "jpeg 2000", "JPEG 2000", "JPEG2000" };
    static final String[] MIMES = { "image/jp2", "image/jp2k", "image/j2k", "image/j2c" };

    private boolean registered = false;

    public NativeJ2kImageReaderSpi() {
        super("Weasis Team", "1.0", NAMES, SUFFIXES, MIMES, NativeJ2kImageReader.class.getName(),
            new Class[] { ImageInputStream.class }, new String[] { NativeJ2kImageWriterSpi.class.getName() }, false, // supportsStandardStreamMetadataFormat
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

        List list = ImageUtil.getJDKImageReaderWriterSPI(registry, "JPEG 2000", false);

        for (int i = 0; i < list.size(); i++) {
            // Set this codec to higher priority
            registry.setOrdering(category, this, list.get(i));
        }
    }

    @Override
    public String getDescription(Locale locale) {
        return "Natively-accelerated JPEG2000 Image Reader (OpenJPEG based)";
    }

    @Override
    public boolean canDecodeInput(Object source) throws IOException {
        if (!(source instanceof ImageInputStream)) {
            return false;
        }
        ImageInputStream iis = (ImageInputStream) source;
        iis.mark();
        try {
            int marker = (iis.read() << 8) | iis.read();

            if (marker == 0xFF4F) {
                return true;
            }

            iis.reset();
            iis.mark();
            byte[] b = new byte[12];
            iis.readFully(b);

            // Verify the signature box
            // The length of the signature box is 12
            if (b[0] != 0 || b[1] != 0 || b[2] != 0 || b[3] != 12) {
                return false;
            }

            // The signature box type is "jP "
            if ((b[4] & 0xff) != 0x6A || (b[5] & 0xFF) != 0x50 || (b[6] & 0xFF) != 0x20 || (b[7] & 0xFF) != 0x20) {
                return false;
            }

            // The signature content is 0x0D0A870A
            if ((b[8] & 0xFF) != 0x0D || (b[9] & 0xFF) != 0x0A || (b[10] & 0xFF) != 0x87 || (b[11] & 0xFF) != 0x0A) {
                return false;
            }

            return true;
        } finally {
            iis.reset();
        }
    }

    @Override
    public ImageReader createReaderInstance(Object extension) throws IIOException {
        return new NativeJ2kImageReader(this);
    }
}
