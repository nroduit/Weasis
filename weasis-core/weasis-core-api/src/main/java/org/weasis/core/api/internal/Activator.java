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
package org.weasis.core.api.internal;

import java.awt.image.renderable.RenderedImageFactory;

import javax.media.jai.JAI;
import javax.media.jai.OperationDescriptorImpl;
import javax.media.jai.OperationRegistry;
import javax.media.jai.RegistryElementDescriptor;
import javax.media.jai.registry.RIFRegistry;
import javax.media.jai.util.ImagingListener;

import org.apache.felix.prefs.BackingStore;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.image.op.ExtremaRangeLimitDescriptor;
import org.weasis.core.api.image.op.FormatBinaryDescriptor;
import org.weasis.core.api.image.op.NotBinaryDescriptor;
import org.weasis.core.api.image.op.RectifySignedShortDataDescriptor;
import org.weasis.core.api.image.op.RectifyUShortToShortDataDescriptor;
import org.weasis.core.api.image.op.ThresholdToBinDescriptor;
import org.weasis.core.api.image.util.ImageToolkit;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.service.DataFileBackingStoreImpl;
import org.weasis.core.api.util.ProxyDetector;

public class Activator implements BundleActivator, ServiceListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(Activator.class);

    private static final String codecFilter = String.format("(%s=%s)", Constants.OBJECTCLASS, Codec.class.getName()); //$NON-NLS-1$
    private BundleContext bundleContext;

    public void start(final BundleContext bundleContext) throws Exception {
        this.bundleContext = bundleContext;
        bundleContext.registerService(BackingStore.class.getName(), new DataFileBackingStoreImpl(bundleContext), null);
        bundleContext.addServiceListener(this, codecFilter);
        ServiceTracker m_tracker = new ServiceTracker(bundleContext, Codec.class.getName(), null);
        // Do not close tracker. It will uregister services
        m_tracker.open();
        Object[] services = m_tracker.getServices();
        for (int i = 0; (services != null) && (i < services.length); i++) {
            if (!BundleTools.CODEC_PLUGINS.contains(services[i]) && services[i] instanceof Codec) {
                Codec codec = (Codec) services[i];
                BundleTools.CODEC_PLUGINS.add(codec);
                LOGGER.info("Register Codec Plug-in: {}", codec.getCodecName()); //$NON-NLS-1$
            }
        }
        JAI jai = getJAI();
        OperationRegistry or = jai.getOperationRegistry();

        jai.setImagingListener(new ImagingListener() {

            public boolean errorOccurred(String message, Throwable thrown, Object where, boolean isRetryable)
                throws RuntimeException {
                LOGGER.error("JAI error ocurred: {}", message); //$NON-NLS-1$
                return false;
            }
        });
        registerOp(or, new FormatBinaryDescriptor());
        registerOp(or, new NotBinaryDescriptor());
        registerOp(or, new ExtremaRangeLimitDescriptor());
        registerOp(or, new ThresholdToBinDescriptor());
        registerOp(or, new RectifySignedShortDataDescriptor());
        registerOp(or, new RectifyUShortToShortDataDescriptor());

        // TODO manage memory setting ?
        ImageToolkit.setJaiCacheMemoryCapacity(128);

        // Allows to connect through a proxy initialized by Java Webstart
        ProxyDetector.setProxyFromJavaWebStart();
    }

    public void stop(BundleContext bundleContext) throws Exception {
        this.bundleContext = null;
        BundleTools.saveSystemPreferences();
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

    public static void registerOp(OperationRegistry or, OperationDescriptorImpl descriptor) {
        String name = descriptor.getName();
        String[] mode = descriptor.getSupportedModes();
        RegistryElementDescriptor val = or.getDescriptor(mode[0], name);
        if (val == null) {
            or.registerDescriptor(descriptor);
            RIFRegistry.register(null, name, "org.weasis.core.api.image.op", (RenderedImageFactory) descriptor); //$NON-NLS-1$
        }
    }

    public synchronized void serviceChanged(ServiceEvent event) {
        ServiceReference m_ref = event.getServiceReference();
        Codec codec = (Codec) bundleContext.getService(m_ref);
        if (codec == null) {
            return;
        }

        // TODO manage when several identical MimeType, register the default one
        if (event.getType() == ServiceEvent.REGISTERED) {

            if (!BundleTools.CODEC_PLUGINS.contains(codec)) {
                BundleTools.CODEC_PLUGINS.add(codec);
                LOGGER.info("Register Codec Plug-in: {}", codec.getCodecName()); //$NON-NLS-1$
            }
        } else if (event.getType() == ServiceEvent.UNREGISTERING) {
            if (BundleTools.CODEC_PLUGINS.contains(codec)) {
                LOGGER.info("Unregister Codec Plug-in: {}", codec.getCodecName()); //$NON-NLS-1$
                BundleTools.CODEC_PLUGINS.remove(codec);
                // Unget service object and null references.
                bundleContext.ungetService(m_ref);
            }
        }
    }

}
