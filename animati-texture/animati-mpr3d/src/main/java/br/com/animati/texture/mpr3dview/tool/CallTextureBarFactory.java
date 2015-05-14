/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Inform??tica Ltda.
 * (http://www.animati.com.br)
 */
package br.com.animati.texture.mpr3dview.tool;

import java.util.Hashtable;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.InsertableFactory;

/**
 *
 * @author Gabriela Carla Bauerman (gabriela@animati.com.br)
 * @version 2015, 14 May.
 */
@org.apache.felix.scr.annotations.Component(immediate = false)
@Service
@Property(name = "org.weasis.dicom.viewer2d.View2dContainer", value = "true")
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
