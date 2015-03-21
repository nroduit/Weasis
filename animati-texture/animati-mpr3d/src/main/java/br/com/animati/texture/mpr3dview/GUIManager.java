/*
 * @copyright Copyright (c) 2013 Animati Sistemas de Informática Ltda.
 * (http://www.animati.com.br)
 */
package br.com.animati.texture.mpr3dview;

import br.com.animati.texture.mpr3dview.api.AbstractViewsContainer;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.SeriesEvent;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.graphic.AngleToolGraphic;
import org.weasis.core.ui.graphic.Graphic;
import org.weasis.core.ui.graphic.LineGraphic;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomModel;

/**
 * Deals with global events of interface:
 *  - Selected Container;
 *  - Mouse Actions;
 *  - Measurements list, next graphic to create;
 * 
 * @author Gabriela Carla Bauerman (gabriela@animati.com.br)
 * @version 2013, 11 sep.
 */
public class GUIManager implements PropertyChangeListener {
    
    public static final List<ActionW> actionsButtons =
            Collections.synchronizedList(new ArrayList<ActionW>(Arrays.asList(
            new ActionW[] { ActionW.PAN, ActionW.WINLEVEL,
            ActionW.SCROLL_SERIES, ActionW.ZOOM, ActionW.ROTATION,
            ActionW.MEASURE, ActionW.CONTEXTMENU, ActionW.NO_ACTION })));

    public static final ActionW[] actionsScroll = {
        ActionW.SCROLL_SERIES, ActionW.ZOOM, ActionW.ROTATION,
        ActionW.NO_ACTION };
    
    protected MouseActions mouseActions = new MouseActions(null);
    

    /** Available measurements. */
    public static final ArrayList<Graphic> graphicList =
            new ArrayList<Graphic>();
    /** Next Graphic to use. */
    private Graphic nextGraphic;

    
    private HashMap<ActionW, Double> mouseSensitivities;
    
    private boolean reportVoiceOn = false;

    private GUIManager() { }
    
    public static GUIManager getInstance() {
        return GUIManagerHolder.INSTANCE;
    }

    public List<ViewerPlugin<?>> getAllViewerPlugins() {
        return UIManager.VIEWER_PLUGINS;
    }
    
    public void initDefaultListeners(DataExplorerModel model) {
        EventPublisher.getInstance().addPropertyChangeListener(
                ActionW.DRAW_MEASURE.cmd() + "|graphics.dragComplete|", this);
        
        //Publish DicomModel events without lock thread.
        if (model != null) {
            model.addPropertyChangeListener(new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    if (evt.getNewValue() instanceof SeriesEvent) {
                        SeriesEvent event = (SeriesEvent) evt.getNewValue();
                        if (SeriesEvent.Action.AddImage.equals(event.getActionCommand())) {
                            EventPublisher.getInstance().publish(
                                    new PropertyChangeEvent(evt.getSource(),
                                    "DicomModel.AddImage", evt.getOldValue(),
                                    evt.getNewValue()));
                        }
                    }
                }
            });
        }
    }

    /**
     * @return the nextGraphic
     */
    public Graphic getNextGraphic() {
        if (nextGraphic == null && !graphicList.isEmpty()) {
            nextGraphic = graphicList.get(0);
        }
        return nextGraphic;
    }

    /**
     * @param graphic the nextGraphic to set
     */
    public void setNextGraphic(Graphic graphic) {
        nextGraphic = graphic;
    }

    

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (ActionW.DRAW_MEASURE.cmd().equals(evt.getPropertyName())
                && !evt.getSource().equals(this)
                && evt.getNewValue() instanceof Graphic) {
            System.out.println(" calling setNextGraphics !");
                setNextGraphic((Graphic) evt.getNewValue());
        } else if ("graphics.dragComplete".equals(evt.getPropertyName())) {
//            if (measureSettings.isDrawOnlyOnce()) {
                setNextGraphic(graphicList.get(0));
                EventPublisher.getInstance().publish(new PropertyChangeEvent(
                        this, ActionW.DRAW_MEASURE.cmd(), null,
                        graphicList.get(0)));
//            }
        }
    }
    
    /**
     * @return Series selected on the last VIEWER_SELECTED event,
     * IF it is still marked as selected.
     */
    public MediaSeries getSelectedSeries() {
        ViewerPlugin plugin = getSelectedViewerPlugin();
        if (plugin instanceof AbstractViewsContainer) {
            return ((AbstractViewsContainer) plugin).getSelectedSeries();
        } else if (plugin instanceof ImageViewerPlugin) {
            DefaultView2d pane = ((ImageViewerPlugin) plugin).getSelectedImagePane();
            if (pane != null) {
                return pane.getSeries();
            }
        }
        return null;
    }
    
    private static class GUIManagerHolder {
        private static final GUIManager INSTANCE = new GUIManager();
    }
    
    private ViewerPlugin selectedViewer;
    
    public ViewerPlugin getSelectedViewerPlugin() {
        return selectedViewer;
    }
    
    public void setSelectedViewerPlugin(ViewerPlugin newSelectedViewer) {
        ViewerPlugin old = selectedViewer;
        if (old != null) {
            old.setSelected(false);
        }
        selectedViewer = newSelectedViewer;
        EventPublisher.getInstance().publish(new PropertyChangeEvent(
                this, EventPublisher.CONTAINER_SELECTED, old, newSelectedViewer));
    }
    
    public MouseActions getMouseActions() {
        return mouseActions;
    }
    
    public void setMouseActions(final MouseActions mActions) {
        mouseActions = mActions;
    }
    
    public List<ActionW> getMouseButtonActions() {
        List<ActionW> list = null;
        if (selectedViewer instanceof AbstractViewsContainer) {
            list = ((AbstractViewsContainer) selectedViewer).getMouseButtonActions();
        }
        if (list == null) {
            list = actionsButtons;
        }
        return list;
    }
    
    public ActionW[] getMouseScrollActions() {
        ActionW[] list = null;
        if (selectedViewer instanceof AbstractViewsContainer) {
            list = ((AbstractViewsContainer) selectedViewer).getMouseScrollActions();
        }
        if (list == null) {
            list = actionsScroll;
        }
        return list;
    }
    
    public ActionW getActionFromkeyEvent(int keyEvent, int modifier) {
        if (keyEvent != 0) {
            for (ActionW action : actionsButtons) {
                if (action.getKeyCode() == keyEvent && action.getModifier() == modifier) {
                    return action;
                }
            }
            if (modifier == 0) {
                if (keyEvent == KeyEvent.VK_D) {
                    for (Object obj : GUIManager.graphicList) {
                        if (obj instanceof LineGraphic) {
                            GUIManager.getInstance().setNextGraphic((LineGraphic) obj);
                            return ActionW.MEASURE;
                        }
                    }
                } else if (keyEvent == KeyEvent.VK_A) {
                    for (Object obj : GUIManager.graphicList) {
                        if (obj instanceof AngleToolGraphic) {
                            GUIManager.getInstance().setNextGraphic((AngleToolGraphic) obj);
                            return ActionW.MEASURE;
                        }
                    }
                }
            }
        }
        return null;
    }
    
    public DicomModel getActiveDicomModel() {
        DataExplorerView explorerplugin = UIManager.getExplorerplugin(DicomExplorer.NAME);
        return (DicomModel) explorerplugin.getDataExplorerModel();
    }
}
