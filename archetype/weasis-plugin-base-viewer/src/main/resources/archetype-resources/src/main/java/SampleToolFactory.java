#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package};

import java.util.Hashtable;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.ui.docking.Insertable;
import org.weasis.core.ui.docking.Insertable.Type;
import org.weasis.core.ui.docking.InsertableFactory;

@Component(immediate = false)
@Service
@Property(name = "org.weasis.base.viewer2d.View2dContainer", value = "true")
public class SampleToolFactory implements InsertableFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(SampleToolFactory.class);

    private SampleTool toolPane = null;

    @Override
    public Type getType() {
        return Type.TOOL;
    }

    @Override
    public Insertable createInstance(Hashtable<String, Object> properties) {
        if (toolPane == null) {
            toolPane = new SampleTool(getType());
        }
        return toolPane;
    }

    @Override
    public void dispose(Insertable tool) {
        if (toolPane != null) {
            toolPane = null;
        }
    }

    @Override
    public boolean isComponentCreatedByThisFactory(Insertable tool) {
        return tool instanceof SampleTool;
    }

    @Activate
    protected void activate(ComponentContext context) {
        LOGGER.info("Activate the Point Counting panel");
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {
        LOGGER.info("Deactivate the Point Counting panel");
    }

}