/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.viewer2d;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.Insertable.Type;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.SeriesEvent;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.LangUtil;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.editor.image.RotationToolBar;
import org.weasis.core.ui.editor.image.SynchData;
import org.weasis.core.ui.editor.image.SynchView;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerToolBar;
import org.weasis.core.ui.editor.image.ZoomToolBar;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.editor.image.dockable.MiniTool;
import org.weasis.core.ui.util.ColorLayerUI;
import org.weasis.core.ui.util.DefaultAction;
import org.weasis.core.ui.util.PrintDialog;
import org.weasis.core.ui.util.Toolbar;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.KOSpecialElement;
import org.weasis.dicom.codec.PRSpecialElement;
import org.weasis.dicom.codec.PresentationStateReader;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.ExportToolBar;
import org.weasis.dicom.explorer.ImportToolBar;
import org.weasis.dicom.explorer.print.DicomPrintDialog;
import org.weasis.dicom.viewer2d.dockable.DisplayTool;
import org.weasis.dicom.viewer2d.dockable.ImageTool;

@SuppressWarnings("serial")
public class View2dContainer extends ImageViewerPlugin<DicomImageElement> implements PropertyChangeListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(View2dContainer.class);

    // Unmodifiable list of the default synchronization elements
    public static final List<SynchView> DEFAULT_SYNCH_LIST =
        Arrays.asList(SynchView.NONE, SynchView.DEFAULT_STACK, SynchView.DEFAULT_TILE);

    public static final GridBagLayoutModel VIEWS_2x1_r1xc2_dump =
        new GridBagLayoutModel(View2dContainer.class.getResourceAsStream("/config/layoutModel.xml"), "layout_dump", //$NON-NLS-1$ //$NON-NLS-2$
            Messages.getString("View2dContainer.layout_dump")); //$NON-NLS-1$
    public static final GridBagLayoutModel VIEWS_2x1_r1xc2_histo =
        new GridBagLayoutModel(View2dContainer.class.getResourceAsStream("/config/layoutModelHisto.xml"), "layout_histo", //$NON-NLS-1$ //$NON-NLS-2$
            Messages.getString("View2dContainer.histogram")); //$NON-NLS-1$
    // Unmodifiable list of the default layout elements
    public static final List<GridBagLayoutModel> DEFAULT_LAYOUT_LIST = Arrays.asList(VIEWS_1x1, VIEWS_1x2, VIEWS_2x1,
        VIEWS_2x2_f2, VIEWS_2_f1x2, VIEWS_2x1_r1xc2_dump, VIEWS_2x1_r1xc2_histo, VIEWS_2x2);

    // Static tools shared by all the View2dContainer instances, tools are registered when a container is selected
    // Do not initialize tools in a static block (order initialization issue with eventManager), use instead a lazy
    // initialization with a method.
    public static final List<Toolbar> TOOLBARS = Collections.synchronizedList(new ArrayList<Toolbar>());
    public static final List<DockableTool> TOOLS = Collections.synchronizedList(new ArrayList<DockableTool>());
    private static volatile boolean initComponents = false;

    public View2dContainer() {
        this(VIEWS_1x1, null, View2dFactory.NAME, MimeInspector.dicomIcon, null);
    }

    public View2dContainer(GridBagLayoutModel layoutModel, String uid, String pluginName, Icon icon, String tooltips) {
        super(EventManager.getInstance(), layoutModel, uid, pluginName, icon, tooltips);
        setSynchView(SynchView.DEFAULT_STACK);
        addComponentListener(new ComponentAdapter() {

            @Override
            public void componentResized(ComponentEvent e) {
                ImageViewerPlugin<DicomImageElement> container =
                    EventManager.getInstance().getSelectedView2dContainer();
                if (container == View2dContainer.this) {
                    Optional<ComboItemListener> layoutAction =
                        EventManager.getInstance().getAction(ActionW.LAYOUT, ComboItemListener.class);
                    layoutAction.ifPresent(a -> a.setDataListWithoutTriggerAction(getLayoutList().toArray()));
                }
            }
        });

        if (!initComponents) {
            initComponents = true;

            // Add standard toolbars
            final BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
            EventManager evtMg = EventManager.getInstance();

            String bundleName = context.getBundle().getSymbolicName();
            String componentName = InsertableUtil.getCName(this.getClass());
            String key = "enable"; //$NON-NLS-1$

            if (InsertableUtil.getBooleanProperty(BundleTools.SYSTEM_PREFERENCES, bundleName, componentName,
                InsertableUtil.getCName(ImportToolBar.class), key, true)) {
                Optional<Toolbar> b =
                    UIManager.EXPLORER_PLUGIN_TOOLBARS.stream().filter(t -> t instanceof ImportToolBar).findFirst();
                b.ifPresent(TOOLBARS::add);
            }
            if (InsertableUtil.getBooleanProperty(BundleTools.SYSTEM_PREFERENCES, bundleName, componentName,
                InsertableUtil.getCName(ExportToolBar.class), key, true)) {
                Optional<Toolbar> b =
                    UIManager.EXPLORER_PLUGIN_TOOLBARS.stream().filter(t -> t instanceof ExportToolBar).findFirst();
                b.ifPresent(TOOLBARS::add);
            }
            if (InsertableUtil.getBooleanProperty(BundleTools.SYSTEM_PREFERENCES, bundleName, componentName,
                InsertableUtil.getCName(ViewerToolBar.class), key, true)) {
                TOOLBARS.add(new ViewerToolBar<>(evtMg, evtMg.getMouseActions().getActiveButtons(),
                    BundleTools.SYSTEM_PREFERENCES, 10));
            }
            if (InsertableUtil.getBooleanProperty(BundleTools.SYSTEM_PREFERENCES, bundleName, componentName,
                InsertableUtil.getCName(MeasureToolBar.class), key, true)) {
                TOOLBARS.add(new MeasureToolBar(evtMg, 11));
            }
            if (InsertableUtil.getBooleanProperty(BundleTools.SYSTEM_PREFERENCES, bundleName, componentName,
                InsertableUtil.getCName(ZoomToolBar.class), key, true)) {
                TOOLBARS.add(new ZoomToolBar(evtMg, 20, true));
            }
            if (InsertableUtil.getBooleanProperty(BundleTools.SYSTEM_PREFERENCES, bundleName, componentName,
                InsertableUtil.getCName(RotationToolBar.class), key, true)) {
                TOOLBARS.add(new RotationToolBar(evtMg, 30));
            }
            if (InsertableUtil.getBooleanProperty(BundleTools.SYSTEM_PREFERENCES, bundleName, componentName,
                InsertableUtil.getCName(DcmHeaderToolBar.class), key, true)) {
                TOOLBARS.add(new DcmHeaderToolBar(evtMg, 35));
            }
            if (InsertableUtil.getBooleanProperty(BundleTools.SYSTEM_PREFERENCES, bundleName, componentName,
                InsertableUtil.getCName(LutToolBar.class), key, true)) {
                TOOLBARS.add(new LutToolBar(evtMg, 40));
            }
            if (InsertableUtil.getBooleanProperty(BundleTools.SYSTEM_PREFERENCES, bundleName, componentName,
                InsertableUtil.getCName(Basic3DToolBar.class), key, true)) {
                TOOLBARS.add(new Basic3DToolBar<DicomImageElement>(50));
            }
            if (InsertableUtil.getBooleanProperty(BundleTools.SYSTEM_PREFERENCES, bundleName, componentName,
                InsertableUtil.getCName(CineToolBar.class), key, true)) {
                TOOLBARS.add(new CineToolBar<DicomImageElement>(80));
            }
            if (InsertableUtil.getBooleanProperty(BundleTools.SYSTEM_PREFERENCES, bundleName, componentName,
                InsertableUtil.getCName(KeyObjectToolBar.class), key, true)) {
                TOOLBARS.add(new KeyObjectToolBar(90));
            }

            PluginTool tool = null;

            if (InsertableUtil.getBooleanProperty(BundleTools.SYSTEM_PREFERENCES, bundleName, componentName,
                InsertableUtil.getCName(MiniTool.class), key, true)) {
                tool = new MiniTool(MiniTool.BUTTON_NAME) {

                    @Override
                    public SliderChangeListener[] getActions() {
                        ArrayList<SliderChangeListener> listeners = new ArrayList<>(3);
                        ActionState seqAction = eventManager.getAction(ActionW.SCROLL_SERIES);
                        if (seqAction instanceof SliderChangeListener) {
                            listeners.add((SliderChangeListener) seqAction);
                        }
                        ActionState zoomAction = eventManager.getAction(ActionW.ZOOM);
                        if (zoomAction instanceof SliderChangeListener) {
                            listeners.add((SliderChangeListener) zoomAction);
                        }
                        ActionState rotateAction = eventManager.getAction(ActionW.ROTATION);
                        if (rotateAction instanceof SliderChangeListener) {
                            listeners.add((SliderChangeListener) rotateAction);
                        }
                        return listeners.toArray(new SliderChangeListener[listeners.size()]);
                    }
                };
                TOOLS.add(tool);
            }

            if (InsertableUtil.getBooleanProperty(BundleTools.SYSTEM_PREFERENCES, bundleName, componentName,
                InsertableUtil.getCName(ImageTool.class), key, true)) {
                tool = new ImageTool(ImageTool.BUTTON_NAME);
                TOOLS.add(tool);
            }

            if (InsertableUtil.getBooleanProperty(BundleTools.SYSTEM_PREFERENCES, bundleName, componentName,
                InsertableUtil.getCName(DisplayTool.class), key, true)) {
                tool = new DisplayTool(DisplayTool.BUTTON_NAME);
                TOOLS.add(tool);
                eventManager.addSeriesViewerListener((SeriesViewerListener) tool);
            }

            if (InsertableUtil.getBooleanProperty(BundleTools.SYSTEM_PREFERENCES, bundleName, componentName,
                InsertableUtil.getCName(MeasureTool.class), key, true)) {
                tool = new MeasureTool(eventManager);
                TOOLS.add(tool);
            }

            InsertableUtil.sortInsertable(TOOLS);

            // Send event to synchronize the series selection.
            DataExplorerView dicomView = UIManager.getExplorerplugin(DicomExplorer.NAME);
            if (dicomView != null) {
                eventManager.addSeriesViewerListener((SeriesViewerListener) dicomView);
            }

            Preferences prefs = BundlePreferences.getDefaultPreferences(context);
            if (prefs != null) {
                InsertableUtil.applyPreferences(TOOLBARS, prefs, bundleName, componentName, Type.TOOLBAR);
                InsertableUtil.applyPreferences(TOOLS, prefs, bundleName, componentName, Type.TOOL);
            }
        }
    }

    @Override
    public void setSelectedImagePaneFromFocus(ViewCanvas<DicomImageElement> viewCanvas) {
        setSelectedImagePane(viewCanvas);
        if (viewCanvas != null && viewCanvas.getSeries() instanceof DicomSeries) {
            DicomSeries series = (DicomSeries) viewCanvas.getSeries();
            DicomSeries.startPreloading(series,
                series.copyOfMedias(
                    (Filter<DicomImageElement>) viewCanvas.getActionValue(ActionW.FILTERED_SERIES.cmd()),
                    viewCanvas.getCurrentSortComparator()),
                viewCanvas.getFrameIndex());
        }
    }

    @Override
    public JMenu fillSelectedPluginMenu(JMenu menuRoot) {
        if (menuRoot != null) {
            menuRoot.removeAll();
            if (eventManager instanceof EventManager) {
                EventManager manager = (EventManager) eventManager;
                JMenu menu = new JMenu(Messages.getString("View2dContainer.3d")); //$NON-NLS-1$
                ActionState scrollAction = EventManager.getInstance().getAction(ActionW.SCROLL_SERIES);
                menu.setEnabled(
                    manager.getSelectedSeries() != null && (scrollAction != null && scrollAction.isActionEnabled()));

                if (menu.isEnabled()) {
                    JMenuItem mpr = new JMenuItem(Messages.getString("View2dContainer.mpr")); //$NON-NLS-1$
                    mpr.addActionListener(Basic3DToolBar.getMprAction());
                    menu.add(mpr);

                    JMenuItem mip = new JMenuItem(Messages.getString("View2dContainer.mip")); //$NON-NLS-1$
                    mip.addActionListener(Basic3DToolBar.getMipAction());
                    menu.add(mip);
                }
                menuRoot.add(menu);
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
    public List<DockableTool> getToolPanel() {
        return TOOLS;
    }

    @Override
    public void setSelected(boolean selected) {
        if (selected) {
            eventManager.setSelectedView2dContainer(this);

            // Send event to select the related patient in Dicom Explorer.
            DataExplorerView dicomView = UIManager.getExplorerplugin(DicomExplorer.NAME);
            if (dicomView != null && dicomView.getDataExplorerModel() instanceof DicomModel) {
                dicomView.getDataExplorerModel().firePropertyChange(
                    new ObservableEvent(ObservableEvent.BasicAction.SELECT, this, null, getGroupID()));
            }

        } else {
            eventManager.setSelectedView2dContainer(null);
        }
    }

    @Override
    public void close() {
        View2dFactory.closeSeriesViewer(this);
        super.close();
    }

    private boolean closeIfNoContent() {
        if (getOpenSeries().isEmpty()) {
            close();
            handleFocusAfterClosing();
            return true;
        }
        return false;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {

        if (evt instanceof ObservableEvent) {
            ObservableEvent event = (ObservableEvent) evt;
            ObservableEvent.BasicAction action = event.getActionCommand();
            Object newVal = event.getNewValue();

            if (newVal instanceof SeriesEvent) {
                SeriesEvent event2 = (SeriesEvent) newVal;

                SeriesEvent.Action action2 = event2.getActionCommand();
                Object source = event2.getSource();
                Object param = event2.getParam();

                if (ObservableEvent.BasicAction.ADD.equals(action)) {

                    if (SeriesEvent.Action.ADD_IMAGE.equals(action2)) {
                        if (source instanceof DicomSeries) {
                            DicomSeries series = (DicomSeries) source;
                            ViewCanvas<DicomImageElement> view2DPane = eventManager.getSelectedViewPane();
                            if (view2DPane != null) {
                                DicomImageElement img = view2DPane.getImage();
                                if (img != null && view2DPane.getSeries() == series) {
                                    ActionState seqAction = eventManager.getAction(ActionW.SCROLL_SERIES);
                                    if (seqAction instanceof SliderCineListener) {
                                        SliderCineListener sliceAction = (SliderCineListener) seqAction;
                                        if (param instanceof DicomImageElement) {
                                            Filter<DicomImageElement> filter = (Filter<DicomImageElement>) view2DPane
                                                .getActionValue(ActionW.FILTERED_SERIES.cmd());
                                            int imgIndex = series.getImageIndex(img, filter,
                                                view2DPane.getCurrentSortComparator());
                                            if (imgIndex < 0) {
                                                imgIndex = 0;
                                                // add again the series for registering listeners
                                                // (require at least one image)
                                                view2DPane.setSeries(series, null);
                                            }
                                            if (imgIndex >= 0) {
                                                sliceAction.setSliderMinMaxValue(1, series.size(filter), imgIndex + 1);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (SeriesEvent.Action.UPDATE_IMAGE.equals(action2)) {
                        if (source instanceof DicomImageElement) {
                            DicomImageElement dcm = (DicomImageElement) source;
                            for (ViewCanvas<DicomImageElement> v : view2ds) {
                                if (dcm == v.getImage()) {
                                    // Force to repaint the same image
                                    if (v.getImageLayer().getDisplayImage() == null) {
                                        v.setActionsInView(ActionW.PROGRESSION.cmd(), param);
                                        // Set image to null for getting correct W/L values
                                        v.getImageLayer().setImage(null, null);
                                        v.setSeries(v.getSeries());
                                    } else {
                                        v.propertyChange(
                                            new PropertyChangeEvent(dcm, ActionW.PROGRESSION.cmd(), null, param));
                                    }
                                }
                            }
                        }
                    } else if (SeriesEvent.Action.PRELOADING.equals(action2)) {
                        if (source instanceof DicomSeries) {
                            DicomSeries dcm = (DicomSeries) source;
                            for (ViewCanvas<DicomImageElement> v : view2ds) {
                                if (dcm == v.getSeries()) {
                                    v.getJComponent().repaint(v.getInfoLayer().getPreloadingProgressBound());
                                }
                            }
                        }
                    }
                } else if (ObservableEvent.BasicAction.UPDATE.equals(action)) {
                    if (SeriesEvent.Action.UPDATE.equals(action2)) {
                        if (source instanceof KOSpecialElement) {
                            setKOSpecialElement((KOSpecialElement) source, null, false, param.equals("updateAll")); //$NON-NLS-1$
                        }
                    }
                }
            } else if (ObservableEvent.BasicAction.REMOVE.equals(action)) {
                if (newVal instanceof MediaSeriesGroup) {
                    MediaSeriesGroup group = (MediaSeriesGroup) newVal;
                    // Patient Group
                    if (TagD.getUID(Level.PATIENT).equals(group.getTagID())) {
                        if (group.equals(getGroupID())) {
                            // Close the content of the plug-in
                            close();
                            handleFocusAfterClosing();
                        }
                    }
                    // Study Group
                    else if (TagD.getUID(Level.STUDY).equals(group.getTagID())) {
                        if (event.getSource() instanceof DicomModel) {
                            DicomModel model = (DicomModel) event.getSource();
                            for (ViewCanvas<DicomImageElement> v : view2ds) {
                                if (group.equals(model.getParent(v.getSeries(), DicomModel.study))) {
                                    v.setSeries(null);
                                    if (closeIfNoContent()) {
                                        return;
                                    }
                                }
                            }
                        }
                    }
                    // Series Group
                    else if (TagD.getUID(Level.SERIES).equals(group.getTagID())) {
                        for (ViewCanvas<DicomImageElement> v : view2ds) {
                            if (newVal.equals(v.getSeries())) {
                                v.setSeries(null);
                                if (closeIfNoContent()) {
                                    return;
                                }
                            }
                        }
                    }

                }
            } else if (ObservableEvent.BasicAction.REPLACE.equals(action)) {
                if (newVal instanceof Series) {
                    Series series = (Series) newVal;
                    for (ViewCanvas<DicomImageElement> v : view2ds) {
                        MediaSeries<DicomImageElement> s = v.getSeries();
                        if (series.equals(s)) {
                            /*
                             * Set to null to be sure that all parameters from the view are apply again to the Series
                             * (for instance it is the same series with more images)
                             */
                            v.setSeries(null);
                            v.setSeries(series, null);
                        }
                    }
                }
            } else if (ObservableEvent.BasicAction.UPDATE.equals(action)) {

                DicomSpecialElement specialElement = null;

                // When a dicom KO element is loaded an ObservableEvent.BasicAction.Update is sent
                // Either it's a new DicomObject or it's content is updated

                // TODO - a choice should be done about sending either a DicomSpecialElement or a Series object as the
                // new value for this event. A DicomSpecialElement seems to be a better choice since a Series of
                // DicomSpecialElement do not necessarily concerned the series in the Viewer2dContainer

                if (newVal instanceof Series) {
                    specialElement = DicomModel.getFirstSpecialElement((Series) newVal, DicomSpecialElement.class);
                } else if (newVal instanceof DicomSpecialElement) {
                    specialElement = (DicomSpecialElement) newVal;
                }

                if (specialElement instanceof PRSpecialElement) {
                    for (ViewCanvas<DicomImageElement> view : view2ds) {
                        if (view instanceof View2d) {
                            DicomImageElement img = view.getImage();
                            if (img != null) {
                                if (PresentationStateReader.isModuleAppicable(
                                    TagD.getTagValue(specialElement, Tag.ReferencedSeriesSequence, Attributes[].class),
                                    img)) {
                                    ((View2d) view).updatePR();
                                }
                            }
                        }
                    }
                }

                /*
                 * Update if necessary all the views with the KOSpecialElement
                 */
                else if (specialElement instanceof KOSpecialElement) {
                    setKOSpecialElement((KOSpecialElement) specialElement, null, false, false);
                }
            } else if (ObservableEvent.BasicAction.SELECT.equals(action)) {
                if (newVal instanceof KOSpecialElement) {
                    // Match using UID of the plugin window and the source event
                    if (this.getDockableUID().equals(evt.getSource())) {
                        setKOSpecialElement((KOSpecialElement) newVal, true, true, false);
                    }
                }
            }
        }
    }

    private void setKOSpecialElement(KOSpecialElement updatedKOSelection, Boolean enableFilter, boolean forceUpdate,
        boolean updateAll) {
        ViewCanvas<DicomImageElement> selectedView = getSelectedImagePane();

        if (updatedKOSelection != null && selectedView instanceof View2d) {
            if (SynchData.Mode.TILE.equals(this.getSynchView().getSynchData().getMode())) {

                ActionState koSelection = selectedView.getEventManager().getAction(ActionW.KO_SELECTION);
                if (koSelection instanceof ComboItemListener) {
                    ((ComboItemListener) koSelection).setSelectedItem(updatedKOSelection);
                }

                if (forceUpdate || enableFilter != null) {
                    ActionState koFilterAction = selectedView.getEventManager().getAction(ActionW.KO_FILTER);
                    if (koFilterAction instanceof ToggleButtonListener) {
                        if (enableFilter == null) {
                            enableFilter =
                                LangUtil.getNULLtoFalse((Boolean) selectedView.getActionValue(ActionW.KO_FILTER.cmd()));
                        }
                        ((ToggleButtonListener) koFilterAction).setSelected(enableFilter);
                    }
                }

                if (updateAll) {
                    List<ViewCanvas<DicomImageElement>> viewList = getImagePanels(true);
                    for (ViewCanvas<DicomImageElement> view : viewList) {
                        ((View2d) view).updateKOButtonVisibleState();
                    }
                } else {
                    ((View2d) selectedView).updateKOButtonVisibleState();
                }

            } else {
                /*
                 * Set the selected view at the end of the list to trigger the synchronization of the SCROLL_SERIES
                 * action at the end of the process
                 */
                List<ViewCanvas<DicomImageElement>> viewList = getImagePanels(true);

                for (ViewCanvas<DicomImageElement> view : viewList) {

                    if (!(view.getSeries() instanceof DicomSeries) || !(view instanceof View2d)) {
                        continue;
                    }

                    if (forceUpdate || updatedKOSelection == view.getActionValue(ActionW.KO_SELECTION.cmd())) {
                        KOManager.updateKOFilter(view, forceUpdate ? updatedKOSelection : null, enableFilter, -1);
                    }

                    DicomSeries dicomSeries = (DicomSeries) view.getSeries();
                    String seriesInstanceUID = TagD.getTagValue(dicomSeries, Tag.SeriesInstanceUID, String.class);

                    if (!updatedKOSelection.containsSeriesInstanceUIDReference(seriesInstanceUID)) {
                        continue;
                    }

                    ((View2d) view).updateKOButtonVisibleState();
                }
            }

            EventManager.getInstance().updateKeyObjectComponentsListener(selectedView);
        }
    }

    @Override
    public int getViewTypeNumber(GridBagLayoutModel layout, Class<?> defaultClass) {
        return View2dFactory.getViewTypeNumber(layout, defaultClass);
    }

    @Override
    public boolean isViewType(Class<?> defaultClass, String type) {
        if (defaultClass != null) {
            try {
                Class<?> clazz = Class.forName(type);
                return defaultClass.isAssignableFrom(clazz);
            } catch (Exception e) {
                LOGGER.error("Checking view", e); //$NON-NLS-1$
            }
        }
        return false;
    }

    @Override
    public DefaultView2d<DicomImageElement> createDefaultView(String classType) {
        return new View2d(eventManager);
    }

    @Override
    public JComponent createUIcomponent(String clazz) {
        if (isViewType(DefaultView2d.class, clazz)) {
            return createDefaultView(clazz);
        }

        try {
            // FIXME use classloader.loadClass or injection
            return buildInstance(Class.forName(clazz));
        } catch (Exception e) {
            LOGGER.error("Cannot create {}", clazz, e); //$NON-NLS-1$
        }
        return null;
    }

    @Override
    public synchronized List<Toolbar> getToolBar() {
        return TOOLBARS;
    }

    @Override
    public List<Action> getExportActions() {
        List<Action> actions = selectedImagePane == null ? null : selectedImagePane.getExportToClipboardAction();
        return actions;
    }


    @Override
    public List<Action> getPrintActions() {
        ArrayList<Action> actions = new ArrayList<>(2);
        final String title = Messages.getString("View2dContainer.print_layout"); //$NON-NLS-1$
        DefaultAction printStd = new DefaultAction(title,
            new ImageIcon(ImageViewerPlugin.class.getResource("/icon/16x16/printer.png")), event -> { //$NON-NLS-1$
                ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(View2dContainer.this);
                PrintDialog<DicomImageElement> dialog =
                    new PrintDialog<>(SwingUtilities.getWindowAncestor(View2dContainer.this), title, eventManager);
                ColorLayerUI.showCenterScreen(dialog, layer);
            });
        printStd.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_P, 0));
        actions.add(printStd);

        final String title2 = Messages.getString("View2dContainer.dcm_print"); //$NON-NLS-1$
        DefaultAction printStd2 = new DefaultAction(title2, null, event -> {
            ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(View2dContainer.this);
            DicomPrintDialog<DicomImageElement> dialog =
                new DicomPrintDialog<>(SwingUtilities.getWindowAncestor(View2dContainer.this), title2, eventManager);
            ColorLayerUI.showCenterScreen(dialog, layer);
        });
        actions.add(printStd2);
        return actions;
    }

    @Override
    public List<SynchView> getSynchList() {
        return DEFAULT_SYNCH_LIST;
    }

    @Override
    public List<GridBagLayoutModel> getLayoutList() {
        int rx = 1;
        int ry = 1;
        double ratio = getWidth() / (double) getHeight();
        if (ratio >= 1.0) {
            rx = (int) Math.round(ratio * 1.5);
        } else {
            ry = (int) Math.round((1.0 / ratio) * 1.5);
        }

        ArrayList<GridBagLayoutModel> list = new ArrayList<>(DEFAULT_LAYOUT_LIST);
        // Exclude 1x1
        if (rx != ry && rx != 0 && ry != 0) {
            int factorLimit = (int) (rx == 1 ? Math.round(getWidth() / 512.0) : Math.round(getHeight() / 512.0));
            if (factorLimit < 1) {
                factorLimit = 1;
            }
            if (rx > ry) {
                int step = 1 + (rx / 20);
                for (int i = rx / 2; i < rx; i = i + step) {
                    addLayout(list, factorLimit, i, ry);
                }
            } else {
                int step = 1 + (ry / 20);
                for (int i = ry / 2; i < ry; i = i + step) {
                    addLayout(list, factorLimit, rx, i);
                }
            }

            addLayout(list, factorLimit, rx, ry);
        }
        Collections.sort(list, (o1, o2) -> Integer.compare(o1.getConstraints().size(), o2.getConstraints().size()));
        return list;
    }

    private void addLayout(List<GridBagLayoutModel> list, int factorLimit, int rx, int ry) {
        for (int i = 1; i <= factorLimit; i++) {
            if (i > 2 || i * ry > 2 || i * rx > 2) {
                if (i * ry < 50 && i * rx < 50) {
                    list.add(ImageViewerPlugin.buildGridBagLayoutModel(i * ry, i * rx, view2dClass.getName()));
                }
            }
        }
    }
}
