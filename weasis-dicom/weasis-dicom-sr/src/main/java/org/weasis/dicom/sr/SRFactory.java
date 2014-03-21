package org.weasis.dicom.sr;

import java.util.List;
import java.util.Map;

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomModel;

@Component(immediate = false)
@Service
@Property(name = "service.name", value = "DICOM SR Viewer")
public class SRFactory implements SeriesViewerFactory {

    public static final String NAME = "DICOM SR Viewer";
    public static final Icon ICON = new ImageIcon(MediaElement.class.getResource("/icon/22x22/text-x-generic.png")); //$NON-NLS-1$

    public SRFactory() {
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "DICOM Structured Report";
    }

    @Override
    public SeriesViewer<? extends MediaElement<?>> createSeriesViewer(Map<String, Object> properties) {
        GridBagLayoutModel model = SRContainer.VIEWS_1x1;
        if (properties != null) {
            Object obj = properties.get(org.weasis.core.api.image.GridBagLayoutModel.class.getName());
            if (obj instanceof GridBagLayoutModel) {
                model = (GridBagLayoutModel) obj;
            }
        }

        SRContainer instance = new SRContainer(model);
        if (properties != null) {
            Object obj = properties.get(DataExplorerModel.class.getName());
            if (obj instanceof DicomModel) {
                // Register the PropertyChangeListener
                DicomModel m = (DicomModel) obj;
                m.addPropertyChangeListener(instance);
            }
        }
        return instance;
    }

    public static void closeSeriesViewer(SRContainer mprContainer) {
        // Unregister the PropertyChangeListener
        DataExplorerView dicomView = UIManager.getExplorerplugin(DicomExplorer.NAME);
        if (dicomView != null) {
            dicomView.getDataExplorerModel().removePropertyChangeListener(mprContainer);
        }
    }

    @Override
    public boolean canReadMimeType(String mimeType) {
        return DicomMediaIO.SERIES_SR_MIMETYPE.equals(mimeType);
    }

    @Override
    public boolean isViewerCreatedByThisFactory(SeriesViewer<? extends MediaElement<?>> viewer) {
        if (viewer instanceof SRContainer) {
            return true;
        }
        return false;
    }

    @Override
    public int getLevel() {
        return 25;
    }

    @Override
    public List<Action> getOpenActions() {
        return null;
    }

    @Override
    public boolean canAddSeries() {
        return false;
    }

    @Override
    public boolean canExternalizeSeries() {
        return true;
    }

}
