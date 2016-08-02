package org.weasis.dicom.explorer;

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.felix.service.command.CommandProcessor;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.DataExplorerViewFactory;
import org.weasis.core.api.explorer.model.DataExplorerModel;

@Component(immediate = false)
@Service
@Properties(value = { @Property(name = "service.name", value = "DICOM Explorer"),
    @Property(name = "service.description", value = "Explore Dicom data by patient, study and series") })
public class DicomExplorerFactory implements DataExplorerViewFactory {

    private DicomExplorer explorer = null;
    private DicomModel model = null;

    @Override
    public DataExplorerView createDataExplorerView(Hashtable<String, Object> properties) {
        buildDicomModel(null);
        if (explorer == null) {
            explorer = new DicomExplorer(model);
            model.addPropertyChangeListener(explorer);
        }
        return explorer;
    }

    private void buildDicomModel(ComponentContext context) {
        if (model == null) {
            model = new DicomModel();
            if (context != null) {
                registerCommands(context);
            }
        } else if (context != null) {
            ServiceReference<?>[] val = null;
            try {
                val = context.getBundleContext().getServiceReferences(DicomModel.class.getName(), null);
            } catch (InvalidSyntaxException e) {
                // Do nothing
            }
            if (val == null || val.length == 0) {
                registerCommands(context);
            }
        }
    }

    private void registerCommands(ComponentContext context) {
        Dictionary<String, Object> dict = new Hashtable<>();
        dict.put(CommandProcessor.COMMAND_SCOPE, "dicom"); //$NON-NLS-1$
        dict.put(CommandProcessor.COMMAND_FUNCTION, DicomModel.functions);
        context.getBundleContext().registerService(DicomModel.class.getName(), model, dict);
    }

    @Activate
    protected void activate(ComponentContext context) {
        buildDicomModel(context);
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {
        if (explorer != null) {
            DataExplorerModel dataModel = explorer.getDataExplorerModel();
            dataModel.removePropertyChangeListener(explorer);
            explorer = null;
        }
        model = null;
    }
}