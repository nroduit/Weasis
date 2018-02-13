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

import java.awt.image.IndexColorModel;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.nio.ByteOrder;

import javax.imageio.IIOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageWriteParam;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.stream.ImageOutputStream;

import org.weasis.image.jni.ImageParameters;
import org.weasis.image.jni.NativeImageWriter;
import org.weasis.jpeg.internal.CharlsCodec;

import com.sun.media.imageioimpl.common.ImageUtil;
import com.sun.media.imageioimpl.plugins.clib.OutputStreamAdapter;

 class NativeJLSImageWriter extends NativeImageWriter {

    NativeJLSImageWriter(ImageWriterSpi originatingProvider) throws IOException {
        super(originatingProvider);
    }

    @Override
    public ImageWriteParam getDefaultWriteParam() {
        return new CLibJPEGImageWriteParam(getLocale());
    }

    @Override
    public void write(IIOMetadata streamMetadata, IIOImage image, ImageWriteParam param) throws IOException {
        if (output == null) {
            throw new IllegalStateException("input cannot be null");
        }

        OutputStreamAdapter stream;
        if (output instanceof ImageOutputStream) {
            stream = new OutputStreamAdapter((ImageOutputStream) output);
        } else {
            throw new IllegalArgumentException("input is not an ImageInputStream!");
        }

        RenderedImage renderedImage = image.getRenderedImage();

        // Throws exception if the renderedImage cannot be encoded.
        ImageUtil.canEncodeImage(this, renderedImage.getColorModel(), renderedImage.getSampleModel());

        if (renderedImage.getColorModel() instanceof IndexColorModel) {
            renderedImage = convertTo3BandRGB(renderedImage);
        }

        try {
            NativeJPEGImage nImage = new NativeJPEGImage();

            int[] supportedFormats = new int[] { ImageParameters.CM_GRAY, ImageParameters.CM_S_RGB };
            formatInputDataBuffer(nImage, renderedImage, param, false, supportedFormats);

            JpegParameters params = nImage.getJpegParameters();
            params.setBigEndian((stream.getStream()).getByteOrder() == ByteOrder.BIG_ENDIAN);

            CharlsCodec encoder = getCodec();
            String error = encoder.compress(nImage, stream.getStream(), param);
            if (error != null) {
                throw new IIOException("Native JPEG-LS encoding error: " + error);
            }
            encoder.dispose();

        } catch (Throwable t) {
            throw new IIOException("Native JPEG-LS encoding error", t);
        }

    }

    @Override
    protected CharlsCodec getCodec() {
        return new CharlsCodec();
    }

}
