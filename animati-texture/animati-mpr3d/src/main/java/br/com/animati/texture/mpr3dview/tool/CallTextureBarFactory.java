/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Inform??tica Ltda.
 * (http://www.animati.com.br)
 */
package br.com.animati.texture.mpr3dview.tool;

import java.util.Hashtable;

import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.InsertableFactory;

/**
 *
 * @author Gabriela Carla Bauerman (gabriela@animati.com.br)
 * @version 2015, 14 May.
 */

@org.osgi.service.component.annotations.Component(service = InsertableFactory.class, immediate = false, property = {
"org.weasis.dicom.viewer2d.View2dContainer=true"  })
public class CallTextureBarFactory implements InsertableFactory {

    @Override
    public Insertable createInstance(Hashtable<String, Object> properties) {
        return new CallTextureToolbar(60);
    }

    @Override
    public void dispose(Insertable component) {
    }

    @Override
    public boolean isComponentCreatedByThisFactory(Insertable component) {
        return component instanceof CallTextureToolbar;
    }

    @Override
    public Insertable.Type getType() {
        return Insertable.Type.TOOLBAR;
    }

}
