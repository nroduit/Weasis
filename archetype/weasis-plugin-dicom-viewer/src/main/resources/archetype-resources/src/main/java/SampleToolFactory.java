#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package};

import java.util.Hashtable;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.Insertable.Type;
import org.weasis.core.api.gui.InsertableFactory;

@org.osgi.service.component.annotations.Component(
    service = InsertableFactory.class,
    property = {"org.weasis.dicom.viewer2d.View2dContainer=true"})
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

    // ================================================================================
    // OSGI service implementation
    // ================================================================================

    @Activate
    protected void activate(ComponentContext context) {
        LOGGER.info("Activate the Sample panel");
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {
        LOGGER.info("Deactivate the Sample panel");
    }
}