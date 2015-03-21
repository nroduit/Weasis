/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Inform??tica Ltda.
 * (http://www.animati.com.br)
 */
package br.com.animati.texture.mpr3dview;

import br.com.animati.texture.codec.ImageSeriesFactory;
import br.com.animati.texture.codec.TextureDicomSeries;
import br.com.animati.texture.mpr3dview.api.AbstractViewsContainer;
import br.com.animati.texture.mpr3dview.api.ActionDataModel;
import br.com.animati.texture.mpr3dview.api.GridElement;
import br.com.animati.texture.mpr3dview.tool.GraphicsToolbar;
import br.com.animati.texture.mpr3dview.tool.ImageTool;
import br.com.animati.texture.mpr3dview.tool.View3dToolBar;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import javax.swing.ImageIcon;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.image.LayoutConstraints;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.graphic.Graphic;
import org.weasis.core.ui.util.Toolbar;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.SortSeriesStack;
import org.weasis.dicom.explorer.DicomModel;

/**
 * 
 * 
 * @author Gabriela Carla Bauermann (gabriela@animati.com.br)
 * @version 2013, 16 Jul.
 */
public class View3DContainer extends AbstractViewsContainer {
    
    public static final GridBagLayoutModel VIEWS_2x2_mpr = new GridBagLayoutModel(
        new LinkedHashMap<LayoutConstraints, Component>(4), "mpr4", "MPR 4 Views",
        new ImageIcon(ViewerPlugin.class.getResource("/icon/22x22/layout2x2.png")));
    static {
        LinkedHashMap<LayoutConstraints, Component> constraints = VIEWS_2x2_mpr.getConstraints();
        constraints.put(new LayoutConstraints("MPR.AXIAL", 0, 0, 0, 1, 1, 0.5, 0.5,
            GridBagConstraints.CENTER, GridBagConstraints.BOTH), null);
        constraints.put(new LayoutConstraints("MPR.CORONAL", 1, 1, 0, 1, 1, 0.5, 0.5,
            GridBagConstraints.CENTER, GridBagConstraints.BOTH), null);
        constraints.put(new LayoutConstraints("MPR.SAGITTAL", 2, 0, 1, 1, 1, 0.5, 0.5,
            GridBagConstraints.CENTER, GridBagConstraints.BOTH), null);
        constraints.put(new LayoutConstraints("MPR.3D", 3, 1, 1, 1, 1, 0.5, 0.5,
            GridBagConstraints.CENTER, GridBagConstraints.BOTH), null);
    }
    
    public static final GridBagLayoutModel VIEWS_2x1_mpr = new GridBagLayoutModel(
        new LinkedHashMap<LayoutConstraints, Component>(3), "mpr", "MPR 3 Views",
        new ImageIcon(View3DContainer.class.getResource("/icon/22x22/layout_mpr3.png")));
    static {
        LinkedHashMap<LayoutConstraints, Component> constraints = VIEWS_2x1_mpr.getConstraints();
        constraints.put(new LayoutConstraints("MPR.AXIAL", 0, 0, 0, 1, 2, 0.5, 1.0,
            GridBagConstraints.CENTER, GridBagConstraints.BOTH), null);
        constraints.put(new LayoutConstraints("MPR.CORONAL", 1, 1, 0, 1, 1, 0.5, 0.5,
            GridBagConstraints.CENTER, GridBagConstraints.BOTH), null);
        constraints.put(new LayoutConstraints("MPR.SAGITTAL", 2, 1, 1, 1, 1, 0.5, 0.5,
            GridBagConstraints.CENTER, GridBagConstraints.BOTH), null);
    }
    
    public static final List<ActionW> actionsButs =
            Collections.synchronizedList(new ArrayList<ActionW>(Arrays.asList(
            new ActionW[] {ActionW.CROSSHAIR, ActionW.PAN, ActionW.WINLEVEL,
            ActionW.SCROLL_SERIES, ActionW.ZOOM, ActionW.ROTATION,
            ActionW.MEASURE, //ActionW.CONTEXTMENU,
            ActionW.NO_ACTION})));
    
    public static final List<DockableTool> TOOLS =
            Collections.synchronizedList(new ArrayList<DockableTool>());
    public static final List<Toolbar> TOOLBARS =
            Collections.synchronizedList(new ArrayList<Toolbar>());
    private static volatile boolean INI_COMPONENTS = false;
    

    /**
     * Inits a container with a layout (or null to use default).
     *
     * @param layout First layout.
     */
    public View3DContainer(GridBagLayoutModel layout) {
        super(View3DFactory.NAME, View3DFactory.ICON, layout);
        
        //Listen to texture factory events:
        ImageSeriesFactory.addPropertyChangeListener(this);
        
        initActions();
        initStaticComponents();
        
        //MouseAction
        MouseActions mouseActions = GUIManager.getInstance().getMouseActions();
        mouseActions.setLeft(ActionW.CROSSHAIR.cmd());
        setMouseActions(mouseActions);
    }

    private void initActions() {
        actions.add(new ActionDataModel(ActionW.LAYOUT, getLayoutList(),
                getOriginalLayoutModel()) {        
            @Override
            public Object getActionValue() {
                return getOriginalLayoutModel();
            }
            @Override
            public Object getActionData() {
                return getLayoutList();
            }
        });
    }
    
    @Override
    public void addSeries(MediaSeries series) {
        if (series == null) {
            return;
        }
        try {
            grid.addSeries(series, SortSeriesStack.instanceNumber);
        } catch (Exception ex) {
            close();
            showErrorMessage();
            return;
        }

        setPluginName((String) series.getTagValue(TagW.PatientName));
        setSelected(true);        
    }
    
    private void showErrorMessage() {
        View3DFactory.showHANotAvailableMsg(this);
    }

    @Override
    protected void removeContent(final ObservableEvent event) {
        Object newVal = event.getNewValue();
        //Only one series on this container...
        if (newVal instanceof DicomSeries) {
            DicomSeries dicomSeries = (DicomSeries) newVal;
            for (GridElement view : grid.getViews()) {
                MediaSeries<DicomImageElement> ser = view.getSeries();
                if (dicomSeries.equals(ser)) {
                    close();
                }
            }
        } else if (newVal instanceof MediaSeriesGroup) {
            MediaSeriesGroup group = (MediaSeriesGroup) newVal;
            // Patient Group
            if (TagW.PatientPseudoUID.equals(group.getTagID())) {
                if (group.equals(getGroupID())) {
                    close();
                }
            // Study Group
            } else if (TagW.StudyInstanceUID.equals(group.getTagID())) {
                if (event.getSource() instanceof DicomModel) {
                    DicomModel model = (DicomModel) event.getSource();
                    for (MediaSeriesGroup removedSerie : model.getChildren(group)) {
                        for (GridElement view : grid.getViews()) {
                            MediaSeries<DicomImageElement> ser = view.getSeries();
                            if (removedSerie.equals(ser)) {
                                close();
                            }
                        }
                    }
                }
            }
        }
    }
    
    @Override
    protected void handleLoadCompleteEvent(PropertyChangeEvent event) {
        MediaSeries ms = (MediaSeries) event.getNewValue();
        
        Component firstView = grid.getViews().get(0).getComponent();
        if (firstView instanceof ViewTexture) {
            final ViewTexture view = (ViewTexture) firstView;
            
            TextureDicomSeries ser = view.getSeriesObject();

            if (ser != null) {
                //Must set after loadComplete
                double[] op = ser.getOriginalSeriesOrientationPatient();
                if (op != null && op.length == 6) {
                    grid.updateViewersWithSeries(ms,
                            new PropertyChangeEvent(
                            this, "texture.orientationPatient", null, op));

                    //BUGfix:
                    for (GridElement gridElement : grid.getViews()) {
                        if (gridElement.getComponent() instanceof ViewTexture) {
                            ((ViewTexture) gridElement.getComponent()).forceResize();
                        }
                    }
                }
            }
        }
    }
    
    @Override
    public List<GridBagLayoutModel> getLayoutList() {
        List<GridBagLayoutModel> list = new ArrayList<GridBagLayoutModel>();
        list.add(VIEWS_2x1_mpr);
        list.add(VIEWS_2x2_mpr);
        
        return list;
    }

    @Override
    public GridBagLayoutModel getOriginalLayoutModel() {
        GridBagLayoutModel layoutModel = grid.getLayoutModel();
        if (layoutModel.getId().equals(VIEWS_2x2_mpr.getId())) {
            return VIEWS_2x2_mpr;
        } else if (layoutModel.getId().equals(VIEWS_2x1_mpr.getId())) {
            return VIEWS_2x1_mpr;
        }
        return ImageViewerPlugin.VIEWS_1x1;

    }
    
    @Override 
    public JMenu fillSelectedPluginMenu(JMenu menu) {
        if (menu != null) {
            menu.removeAll();
            menu.setText(View3DFactory.NAME);
            
            final JMenuItem refresh = new JMenuItem("Refresh texture");
            refresh.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    EventPublisher.getInstance().publish(
                            new PropertyChangeEvent(refresh,
                             EventPublisher.VIEWER_DO_ACTION + "RefreshTexture", null, null));
                }
            });
            menu.add(refresh);
            menu.add(getComparatorMenu());
        }
        return menu; 
    }

    public List<ActionW> getMouseButtonActions() {
        return actionsButs;
    }


    public ActionW[] getMouseScrollActions() {
        return null; //GUIManager will use defaults.
    }
    
    @Override
    public void propertyChange(final PropertyChangeEvent event) {
        String command = event.getPropertyName();
        if ("texture.replaced".equals(command)) {
            if (event.getNewValue() instanceof TextureDicomSeries) {
                TextureDicomSeries texture
                        = (TextureDicomSeries) event.getNewValue();
                GridElement selectedView = grid.getSelectedView();
                if (selectedView != null && texture.getSeries().equals(
                        selectedView.getSeries())
                        && !texture.equals(((ViewTexture) selectedView.getComponent())
                                .getParentImageSeries())) {
                    try {
                        grid.addSeries(texture.getSeries(),
                                texture.getSeriesComparator());
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        } else if ((EventPublisher.VIEWER_DO_ACTION
                + ActionW.SORTSTACK.cmd()).equals(command)
                || (EventPublisher.VIEWER_DO_ACTION
                + ActionW.INVERSESTACK.cmd()).equals(command)) {
            //Must be done by the grid
            grid.changeSeriesComparator(grid.getSelectedView(), event.getNewValue());
        } else {
            super.propertyChange(event);
        }
    }
    
    /**
     * Overriden because this Countainer can be closed before the
     * first call to setSelected.
     * 
     * If that happens, all toolbars get visible and viewer not. Need a
     * way out.
     * 
     * @param selected 
     */
    @Override
    public void setSelected(boolean selected) {
        if (GUIManager.getInstance().getAllViewerPlugins().contains(this)) {
            super.setSelected(selected);
        }
    }
    
    
    /* ------- Static components related. ------------------------- */

    @Override
    public List<Toolbar> getToolBar() {
        return TOOLBARS;
    }

    @Override
    public List<DockableTool> getToolPanel() {
        return TOOLS;
    }

    private void initStaticComponents() {
        if (!INI_COMPONENTS) {
            INI_COMPONENTS = true;

            TOOLS.add(new ImageTool());
             
            View3dToolBar view3dToolBar = new View3dToolBar(
                    GUIManager.getInstance().getMouseActions(),
                    GUIManager.getInstance().getMouseActions().getActiveButtons(), 1);
            EventPublisher.getInstance().addPropertyChangeListener(
                    view3dToolBar.getListenerRegex(), view3dToolBar);
            TOOLBARS.add(view3dToolBar);
            
            GUIManager.getInstance().initDefaultListeners(
                    GUIManager.getInstance().getActiveDicomModel());
            
            Graphic[] graphics = GUIManager.graphicList.toArray(new Graphic[GUIManager.graphicList.size()]);
            TOOLBARS.add(new GraphicsToolbar(10, graphics));
        }
    }

}        
