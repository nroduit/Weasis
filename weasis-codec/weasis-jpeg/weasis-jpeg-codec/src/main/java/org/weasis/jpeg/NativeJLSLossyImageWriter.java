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

import java.awt.image.RenderedImage;
import java.io.IOException;

import javax.imageio.ImageWriteParam;
import javax.imageio.spi.ImageWriterSpi;

import org.weasis.image.jni.NativeImage;
 
final class NativeJLSLossyImageWriter extends NativeJLSImageWriter {

    NativeJLSLossyImageWriter(ImageWriterSpi originatingProvider) throws IOException {
        super(originatingProvider);
    }

    @Override
    protected void formatInputDataBuffer(NativeImage nImage, RenderedImage image, ImageWriteParam param,
        boolean allowBilevel, int[] supportedFormats) {
        super.formatInputDataBuffer(nImage, image, param, allowBilevel, supportedFormats);
        if (nImage instanceof NativeJPEGImage) {
            ((NativeJPEGImage) nImage).getJpegParameters().setAllowedLossyError(2);
        }
    }
}
