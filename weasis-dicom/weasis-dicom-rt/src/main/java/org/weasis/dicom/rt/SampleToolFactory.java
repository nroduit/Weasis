package org.weasis.dicom.rt;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.Insertable.Type;
import org.weasis.core.api.gui.InsertableFactory;

import java.util.Hashtable;


/**
 * Created by toskrip on 2/4/15.
 */

// I need to register the tool when opening series of specific modality or when opening the viewer
@Component(immediate = false)
@Service
@Property(name = "org.weasis.dicom.rt.RTSpecialElement", value = "true")
public class SampleToolFactory implements InsertableFactory {

    private final Logger LOGGER = LoggerFactory.getLogger(SampleToolFactory.class);

    @Override
    public Insertable createInstance(Hashtable<String, Object> properties) {
        return null; //new SampleTool();
    }

    @Override
    public void dispose(Insertable component) {
        if (component != null) {
            // Remove all the registered listeners or other behaviors links with other existing components if exists.
        }
    }

    @Override
    public boolean isComponentCreatedByThisFactory(Insertable component) {
        return component instanceof SampleTool;
    }

    @Override
    public Type getType() {
        return Type.TOOL;
    }

}
