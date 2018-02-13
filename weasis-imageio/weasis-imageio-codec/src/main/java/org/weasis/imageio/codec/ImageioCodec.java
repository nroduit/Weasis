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
package org.weasis.imageio.codec;

import java.net.URI;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import javax.imageio.ImageIO;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.spi.ImageWriterSpi;
import javax.media.jai.OperationDescriptorImpl;
import javax.media.jai.OperationRegistry;
import javax.media.jai.RegistryElementDescriptor;
import javax.media.jai.registry.CollectionRegistryMode;
import javax.media.jai.registry.RenderableRegistryMode;
import javax.media.jai.registry.RenderedRegistryMode;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Deactivate;
import org.weasis.core.api.image.util.JAIUtil;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.MediaReader;

import com.sun.media.imageioimpl.common.ImageioUtil;
import com.sun.media.imageioimpl.plugins.bmp.BMPImageReaderSpi;
import com.sun.media.imageioimpl.plugins.bmp.BMPImageWriterSpi;
import com.sun.media.imageioimpl.plugins.gif.GIFImageWriterSpi;
import com.sun.media.imageioimpl.plugins.jpeg.CLibJPEGImageReaderSpi;
import com.sun.media.imageioimpl.plugins.jpeg.CLibJPEGImageWriterSpi;
import com.sun.media.imageioimpl.plugins.jpeg2000.J2KImageReaderCodecLibSpi;
import com.sun.media.imageioimpl.plugins.jpeg2000.J2KImageReaderSpi;
import com.sun.media.imageioimpl.plugins.jpeg2000.J2KImageWriterCodecLibSpi;
import com.sun.media.imageioimpl.plugins.jpeg2000.J2KImageWriterSpi;
import com.sun.media.imageioimpl.plugins.png.CLibPNGImageReaderSpi;
import com.sun.media.imageioimpl.plugins.png.CLibPNGImageWriterSpi;
import com.sun.media.imageioimpl.plugins.pnm.PNMImageReaderSpi;
import com.sun.media.imageioimpl.plugins.pnm.PNMImageWriterSpi;
import com.sun.media.imageioimpl.plugins.raw.RawImageReaderSpi;
import com.sun.media.imageioimpl.plugins.raw.RawImageWriterSpi;
import com.sun.media.imageioimpl.plugins.tiff.TIFFImageReaderSpi;
import com.sun.media.imageioimpl.plugins.tiff.TIFFImageWriterSpi;
import com.sun.media.imageioimpl.plugins.wbmp.WBMPImageReaderSpi;
import com.sun.media.imageioimpl.plugins.wbmp.WBMPImageWriterSpi;
import com.sun.media.imageioimpl.stream.ChannelImageInputStreamSpi;
import com.sun.media.imageioimpl.stream.ChannelImageOutputStreamSpi;
import com.sun.media.jai.imageioimpl.ImageReadWriteSpi;
import com.sun.media.jai.operator.ImageReadDescriptor;
import com.sun.media.jai.operator.ImageWriteDescriptor;

@org.osgi.service.component.annotations.Component(service = Codec.class, immediate = false)
public class ImageioCodec implements Codec {

    @Override
    public String[] getReaderMIMETypes() {
        List<String> list = new ArrayList<>();
        for (String s : ImageIO.getReaderMIMETypes()) {
            list.add(s);
        }
        list.add("image/x-ms-bmp"); //$NON-NLS-1$
        return list.toArray(new String[list.size()]);
    }

    @Override
    public String[] getReaderExtensions() {
        return ImageIO.getReaderFileSuffixes();
    }

