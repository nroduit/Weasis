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
package org.weasis.jpeg.internal;

import javax.imageio.spi.ImageReaderSpi;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.weasis.jpeg.NativeJLSImageReaderSpi;
import org.weasis.jpeg.NativeJLSImageWriterSpi;
import org.weasis.jpeg.NativeJPEGImageReaderSpi;

import com.sun.media.imageioimpl.common.ImageioUtil;

public class Activator implements BundleActivator {

    @Override
    public void start(final BundleContext bundleContext) throws Exception {
        // Must be used in the lowest priority (problem with tiles). In DICOM used explicitly.
        ImageioUtil.registerServiceProviderInLowestPriority(NativeJPEGImageReaderSpi.class, ImageReaderSpi.class, "jpeg");
        ImageioUtil.registerServiceProvider(NativeJLSImageReaderSpi.class);
        ImageioUtil.registerServiceProvider(NativeJLSImageWriterSpi.class);

    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception {
        ImageioUtil.unRegisterServiceProvider(NativeJPEGImageReaderSpi.class);
        ImageioUtil.unRegisterServiceProvider(NativeJLSImageReaderSpi.class);
        ImageioUtil.unRegisterServiceProvider(NativeJLSImageWriterSpi.class);
        // JpegCodec.destroy();
    }

}
