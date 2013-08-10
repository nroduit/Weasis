package org.weasis.base.explorer;

import java.util.Hashtable;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.osgi.service.component.ComponentContext;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.DataExplorerViewFactory;

@Component(immediate = false)
@Service
@Properties(value = { @Property(name = "service.name", value = "Media Explorer"),
    @Property(name = "service.description", value = "Explore supported media files in tree view") })
public class DefaultExplorerFactory implements DataExplorerViewFactory {

    private DefaultExplorer explorer = null;
    private FileTreeModel model = null;

    @Override
    public DataExplorerView createDataExplorerView(Hashtable<String, Object> properties) {
        if (model == null) {
            model = JIUtility.createTreeModel();
        }
        if (explorer == null) {
            explorer = new DefaultExplorer(model);
            explorer.iniLastPath();
        }
        return explorer;
    }

    @Activate
    protected void activate(ComponentContext context) {
        if (model == null) {
            model = JIUtility.createTreeModel();
            // Dictionary<String, Object> dict = new Hashtable<String, Object>();
            //            dict.put(CommandProcessor.COMMAND_SCOPE, "image"); //$NON-NLS-1$
            // dict.put(CommandProcessor.COMMAND_FUNCTION, FileTreeModel.functions);
            // context.getBundleContext().registerService(FileTreeModel.class.getName(), model, dict);
        }
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {
        if (explorer != null) {
            explorer.saveLastPath();
        }
        explorer = null;
        model = null;
    }

}