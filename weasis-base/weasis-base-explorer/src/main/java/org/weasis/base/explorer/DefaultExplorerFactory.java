package org.weasis.base.explorer;

import java.util.Hashtable;

import org.osgi.service.component.ComponentContext;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.DataExplorerViewFactory;

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

    protected void activate(ComponentContext context) {
        if (model == null) {
            model = JIUtility.createTreeModel();
            // Dictionary<String, Object> dict = new Hashtable<String, Object>();
            //            dict.put(CommandProcessor.COMMAND_SCOPE, "image"); //$NON-NLS-1$
            // dict.put(CommandProcessor.COMMAND_FUNCTION, FileTreeModel.functions);
            // context.getBundleContext().registerService(FileTreeModel.class.getName(), model, dict);
        }
    }

    protected void deactivate(ComponentContext context) {
        if (explorer != null) {
            explorer.saveLastPath();
        }
        explorer = null;
        model = null;
    }

}