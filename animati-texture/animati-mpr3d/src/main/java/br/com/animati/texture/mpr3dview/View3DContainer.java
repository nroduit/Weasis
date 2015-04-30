/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Inform??tica Ltda.
 * (http://www.animati.com.br)
 */
package br.com.animati.texture.mpr3dview;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.image.LayoutConstraints;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.SeriesComparator;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.editor.image.RotationToolBar;
import org.weasis.core.ui.editor.image.SynchData;
import org.weasis.core.ui.editor.image.SynchView;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.editor.image.ViewerToolBar;
import org.weasis.core.ui.editor.image.ZoomToolBar;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.util.Toolbar;
import org.weasis.core.ui.util.WtoolBar;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.viewer2d.LutToolBar;

import br.com.animati.texture.codec.ImageSeriesFactory;
import br.com.animati.texture.codec.TextureDicomSeries;
import br.com.animati.texture.mpr3dview.ViewTexture.ViewType;
import br.com.animati.texture.mpr3dview.api.ActionWA;
import br.com.animati.texture.mpr3dview.internal.Activator;
import br.com.animati.texture.mpr3dview.tool.ImageTool;
import br.com.animati.texturedicom.ControlAxes;
import br.com.animati.texturedicom.ImageSeries;

/**
 * 
 * 
 * @author Gabriela Carla Bauermann (gabriela@animati.com.br)
 * @version 2013, 16 Jul.
 */
public class View3DContainer extends ImageViewerPlugin<DicomImageElement> implements PropertyChangeListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(View3DContainer.class);

    public static final List<SynchView> SYNCH_LIST = Collections.synchronizedList(new ArrayList<SynchView>());

    static SynchView DEFAULT_MPR;
    static {
        SYNCH_LIST.add(SynchView.NONE);

        HashMap<String, Boolean> actions = new HashMap<String, Boolean>();
        actions.put(ActionW.RESET.cmd(), true);
        actions.put(ActionW.ZOOM.cmd(), true);
        actions.put(ActionW.WINDOW.cmd(), true);
        actions.put(ActionW.LEVEL.cmd(), true);
        actions.put(ActionW.PRESET.cmd(), true);
        actions.put(ActionW.LUT_SHAPE.cmd(), true);
        actions.put(ActionW.LUT.cmd(), true);
        actions.put(ActionW.INVERT_LUT.cmd(), true);
        actions.put(ActionW.FILTER.cmd(), true);
        actions.put(ActionWA.MIP_OPTION.cmd(), true);
        actions.put(ActionWA.MIP_DEPTH.cmd(), true);
        actions.put(ActionWA.SMOOTHING.cmd(), true);
        DEFAULT_MPR = new SynchView("MPR synch", "mpr", SynchData.Mode.Stack, //$NON-NLS-1$ //$NON-NLS-2$
            new ImageIcon(SynchView.class.getResource("/icon/22x22/tile.png")), actions); //$NON-NLS-1$

        SYNCH_LIST.add(DEFAULT_MPR);
    }

    public static final GridBagLayoutModel VIEWS_2x1_mpr = new GridBagLayoutModel(
        new LinkedHashMap<LayoutConstraints, Component>(3), "mpr", "MPR Views", new ImageIcon(
            View3DContainer.class.getResource("/icon/22x22/layout_mpr3.png")));
    static {
        LinkedHashMap<LayoutConstraints, Component> constraints = VIEWS_2x1_mpr.getConstraints();
        constraints.put(new LayoutConstraints(ViewTexture.class.getName(), 0, 0, 0, 1, 2, 0.5, 1.0,
            GridBagConstraints.CENTER, GridBagConstraints.BOTH), null);
        constraints.put(new LayoutConstraints(ViewTexture.class.getName(), 1, 1, 0, 1, 1, 0.5, 0.5,
            GridBagConstraints.CENTER, GridBagConstraints.BOTH), null);
        constraints.put(new LayoutConstraints(ViewTexture.class.getName(), 2, 1, 1, 1, 1, 0.5, 0.5,
            GridBagConstraints.CENTER, GridBagConstraints.BOTH), null);
    }

    public static final GridBagLayoutModel VIEWS_2x2_mpr = new GridBagLayoutModel(
        new LinkedHashMap<LayoutConstraints, Component>(4), "mpr4", "MPR-3D Views", new ImageIcon(
            ViewerPlugin.class.getResource("/icon/22x22/layout2x2.png")));
    static {
        LinkedHashMap<LayoutConstraints, Component> constraints = VIEWS_2x2_mpr.getConstraints();
        constraints.put(new LayoutConstraints(ViewTexture.class.getName(), 0, 0, 0, 1, 1, 0.5, 0.5,
            GridBagConstraints.CENTER, GridBagConstraints.BOTH), null);
        constraints.put(new LayoutConstraints(ViewTexture.class.getName(), 1, 1, 0, 1, 1, 0.5, 0.5,
            GridBagConstraints.CENTER, GridBagConstraints.BOTH), null);
        constraints.put(new LayoutConstraints(ViewTexture.class.getName(), 2, 0, 1, 1, 1, 0.5, 0.5,
            GridBagConstraints.CENTER, GridBagConstraints.BOTH), null);
        constraints.put(new LayoutConstraints(ViewTexture.class.getName(), 3, 1, 1, 1, 1, 0.5, 0.5,
            GridBagConstraints.CENTER, GridBagConstraints.BOTH), null);
    }

    public static final List<GridBagLayoutModel> LAYOUT_LIST = Collections
        .synchronizedList(new ArrayList<GridBagLayoutModel>());
    static {
        LAYOUT_LIST.add(VIEWS_2x1_mpr);
        LAYOUT_LIST.add(VIEWS_2x2_mpr);
    }

    public static final List<DockableTool> TOOLS = Collections.synchronizedList(new ArrayList<DockableTool>());
    public static final List<Toolbar> TOOLBARS = Collections.synchronizedList(new ArrayList<Toolbar>());
    private static volatile boolean INI_COMPONENTS = false;

    protected ControlAxes controlAxes;

    public View3DContainer() {
        this(View3DContainer.VIEWS_2x1_mpr, null, View3DFactory.NAME, View3DFactory.ICON, null);
    }

    public View3DContainer(GridBagLayoutModel layoutModel, String uid, String pluginName, Icon icon, String tooltips) {
        super(GUIManager.getInstance(), layoutModel, uid, pluginName, icon, tooltips);

        // Listen to texture factory events:
        ImageSeriesFactory.addPropertyChangeListener(this);

        // initActions();
        initStaticComponents();

        final ViewerToolBar toolBar = getViewerToolBar();

        if (toolBar != null) {
            String command = ActionW.CROSSHAIR.cmd();
            MouseActions mouseActions = eventManager.getMouseActions();
            String lastAction = mouseActions.getAction(MouseActions.LEFT);
            if (!command.equals(lastAction)) {
                mouseActions.setAction(MouseActions.LEFT, command);
                toolBar.changeButtonState(MouseActions.LEFT, command);
            }
        }
    }

    @Override
    public void addSeries(MediaSeries series) {
        if (series == null) {
            return;
        }
        try {
            if (Activator.useHardwareAcceleration) {
                ImageSeriesFactory factory = new ImageSeriesFactory();
                try {
                    Comparator  sort = null;
                    ActionState action = eventManager.getAction(ActionW.SORTSTACK);
                    if (action instanceof ComboItemListener && action.isActionEnabled()) {                       
                         sort = (Comparator) ((ComboItemListener)action).getSelectedItem();
                        if(sort != null){
                            ActionState inverse = eventManager.getAction(ActionW.INVERSESTACK);
                            if (inverse instanceof ToggleButtonListener && action.isActionEnabled() && sort instanceof SeriesComparator) {
                                if(((ToggleButtonListener)inverse).isSelected()){
                                    sort = ((SeriesComparator) sort).getReversOrderComparator();
                                }
                            }
                        }
                    }
                    
                    
                    TextureDicomSeries imSeries = factory.createImageSeries(series, sort, false);
                    controlAxes = new ControlAxes(imSeries);
                    double[] op = imSeries.getOriginalSeriesOrientationPatient();
                    setControlAxesBaseOrientation(imSeries.getSeries(), op);

                    for (ViewCanvas<DicomImageElement> view : view2ds) {
                        if (view instanceof ViewTexture) {
                            ViewTexture vt = (ViewTexture) view;
                            vt.setSeries(imSeries);
                            vt.applyProfile(vt.getViewType(), controlAxes);
                            // BugFix:
                            vt.forceResize();
                        } else {
                            LOGGER.error("No ViewTextures...");
                        }
                    }
                    // setSelectedView(view2ds.get(0));

                } catch (Exception ex) {
                    LOGGER.error("Exception ao formar texture para o MPR! " + ex);
                    ex.printStackTrace();
                    // Ends up here if:
                    // Bad Video card driver.
                    // Video card memory full.
                    throw ex; // Expected to be javax.media.opengl.GLException
                }
            } else {
                showErrorMessage();
            }
        } catch (Exception ex) {
            close();
            showErrorMessage();
            return;
        }

        setPluginName((String) series.getTagValue(TagW.PatientName));
        setSelected(true);
    }
    
    @Override
    protected synchronized void setLayoutModel(GridBagLayoutModel layoutModel) {
        TextureDicomSeries series = null;
        if (view2ds.size() > 0 && view2ds.get(0) instanceof ViewTexture) {
            series = ((ViewTexture) view2ds.get(0)).getSeriesObject();
        }
        super.setLayoutModel(layoutModel);

        final LinkedHashMap<LayoutConstraints, Component> elements = getLayoutModel().getConstraints();
        Iterator<Entry<LayoutConstraints, Component>> enumVal = elements.entrySet().iterator();

        if (series != null) {
            controlAxes = new ControlAxes(series);
            double[] op = series.getOriginalSeriesOrientationPatient();
            setControlAxesBaseOrientation(series.getSeries(), op);
        }

        while (enumVal.hasNext()) {
            Entry<LayoutConstraints, Component> next = enumVal.next();
            LayoutConstraints key = next.getKey();
            Component c = next.getValue();

            if (c instanceof ViewTexture) {
                ViewTexture vt = (ViewTexture) c;
                ViewType viewType;
                switch (key.getLayoutID()) {
                    case 0:
                        viewType = ViewType.AXIAL;
                        break;
                    case 1:
                        viewType = ViewType.CORONAL;
                        break;
                    case 2:
                        viewType = ViewType.SAGITTAL;
                        break;
                    default:
                        viewType = ViewType.VOLUME3D;
                        break;
                }
                vt.setViewType(viewType);
                if (series != null) {
                    vt.setSeries(series);
                }
                vt.applyProfile(viewType, controlAxes);

                // BugFix:
                vt.forceResize();
            }
        }
        if (series != null) {
            eventManager.updateComponentsListener(selectedImagePane);
        }
    }

    public void updateViewersWithSeries(final MediaSeries mediaSeries, final PropertyChangeEvent event) {
        if ("texture.orientationPatient".equals(event.getPropertyName()) && controlAxes != null) {
            setControlAxesBaseOrientation(mediaSeries, (double[]) event.getNewValue());
            // Reset to Axial
            String[] resets = new String[] { "resetToAxial" };
            EventPublisher.getInstance().publish(
                new PropertyChangeEvent(this, EventPublisher.VIEWER_DO_ACTION + ActionW.RESET.cmd(), null, resets));
        }

        for (ViewCanvas<DicomImageElement> gridElement : getImagePanels()) {
            if (mediaSeries != null
                    && mediaSeries.equals(gridElement.getSeries())) {
                gridElement.propertyChange(event);
            }
        } 
    }

    protected void setControlAxesBaseOrientation(MediaSeries mediaSeries, double[] op) {

        if (controlAxes != null) {
            // set to null!
            controlAxes.unsetBaseOrientation();
        }

        if (controlAxes != null && controlAxes.getImageSeries() != null
            && controlAxes.getImageSeries() instanceof TextureDicomSeries) {
            TextureDicomSeries texture = (TextureDicomSeries) controlAxes.getImageSeries();
            MediaSeries series = texture.getSeries();
            if (mediaSeries != null && mediaSeries.equals(series)) {
                if (op != null && op.length == 6) {
                    if (!texture.hasNegativeSliceSpacing()) {
                        controlAxes.setBaseOrientation(op[0], op[1], op[2], op[3], op[4], op[5]);
                    }
                }
            }
        }
    }

    /**
     * Sets the global controlAxes object according to texture and profile. Sets to null if profile does not requires
     * one.
     * 
     * @param texture
     * @param id
     */
    protected void setControlAxes(ImageSeries texture, String layoutId) {
        if (texture != null && layoutId.startsWith("mpr")) {
            controlAxes = new ControlAxes(texture);
            setControlAxesBaseOrientation(((TextureDicomSeries) texture).getSeries(),
                ((TextureDicomSeries) texture).getOriginalSeriesOrientationPatient());
        } else if (controlAxes != null) {
            controlAxes.setControlledCanvas(0, null);
            controlAxes.setControlledCanvas(1, null);
            controlAxes.setControlledCanvas(2, null);
            controlAxes.watchingCanvases.clear();
            controlAxes = null;
        }
    }

    private void showErrorMessage() {
        View3DFactory.showHANotAvailableMsg(this);
    }

     protected void removeContent(final ObservableEvent event) {
        Object newVal = event.getNewValue();
        // Only one series on this container...
        if (newVal instanceof DicomSeries) {
            DicomSeries dicomSeries = (DicomSeries) newVal;
            for (ViewCanvas<DicomImageElement> view : getImagePanels()) {
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
                        for (ViewCanvas<DicomImageElement> view : getImagePanels()) {
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


    protected void handleLoadCompleteEvent(PropertyChangeEvent event) {
        MediaSeries ms = (MediaSeries) event.getNewValue();

        Component firstView = getImagePanels().get(0).getJComponent();
        if (firstView instanceof ViewTexture) {
            final ViewTexture view = (ViewTexture) firstView;

            TextureDicomSeries ser = view.getSeriesObject();

            if (ser != null) {
                // Must set after loadComplete
                double[] op = ser.getOriginalSeriesOrientationPatient();
                if (op != null && op.length == 6) {
                    updateViewersWithSeries(ms, new PropertyChangeEvent(this,
                            "texture.orientationPatient", null, op));

                    // BUGfix:
                    for (ViewCanvas gridElement : getImagePanels()) {
                        if (gridElement instanceof ViewTexture) {
                            ((ViewTexture) gridElement).forceResize();
                        }
                    }
                }
            }
        }
    }

    
    @Override
    public JMenu fillSelectedPluginMenu(JMenu menuRoot) {
        if (menuRoot != null) {
            menuRoot.removeAll();
            if (eventManager instanceof GUIManager) {
                GUIManager manager = (GUIManager) eventManager;

                final JMenuItem refresh = new JMenuItem("Refresh texture");
                refresh.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        ImageViewerPlugin<DicomImageElement> container = eventManager.getSelectedView2dContainer();
                        ViewCanvas<DicomImageElement> view = container.getSelectedImagePane();
                        if (view instanceof ViewTexture) {
                            ((ViewTexture) view).refreshTexture();
                        }
                    }
                });
                menuRoot.add(refresh);
                // menu.add(getComparatorMenu());
                menuRoot.add(new JSeparator());
                JMVUtils.addItemToMenu(menuRoot, manager.getPresetMenu(null));
                JMVUtils.addItemToMenu(menuRoot, manager.getLutShapeMenu(null));
                JMVUtils.addItemToMenu(menuRoot, manager.getLutMenu(null));
                JMVUtils.addItemToMenu(menuRoot, manager.getLutInverseMenu(null));
                JMVUtils.addItemToMenu(menuRoot, manager.getFilterMenu(null));
                menuRoot.add(new JSeparator());
                JMVUtils.addItemToMenu(menuRoot, manager.getZoomMenu(null));
                JMVUtils.addItemToMenu(menuRoot, manager.getOrientationMenu(null));
                JMVUtils.addItemToMenu(menuRoot, manager.getSortStackMenu(null));
                menuRoot.add(new JSeparator());
                menuRoot.add(manager.getResetMenu(null));
            }
        }
        return menuRoot;
    }

    @Override
    public void close() {
        super.close();
        View3DFactory.closeSeriesViewer(this);

        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {
                for (ViewCanvas v : view2ds) {
                    resetMaximizedSelectedImagePane(v);
                    v.disposeView();
                }
            }
        });

    }

    @Override
    public void propertyChange(final PropertyChangeEvent event) {
        String command = event.getPropertyName();
        if (ImageSeriesFactory.TEXTURE_REPLACED.equals(command)) {
            if (event.getNewValue() instanceof TextureDicomSeries) {
                TextureDicomSeries texture = (TextureDicomSeries) event.getNewValue();
                ViewCanvas<DicomImageElement> selected = getSelectedImagePane();
                if (selected != null && texture.getSeries().equals(selected.getSeries())
                    && !texture.equals(((ViewTexture) selected).getParentImageSeries())) {
                    try {
                        addSeries(texture.getSeries());
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
        
        if (command.startsWith("texture") && event.getNewValue() instanceof MediaSeries) {
            MediaSeries ms = (MediaSeries) event.getNewValue();
            updateViewersWithSeries(ms, event);
            if (ImageSeriesFactory.TEXTURE_LOAD_COMPLETE.equals(command)) {
               handleLoadCompleteEvent(event);
            }
        } else {
            if (command.startsWith(EventPublisher.VIEWER_DO_ACTION)
                    && selectedImagePane != null) {
                selectedImagePane.propertyChange(event);
            } else {
                if (event instanceof ObservableEvent) {
                    handlesObservEvent(event);
                }
            }
        }
    }
    
    private void handlesObservEvent(final PropertyChangeEvent event){
        ObservableEvent obEvt = (ObservableEvent) event;
        ObservableEvent.BasicAction action = obEvt.getActionCommand();
        if (ObservableEvent.BasicAction.Remove.equals(action)) {
            removeContent(obEvt);
        } 
        //TODO: threat AddImage events or not? MPR doesn't.
//        else if (event.getNewValue() instanceof SeriesEvent) {
//            SeriesEvent seriesEvt = (SeriesEvent) event.getNewValue();
//            if (SeriesEvent.Action.AddImage.equals(seriesEvt.getActionCommand())) {
//                
//            }
//        }
    }

    /**
     * Overriden because this Countainer can be closed before the first call to setSelected.
     * 
     * If that happens, all toolbars get visible and viewer not. Need a way out.
     * 
     * @param selected
     */
    @Override
    public void setSelected(boolean selected) {
        GUIManager eventManager = GUIManager.getInstance();
        if (selected) {
            eventManager.setSelectedView2dContainer(this);

            // Send event to select the related patient in Dicom Explorer.
            DataExplorerView dicomView = UIManager.getExplorerplugin(DicomExplorer.NAME);
            if (dicomView != null && dicomView.getDataExplorerModel() instanceof DicomModel) {
                dicomView.getDataExplorerModel().firePropertyChange(
                    new ObservableEvent(ObservableEvent.BasicAction.Select, this, null, getGroupID()));
            }

        } else {
            eventManager.setSelectedView2dContainer(null);
        }
    }

    @Override
    public void addSeriesList(List<MediaSeries<DicomImageElement>> seriesList, boolean removeOldSeries) {
        if (seriesList != null && seriesList.size() > 0) {
            addSeries(seriesList.get(0));
        }
    }

    @Override
    public void selectLayoutPositionForAddingSeries(List<MediaSeries<DicomImageElement>> seriesList) {
        // Do it in addSeries()
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

            GUIManager evtMg = GUIManager.getInstance();

            TOOLBARS.add(new ViewerToolBar<DicomImageElement>(evtMg, evtMg.getMouseActions().getActiveButtons(),
                BundleTools.SYSTEM_PREFERENCES, 10));

            TOOLBARS.add(new MeasureToolBar(evtMg, 11));
            TOOLBARS.add(new ZoomToolBar(evtMg, 20, false));
            TOOLBARS.add(new RotationToolBar(evtMg, 30));
            TOOLBARS.add(new LutToolBar(evtMg, 40));

            MeasureTool tool = new MeasureTool(evtMg);
            TOOLS.add(tool);

            TOOLS.add(new ImageTool());
        }
    }

    @Override
    public boolean isViewType(Class defaultClass, String type) {
        if (defaultClass != null) {
            try {
                Class clazz = Class.forName(type);
                return defaultClass.isAssignableFrom(clazz);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    @Override
    public int getViewTypeNumber(GridBagLayoutModel layout, Class defaultClass) {
        return View3DFactory.getViewTypeNumber(layout, defaultClass);
    }

    @Override
    public ViewCanvas<DicomImageElement> createDefaultView(String classType) {
        try {
            return new ViewTexture(eventManager, null);
        } catch (Exception e) {
            LOGGER.error("Cannot create a 3D view: {}", e.getMessage());
        }
        return null;
    }

    @Override
    public Component createUIcomponent(String clazz) {
        if (isViewType(ViewTexture.class, clazz)) {
            return createDefaultView(clazz).getJComponent();
        }

        try {
            // FIXME use classloader.loadClass or injection
            Class cl = Class.forName(clazz);
            JComponent component = (JComponent) cl.newInstance();
            if (component instanceof SeriesViewerListener) {
                eventManager.addSeriesViewerListener((SeriesViewerListener) component);
            }
            return component;

        } catch (InstantiationException e1) {
            e1.printStackTrace();
        } catch (IllegalAccessException e1) {
            e1.printStackTrace();
        }

        catch (ClassNotFoundException e1) {
            e1.printStackTrace();
        } catch (ClassCastException e1) {
            e1.printStackTrace();
        }
        return null;
    }

    @Override
    public WtoolBar getStatusBar() {
        return null;
    }

    @Override
    public List<Action> getExportActions() {
        //Doesn't export yet.
        return null;
    }

    @Override
    public List<Action> getPrintActions() {
        //Doesn't have anything printable yet.
        return null;
    }

    @Override
    public List<SynchView> getSynchList() {
        return SYNCH_LIST;
    }

    @Override
    public List<GridBagLayoutModel> getLayoutList() {
        return LAYOUT_LIST;
    }
}
