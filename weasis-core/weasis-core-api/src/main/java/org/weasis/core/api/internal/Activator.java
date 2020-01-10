/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.api.internal;

import java.io.File;
import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.felix.prefs.BackingStore;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.LangUtil;

public class Activator implements BundleActivator, ServiceListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(Activator.class);

    @Override
    public void start(BundleContext bundleContext) throws Exception {
        bundleContext.registerService(BackingStore.class.getName(), new StreamBackingStoreImpl(bundleContext), null);

        for (ServiceReference<Codec> service : bundleContext.getServiceReferences(Codec.class, null)) {
            registerCodecPlugins(bundleContext.getService(service));
        }

        bundleContext.addServiceListener(this, String.format("(%s=%s)", Constants.OBJECTCLASS, Codec.class.getName()));//$NON-NLS-1$

        // Allows to connect through a proxy initialized by Java Webstart
        if (!LangUtil.geEmptytoTrue(System.getProperty("http.bundle.cache"))) { //$NON-NLS-1$
            ProxyUtils.setProxyFromJavaWebStart();
        }

        initLoggerAndAudit(bundleContext);
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception {
        BundleTools.saveSystemPreferences();
    }

    @Override
    public synchronized void serviceChanged(ServiceEvent event) {

        ServiceReference<?> sRef = event.getServiceReference();
        BundleContext context = AppProperties.getBundleContext(sRef);
        Codec codec = null;
        try {
            codec = (Codec) context.getService(sRef);
        } catch (RuntimeException e) {
            LOGGER.error("Cannot get service of {}", sRef.getBundle(), e); //$NON-NLS-1$
        }
        if (codec == null) {
            return;
        }

        // TODO manage when several identical MimeType, register the default one
        if (event.getType() == ServiceEvent.REGISTERED) {
            registerCodecPlugins(codec);
        } else if (event.getType() == ServiceEvent.UNREGISTERING) {
            if (BundleTools.CODEC_PLUGINS.contains(codec)) {
                LOGGER.info("Unregister Image Codec Plug-in: {}", codec.getCodecName()); //$NON-NLS-1$
                BundleTools.CODEC_PLUGINS.remove(codec);
            }
            // Unget service object and null references.
            context.ungetService(sRef);
        }
    }

    private static void registerCodecPlugins(Codec codec) {
        if (codec != null && !BundleTools.CODEC_PLUGINS.contains(codec)) {
            BundleTools.CODEC_PLUGINS.add(codec);
            LOGGER.info("Register Image Codec Plug-in: {}", codec.getCodecName()); //$NON-NLS-1$
        }
    }

    private static void initLoggerAndAudit(BundleContext bundleContext) throws IOException {
        // Audit log for giving statistics about usage of Weasis
        String loggerKey = "audit.log"; //$NON-NLS-1$
        String[] loggerVal = new String[] { "org.weasis.core.api.service.AuditLog" }; //$NON-NLS-1$
        // Activate audit log by adding an entry "audit.log=true" in Weasis.
        if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty(loggerKey, false)) {
            AuditLog.createOrUpdateLogger(bundleContext, loggerKey, loggerVal, "DEBUG", //$NON-NLS-1$
                AppProperties.WEASIS_PATH + File.separator + "log" + File.separator + "audit-" //$NON-NLS-1$ //$NON-NLS-2$
                    + AppProperties.WEASIS_USER + ".log", //$NON-NLS-1$
                "{0,date,dd.MM.yyyy HH:mm:ss.SSS} *{4}* {5}", null, null, "0"); //$NON-NLS-1$ //$NON-NLS-2$
            AuditLog.LOGGER.info("Start audit log session"); //$NON-NLS-1$
        } else {
            ServiceReference<ConfigurationAdmin> configurationAdminReference =
                bundleContext.getServiceReference(ConfigurationAdmin.class);
            if (configurationAdminReference != null) {
                ConfigurationAdmin confAdmin = bundleContext.getService(configurationAdminReference);
                if (confAdmin != null) {
                    Configuration logConfiguration = AuditLog.getLogConfiguration(confAdmin, loggerKey, loggerVal[0]);
                    if (logConfiguration == null) {
                        logConfiguration = confAdmin
                            .createFactoryConfiguration("org.apache.sling.commons.log.LogManager.factory.config", null); //$NON-NLS-1$
                        Dictionary<String, Object> loggingProperties = new Hashtable<>();
                        loggingProperties.put("org.apache.sling.commons.log.level", "ERROR"); //$NON-NLS-1$ //$NON-NLS-2$
                        loggingProperties.put("org.apache.sling.commons.log.names", loggerVal); //$NON-NLS-1$
                        // add this property to give us something unique to re-find this configuration
                        loggingProperties.put(loggerKey, loggerVal[0]);
                        logConfiguration.update(loggingProperties);
                    } else {
                        Dictionary loggingProperties = logConfiguration.getProperties();
                        loggingProperties.remove(AuditLog.LOG_FILE);
                        logConfiguration.update(loggingProperties);
                    }
                }
            }
        }
    }
}
