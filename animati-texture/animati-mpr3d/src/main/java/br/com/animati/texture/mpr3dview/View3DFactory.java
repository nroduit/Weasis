/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Informática Ltda.
 * (http://www.animati.com.br)
 */
package br.com.animati.texture.mpr3dview;

import java.awt.Component;
import java.util.List;
import java.util.Map;

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;

import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.explorer.DicomModel;

import br.com.animati.texture.mpr3dview.internal.Activator;
import br.com.animati.texture.mpr3dview.internal.Messages;

/**
 *
 * @author Gabriela Carla Bauermann (gabriela@animati.com.br)
 * @version 2013, 16 Jul.
 */

@org.apache.felix.scr.annotations.Component(immediate = false)
@Service
@Property(name = "service.name", value = "MPR 3D Viewer")
public class View3DFactory implements SeriesViewerFactory {

    public static final String NAME = "MPR 3D";

    public static final ImageIcon ICON = new ImageIcon(View3DContainer.class.getResource("/icon/16x16/mpr3d.png"));

    @Override
    public SeriesViewer createSeriesViewer(Map<String, Object> properties) {

        if (Activator.useHardwareAcceleration) {
            View3DContainer instance = new View3DContainer(View3DContainer.VIEWS_2x1_mpr);

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

        // TODO: parent!
        showHANotAvailableMsg(null);
        return null;
    }

    @Override
    public boolean canReadMimeType(String mimeType) {
        return DicomMediaIO.SERIES_MIMETYPE.equals(mimeType);
    }

    @Override
    public boolean isViewerCreatedByThisFactory(SeriesViewer viewer) {
        if (viewer instanceof View3DContainer) {
            return true;
        }
        return false;
    }

    @Override
    public int getLevel() {
        // Deixa em segundo, por enquanto:
        return 10;
    }

    @Override
    public boolean canAddSeries() {
        return false;
    }

    @Override
    public boolean canExternalizeSeries() {
        return true;
    }

    @Override
    public List<Action> getOpenActions() {
        return null;
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
        return NAME;
    }

    public static void showHANotAvailableMsg(Component parent) {
        JOptionPane.showMessageDialog(parent, Messages.getString("View3DFactory.error.HANotAvailable"));
    }

}
