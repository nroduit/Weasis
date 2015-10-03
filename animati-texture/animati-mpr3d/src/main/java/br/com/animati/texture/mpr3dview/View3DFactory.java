/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Informática Ltda.
 * (http://www.animati.com.br)
 */
package br.com.animati.texture.mpr3dview;

import java.awt.Component;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;

import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.image.LayoutConstraints;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.explorer.DicomExplorer;
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

    @Override
    public SeriesViewer createSeriesViewer(Map<String, Object> properties) {

        if (Activator.useHardwareAcceleration) {
            GridBagLayoutModel model = View3DContainer.VIEWS_2x1_mpr;
            String uid = null;
            if (properties != null) {
                Object obj = properties.get(org.weasis.core.api.image.GridBagLayoutModel.class.getName());
                if (obj instanceof GridBagLayoutModel) {
                    model = (GridBagLayoutModel) obj;
                } else {
                    obj = properties.get(ViewTexture.class.getName());
                    if (obj instanceof Integer) {
                        ActionState layout = GUIManager.getInstance().getAction(ActionW.LAYOUT);
                        if (layout instanceof ComboItemListener) {
                            Object[] list = ((ComboItemListener) layout).getAllItem();
                            for (Object m : list) {
                                if (m instanceof GridBagLayoutModel) {
                                    if (getViewTypeNumber((GridBagLayoutModel) m, ViewTexture.class) >= (Integer) obj) {
                                        model = (GridBagLayoutModel) m;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }

                // Set UID
                Object val = properties.get(ViewerPluginBuilder.UID);
                if (val instanceof String) {
                    uid = (String) val;
                }
            }
            View3DContainer instance = new View3DContainer(model, uid, getUIName(), getIcon(), null);
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

    public static int getViewTypeNumber(GridBagLayoutModel layout, Class defaultClass) {
        int val = 0;
        if (layout != null && defaultClass != null) {
            Iterator<LayoutConstraints> enumVal = layout.getConstraints().keySet().iterator();
            while (enumVal.hasNext()) {
                try {
                    Class clazz = Class.forName(enumVal.next().getType());
                    if (defaultClass.isAssignableFrom(clazz)) {
                        val++;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return val;
    }

    public static void closeSeriesViewer(View3DContainer view3dContainer) {
        // Unregister the PropertyChangeListener
        DataExplorerView dicomView = UIManager.getExplorerplugin(DicomExplorer.NAME);
        if (dicomView != null) {
            dicomView.getDataExplorerModel().removePropertyChangeListener(view3dContainer);
        }
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

    public static void showHANotAvailableMsg(Component parent) {
        JOptionPane.showMessageDialog(parent, Messages.getString("View3DFactory.error.HANotAvailable"));
    }

}
