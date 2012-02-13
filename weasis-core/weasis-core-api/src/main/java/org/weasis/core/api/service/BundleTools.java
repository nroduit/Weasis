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
package org.weasis.core.api.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.weasis.core.api.gui.util.AbstractProperties;
import org.weasis.core.api.internal.Activator;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.util.FileUtil;

public class BundleTools {
    public enum LEVEL {
        DEBUG, INFO, WARN, ERROR, FATAL;

        public static LEVEL getLevel(String level) {
            try {
                return LEVEL.valueOf(level);
            } catch (Exception e) {
            }
            return INFO;
        }
    };

    public static final String LOG_LEVEL = "org.apache.sling.commons.log.level";
    public static final String LOG_FILE = "org.apache.sling.commons.log.file";
    public static final String LOG_FILE_NUMBER = "org.apache.sling.commons.log.file.number";
    public static final String LOG_FILE_SIZE = "org.apache.sling.commons.log.file.size";
    public static final String LOG_PATTERN = "org.apache.sling.commons.log.pattern";
    public static final String LOG_LOGGERS = "org.apache.sling.commons.log.names";

    public static final List<Codec> CODEC_PLUGINS = Collections.synchronizedList(new ArrayList<Codec>());
    private static final File propsFile;
    static {
        String user = System.getProperty("weasis.user", null); //$NON-NLS-1$
        if (user == null) {
            propsFile = new File(AbstractProperties.WEASIS_PATH, "weasis.properties"); //$NON-NLS-1$
        } else {
            File dir = new File(AbstractProperties.WEASIS_PATH + File.separator + "preferences" + File.separator //$NON-NLS-1$
                + user);
            try {
                dir.mkdirs();
            } catch (Exception e) {
                dir = new File(AbstractProperties.WEASIS_PATH);
                e.printStackTrace();
            }
            propsFile = new File(dir, "weasis.properties"); //$NON-NLS-1$
        }
    }
    public static final WProperties SYSTEM_PREFERENCES = new WProperties();
    static {
        FileUtil.readProperties(propsFile, SYSTEM_PREFERENCES);
        if (!propsFile.canRead()) {
            try {
                propsFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static Codec getCodec(String mimeType, String preferredCodec) {
        Codec codec = null;
        synchronized (BundleTools.CODEC_PLUGINS) {
            for (Codec c : BundleTools.CODEC_PLUGINS) {
                if (c.isMimeTypeSupported(mimeType)) {
                    if (c.getCodecName().equals(preferredCodec)) {
                        codec = c;
                        break;
                    }
                    // If the preferred codec cannot be find, the first-found codec is retained
                    if (codec == null) {
                        codec = c;
                    }
                }
            }
            return codec;
        }
    }

    public static void saveSystemPreferences() {
        FileUtil.storeProperties(propsFile, SYSTEM_PREFERENCES, null);
    }

    public static void createOrUpdateLogger(String loggerKey, String[] loggerVal, String level, String logFile,
        String pattern, Integer nbFiles) {
        if (loggerKey != null && loggerVal != null && loggerVal.length > 0) {
            BundleContext bundleContext = Activator.getBundleContext();
            if (bundleContext != null) {
                ServiceReference configurationAdminReference =
                    bundleContext.getServiceReference(ConfigurationAdmin.class.getName());
                if (configurationAdminReference != null) {
                    ConfigurationAdmin confAdmin =
                        (ConfigurationAdmin) bundleContext.getService(configurationAdminReference);
                    if (confAdmin != null) {
                        try {
                            Dictionary<String, Object> loggingProperties;
                            Configuration logConfiguration =
                                BundleTools.getLogConfiguration(confAdmin, "(" + loggerKey + "=" + loggerVal[0] + ")");
                            if (logConfiguration == null) {
                                logConfiguration =
                                    confAdmin.createFactoryConfiguration(
                                        "org.apache.sling.commons.log.LogManager.factory.config", null);
                                loggingProperties = new Hashtable<String, Object>();
                                loggingProperties.put(BundleTools.LOG_LOGGERS, loggerVal);
                                // add this property to give us something unique to re-find this configuration
                                loggingProperties.put(loggerKey, loggerVal[0]);
                            } else {
                                loggingProperties = logConfiguration.getProperties();
                            }
                            loggingProperties.put(BundleTools.LOG_LEVEL, level.toString());
                            if (logFile != null) {
                                loggingProperties.put(BundleTools.LOG_FILE, logFile);
                            }
                            if (nbFiles != null) {
                                loggingProperties.put(BundleTools.LOG_FILE_NUMBER, nbFiles);
                            }
                            if (pattern != null) {
                                loggingProperties.put(BundleTools.LOG_PATTERN, pattern);
                            }
                            // org.apache.sling.commons.log.pattern={0,date,dd.MM.yyyy HH:mm:ss.SSS} *{4} {1}* [{2}] {3}
                            // {5}
                            logConfiguration.update(loggingProperties);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    public static Configuration getLogConfiguration(ConfigurationAdmin confAdmin, String filter) throws IOException {
        Configuration logConfiguration = null;
        try {
            Configuration[] configs = confAdmin.listConfigurations(filter);
            if (configs != null && configs.length > 0) {
                logConfiguration = configs[0];
            }
        } catch (InvalidSyntaxException e) {
            // ignore this as we'll create what we need
        }
        return logConfiguration;
    }

}
