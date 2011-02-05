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

import javax.imageio.spi.IIORegistry;

import org.dcm4che2.imageioimpl.plugins.rle.RLEImageReaderSpi;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.dicom.codec.pref.DicomPrefManager;

public class Activator implements BundleActivator {
    public final static BundlePreferences PREFERENCES = new BundlePreferences();
    private BundleContext bundleContext = null;

    // @Override
    public void start(final BundleContext bundleContext) throws Exception {
        this.bundleContext = bundleContext;
        PREFERENCES.init(bundleContext);
        // Register SPI in imageio registry with the classloader of this bundle (provides also the classpath for
        // discovering the SPI files). Here are the codecs:
        // org.dcm4che2.imageioimpl.plugins.rle.RLEImageReaderSpi
        // org.dcm4che2.imageioimpl.plugins.dcm.DicomImageReaderSpi
        // org.dcm4che2.imageioimpl.plugins.dcm.DicomImageWriterSpi
        IIORegistry registry = IIORegistry.getDefaultInstance();
        registry.registerServiceProvider(new RLEImageReaderSpi());
        // registry.registerServiceProvider(org.dcm4che2.imageioimpl.plugins.dcm.DicomImageReaderSpi.class);
        // registry.registerServiceProvider(org.dcm4che2.imageioimpl.plugins.dcm.DicomImageWriterSpi.class);

        ServiceReference configurationAdminReference =
            bundleContext.getServiceReference(ConfigurationAdmin.class.getName());
        if (configurationAdminReference != null) {
            ConfigurationAdmin confAdmin = (ConfigurationAdmin) bundleContext.getService(configurationAdminReference);

            Configuration configuration =
                confAdmin.createFactoryConfiguration("org.apache.sling.commons.log.LogManager.factory.config", null);
            Dictionary<String, Object> loggingProperties = new Hashtable<String, Object>();
            loggingProperties.put("pid", this.getClass().getName());
            loggingProperties.put("org.apache.sling.commons.log.level", "INFO");
            loggingProperties.put("org.apache.sling.commons.log.names", "org.dcm4che2.imageio.ItemParser");
            configuration.update(loggingProperties);

        }
    }

    // @Override
    public void stop(BundleContext bundleContext) throws Exception {
        DicomPrefManager.getInstance().savePreferences();
        // TODO unregister rle reader
        PREFERENCES.close();
        this.bundleContext = null;
    }

}
