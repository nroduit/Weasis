package org.weasis.dicom.viewer2d.pref;

import java.util.Hashtable;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.weasis.core.api.gui.PreferencesPageFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;

@Component(immediate = false)
@Service
public class ViewerPrefFactory implements PreferencesPageFactory {

    @Override
    public AbstractItemDialogPage createPreferencesPage(Hashtable<String, Object> properties) {
        return new ViewerPrefView();
    }

}
