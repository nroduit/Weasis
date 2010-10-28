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
package imageioext.j2k.codec.internal;

import it.geosolutions.imageio.imageioimpl.imagereadmt.ImageReadDescriptorMT;
import it.geosolutions.imageio.plugins.jp2k.JP2KKakaduImageReaderSpi;
import it.geosolutions.imageio.stream.input.spi.FileImageInputStreamExtImplSpi;
import it.geosolutions.imageio.stream.input.spi.StringImageInputStreamSpi;
import it.geosolutions.imageio.stream.input.spi.URLImageInputStreamSpi;
import it.geosolutions.util.KakaduUtilities;

import java.util.Iterator;
import java.util.List;

import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.spi.ImageReaderWriterSpi;
import javax.imageio.spi.ServiceRegistry;
import javax.media.jai.JAI;
import javax.media.jai.OperationRegistry;
import javax.media.jai.RegistryElementDescriptor;
import javax.media.jai.registry.RenderedRegistryMode;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.weasis.core.api.gui.util.AbstractProperties;

public class Activator implements BundleActivator {
    private static final String[] MIMETypes =
        { "image/jp2", "image/jp2k", "image/j2k", "image/j2c", "image/jpeg2000", "image/jpeg2000-image", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
            "image/x-jpeg2000-image" }; //$NON-NLS-1$

    private static final String KDU_AUX_LIB = System.mapLibraryName("KDU_A64R"); //$NON-NLS-1$
    private static final String KDU_CORE_LIB = System.mapLibraryName("KDU_V64R"); //$NON-NLS-1$

    private BundleContext bundleContext = null;

    public void start(final BundleContext bundleContext) throws Exception {
        this.bundleContext = bundleContext;
        if (AbstractProperties.OPERATING_SYSTEM.startsWith("win")) { //$NON-NLS-1$
            try {
                // Upper Case dll name fixes dependency issues during runtime
                System.loadLibrary(KDU_CORE_LIB);
                System.loadLibrary(KDU_AUX_LIB);
            } catch (UnsatisfiedLinkError e) {
                e.printStackTrace();
            }
        }

        // Register the ImageReadMT operation
        ImageReadDescriptorMT.register(JAI.getDefaultInstance());

        /*
         * Overrides the method that register the service. This method do not deregister other jpeg2000 service, only
         * reorder.
         */
        JP2KKakaduImageReaderSpi kakakuSpi = new JP2KKakaduImageReaderSpi() {
            @Override
            public synchronized void onRegistration(ServiceRegistry registry, Class category) {
                // Do not want to call super (super.super is not possible in Java)
                // super.onRegistration(registry, category);
                if (registered) {
                    return;
                }

                registered = true;
                if (!KakaduUtilities.isKakaduAvailable()) {
                    final IIORegistry iioRegistry = (IIORegistry) registry;
                    final Class<ImageReaderSpi> spiClass = ImageReaderSpi.class;
                    final Iterator<ImageReaderSpi> iter = iioRegistry.getServiceProviders(spiClass, true);
                    while (iter.hasNext()) {
                        final ImageReaderSpi provider = iter.next();
                        if (provider instanceof JP2KKakaduImageReaderSpi) {
                            registry.deregisterServiceProvider(provider);
                        }
                    }
                    return;
                }
                final List<ImageReaderWriterSpi> readers =
                    KakaduUtilities.getJDKImageReaderWriterSPI(registry, "jpeg2000", true); //$NON-NLS-1$
                for (ImageReaderWriterSpi elem : readers) {
                    if (elem instanceof ImageReaderSpi) {
                        final ImageReaderSpi spi = (ImageReaderSpi) elem;
                        if (spi == this) {
                            continue;
                        }
                        // registry.deregisterServiceProvider(spi);
                        registry.setOrdering(category, this, spi);
                    }
                }
            }

            @Override
            public String[] getMIMETypes() {
                return MIMETypes == null ? null : (String[]) MIMETypes.clone();
            }
        };
        IIORegistry registry = IIORegistry.getDefaultInstance();
        try {
            registry.registerServiceProvider(kakakuSpi);
            registry.registerServiceProvider(new FileImageInputStreamExtImplSpi());
            registry.registerServiceProvider(new URLImageInputStreamSpi());
            registry.registerServiceProvider(new StringImageInputStreamSpi());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stop(BundleContext bundleContext) throws Exception {
        IIORegistry registry = IIORegistry.getDefaultInstance();
        unRegisterServiceProvider(registry, JP2KKakaduImageReaderSpi.class);
        unRegisterServiceProvider(registry, FileImageInputStreamExtImplSpi.class);
        unRegisterServiceProvider(registry, URLImageInputStreamSpi.class);
        unRegisterServiceProvider(registry, StringImageInputStreamSpi.class);

        // Unregister the ImageReadMT operation
        final OperationRegistry registryJAI = JAI.getDefaultInstance().getOperationRegistry();
        RegistryElementDescriptor descriptor = registryJAI.getDescriptor(ImageReadDescriptorMT.class, "ImageReadMT"); //$NON-NLS-1$
        if (descriptor != null) {
            registryJAI.unregisterDescriptor(descriptor);
        }
        Object factory = registryJAI.getFactory(RenderedRegistryMode.MODE_NAME, "ImageReadMT"); //$NON-NLS-1$
        if (factory != null) {
            registryJAI.unregisterFactory(RenderedRegistryMode.MODE_NAME, "ImageReadMT", "it.geosolutions", factory); //$NON-NLS-1$ //$NON-NLS-2$
        }
        this.bundleContext = null;
    }

    private static void unRegisterServiceProvider(IIORegistry registry, Class clazz) {
        Object spi = registry.getServiceProviderByClass(clazz);
        if (spi != null) {
            registry.deregisterServiceProvider(spi);
        }
    }

}
