package org.weasis.dicom.codec.pref;

import org.osgi.service.prefs.Preferences;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.dicom.codec.internal.Activator;

public class DicomPrefManager {

    /** The single instance of this singleton class. */

    private static DicomPrefManager instance;

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
        Preferences pref = Activator.PREFERENCES.getDefaultPreferences();
        if (pref != null) {
            Preferences prefNode = pref.node("dicom"); //$NON-NLS-1$

        }

    }

    public void savePreferences() {
        Preferences prefs = Activator.PREFERENCES.getDefaultPreferences();
        if (prefs != null) {
            Preferences prefNode = prefs.node("dicom"); //$NON-NLS-1$
            BundlePreferences.putStringPreferences(prefNode, "jpeg2000.reader", ""); //$NON-NLS-1$

        }
    }
}
