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

import java.awt.image.IndexColorModel;
import java.awt.image.RenderedImage;
import java.io.IOException;

import javax.imageio.IIOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.stream.ImageOutputStream;

import org.weasis.image.jni.ImageParameters;
import org.weasis.image.jni.NativeImageWriter;
import org.weasis.openjpeg.internal.OpenJpegCodec;

import com.sun.media.imageio.plugins.jpeg2000.J2KImageWriteParam;
import com.sun.media.imageioimpl.common.ImageUtil;
import com.sun.media.imageioimpl.plugins.clib.OutputStreamAdapter;
import com.sun.media.imageioimpl.plugins.jpeg2000.J2KMetadata;

final class NativeJ2kImageWriter extends NativeImageWriter {

    NativeJ2kImageWriter(ImageWriterSpi originatingProvider) throws IOException {
        super(originatingProvider);
    }

    @Override
    public ImageWriteParam getDefaultWriteParam() {
        return new J2KImageWriteParam();
    }

    @Override
    public IIOMetadata getDefaultImageMetadata(ImageTypeSpecifier imageType, ImageWriteParam param) {
        return new J2KMetadata(imageType, param, this);
    }

    @Override
    public void write(IIOMetadata streamMetadata, IIOImage image, ImageWriteParam param) throws IOException {
        if (output == null) {
            throw new IllegalStateException("output == null");
        }

        OutputStreamAdapter stream;
        if (output instanceof ImageOutputStream) {
            stream = new OutputStreamAdapter((ImageOutputStream) output);
        } else {
            throw new IllegalArgumentException("!(output instanceof ImageOutputStream)");
        }

        RenderedImage renderedImage = image.getRenderedImage();

        // Throws exception if the renderedImage cannot be encoded.
        ImageUtil.canEncodeImage(this, renderedImage.getColorModel(), renderedImage.getSampleModel());

        if (renderedImage.getColorModel() instanceof IndexColorModel) {
            renderedImage = convertTo3BandRGB(renderedImage);
        }

        try {
            NativeJ2kImage nImage = new NativeJ2kImage();

            int[] supportedFormats = new int[] { ImageParameters.CM_GRAY, ImageParameters.CM_S_RGB,
                ImageParameters.CM_S_YCC, ImageParameters.CM_E_YCC, ImageParameters.CM_CMYK };
            formatInputDataBuffer(nImage, renderedImage, param, false, supportedFormats);

            OpenJpegCodec encoder = getCodec();
            String error = encoder.compress(nImage, stream.getStream(), param);
            if (error != null) {
                throw new IIOException("Native JPEG encoding error: " + error);
            }
            encoder.dispose();
        } catch (Exception e) {
            throw new IIOException("Native JPEG encoding error", e);
        }
    }

    @Override
    protected OpenJpegCodec getCodec() {
        return new OpenJpegCodec();
    }

}
