#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package};

import java.util.Hashtable;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.Insertable.Type;
import org.weasis.core.api.gui.InsertableFactory;

@org.osgi.service.component.annotations.Component(service = InsertableFactory.class, immediate = false, property = {
"org.weasis.base.viewer2d.View2dContainer=true"  })
public class SampleToolbarFactory implements InsertableFactory {
    private final Logger LOGGER = LoggerFactory.getLogger(SampleToolbarFactory.class);

    @Override
    public Type getType() {
        return Type.TOOLBAR;
    }

    @Override
    public Insertable createInstance(Hashtable<String, Object> properties) {
        return new SampleToolBar<ImageElement>();
    }

    @Override
    public boolean isComponentCreatedByThisFactory(Insertable component) {
        return component instanceof SampleToolBar;
    }

    @Override
    public void dispose(Insertable bar) {
        if (bar != null) {
            // Remove all the registered listeners or other behaviors links with other existing components if exists.
        }
    }

    @Activate
    protected void activate(ComponentContext context) throws Exception {
        LOGGER.info("Activate the Sample tool bar");
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {
        LOGGER.info("Deactivate the Sample tool bar");
    }

}
