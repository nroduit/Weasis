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
import java.util.Locale;

import javax.imageio.IIOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageWriteParam;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.stream.ImageOutputStream;

import org.weasis.image.jni.ImageParameters;
import org.weasis.image.jni.NativeImageWriter;
import org.weasis.jpeg.internal.JpegCodec;

import com.sun.media.imageioimpl.common.ImageUtil;
import com.sun.media.imageioimpl.plugins.clib.OutputStreamAdapter;

final class NativeJPEGImageWriter extends NativeImageWriter {

    NativeJPEGImageWriter(ImageWriterSpi originatingProvider) throws IOException {
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

        int bitDepth = renderedImage.getColorModel().getComponentSize(0);
        if ((param == null
            || (param.getCompressionMode() == ImageWriteParam.MODE_EXPLICIT && !param.isCompressionLossless()))
            && bitDepth > 12) {
            throw new IIOException("JPEG baseline encoding is limited to 12 bits: " + this);
        }

        try {
            NativeJPEGImage nImage = new NativeJPEGImage();

            int[] supportedFormats = new int[] { ImageParameters.CM_GRAY, ImageParameters.CM_S_RGB,
                ImageParameters.CM_S_YCC, ImageParameters.CM_YCCK, ImageParameters.CM_CMYK };
            formatInputDataBuffer(nImage, renderedImage, param, false, supportedFormats);

            JpegCodec encoder = getCodec();
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
    protected JpegCodec getCodec() {
        return new JpegCodec();
    }

}

/**
 * This differs from the core JPEG ImageWriteParam in that:
 *
 * <ul>
 * <li>compression types are: "JPEG" (standard), "JPEG-LOSSLESS"
 * (lossless JPEG from 10918-1/ITU-T81), "JPEG-LS" (ISO 14495-1 lossless).</li>
 * <li>compression modes are: MODE_DEFAULT and MODE_EXPLICIT and the other modes (MODE_DISABLED and
 * MODE_COPY_FROM_METADATA) cause an UnsupportedOperationException.</li>
 * <li>isCompressionLossless() will return true if type is NOT "JPEG".</li>
 * </ul>
 */
final class CLibJPEGImageWriteParam extends ImageWriteParam {
    private static final float DEFAULT_COMPRESSION_QUALITY = 0.75F;

    static final String LOSSY_COMPRESSION_TYPE = "JPEG";
    static final String LOSSLESS_COMPRESSION_TYPE = "JPEG-LOSSLESS";
    static final String LS_COMPRESSION_TYPE = "JPEG-LS";

    private static final String[] compressionQualityDescriptions =
        new String[] { "Minimum useful", "Visually lossless", "Maximum useful" };

    CLibJPEGImageWriteParam(Locale locale) {
        super(locale);

        canWriteCompressed = true;
        compressionMode = MODE_EXPLICIT;
        compressionQuality = DEFAULT_COMPRESSION_QUALITY;
        compressionType = LOSSY_COMPRESSION_TYPE;
        compressionTypes = new String[] { LOSSY_COMPRESSION_TYPE, LOSSLESS_COMPRESSION_TYPE, LS_COMPRESSION_TYPE };
    }

    @Override
    public String[] getCompressionQualityDescriptions() {
        super.getCompressionQualityDescriptions();
        return compressionQualityDescriptions;
    }

    @Override
    public float[] getCompressionQualityValues() {
        super.getCompressionQualityValues();

        return new float[] { 0.05F, // "Minimum useful"
            0.75F, // "Visually lossless"
            0.95F }; // "Maximum useful"
    }

    @Override
    public boolean isCompressionLossless() {
        super.isCompressionLossless();

        return !compressionType.equalsIgnoreCase(LOSSY_COMPRESSION_TYPE);
    }

    @Override
    public void setCompressionMode(int mode) {
        if (mode == MODE_DISABLED || mode == MODE_COPY_FROM_METADATA) {
            throw new UnsupportedOperationException("mode == MODE_DISABLED || mode == MODE_COPY_FROM_METADATA");
        }

        super.setCompressionMode(mode);
    }

    @Override
    public void unsetCompression() {
        super.unsetCompression();

        compressionQuality = DEFAULT_COMPRESSION_QUALITY;
        compressionType = LOSSY_COMPRESSION_TYPE;
    }
}
