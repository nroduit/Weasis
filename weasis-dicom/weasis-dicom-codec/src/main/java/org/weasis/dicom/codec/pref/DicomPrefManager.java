/*******************************************************************************
 * Copyright (c) 2011 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.dicom.codec.pref;

import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.prefs.Preferences;
import org.weasis.core.api.service.BundlePreferences;

public class DicomPrefManager {

    /** The single instance of this singleton class. */

    private static DicomPrefManager instance;
    private String j2kReader;

    /**
     * Return the single instance of this class. This method guarantees the singleton property of this class.
     */
    public static synchronized DicomPrefManager getInstance() {
        if (instance == null) {
            instance = new DicomPrefManager();
        }
        return instance;
    }

    private DicomPrefManager() {
        restoreDefaultValues();
        if ("superuser".equals(System.getProperty("weasis.user.prefs"))) { //$NON-NLS-1$ //$NON-NLS-2$
            final BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
            Preferences pref = BundlePreferences.getDefaultPreferences(context);
            if (pref != null) {
                Preferences prefNode = pref.node("dicom"); //$NON-NLS-1$
                j2kReader = prefNode.get("jpeg2000.reader", null); //$NON-NLS-1$
            }
        }
    }

    public void restoreDefaultValues() {
        j2kReader = null;
    }

    public void savePreferences() {
        if ("superuser".equals(System.getProperty("weasis.user.prefs"))) { //$NON-NLS-1$ //$NON-NLS-2$
            final BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
            Preferences prefs = BundlePreferences.getDefaultPreferences(context);
            if (prefs != null) {
                Preferences prefNode = prefs.node("dicom"); //$NON-NLS-1$
                BundlePreferences.putStringPreferences(prefNode, "jpeg2000.reader", j2kReader); //$NON-NLS-1$
            }
        }
    }

    public String getJ2kReader() {
        return j2kReader;
    }

    public void setJ2kReader(String j2kReader) {
        this.j2kReader = j2kReader;
    }

}
