package org.weasis.dicom.explorer;

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.felix.service.command.CommandProcessor;
import org.osgi.service.component.ComponentContext;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.DataExplorerViewFactory;
import org.weasis.core.api.explorer.model.DataExplorerModel;

public class DicomExplorerFactory implements DataExplorerViewFactory {

    private DicomExplorer explorer = null;
    private DicomModel model = null;

    @Override
    public DataExplorerView createDataExplorerView(Hashtable<String, Object> properties) {
        if (model == null) {
            model = new DicomModel();
        }
        if (explorer == null) {
            explorer = new DicomExplorer(model);
            model.addPropertyChangeListener(explorer);
        }
        return explorer;
    }

    protected void activate(ComponentContext context) {
        if (model == null) {
            model = new DicomModel();
            Dictionary<String, Object> dict = new Hashtable<String, Object>();
            dict.put(CommandProcessor.COMMAND_SCOPE, "dicom"); //$NON-NLS-1$
            dict.put(CommandProcessor.COMMAND_FUNCTION, DicomModel.functions);
            context.getBundleContext().registerService(DicomModel.class.getName(), model, dict);
        }
    }

    protected void deactivate(ComponentContext context) {
        if (explorer != null) {
            DataExplorerModel model = explorer.getDataExplorerModel();
            model.removePropertyChangeListener(explorer);
            explorer = null;
        }
        model = null;
    }
}