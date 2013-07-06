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
package org.weasis.dicom.codec.internal;

import java.util.Dictionary;
import java.util.Hashtable;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.service.ImageioUtil;
import org.weasis.dicom.codec.DicomCodec;
import org.weasis.dicom.codec.pref.DicomPrefManager;

public class Activator implements BundleActivator {

    private static final String LOGGER_KEY = "always.info.ItemParser";
    private static final String LOGGER_VAL = "org.dcm4che.imageio.ItemParser";

    // @Override
    @Override
    public void start(final BundleContext bundleContext) throws Exception {
        // Register SPI in imageio registry with the classloader of this bundle (provides also the classpath for
        // discovering the SPI files). Here are the codecs:
        // org.dcm4che.imageioimpl.plugins.rle.RLEImageReaderSpi
        // org.dcm4che.imageioimpl.plugins.dcm.DicomImageReaderSpi
        // org.dcm4che.imageioimpl.plugins.dcm.DicomImageWriterSpi
        ImageioUtil.registerServiceProvider(DicomCodec.RLEImageReaderSpi);
        ImageioUtil.registerServiceProvider(DicomCodec.DicomImageReaderSpi);

        ConfigurationAdmin confAdmin = BundlePreferences.getService(bundleContext, ConfigurationAdmin.class);
        if (confAdmin != null) {
            Configuration logConfiguration = AuditLog.getLogConfiguration(confAdmin, LOGGER_KEY, LOGGER_VAL);
            if (logConfiguration == null) {
                logConfiguration =
                    confAdmin
                        .createFactoryConfiguration("org.apache.sling.commons.log.LogManager.factory.config", null);
                Dictionary<String, Object> loggingProperties = new Hashtable<String, Object>();
                loggingProperties.put("org.apache.sling.commons.log.level", "INFO");
                // loggingProperties.put("org.apache.sling.commons.log.file", "logs.log");
                loggingProperties.put("org.apache.sling.commons.log.names", LOGGER_VAL);
                // add this property to give us something unique to re-find this configuration
                loggingProperties.put(LOGGER_KEY, LOGGER_VAL);
                logConfiguration.update(loggingProperties);
            }
        }
    }

    // @Override
    @Override
    public void stop(BundleContext bundleContext) throws Exception {
        DicomPrefManager.getInstance().savePreferences();
        ImageioUtil.deregisterServiceProvider(DicomCodec.RLEImageReaderSpi);
        ImageioUtil.deregisterServiceProvider(DicomCodec.DicomImageReaderSpi);
    }

}