    @Override
    public boolean isMimeTypeSupported(String mimeType) {
        if (mimeType != null) {
            for (String mime : getReaderMIMETypes()) {
                if (mimeType.equals(mime)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public MediaReader getMediaIO(URI media, String mimeType, Hashtable<String, Object> properties) {
        if (isMimeTypeSupported(mimeType)) {
            return new ImageElementIO(media, mimeType, this);
        }
        return null;
    }

    @Override
    public String getCodecName() {
        return "Sun java imageio"; //$NON-NLS-1$
    }

    @Override
    public String[] getWriterExtensions() {
        return ImageIO.getWriterFileSuffixes();
    }

    @Override
    public String[] getWriterMIMETypes() {
        return ImageIO.getWriterMIMETypes();
    }

    // ================================================================================
    // OSGI service implementation
    // ================================================================================

    @Activate
    protected void activate(ComponentContext context) {
        // Do not use cache. Images must be download locally before reading them.
        ImageIO.setUseCache(false);

        // SPI Issue Resolution
        // Register imageio SPI with the classloader of this bundle
        // and unregister imageio SPI if imageio.jar is also in the jre/lib/ext folder

        Class[] jaiCodecs = { ChannelImageInputStreamSpi.class, ChannelImageOutputStreamSpi.class,
            J2KImageReaderSpi.class, J2KImageReaderCodecLibSpi.class, WBMPImageReaderSpi.class, BMPImageReaderSpi.class,
            PNMImageReaderSpi.class, RawImageReaderSpi.class, TIFFImageReaderSpi.class, J2KImageWriterSpi.class,
            J2KImageWriterCodecLibSpi.class, WBMPImageWriterSpi.class, BMPImageWriterSpi.class, GIFImageWriterSpi.class,
            PNMImageWriterSpi.class, RawImageWriterSpi.class, TIFFImageWriterSpi.class };

        for (Class c : jaiCodecs) {
            ImageioUtil.registerServiceProvider(c);
        }

        // Set priority to these codec which have better performance to the one in JRE
        ImageioUtil.registerServiceProviderInHighestPriority(CLibJPEGImageReaderSpi.class, ImageReaderSpi.class,
            "jpeg"); //$NON-NLS-1$
        ImageioUtil.registerServiceProviderInHighestPriority(CLibJPEGImageWriterSpi.class, ImageWriterSpi.class,
            "jpeg"); //$NON-NLS-1$
        ImageioUtil.registerServiceProviderInHighestPriority(CLibPNGImageReaderSpi.class, ImageReaderSpi.class, "png"); //$NON-NLS-1$
        ImageioUtil.registerServiceProviderInHighestPriority(CLibPNGImageWriterSpi.class, ImageWriterSpi.class, "png"); //$NON-NLS-1$

        // TODO Should be in properties?
        // Unregister sun native jpeg codec
        // ImageioUtil.unRegisterServiceProvider(registry, CLibJPEGImageReaderSpi.class);

        // Register the ImageRead and ImageWrite operation for JAI
        new ImageReadWriteSpi().updateRegistry(JAIUtil.getOperationRegistry());
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {
        Class[] jaiCodecs = { ChannelImageInputStreamSpi.class, ChannelImageOutputStreamSpi.class,
            CLibJPEGImageReaderSpi.class, CLibPNGImageReaderSpi.class, J2KImageReaderSpi.class,
            J2KImageReaderCodecLibSpi.class, WBMPImageReaderSpi.class, BMPImageReaderSpi.class, PNMImageReaderSpi.class,
            RawImageReaderSpi.class, TIFFImageReaderSpi.class, CLibJPEGImageWriterSpi.class,
            CLibPNGImageWriterSpi.class, J2KImageWriterSpi.class, J2KImageWriterCodecLibSpi.class,
            WBMPImageWriterSpi.class, BMPImageWriterSpi.class, GIFImageWriterSpi.class, PNMImageWriterSpi.class,
            RawImageWriterSpi.class, TIFFImageWriterSpi.class };

        for (Class c : jaiCodecs) {
            ImageioUtil.unRegisterServiceProvider(c);
        }

        OperationRegistry reg = JAIUtil.getOperationRegistry();
        OperationDescriptorImpl[] desc =
            new OperationDescriptorImpl[] { new ImageReadDescriptor(), new ImageWriteDescriptor() };
        for (OperationDescriptorImpl d : desc) {
            String[] supportedModes =
                { RenderedRegistryMode.MODE_NAME, RenderableRegistryMode.MODE_NAME, CollectionRegistryMode.MODE_NAME };
            for (String mode : supportedModes) {
                Iterator<?> list = reg.getFactoryIterator(mode, d.getName());
                while (list.hasNext()) {
                    Object obj = list.next();
                    reg.unregisterFactory(mode, d.getName(), "com.sun.media.jai", obj); //$NON-NLS-1$
                }
            }
            RegistryElementDescriptor dr = reg.getDescriptor(RenderedRegistryMode.MODE_NAME, d.getName());
            if (dr != null) {
                reg.unregisterDescriptor(dr);
            }
        }
    }

}
