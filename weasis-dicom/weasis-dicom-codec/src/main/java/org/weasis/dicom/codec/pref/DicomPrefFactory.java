package org.weasis.dicom.codec.pref;

import java.util.Hashtable;

import org.weasis.core.api.gui.PreferencesPageFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;

public class DicomPrefFactory implements PreferencesPageFactory {

    @Override
    public AbstractItemDialogPage createPreferencesPage(Hashtable<String, Object> properties) {
        if (properties != null) {
            if ("superuser".equals(properties.get("weasis.user.prefs")))
                return new DicomPrefView();
        }
        return null;
    }
}