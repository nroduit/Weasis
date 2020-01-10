/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.send;

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
import org.weasis.dicom.explorer.CheckTreeModel;
import org.weasis.dicom.explorer.DicomExportFactory;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.ExportDicom;

@org.osgi.service.component.annotations.Component(service = DicomExportFactory.class, immediate = false)
public class SendDicomFactory implements DicomExportFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(SendDicomFactory.class);

    static final Properties EXPORT_PERSISTENCE = new Properties();

    @Override
    public ExportDicom createDicomExportPage(Hashtable<String, Object> properties) {
        if (properties != null && BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.export.dicom.send", true)) { //$NON-NLS-1$
            DicomModel dicomModel = (DicomModel) properties.get(DicomModel.class.getName());
            CheckTreeModel treeModel = (CheckTreeModel) properties.get(CheckTreeModel.class.getName());
            if (dicomModel != null && treeModel != null) {
                return new SendDicomView(dicomModel, treeModel);
            }
        }
        return null;
    }

    // ================================================================================
    // OSGI service implementation
    // ================================================================================

    @Activate
    protected void activate(ComponentContext context) throws Exception {
        LOGGER.info("DICOM Send is activated"); //$NON-NLS-1$
        FileUtil.readProperties(
            new File(BundlePreferences.getDataFolder(context.getBundleContext()), "export.properties"), //$NON-NLS-1$
            EXPORT_PERSISTENCE);
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {
        LOGGER.info("DICOM Send is deactivated"); //$NON-NLS-1$
        FileUtil.storeProperties(
            new File(BundlePreferences.getDataFolder(context.getBundleContext()), "export.properties"), //$NON-NLS-1$
            EXPORT_PERSISTENCE, null);

    }
}
