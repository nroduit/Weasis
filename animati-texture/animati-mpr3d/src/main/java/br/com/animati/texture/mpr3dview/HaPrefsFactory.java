/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Informática Ltda.
 * (http://www.animati.com.br)
 */

package br.com.animati.texture.mpr3dview;

import java.util.Hashtable;

import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.PreferencesPageFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;

/**
 *
 * @author Gabriela Bauermann (gabriela@animati.com.br)
 * @version
 */

@org.osgi.service.component.annotations.Component(service = PreferencesPageFactory.class, immediate = false)
public class HaPrefsFactory implements PreferencesPageFactory {

    @Override
    public AbstractItemDialogPage createInstance(Hashtable<String, Object> properties) {
        return new HaPrefsPage();
    }

    @Override
    public void dispose(Insertable component) {
    }

    @Override
    public boolean isComponentCreatedByThisFactory(Insertable component) {
        return component instanceof HaPrefsPage;
    }

    @Override
    public Insertable.Type getType() {
        return Insertable.Type.PREFERENCES;
    }

}
