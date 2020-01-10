/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.qr;

import java.io.File;
import java.util.Hashtable;
import java.util.Properties;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.explorer.DicomImportFactory;
import org.weasis.dicom.explorer.ImportDicom;

@org.osgi.service.component.annotations.Component(service = DicomImportFactory.class, immediate = false)
public class DicomQrFactory implements DicomImportFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(DicomQrFactory.class);

    public static final Properties IMPORT_PERSISTENCE = new Properties();

    // public static final ArrayList<SearchParameters> SEARCH_ITEMS = new ArrayList<>();

    private static final String PREFERENCE_NODE = "qr.prefs"; //$NON-NLS-1$

    @Override
    public ImportDicom createDicomImportPage(Hashtable<String, Object> properties) {
        if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.import.dicom.qr", true)) { //$NON-NLS-1$
            return new DicomQrView();
        }
        return null;
    }

    // ================================================================================
    // OSGI service implementation
    // ================================================================================

    @Activate
    protected void activate(ComponentContext context) throws Exception {
        LOGGER.info("DICOM Q/R is activated"); //$NON-NLS-1$
        FileUtil.readProperties(
            new File(BundlePreferences.getDataFolder(context.getBundleContext()), "import.properties"), //$NON-NLS-1$
            IMPORT_PERSISTENCE);

        // SEARCH_ITEMS.clear();
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {
        LOGGER.info("DICOM Q/R is deactivated"); //$NON-NLS-1$
        FileUtil.storeProperties(
            new File(BundlePreferences.getDataFolder(context.getBundleContext()), "import.properties"), //$NON-NLS-1$
            IMPORT_PERSISTENCE, null);

        // Preferences prefs = BundlePreferences.getDefaultPreferences(context.getBundleContext());
        // if (prefs != null) {
        // Preferences p = prefs.node(DicomQrFactory.PREFERENCE_NODE);
        // // Forget the Selection Graphic
        // for (int i = 1; i < SEARCH_ITEMS.size(); i++) {
        // SearchParameters item = SEARCH_ITEMS.get(i);
        //
        // List<DicomParam> list = item.getParameters();
        // if (list != null && !list.isEmpty()) {
        // Preferences gpref = p.node(item.toString());
        //
        // }
        // }
        // }
    }

}
