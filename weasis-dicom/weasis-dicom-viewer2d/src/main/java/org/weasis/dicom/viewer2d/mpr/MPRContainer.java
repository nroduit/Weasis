/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.viewer2d.mpr;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JProgressBar;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;

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
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.image.LayoutConstraints;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.api.util.StringUtil.Suffix;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.image.CrosshairListener;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.editor.image.RotationToolBar;
import org.weasis.core.ui.editor.image.SynchData;
import org.weasis.core.ui.editor.image.SynchView;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerToolBar;
import org.weasis.core.ui.editor.image.ZoomToolBar;
import org.weasis.core.ui.util.ColorLayerUI;
import org.weasis.core.ui.util.DefaultAction;
import org.weasis.core.ui.util.PrintDialog;
import org.weasis.core.ui.util.Toolbar;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.codec.geometry.ImageOrientation;
import org.weasis.dicom.codec.geometry.ImageOrientation.Label;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.ExportToolBar;
import org.weasis.dicom.explorer.ImportToolBar;
import org.weasis.dicom.explorer.print.DicomPrintDialog;
import org.weasis.dicom.viewer2d.DcmHeaderToolBar;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.LutToolBar;
import org.weasis.dicom.viewer2d.Messages;
import org.weasis.dicom.viewer2d.View2dContainer;
import org.weasis.dicom.viewer2d.View2dFactory;
import org.weasis.dicom.viewer2d.mpr.MprView.SliceOrientation;

