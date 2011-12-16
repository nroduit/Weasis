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
package org.weasis.dicom.explorer.internal;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.wado.DicomManager;

public class Activator implements BundleActivator {

    public static final BundlePreferences PREFERENCES = new BundlePreferences();

    @Override
    public void start(final BundleContext context) throws Exception {
        PREFERENCES.init(context);
        String cache = context.getProperty("weasis.portable.dicom.cache");
        DicomManager.getInstance().setPortableDirCache(!((cache != null) && cache.equalsIgnoreCase("false")));//$NON-NLS-1$
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        // Save preferences
        DicomManager.getInstance().savePreferences();
        PREFERENCES.close();
        DataExplorerView explorer = UIManager.getExplorerplugin(DicomExplorer.NAME);
        if (explorer instanceof DicomExplorer) {
            DicomExplorer dexp = (DicomExplorer) explorer;
            // Remove image in viewers, in image cache and close the image stream
            ((DicomModel) dexp.getDataExplorerModel()).dispose();
        }
    }

}
