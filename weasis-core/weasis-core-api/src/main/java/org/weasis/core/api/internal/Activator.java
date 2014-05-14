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

import java.io.File;
import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;

import javax.media.jai.JAI;
import javax.media.jai.OperationRegistry;
import javax.media.jai.util.ImagingListener;

import org.apache.felix.prefs.BackingStore;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.image.op.FormatBinaryDescriptor;
import org.weasis.core.api.image.op.ImageStatistics2Descriptor;
import org.weasis.core.api.image.op.ImageStatisticsDescriptor;
import org.weasis.core.api.image.op.NotBinaryDescriptor;
import org.weasis.core.api.image.op.RectifySignedShortDataDescriptor;
import org.weasis.core.api.image.op.RectifyUShortToShortDataDescriptor;
import org.weasis.core.api.image.op.ShutterDescriptor;
import org.weasis.core.api.image.op.ThresholdToBinDescriptor;
import org.weasis.core.api.image.util.JAIUtil;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.service.DataFileBackingStoreImpl;
import org.weasis.core.api.util.ProxyDetector;

public class Activator implements BundleActivator, ServiceListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(Activator.class);

    @Override
    public void start(BundleContext bundleContext) throws Exception {
        bundleContext.registerService(BackingStore.class.getName(), new DataFileBackingStoreImpl(bundleContext), null);

        for (ServiceReference<Codec> service : bundleContext.getServiceReferences(Codec.class, null)) {
            Codec codec = bundleContext.getService(service);
            if (codec != null && !BundleTools.CODEC_PLUGINS.contains(codec)) {
                BundleTools.CODEC_PLUGINS.add(codec);
                LOGGER.info("Register Codec Plug-in: {}", codec.getCodecName()); //$NON-NLS-1$
            }
        }

        bundleContext.addServiceListener(this, String.format("(%s=%s)", Constants.OBJECTCLASS, Codec.class.getName()));//$NON-NLS-1$

        JAI jai = JAIUtil.getJAI();
        OperationRegistry or = jai.getOperationRegistry();

        jai.setImagingListener(new ImagingListener() {

            @Override
            public boolean errorOccurred(String message, Throwable thrown, Object where, boolean isRetryable)
                throws RuntimeException {
                LOGGER.error("JAI error ocurred: {}", message); //$NON-NLS-1$
                return false;
            }
        });
        JAIUtil.registerOp(or, new FormatBinaryDescriptor());
        JAIUtil.registerOp(or, new NotBinaryDescriptor());
        JAIUtil.registerOp(or, new ImageStatisticsDescriptor());
        JAIUtil.registerOp(or, new ImageStatistics2Descriptor());
        JAIUtil.registerOp(or, new ShutterDescriptor());
        JAIUtil.registerOp(or, new ThresholdToBinDescriptor());
        JAIUtil.registerOp(or, new RectifySignedShortDataDescriptor());
        JAIUtil.registerOp(or, new RectifyUShortToShortDataDescriptor());

        // TODO manage memory setting ?
        jai.getTileCache().setMemoryCapacity(128 * 1024L * 1024L);

        // Allows to connect through a proxy initialized by Java Webstart
        ProxyDetector.setProxyFromJavaWebStart();

        initLoggerAndAudit(bundleContext);
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception {
    }

    @Override
    public synchronized void serviceChanged(ServiceEvent event) {
        ServiceReference<?> m_ref = event.getServiceReference();
        BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
        Codec codec = (Codec) context.getService(m_ref);
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
            }
            // Unget service object and null references.
            context.ungetService(m_ref);
        }
    }

    private void initLoggerAndAudit(BundleContext bundleContext) throws IOException {
        // Audit log for giving statistics about usage of Weasis
        String loggerKey = "audit.log"; //$NON-NLS-1$
        String[] loggerVal = new String[] { "org.weasis.core.api.service.AuditLog" }; //$NON-NLS-1$
        // Activate audit log by adding an entry "audit.log=true" in Weasis.
        String audit = bundleContext.getProperty(loggerKey);
        if (audit != null && audit.equalsIgnoreCase("true")) { //$NON-NLS-1$
            AuditLog.createOrUpdateLogger(bundleContext, loggerKey, loggerVal, "DEBUG", AppProperties.WEASIS_PATH //$NON-NLS-1$
                + File.separator + "log" + File.separator + "audit-" + AppProperties.WEASIS_USER + ".log", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                "{0,date,dd.MM.yyyy HH:mm:ss.SSS} *{4}* {5}", null, null); //$NON-NLS-1$
            AuditLog.LOGGER.info("Start audit log session"); //$NON-NLS-1$
        } else {
            ServiceReference<ConfigurationAdmin> configurationAdminReference =
                bundleContext.getServiceReference(ConfigurationAdmin.class);
            if (configurationAdminReference != null) {
                ConfigurationAdmin confAdmin = bundleContext.getService(configurationAdminReference);
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
