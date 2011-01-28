/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.imageio.codec.internal;

import javax.imageio.spi.IIORegistry;
import javax.media.jai.JAI;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import com.sun.media.jai.imageioimpl.ImageReadWriteSpi;

public class Activator implements BundleActivator {

    public void start(final BundleContext bundleContext) throws Exception {
        // SPI Issue Resolution
        // Register imageio SPI with the classloader of this bundle
        // and unregister imageio SPI if imageio.jar is also in the jre/lib/ext folder
        IIORegistry registry = IIORegistry.getDefaultInstance();
        registerServiceProvider(registry, com.sun.media.imageioimpl.stream.ChannelImageInputStreamSpi.class);
        registerServiceProvider(registry, com.sun.media.imageioimpl.stream.ChannelImageOutputStreamSpi.class);

        registerServiceProvider(registry, com.sun.media.imageioimpl.plugins.jpeg.CLibJPEGImageReaderSpi.class);
        registerServiceProvider(registry, com.sun.media.imageioimpl.plugins.png.CLibPNGImageReaderSpi.class);
        registerServiceProvider(registry, com.sun.media.imageioimpl.plugins.jpeg2000.J2KImageReaderSpi.class);
        registerServiceProvider(registry, com.sun.media.imageioimpl.plugins.jpeg2000.J2KImageReaderCodecLibSpi.class);
        registerServiceProvider(registry, com.sun.media.imageioimpl.plugins.wbmp.WBMPImageReaderSpi.class);
        registerServiceProvider(registry, com.sun.media.imageioimpl.plugins.bmp.BMPImageReaderSpi.class);
        registerServiceProvider(registry, com.sun.media.imageioimpl.plugins.raw.RawImageReaderSpi.class);
        registerServiceProvider(registry, com.sun.media.imageioimpl.plugins.tiff.TIFFImageReaderSpi.class);

        registerServiceProvider(registry, com.sun.media.imageioimpl.plugins.jpeg.CLibJPEGImageWriterSpi.class);
        registerServiceProvider(registry, com.sun.media.imageioimpl.plugins.png.CLibPNGImageWriterSpi.class);
        registerServiceProvider(registry, com.sun.media.imageioimpl.plugins.jpeg2000.J2KImageWriterSpi.class);
        registerServiceProvider(registry, com.sun.media.imageioimpl.plugins.jpeg2000.J2KImageWriterCodecLibSpi.class);
        registerServiceProvider(registry, com.sun.media.imageioimpl.plugins.wbmp.WBMPImageWriterSpi.class);
        registerServiceProvider(registry, com.sun.media.imageioimpl.plugins.bmp.BMPImageWriterSpi.class);
        registerServiceProvider(registry, com.sun.media.imageioimpl.plugins.gif.GIFImageWriterSpi.class);
        registerServiceProvider(registry, com.sun.media.imageioimpl.plugins.pnm.PNMImageWriterSpi.class);
        registerServiceProvider(registry, com.sun.media.imageioimpl.plugins.raw.RawImageWriterSpi.class);
        registerServiceProvider(registry, com.sun.media.imageioimpl.plugins.tiff.TIFFImageWriterSpi.class);

        // Register the ImageRead and ImageWrite operation for JAI
        new ImageReadWriteSpi().updateRegistry(getJAI().getOperationRegistry());
    }

    public void stop(BundleContext bundleContext) throws Exception {
        IIORegistry registry = IIORegistry.getDefaultInstance();
        unRegisterServiceProvider(registry, com.sun.media.imageioimpl.stream.ChannelImageInputStreamSpi.class);
        unRegisterServiceProvider(registry, com.sun.media.imageioimpl.stream.ChannelImageOutputStreamSpi.class);

        unRegisterServiceProvider(registry, com.sun.media.imageioimpl.plugins.jpeg.CLibJPEGImageReaderSpi.class);
        unRegisterServiceProvider(registry, com.sun.media.imageioimpl.plugins.png.CLibPNGImageReaderSpi.class);
        unRegisterServiceProvider(registry, com.sun.media.imageioimpl.plugins.jpeg2000.J2KImageReaderSpi.class);
        unRegisterServiceProvider(registry, com.sun.media.imageioimpl.plugins.jpeg2000.J2KImageReaderCodecLibSpi.class);
        unRegisterServiceProvider(registry, com.sun.media.imageioimpl.plugins.wbmp.WBMPImageReaderSpi.class);
        unRegisterServiceProvider(registry, com.sun.media.imageioimpl.plugins.bmp.BMPImageReaderSpi.class);
        unRegisterServiceProvider(registry, com.sun.media.imageioimpl.plugins.raw.RawImageReaderSpi.class);
        unRegisterServiceProvider(registry, com.sun.media.imageioimpl.plugins.tiff.TIFFImageReaderSpi.class);

        unRegisterServiceProvider(registry, com.sun.media.imageioimpl.plugins.jpeg.CLibJPEGImageWriterSpi.class);
        unRegisterServiceProvider(registry, com.sun.media.imageioimpl.plugins.png.CLibPNGImageWriterSpi.class);
        unRegisterServiceProvider(registry, com.sun.media.imageioimpl.plugins.jpeg2000.J2KImageWriterSpi.class);
        unRegisterServiceProvider(registry, com.sun.media.imageioimpl.plugins.jpeg2000.J2KImageWriterCodecLibSpi.class);
        unRegisterServiceProvider(registry, com.sun.media.imageioimpl.plugins.wbmp.WBMPImageWriterSpi.class);
        unRegisterServiceProvider(registry, com.sun.media.imageioimpl.plugins.bmp.BMPImageWriterSpi.class);
        unRegisterServiceProvider(registry, com.sun.media.imageioimpl.plugins.gif.GIFImageWriterSpi.class);
        unRegisterServiceProvider(registry, com.sun.media.imageioimpl.plugins.pnm.PNMImageWriterSpi.class);
        unRegisterServiceProvider(registry, com.sun.media.imageioimpl.plugins.raw.RawImageWriterSpi.class);
        unRegisterServiceProvider(registry, com.sun.media.imageioimpl.plugins.tiff.TIFFImageWriterSpi.class);

    }

    public static JAI getJAI() {
        // Issue Resolution: necessary when jai already exist in JRE
        // Change to the bundle classloader for loading the services providers (spi) correctly.
        ClassLoader bundleClassLoader = JAI.class.getClassLoader();
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(bundleClassLoader);
        JAI jai = JAI.getDefaultInstance();
        Thread.currentThread().setContextClassLoader(originalClassLoader);
        return jai;
    }

    private static void registerServiceProvider(IIORegistry registry, Class clazz) {
        Class spiClass = null;
        try {
            // If JRE contains imageio.jar in lib/ext, get spi classes and unregister them
            spiClass = Class.forName(clazz.getName(), true, ClassLoader.getSystemClassLoader());
        } catch (ClassNotFoundException e) {
        }
        if (spiClass != null) {
            Object spi = registry.getServiceProviderByClass(spiClass);
            if (spi != null) {
                registry.deregisterServiceProvider(spi);
            }
        }
        try {
            // Resister again the spi classes with the bundle classloader
            registry.registerServiceProvider(clazz.newInstance());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void unRegisterServiceProvider(IIORegistry registry, Class clazz) {
        Object spi = registry.getServiceProviderByClass(clazz);
        if (spi != null) {
            registry.deregisterServiceProvider(spi);
        }
    }
}
