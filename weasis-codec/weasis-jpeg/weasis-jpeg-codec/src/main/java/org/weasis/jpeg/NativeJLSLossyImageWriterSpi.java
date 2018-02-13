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
import java.util.Locale;

import javax.imageio.ImageWriter;

/**
 */
public class NativeJLSLossyImageWriterSpi extends NativeJLSImageWriterSpi {

    public NativeJLSLossyImageWriterSpi() {
        super(NativeJLSLossyImageWriter.class);
    }

    @Override
    public String getDescription(Locale locale) {
        return "Natively-accelerated JPEG-LS near-lossless Image Writer (CharLS based)";
    }

    @Override
    public ImageWriter createWriterInstance(Object extension) throws IOException {
        return new NativeJLSLossyImageWriter(this);
    }
}
