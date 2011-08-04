package org.weasis.dicom.viewer2d.pref;

import java.util.Hashtable;

import org.weasis.core.api.gui.PreferencesPageFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;

public class ViewerPrefFactory implements PreferencesPageFactory {

    @Override
    public AbstractItemDialogPage createPreferencesPage(Hashtable<String, Object> properties) {
        return new ViewerPrefView();
    }

}
