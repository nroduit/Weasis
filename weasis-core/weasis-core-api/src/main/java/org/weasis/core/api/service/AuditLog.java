/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.api.service;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;

import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StringUtil;

/**
 * The Class AuditLog allows to write specific traces for the application usage.
 *
 */
public class AuditLog {
    // Share this logger
    public static final Logger LOGGER = LoggerFactory.getLogger(AuditLog.class);

    public static final String LOG_LEVEL = "org.apache.sling.commons.log.level"; //$NON-NLS-1$
    public static final String LOG_STACKTRACE_LIMIT = "org.apache.sling.commons.log.stack.limit"; //$NON-NLS-1$
    public static final String LOG_FILE_ACTIVATION = "org.apache.sling.commons.log.file.activate"; //$NON-NLS-1$
    public static final String LOG_FILE = "org.apache.sling.commons.log.file"; //$NON-NLS-1$
    public static final String LOG_FILE_NUMBER = "org.apache.sling.commons.log.file.number"; //$NON-NLS-1$
    public static final String LOG_FILE_SIZE = "org.apache.sling.commons.log.file.size"; //$NON-NLS-1$
    public static final String LOG_PATTERN = "org.apache.sling.commons.log.pattern"; //$NON-NLS-1$
    public static final String LOG_LOGGERS = "org.apache.sling.commons.log.names"; //$NON-NLS-1$

    public static final String MARKER_PERF = "*PERF*"; //$NON-NLS-1$

    public enum LEVEL {
        TRACE, DEBUG, INFO, WARN, ERROR;

        public static LEVEL getLevel(String level) {
            try {
                return LEVEL.valueOf(level);
            } catch (Exception ignore) {
                // Do nothing
            }
            return INFO;
        }
    }

    public static void createOrUpdateLogger(BundleContext bundleContext, String loggerKey, String[] loggerVal,
        String level, String logFile, String pattern, String nbFiles, String logSize, String limit) {
        if (bundleContext != null && loggerKey != null && loggerVal != null && loggerVal.length > 0) {
            ServiceReference<?> configurationAdminReference =
                bundleContext.getServiceReference(ConfigurationAdmin.class.getName());
            if (configurationAdminReference != null) {
                ConfigurationAdmin confAdmin =
                    (ConfigurationAdmin) bundleContext.getService(configurationAdminReference);
                if (confAdmin != null) {
                    try {
                        Dictionary<String, Object> loggingProperties;
                        Configuration logConfiguration = getLogConfiguration(confAdmin, loggerKey, loggerVal[0]);
                        if (logConfiguration == null) {
                            logConfiguration = confAdmin.createFactoryConfiguration(
                                "org.apache.sling.commons.log.LogManager.factory.config", null); //$NON-NLS-1$
                            loggingProperties = new Hashtable<>();
                            loggingProperties.put(LOG_LOGGERS, loggerVal);
                            // add this property to give us something unique to re-find this configuration
                            loggingProperties.put(loggerKey, loggerVal[0]);
                        } else {
                            loggingProperties = logConfiguration.getProperties();
                        }
                        loggingProperties.put(LOG_LEVEL, level == null ? "INFO" : level); //$NON-NLS-1$
                        if (logFile != null) {
                            loggingProperties.put(LOG_FILE, logFile);
                        }
                        if (nbFiles != null) {
                            loggingProperties.put(LOG_FILE_NUMBER, nbFiles);
                        }
                        if (logSize != null) {
                            loggingProperties.put(LOG_FILE_SIZE, logSize);
                        }
                        if (pattern != null) {
                            loggingProperties.put(LOG_PATTERN, pattern);
                        }
                        if (limit != null) {
                            loggingProperties.put(LOG_STACKTRACE_LIMIT, StringUtil.hasText(limit) ? limit : "-1"); //$NON-NLS-1$
                        }
                        logConfiguration.update(loggingProperties);
                    } catch (IOException e) {
                        LOGGER.error("Update log parameters", e); //$NON-NLS-1$
                    }
                }
            }
        }
    }

    public static Configuration getLogConfiguration(ConfigurationAdmin confAdmin, String key, String val)
        throws IOException {
        Configuration logConfiguration = null;
        if (key != null && val != null) {
            try {
                Configuration[] configs = confAdmin.listConfigurations("(" + key + "=" + val + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                if (configs != null && configs.length > 0) {
                    logConfiguration = configs[0];
                }
            } catch (InvalidSyntaxException e) {
                LOGGER.error("", e); //$NON-NLS-1$
            }
        }
        return logConfiguration;
    }

    public static void logError(Logger log, Throwable t, String message) {
        if (log.isDebugEnabled()) {
            log.error(message, t);
        } else {
            log.error(t.getMessage());
        }
    }
}
