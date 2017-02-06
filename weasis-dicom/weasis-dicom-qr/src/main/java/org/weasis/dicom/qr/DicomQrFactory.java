/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.qr;

import java.io.File;
import java.util.Hashtable;
import java.util.Properties;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.explorer.DicomImportFactory;
import org.weasis.dicom.explorer.ImportDicom;

@org.osgi.service.component.annotations.Component(service = DicomImportFactory.class, immediate = false)
public class DicomQrFactory implements DicomImportFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(DicomQrFactory.class);

    public static final Properties IMPORT_PERSISTENCE = new Properties();

    @Override
    public ImportDicom createDicomImportPage(Hashtable<String, Object> properties) {
        return new DicomQrView();
    }

    @Activate
    protected void activate(ComponentContext context) throws Exception {
        LOGGER.info("DICOM Q/R is activated"); //$NON-NLS-1$
        FileUtil.readProperties(
            new File(BundlePreferences.getDataFolder(context.getBundleContext()), "import.properties"), //$NON-NLS-1$
            IMPORT_PERSISTENCE);
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {
        LOGGER.info("DICOM Q/R is deactivated"); //$NON-NLS-1$
        FileUtil.storeProperties(
            new File(BundlePreferences.getDataFolder(context.getBundleContext()), "import.properties"), //$NON-NLS-1$
            IMPORT_PERSISTENCE, null);

    }

}
