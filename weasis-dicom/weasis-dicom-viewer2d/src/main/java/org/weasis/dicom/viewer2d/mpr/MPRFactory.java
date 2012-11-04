package org.weasis.dicom.viewer2d.mpr;

import java.awt.Component;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.viewer2d.mpr.MprView.Type;

public class MPRFactory implements SeriesViewerFactory {

    public static final String NAME = "MPR";
    public static final Icon ICON = new ImageIcon(MimeInspector.class.getResource("/icon/16x16/dicom-3d.png")); //$NON-NLS-1$

    public MPRFactory() {
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
        return "Orthogonal MPR";
    }

    @Override
    public SeriesViewer createSeriesViewer(Hashtable<String, Object> properties) {
        GridBagLayoutModel model = MPRContainer.VIEWS_2x1_mpr;
        if (properties != null) {
            Object obj = properties.get(org.weasis.core.api.image.GridBagLayoutModel.class.getName());
            if (obj instanceof GridBagLayoutModel) {
                model = (GridBagLayoutModel) obj;
            }
        }

        MPRContainer instance = new MPRContainer(model);
        if (properties != null) {
            Object obj = properties.get(DataExplorerModel.class.getName());
            if (obj instanceof DicomModel) {
                // Register the PropertyChangeListener
                DicomModel m = (DicomModel) obj;
                m.addPropertyChangeListener(instance);
            }
        }
        int index = 0;
        Iterator<Component> enumVal = model.getConstraints().values().iterator();
        while (enumVal.hasNext()) {
            Component val = enumVal.next();
            if (val instanceof MprView) {
                Type type;
                switch (index) {
                    case 1:
                        type = Type.CORONAL;
                        break;
                    case 2:
                        type = Type.SAGITTAL;
                        break;
                    default:
                        type = Type.AXIAL;
                        break;
                }
                ((MprView) val).setType(type);
                index++;
            }
        }
        return instance;

    }

    public static void closeSeriesViewer(MPRContainer mprContainer) {
        // Unregister the PropertyChangeListener
        DataExplorerView dicomView = UIManager.getExplorerplugin(DicomExplorer.NAME);
        if (dicomView != null) {
            dicomView.getDataExplorerModel().removePropertyChangeListener(mprContainer);
        }
    }

    @Override
    public boolean canReadMimeType(String mimeType) {
        return DicomMediaIO.SERIES_MIMETYPE.equals(mimeType);
    }

    @Override
    public boolean isViewerCreatedByThisFactory(SeriesViewer viewer) {
        if (viewer instanceof MPRContainer) {
            return true;
        }
        return false;
    }

    @Override
    public int getLevel() {
        return 15;
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
