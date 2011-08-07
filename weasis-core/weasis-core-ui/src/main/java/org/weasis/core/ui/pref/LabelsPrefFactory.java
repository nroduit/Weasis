package org.weasis.core.ui.pref;

import java.util.Hashtable;

import org.weasis.core.api.gui.PreferencesPageFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;

public class LabelsPrefFactory implements PreferencesPageFactory {

    @Override
    public AbstractItemDialogPage createPreferencesPage(Hashtable<String, Object> properties) {
        if (properties != null) {
            return new LabelsPrefView();
        }
        return null;
    }

}