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

import org.dcm4che3.util.UIDUtils;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.service.BundleTools;
import org.weasis.dicom.codec.DicomCodec;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSpecialElementFactory;
import org.weasis.dicom.codec.pref.DicomPrefManager;

import com.sun.media.imageioimpl.common.ImageioUtil;

public class Activator implements BundleActivator, ServiceListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(Activator.class);

    private static final String LOGGER_KEY = "always.info.ItemParser"; //$NON-NLS-1$
    private static final String LOGGER_VAL = "org.dcm4che3.imageio.ItemParser"; //$NON-NLS-1$

    @Override
    public void start(final BundleContext bundleContext) throws Exception {
        setDicomRootUID();

        // Register SPI in imageio registry with the classloader of this bundle (provides also the classpath for
        // discovering the SPI files). Here are the codecs:
        // org.dcm4che3.imageioimpl.plugins.rle.RLEImageReaderSpi
        // org.dcm4che3.imageioimpl.plugins.dcm.DicomImageReaderSpi
        // org.dcm4che3.imageioimpl.plugins.dcm.DicomImageWriterSpi
        ImageioUtil.registerServiceProvider(DicomCodec.RLEImageReaderSpi);
        ImageioUtil.registerServiceProvider(DicomCodec.DicomImageReaderSpi);

        ConfigurationAdmin confAdmin = BundlePreferences.getService(bundleContext, ConfigurationAdmin.class);
        if (confAdmin != null) {
            Configuration logConfiguration = AuditLog.getLogConfiguration(confAdmin, LOGGER_KEY, LOGGER_VAL);
            if (logConfiguration == null) {
                logConfiguration = confAdmin
                    .createFactoryConfiguration("org.apache.sling.commons.log.LogManager.factory.config", null); //$NON-NLS-1$
                Dictionary<String, Object> loggingProperties = new Hashtable<>();
                loggingProperties.put("org.apache.sling.commons.log.level", "INFO"); //$NON-NLS-1$ //$NON-NLS-2$
                loggingProperties.put("org.apache.sling.commons.log.names", LOGGER_VAL); //$NON-NLS-1$
                // add this property to give us something unique to re-find this configuration
                loggingProperties.put(LOGGER_KEY, LOGGER_VAL);
                logConfiguration.update(loggingProperties);
            }
        }

        try {
            for (ServiceReference<DicomSpecialElementFactory> service : bundleContext
                .getServiceReferences(DicomSpecialElementFactory.class, null)) {
                DicomSpecialElementFactory factory = bundleContext.getService(service);
                if (factory != null) {
                    for (String modality : factory.getModalities()) {
                        DicomSpecialElementFactory prev = DicomMediaIO.DCM_ELEMENT_FACTORIES.put(modality, factory);
                        if (prev != null) {
                            LOGGER.warn("{} factory has been replaced by {}", prev.getClass(), factory.getClass()); //$NON-NLS-1$
                        }
                        LOGGER.info("Register DicomSpecialElementFactory: {}", factory.getClass()); //$NON-NLS-1$
                    }
                }
            }
        } catch (InvalidSyntaxException e) {
            LOGGER.error("", e);
        }

        bundleContext.addServiceListener(this,
            String.format("(%s=%s)", Constants.OBJECTCLASS, DicomSpecialElementFactory.class.getName()));//$NON-NLS-1$
    }

    // @Override
    @Override
    public void stop(BundleContext bundleContext) throws Exception {
        DicomPrefManager.getInstance().savePreferences();
        ImageioUtil.deregisterServiceProvider(DicomCodec.RLEImageReaderSpi);
        ImageioUtil.deregisterServiceProvider(DicomCodec.DicomImageReaderSpi);
    }

    @Override
    public synchronized void serviceChanged(final ServiceEvent event) {
        ServiceReference<?> mef = event.getServiceReference();
        BundleContext context = FrameworkUtil.getBundle(Activator.this.getClass()).getBundleContext();
        DicomSpecialElementFactory factory = (DicomSpecialElementFactory) context.getService(mef);
        if (factory == null) {
            return;
        }

        if (event.getType() == ServiceEvent.REGISTERED) {
            for (String modality : factory.getModalities()) {
                DicomSpecialElementFactory prev = DicomMediaIO.DCM_ELEMENT_FACTORIES.put(modality, factory);
                if (prev != null) {
                    LOGGER.warn("{} factory has been replaced by {}", prev.getClass(), factory.getClass()); //$NON-NLS-1$
                }
                LOGGER.info("Register DicomSpecialElementFactory: {}", factory.getClass()); //$NON-NLS-1$
            }

        } else if (event.getType() == ServiceEvent.UNREGISTERING) {
            for (String modality : factory.getModalities()) {
                DicomSpecialElementFactory f = DicomMediaIO.DCM_ELEMENT_FACTORIES.get(modality);
                if (factory.equals(f)) {
                    DicomMediaIO.DCM_ELEMENT_FACTORIES.remove(modality);
                } else {
                    LOGGER.warn("Cannot unregister {}, {} is registered instead", factory.getClass(), f.getClass()); //$NON-NLS-1$
                }
                LOGGER.info("Unregister DicomSpecialElementFactory: {}", factory.getClass()); //$NON-NLS-1$
            }
            // Unget service object and null references.
            context.ungetService(mef);
        }
    }

    private static void setDicomRootUID() {
        /**
         * Set value for dicom root UID which should be registered at the
         * http://www.iana.org/assignments/enterprise-numbers <br>
         * Default value is 2.25, this enables users to generate OIDs without any registration procedure
         *
         * @see http://www.dclunie.com/medical-image-faq/html/part2.html#UUID <br>
         *      http://www.oid-info.com/get/2.25 <br>
         *      http://www.itu.int/ITU-T/asn1/uuid.html<br>
         *      http://healthcaresecprivacy.blogspot.ch/2011/02/creating-and-using-unique-id-uuid-oid.html
         */
        String weasisRootUID = BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.dicom.root.uid", UIDUtils.getRoot()); //$NON-NLS-1$
        UIDUtils.setRoot(weasisRootUID);
    }
}
