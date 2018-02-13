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
package org.weasis.openjpeg.internal;

import javax.imageio.spi.ImageReaderSpi;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.weasis.openjpeg.NativeJ2kImageReaderSpi;

import com.sun.media.imageioimpl.common.ImageioUtil;

public class Activator implements BundleActivator {

    @Override
    public void start(final BundleContext bundleContext) throws Exception {
        // Give the priority to this j2k codecs
        ImageioUtil.registerServiceProviderInHighestPriority(NativeJ2kImageReaderSpi.class, ImageReaderSpi.class, "jpeg2000");
        // ImageioUtil.registerServiceProvider(NativeJ2kImageWriterSpi.class);
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception {
        ImageioUtil.unRegisterServiceProvider(NativeJ2kImageReaderSpi.class);
        // ImageioUtil.unRegisterServiceProvider(NativeJ2kImageWriterSpi.class);
    }

}
