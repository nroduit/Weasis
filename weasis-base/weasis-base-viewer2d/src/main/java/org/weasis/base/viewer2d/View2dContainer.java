/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.base.viewer2d;

import java.awt.event.ActionEvent;
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
import java.util.function.Consumer;

import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.base.viewer2d.dockable.DisplayTool;
import org.weasis.base.viewer2d.dockable.ImageTool;
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
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.SeriesEvent;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.editor.image.RotationToolBar;
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

public class View2dContainer extends ImageViewerPlugin<ImageElement> implements PropertyChangeListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(View2dContainer.class);

    // Unmodifiable list of the default synchronization elements
    public static final List<SynchView> DEFAULT_SYNCH_LIST =
        Arrays.asList(SynchView.NONE, SynchView.DEFAULT_STACK, SynchView.DEFAULT_TILE);

    public static final GridBagLayoutModel VIEWS_2x1_r1xc2_histo = new GridBagLayoutModel(
        View2dContainer.class.getResourceAsStream("/config/layoutModelHisto.xml"), "layout_histo", //$NON-NLS-1$ //$NON-NLS-2$
        Messages.getString("View2dContainer.histogram")); //$NON-NLS-1$
    // Unmodifiable list of the default layout elements
    public static final List<GridBagLayoutModel> DEFAULT_LAYOUT_LIST =
        Arrays.asList(VIEWS_1x1, VIEWS_1x2, VIEWS_2x1, VIEWS_2x1_r1xc2_histo, VIEWS_2x2_f2, VIEWS_2_f1x2, VIEWS_2x2);

    // Static tools shared by all the View2dContainer instances, tools are registered when a container is selected
    // Do not initialize tools in a static block (order initialization issue with eventManager), use instead a lazy
    // initialization with a method.
    public static final List<Toolbar> TOOLBARS = Collections.synchronizedList(new ArrayList<Toolbar>());
    public static final List<DockableTool> TOOLS = Collections.synchronizedList(new ArrayList<DockableTool>());
    private static volatile boolean initComponents = false;

    public View2dContainer() {
        this(VIEWS_1x1, null);
    }

    public View2dContainer(GridBagLayoutModel layoutModel, String uid) {
        super(EventManager.getInstance(), layoutModel, uid, ViewerFactory.NAME, MimeInspector.imageIcon, null);
        setSynchView(SynchView.DEFAULT_STACK);
        addComponentListener(new ComponentAdapter() {

            @Override
            public void componentResized(ComponentEvent e) {
                ImageViewerPlugin<ImageElement> container = EventManager.getInstance().getSelectedView2dContainer();
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

            Preferences prefs = BundlePreferences.getDefaultPreferences(context);
            if (prefs != null) {
                InsertableUtil.applyPreferences(TOOLBARS, prefs, bundleName, componentName, Type.TOOLBAR);
                InsertableUtil.applyPreferences(TOOLS, prefs, bundleName, componentName, Type.TOOL);
            }
        }
    }

    @Override
    public JMenu fillSelectedPluginMenu(JMenu menuRoot) {
        if (menuRoot != null) {
            menuRoot.removeAll();

            if (eventManager instanceof EventManager) {
                EventManager manager = (EventManager) eventManager;

                JMVUtils.addItemToMenu(menuRoot, manager.getLutMenu(null));
                JMVUtils.addItemToMenu(menuRoot, manager.getLutInverseMenu(null));
                JMVUtils.addItemToMenu(menuRoot, manager.getFilterMenu(null));
                menuRoot.add(new JSeparator());
                JMVUtils.addItemToMenu(menuRoot, manager.getZoomMenu(null));
                JMVUtils.addItemToMenu(menuRoot, manager.getOrientationMenu(null));
                // JMVUtils.addItemToMenu(menuRoot, manager.getSortStackMenu(null));
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

        } else {
            eventManager.setSelectedView2dContainer(null);
        }
    }

    @Override
    public void close() {
        ViewerFactory.closeSeriesViewer(this);
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
                if (ObservableEvent.BasicAction.ADD.equals(action)) {
                    SeriesEvent.Action action2 = event2.getActionCommand();
                    Object source = event2.getSource();
                    Object param = event2.getParam();

                    if (SeriesEvent.Action.ADD_IMAGE.equals(action2)) {
                        if (source instanceof Series) {
                            Series series = (Series) source;
                            ViewCanvas view2DPane = eventManager.getSelectedViewPane();
                            ImageElement img = view2DPane.getImage();
                            if (img != null && view2DPane.getSeries() == series) {
                                ActionState seqAction = eventManager.getAction(ActionW.SCROLL_SERIES);
                                if (seqAction instanceof SliderCineListener) {
                                    SliderCineListener sliceAction = (SliderCineListener) seqAction;
                                    if (param instanceof ImageElement) {
                                        Filter<ImageElement> filter = (Filter<ImageElement>) view2DPane
                                            .getActionValue(ActionW.FILTERED_SERIES.cmd());
                                        int imgIndex =
                                            series.getImageIndex(img, filter, view2DPane.getCurrentSortComparator());
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
                    } else if (SeriesEvent.Action.PRELOADING.equals(action2)) {
                        if (source instanceof Series) {
                            Series s = (Series) source;
                            for (ViewCanvas<ImageElement> v : view2ds) {
                                if (s == v.getSeries()) {
                                    v.getJComponent().repaint(v.getInfoLayer().getPreloadingProgressBound());
                                }
                            }
                        }
                    }
                }
            } else if (ObservableEvent.BasicAction.REMOVE.equals(action)) {
                if (newVal instanceof MediaSeriesGroup) {
                    MediaSeriesGroup group = (MediaSeriesGroup) newVal;
                    // Patient Group
                    if (TagW.Group.equals(group.getTagID())) {
                        if (group.equals(getGroupID())) {
                            // Close the content of the plug-in
                            close();
                            handleFocusAfterClosing();
                        }
                    }
                    // Series Group
                    else if (TagW.SubseriesInstanceUID.equals(group.getTagID())) {
                        for (ViewCanvas<ImageElement> v : view2ds) {
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
                    for (ViewCanvas<ImageElement> v : view2ds) {
                        MediaSeries<ImageElement> s = v.getSeries();
                        if (series.equals(s)) {
                            // Set to null to be sure that all parameters from the view are apply again to the Series
                            // (in case for instance it is the same series with more images)
                            v.setSeries(null);
                            v.setSeries(series, null);
                        }
                    }
                }
            }
        }
    }

    @Override
    public DefaultView2d<ImageElement> createDefaultView(String classType) {
        return new View2d(eventManager);
    }

    @Override
    public JComponent createUIcomponent(String clazz) {
        if (DefaultView2d.class.getName().equals(clazz) || View2d.class.getName().equals(clazz)) {
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
        return selectedImagePane == null ? super.getExportActions() : selectedImagePane.getExportToClipboardAction();
    }

    @Override
    public int getViewTypeNumber(GridBagLayoutModel layout, Class<?> defaultClass) {
        return ViewerFactory.getViewTypeNumber(layout, defaultClass);
    }

    @Override
    public boolean isViewType(Class<?> defaultClass, String type) {
        if (defaultClass != null) {
            try {
                Class<?> clazz = Class.forName(type);
                return defaultClass.isAssignableFrom(clazz);
            } catch (Exception e) {
                LOGGER.error("Checking view type", e); //$NON-NLS-1$
            }
        }
        return false;
    }

    @Override
    public List<Action> getPrintActions() {
        ArrayList<Action> actions = new ArrayList<>(1);
        final String title = Messages.getString("View2dContainer.print_layout"); //$NON-NLS-1$
        Consumer<ActionEvent> event = e -> {
            ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(View2dContainer.this);
            PrintDialog<?> dialog =
                new PrintDialog<>(SwingUtilities.getWindowAncestor(View2dContainer.this), title, eventManager);
            ColorLayerUI.showCenterScreen(dialog, layer);
        };
        DefaultAction printStd = new DefaultAction(title,
            new ImageIcon(ImageViewerPlugin.class.getResource("/icon/16x16/printer.png")), event); //$NON-NLS-1$
        printStd.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_P, 0));
        actions.add(printStd);
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