public class MPRContainer extends ImageViewerPlugin<DicomImageElement> implements PropertyChangeListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(MPRContainer.class);

    public static final List<SynchView> SYNCH_LIST = Collections.synchronizedList(new ArrayList<SynchView>());

    static SynchView DEFAULT_MPR;

    static {
        SYNCH_LIST.add(SynchView.NONE);

        HashMap<String, Boolean> actions = new HashMap<>();
        actions.put(ActionW.SCROLL_SERIES.cmd(), true);
        actions.put(ActionW.RESET.cmd(), true);
        actions.put(ActionW.ZOOM.cmd(), true);
        actions.put(ActionW.WINDOW.cmd(), true);
        actions.put(ActionW.LEVEL.cmd(), true);
        actions.put(ActionW.PRESET.cmd(), true);
        actions.put(ActionW.LUT_SHAPE.cmd(), true);
        actions.put(ActionW.LUT.cmd(), true);
        actions.put(ActionW.INVERT_LUT.cmd(), true);
        actions.put(ActionW.FILTER.cmd(), true);
        DEFAULT_MPR = new SynchView("MPR synch", "mpr", SynchData.Mode.STACK, //$NON-NLS-1$ //$NON-NLS-2$
            new ImageIcon(SynchView.class.getResource("/icon/22x22/tile.png")), actions); //$NON-NLS-1$

        SYNCH_LIST.add(DEFAULT_MPR);
    }

    public static final GridBagLayoutModel VIEWS_2x1_mpr = new GridBagLayoutModel(
        new LinkedHashMap<LayoutConstraints, Component>(3), "mpr", Messages.getString("MPRContainer.title")); //$NON-NLS-1$ //$NON-NLS-2$

    static {
        Map<LayoutConstraints, Component> constraints = VIEWS_2x1_mpr.getConstraints();
        constraints.put(new LayoutConstraints(MprView.class.getName(), 0, 0, 0, 1, 2, 0.5, 1.0,
            GridBagConstraints.CENTER, GridBagConstraints.BOTH), null);
        constraints.put(new LayoutConstraints(MprView.class.getName(), 1, 1, 0, 1, 1, 0.5, 0.5,
            GridBagConstraints.CENTER, GridBagConstraints.BOTH), null);
        constraints.put(new LayoutConstraints(MprView.class.getName(), 2, 1, 1, 1, 1, 0.5, 0.5,
            GridBagConstraints.CENTER, GridBagConstraints.BOTH), null);

    }

    public static final List<GridBagLayoutModel> LAYOUT_LIST =
        Collections.synchronizedList(new ArrayList<GridBagLayoutModel>());

    static {
        LAYOUT_LIST.add(VIEWS_2x1_mpr);
        LAYOUT_LIST.add(VIEWS_2x2_f2);
        LAYOUT_LIST.add(VIEWS_2_f1x2);
    }

    // Static tools shared by all the View2dContainer instances, tools are registered when a container is selected
    // Do not initialize tools in a static block (order initialization issue with eventManager), use instead a lazy
    // initialization with a method.
    public static final List<Toolbar> TOOLBARS = Collections.synchronizedList(new ArrayList<Toolbar>());
    public static final List<DockableTool> TOOLS = View2dContainer.TOOLS;
    private static volatile boolean initComponents = false;

    private Thread process;
    private String lastCommand;

    public MPRContainer() {
        this(VIEWS_1x1, null);
    }

    public MPRContainer(GridBagLayoutModel layoutModel, String uid) {
        super(EventManager.getInstance(), layoutModel, uid, MPRFactory.NAME, MPRFactory.ICON, null);
        setSynchView(SynchView.NONE);
        if (!initComponents) {
            initComponents = true;
            // Add standard toolbars
            // WProperties props = (WProperties) BundleTools.SYSTEM_PREFERENCES.clone();
            // props.putBooleanProperty("weasis.toolbar.synchbouton", false); //$NON-NLS-1$

            EventManager evtMg = EventManager.getInstance();
            Optional<Toolbar> importBar = View2dContainer.TOOLBARS.stream().filter(b -> b instanceof ImportToolBar).findFirst();
            importBar.ifPresent(TOOLBARS::add);
            Optional<Toolbar> exportBar = View2dContainer.TOOLBARS.stream().filter(b -> b instanceof ExportToolBar).findFirst();
            exportBar.ifPresent(TOOLBARS::add);
            Optional<Toolbar> viewBar = View2dContainer.TOOLBARS.stream().filter(b -> b instanceof ViewerToolBar).findFirst();
            viewBar.ifPresent(TOOLBARS::add);
            TOOLBARS.add(new MeasureToolBar(evtMg, 11));
            TOOLBARS.add(new ZoomToolBar(evtMg, 20, true));
            TOOLBARS.add(new RotationToolBar(evtMg, 30));
            TOOLBARS.add(new DcmHeaderToolBar(evtMg, 35));
            TOOLBARS.add(new LutToolBar(evtMg, 40));

            final BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
            Preferences prefs = BundlePreferences.getDefaultPreferences(context);
            if (prefs != null) {
                String className = this.getClass().getSimpleName().toLowerCase();
                InsertableUtil.applyPreferences(TOOLBARS, prefs, context.getBundle().getSymbolicName(), className,
                    Type.TOOLBAR);
            }
        }
    }

    @Override
    public void setSelectedImagePaneFromFocus(ViewCanvas<DicomImageElement> defaultView2d) {
        setSelectedImagePane(defaultView2d);
    }

    @Override
    public JMenu fillSelectedPluginMenu(JMenu menuRoot) {
        if (menuRoot != null) {
            menuRoot.removeAll();

            if (eventManager instanceof EventManager) {
                EventManager manager = (EventManager) eventManager;

                int count = menuRoot.getItemCount();

                JMVUtils.addItemToMenu(menuRoot, manager.getPresetMenu("weasis.pluginMenu.presets")); //$NON-NLS-1$
                JMVUtils.addItemToMenu(menuRoot, manager.getLutShapeMenu("weasis.pluginMenu.lutShape")); //$NON-NLS-1$
                JMVUtils.addItemToMenu(menuRoot, manager.getLutMenu("weasis.pluginMenu.lut")); //$NON-NLS-1$
                JMVUtils.addItemToMenu(menuRoot, manager.getLutInverseMenu("weasis.pluginMenu.invertLut")); //$NON-NLS-1$
                JMVUtils.addItemToMenu(menuRoot, manager.getFilterMenu("weasis.pluginMenu.filter")); //$NON-NLS-1$

                if (count < menuRoot.getItemCount()) {
                    menuRoot.add(new JSeparator());
                    count = menuRoot.getItemCount();
                }

                JMVUtils.addItemToMenu(menuRoot, manager.getZoomMenu("weasis.pluginMenu.zoom")); //$NON-NLS-1$
                JMVUtils.addItemToMenu(menuRoot, manager.getOrientationMenu("weasis.pluginMenu.orientation")); //$NON-NLS-1$

                if (count < menuRoot.getItemCount()) {
                    menuRoot.add(new JSeparator());
                    count = menuRoot.getItemCount();
                }

                menuRoot.add(manager.getResetMenu("weasis.pluginMenu.reset")); //$NON-NLS-1$
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
        final ViewerToolBar toolBar = getViewerToolBar();
        if (selected) {
            if (toolBar != null) {
                String command = ActionW.CROSSHAIR.cmd();
                MouseActions mouseActions = eventManager.getMouseActions();
                String lastAction = mouseActions.getAction(MouseActions.T_LEFT);
                if (!command.equals(lastAction)) {
                    lastCommand = lastAction;
                    mouseActions.setAction(MouseActions.T_LEFT, command);
                    setMouseActions(mouseActions);
                    toolBar.changeButtonState(MouseActions.T_LEFT, command);
                }
            }

            eventManager.setSelectedView2dContainer(this);

            // Send event to select the related patient in Dicom Explorer.
            DataExplorerView dicomView = UIManager.getExplorerplugin(DicomExplorer.NAME);
            if (dicomView != null && dicomView.getDataExplorerModel() instanceof DicomModel) {
                dicomView.getDataExplorerModel().firePropertyChange(
                    new ObservableEvent(ObservableEvent.BasicAction.SELECT, this, null, getGroupID()));
            }

        } else {
            if (lastCommand != null && toolBar != null) {
                MouseActions mouseActions = eventManager.getMouseActions();
                if (ActionW.CROSSHAIR.cmd().equals(mouseActions.getAction(MouseActions.T_LEFT))) {
                    mouseActions.setAction(MouseActions.T_LEFT, lastCommand);
                    setMouseActions(mouseActions);
                    toolBar.changeButtonState(MouseActions.T_LEFT, lastCommand);
                    lastCommand = null;
                }
            }
            eventManager.setSelectedView2dContainer(null);
        }

    }

    private boolean closeIfNoContent() {
        if (getOpenSeries().isEmpty()) {
            close();
            handleFocusAfterClosing();
            return true;
        }
        return false;
    }
    
    private synchronized void stopCurrentProcess() {
        if (process != null) {
            final Thread t = process;
            process = null;
            t.interrupt();
        }
    }

    @Override
    public void close() {
        stopCurrentProcess();
        MPRFactory.closeSeriesViewer(this);
        super.close();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt instanceof ObservableEvent) {
            ObservableEvent event = (ObservableEvent) evt;
            ObservableEvent.BasicAction action = event.getActionCommand();
            Object newVal = event.getNewValue();
            if (ObservableEvent.BasicAction.REMOVE.equals(action)) {
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
                            // It will reset MIP view
                            v.setSeries(series, null);
                        }
                    }
                }
            }
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
                LOGGER.error("Checking view type", e); //$NON-NLS-1$
            }
        }
        return false;
    }

    @Override
    public DefaultView2d<DicomImageElement> createDefaultView(String classType) {
        return new MprView(eventManager);
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
        return selectedImagePane == null ? super.getExportActions() : selectedImagePane.getExportToClipboardAction();
    }

    @Override
    public List<Action> getPrintActions() {
        ArrayList<Action> actions = new ArrayList<>(1);
        final String title = Messages.getString("View2dContainer.print_layout"); //$NON-NLS-1$
        DefaultAction printStd = new DefaultAction(title,
            new ImageIcon(ImageViewerPlugin.class.getResource("/icon/16x16/printer.png")), event -> { //$NON-NLS-1$
                ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(MPRContainer.this);
                PrintDialog<DicomImageElement> dialog =
                    new PrintDialog<>(SwingUtilities.getWindowAncestor(MPRContainer.this), title, eventManager);
                ColorLayerUI.showCenterScreen(dialog, layer);
            });
        actions.add(printStd);

        final String title2 = Messages.getString("View2dContainer.dcm_print"); //$NON-NLS-1$
        DefaultAction printStd2 = new DefaultAction(title2, null, event -> {
            ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(MPRContainer.this);
            DicomPrintDialog<?> dialog =
                new DicomPrintDialog<>(SwingUtilities.getWindowAncestor(MPRContainer.this), title2, eventManager);
            ColorLayerUI.showCenterScreen(dialog, layer);
        });
        actions.add(printStd2);
        return actions;
    }

    public MprView getMprView(SliceOrientation sliceOrientation) {
        for (ViewCanvas v : view2ds) {
            if (v instanceof MprView) {
                if (sliceOrientation != null && sliceOrientation.equals(((MprView) v).getSliceOrientation())) {
                    return (MprView) v;
                }
            }
        }
        return null;
    }

    @Override
    public void addSeries(MediaSeries<DicomImageElement> sequence) {
        stopCurrentProcess();
        // TODO Should be init elsewhere
        for (int i = 0; i < view2ds.size(); i++) {
            ViewCanvas<DicomImageElement> val = view2ds.get(i);
            if (val instanceof MprView) {
                SliceOrientation sliceOrientation;
                switch (i) {
                    case 1:
                        sliceOrientation = SliceOrientation.CORONAL;
                        break;
                    case 2:
                        sliceOrientation = SliceOrientation.SAGITTAL;
                        break;
                    default:
                        sliceOrientation = SliceOrientation.AXIAL;
                        break;
                }
                ((MprView) val).setType(sliceOrientation);
            }
        }

        final MprView view = selectLayoutPositionForAddingSeries(sequence);
        if (view != null) {
            view.setSeries(sequence);

            String title = TagD.getTagValue(sequence, Tag.PatientName, String.class);
            if (title != null) {
                this.getDockable().setTitleToolTip(title);
                this.setPluginName(StringUtil.getTruncatedString(title, 25, Suffix.THREE_PTS));
            }
            view.repaint();
            process = new Thread(Messages.getString("MPRContainer.build")) { //$NON-NLS-1$
                @Override
                public void run() {
                    try {
                        SeriesBuilder.createMissingSeries(this, MPRContainer.this, view);

                        // Following actions need to be executed in EDT thread
                        GuiExecutor.instance().execute(new Runnable() {

                            @Override
                            public void run() {
                                ActionState synch = eventManager.getAction(ActionW.SYNCH);
                                if (synch instanceof ComboItemListener) {
                                    ((ComboItemListener) synch).setSelectedItem(MPRContainer.DEFAULT_MPR);
                                }
                                // Set the middle image (best choice to propagate the default preset of non CT
                                // modalities)
                                ActionState seqAction = eventManager.getAction(ActionW.SCROLL_SERIES);
                                if (seqAction instanceof SliderChangeListener) {
                                    SliderCineListener sliceAction = (SliderCineListener) seqAction;
                                    sliceAction.setSliderValue(sliceAction.getSliderMax() / 2);
                                }
                                ActionState cross = eventManager.getAction(ActionW.CROSSHAIR);
                                if (cross instanceof CrosshairListener) {
                                    ((CrosshairListener) cross).setPoint(
                                        view.getImageCoordinatesFromMouse(view.getWidth() / 2, view.getHeight() / 2));
                                }
                                // Force to propagate the default preset
                                ActionState presetAction = eventManager.getAction(ActionW.PRESET);
                                if (presetAction instanceof ComboItemListener) {
                                    ComboItemListener p = (ComboItemListener) presetAction;
                                    p.setSelectedItemWithoutTriggerAction(null);
                                    p.setSelectedItem(p.getFirstItem());
                                }
                            }
                        });

                    } catch (final Exception e) {
                        LOGGER.error("Build MPR", e); //$NON-NLS-1$
                        // Following actions need to be executed in EDT thread
                        GuiExecutor.instance().execute(() -> showErrorMessage(view2ds, view, e.getMessage()));
                    }
                }

            };
            process.start();
        } else {
            showErrorMessage(view2ds, null, Messages.getString("MPRContainer.mesg_missing_3d")); //$NON-NLS-1$
        }
    }

    public static void showErrorMessage(List<ViewCanvas<DicomImageElement>> view2ds,
        DefaultView2d<DicomImageElement> view, String message) {
        for (ViewCanvas<DicomImageElement> v : view2ds) {
            if (v != view && v instanceof MprView) {
                JProgressBar bar = ((MprView) v).getProgressBar();
                if (bar == null) {
                    bar = new JProgressBar();
                    Dimension dim = new Dimension(v.getJComponent().getWidth() / 2, 30);
                    bar.setSize(dim);
                    bar.setPreferredSize(dim);
                    bar.setMaximumSize(dim);
                    bar.setValue(0);
                    bar.setStringPainted(true);
                    ((MprView) v).setProgressBar(bar);
                }
                bar.setString(message);
                v.getJComponent().repaint();
            }
        }
    }

    @Override
    public void addSeriesList(List<MediaSeries<DicomImageElement>> seriesList, boolean removeOldSeries) {
        if (seriesList != null && !seriesList.isEmpty()) {
            addSeries(seriesList.get(0));
        }
    }

    @Override
    public void selectLayoutPositionForAddingSeries(List<MediaSeries<DicomImageElement>> seriesList) {
        // Do it in addSeries()
    }

    public MprView selectLayoutPositionForAddingSeries(MediaSeries<DicomImageElement> s) {
        if (s != null) {
            Object img = s.getMedia(MediaSeries.MEDIA_POSITION.MIDDLE, null, null);
            if (img instanceof DicomImageElement) {
                double[] v = TagD.getTagValue((DicomImageElement) img, Tag.ImageOrientationPatient, double[].class);
                if (v != null && v.length == 6) {
                    Label orientation = ImageOrientation.makeImageOrientationLabelFromImageOrientationPatient(v[0],
                        v[1], v[2], v[3], v[4], v[5]);
                    SliceOrientation sliceOrientation = SliceOrientation.AXIAL;
                    if (ImageOrientation.Label.CORONAL.equals(orientation)) {
                        sliceOrientation = SliceOrientation.CORONAL;
                    } else if (ImageOrientation.Label.SAGITTAL.equals(orientation)) {
                        sliceOrientation = SliceOrientation.SAGITTAL;
                    }
                    MprView view = getMprView(sliceOrientation);
                    if (view != null) {
                        setSelectedImagePane(view);
                        return view;
                    }
                }
            }
        }
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
