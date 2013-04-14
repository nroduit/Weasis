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
import java.io.File;
import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;

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
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AbstractProperties;
import org.weasis.core.api.image.op.FormatBinaryDescriptor;
import org.weasis.core.api.image.op.ImageStatistics2Descriptor;
import org.weasis.core.api.image.op.ImageStatisticsDescriptor;
import org.weasis.core.api.image.op.NotBinaryDescriptor;
import org.weasis.core.api.image.op.RectifySignedShortDataDescriptor;
import org.weasis.core.api.image.op.RectifyUShortToShortDataDescriptor;
import org.weasis.core.api.image.op.ShutterDescriptor;
import org.weasis.core.api.image.op.ThresholdToBinDescriptor;
import org.weasis.core.api.image.util.ImageToolkit;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.service.DataFileBackingStoreImpl;
import org.weasis.core.api.util.ProxyDetector;

public class Activator implements BundleActivator, ServiceListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(Activator.class);

    private static final String codecFilter = String.format("(%s=%s)", Constants.OBJECTCLASS, Codec.class.getName()); //$NON-NLS-1$
    private static BundleContext bundleContext;

    @Override
    public void start(final BundleContext context) throws Exception {
        Activator.bundleContext = context;
        context.registerService(BackingStore.class.getName(), new DataFileBackingStoreImpl(context), null);
        context.addServiceListener(this, codecFilter);
        ServiceTracker m_tracker = new ServiceTracker(context, Codec.class.getName(), null);
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

            @Override
            public boolean errorOccurred(String message, Throwable thrown, Object where, boolean isRetryable)
                throws RuntimeException {
                LOGGER.error("JAI error ocurred: {}", message); //$NON-NLS-1$
                return false;
            }
        });
        registerOp(or, new FormatBinaryDescriptor());
        registerOp(or, new NotBinaryDescriptor());
        registerOp(or, new ImageStatisticsDescriptor());
        registerOp(or, new ImageStatistics2Descriptor());
        registerOp(or, new ShutterDescriptor());
        registerOp(or, new ThresholdToBinDescriptor());
        registerOp(or, new RectifySignedShortDataDescriptor());
        registerOp(or, new RectifyUShortToShortDataDescriptor());

        // TODO manage memory setting ?
        ImageToolkit.setJaiCacheMemoryCapacity(128);

        // Allows to connect through a proxy initialized by Java Webstart
        ProxyDetector.setProxyFromJavaWebStart();

        initLoggerAndAudit();
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception {
        Activator.bundleContext = null;
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

    @Override
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

    public static BundleContext getBundleContext() {
        return bundleContext;
    }

    private void initLoggerAndAudit() throws IOException {
        // Audit log for giving statistics about usage of Weasis
        String loggerKey = "audit.log"; //$NON-NLS-1$
        String[] loggerVal = new String[] { "org.weasis.core.api.service.AuditLog" }; //$NON-NLS-1$
        // Activate audit log by adding an entry "audit.log=true" in Weasis.
        String audit = bundleContext.getProperty(loggerKey);
        if (audit != null && audit.equalsIgnoreCase("true")) { //$NON-NLS-1$
            AuditLog.createOrUpdateLogger(loggerKey, loggerVal, "DEBUG", AbstractProperties.WEASIS_PATH //$NON-NLS-1$
                + File.separator + "log" + File.separator + "audit-" + AbstractProperties.WEASIS_USER + ".log", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                "{0,date,dd.MM.yyyy HH:mm:ss.SSS} *{4}* {5}", null, null); //$NON-NLS-1$
            AuditLog.LOGGER.info("Start audit log session"); //$NON-NLS-1$
        } else {
            ServiceReference configurationAdminReference =
                bundleContext.getServiceReference(ConfigurationAdmin.class.getName());
            if (configurationAdminReference != null) {
                ConfigurationAdmin confAdmin =
                    (ConfigurationAdmin) bundleContext.getService(configurationAdminReference);
                if (confAdmin != null) {

                    Configuration logConfiguration = AuditLog.getLogConfiguration(confAdmin, loggerKey, loggerVal[0]);
                    if (logConfiguration == null) {
                        logConfiguration =
                            confAdmin.createFactoryConfiguration(
                                "org.apache.sling.commons.log.LogManager.factory.config", null); //$NON-NLS-1$
                        Dictionary<String, Object> loggingProperties = new Hashtable<String, Object>();
                        loggingProperties.put("org.apache.sling.commons.log.level", "ERROR"); //$NON-NLS-1$ //$NON-NLS-2$
                        // loggingProperties.put("org.apache.sling.commons.log.file", "logs.log");
                        loggingProperties.put("org.apache.sling.commons.log.names", loggerVal); //$NON-NLS-1$
                        // add this property to give us something unique to re-find this configuration
                        loggingProperties.put(loggerKey, loggerVal[0]);
                        logConfiguration.update(loggingProperties);
                    }
                }
            }
        }
    }
}
