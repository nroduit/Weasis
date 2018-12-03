/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.base.viewer2d;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.prefs.Preferences;
import org.weasis.core.api.gui.Insertable.Type;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.BasicActionState;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.gui.util.SliderCineListener.TIME;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.image.FilterOp;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.image.ImageOpNode;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.PseudoColorOp;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.image.op.ByteLut;
import org.weasis.core.api.image.op.ByteLutCollection;
import org.weasis.core.api.image.util.KernelData;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.LangUtil;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.editor.image.SynchEvent;
import org.weasis.core.ui.editor.image.SynchView;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ZoomToolBar;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.GraphicSelectionListener;
import org.weasis.core.ui.util.ColorLayerUI;
import org.weasis.core.ui.util.PrintDialog;

/**
 * The event processing center for this application. This class responses for loading data sets, processing the events
 * from the utility menu that includes changing the operation scope, the layout, window/level, rotation angle, zoom
 * factor, starting/stoping the cining-loop and etc.
 *
 */

public class EventManager extends ImageViewerEventManager<ImageElement> implements ActionListener {

    /** The single instance of this singleton class. */
    private static EventManager instance;

    /**
     * The default private constructor to guarantee the singleton property of this class.
     */

    private EventManager() {
        // Initialize actions with a null value. These are used by mouse or keyevent actions.
        setAction(new BasicActionState(ActionW.WINLEVEL));
        setAction(new BasicActionState(ActionW.CONTEXTMENU));
        setAction(new BasicActionState(ActionW.NO_ACTION));
        setAction(new BasicActionState(ActionW.DRAW));
        setAction(new BasicActionState(ActionW.MEASURE));

        setAction(getMoveTroughSliceAction(10, TIME.MINUTE, 0.1));
        setAction(newWindowAction());
        setAction(newLevelAction());
        setAction(newRotateAction());
        setAction(newZoomAction());
        setAction(newLensZoomAction());

        setAction(newFlipAction());
        setAction(newInverseLutAction());
        setAction(newInverseStackAction());
        setAction(newLensAction());
        setAction(newDrawOnlyOnceAction());

        setAction(newLutAction());
        setAction(newFilterAction());
        setAction(newLayoutAction(View2dContainer.DEFAULT_LAYOUT_LIST
            .toArray(new GridBagLayoutModel[View2dContainer.DEFAULT_LAYOUT_LIST.size()])));
        setAction(newSynchAction(
            View2dContainer.DEFAULT_SYNCH_LIST.toArray(new SynchView[View2dContainer.DEFAULT_SYNCH_LIST.size()])));
        getAction(ActionW.SYNCH, ComboItemListener.class)
            .ifPresent(a -> a.setSelectedItemWithoutTriggerAction(SynchView.DEFAULT_STACK));
        setAction(newMeasurementAction(
            MeasureToolBar.measureGraphicList.toArray(new Graphic[MeasureToolBar.measureGraphicList.size()])));
        setAction(
            newDrawAction(MeasureToolBar.drawGraphicList.toArray(new Graphic[MeasureToolBar.drawGraphicList.size()])));
        setAction(newSpatialUnit(Unit.values()));
        setAction(newPanAction());
        setAction(new BasicActionState(ActionW.RESET));

        final BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
        Preferences prefs = BundlePreferences.getDefaultPreferences(context);
        zoomSetting.applyPreferences(prefs);
        mouseActions.applyPreferences(prefs);
        if (prefs != null) {
            Preferences prefNode = prefs.node("mouse.sensivity"); //$NON-NLS-1$
            getSliderPreference(prefNode, ActionW.WINDOW, 1.25);
            getSliderPreference(prefNode, ActionW.LEVEL, 1.25);
            getSliderPreference(prefNode, ActionW.SCROLL_SERIES, 0.1);
            getSliderPreference(prefNode, ActionW.ROTATION, 0.25);
            getSliderPreference(prefNode, ActionW.ZOOM, 0.1);
        }
        initializeParameters();
    }

    /**
     * Return the single instance of this class. This method guarantees the singleton property of this class.
     */
    public static synchronized EventManager getInstance() {
        if (instance == null) {
            instance = new EventManager();
        }
        return instance;
    }

    private void initializeParameters() {
        enableActions(false);
    }

    private ComboItemListener<KernelData> newFilterAction() {
        return new ComboItemListener<KernelData>(ActionW.FILTER, KernelData.getAllFilters()) {

            @Override
            public void itemStateChanged(Object object) {
                if (object instanceof KernelData) {
                    firePropertyChange(ActionW.SYNCH.cmd(), null,
                        new SynchEvent(getSelectedViewPane(), action.cmd(), object));
                }
            }
        };
    }

    private ComboItemListener<ByteLut> newLutAction() {
        List<ByteLut> luts = new ArrayList<>();
        luts.add(ByteLut.grayLUT);
        ByteLutCollection.readLutFilesFromResourcesDir(luts, ResourceUtil.getResource("luts"));//$NON-NLS-1$
        // Set default first as the list has been sorted
        luts.add(0, ByteLut.defaultLUT);

        return new ComboItemListener<ByteLut>(ActionW.LUT, luts.toArray(new ByteLut[luts.size()])) {
            @Override
            public void itemStateChanged(Object object) {
                if (object instanceof ByteLut) {
                    firePropertyChange(ActionW.SYNCH.cmd(), null,
                        new SynchEvent(getSelectedViewPane(), action.cmd(), object));
                }
            }
        };
    }

    @Override
    public Optional<ActionW> getLeftMouseActionFromkeyEvent(int keyEvent, int modifier) {
        Optional<ActionW> action = super.getLeftMouseActionFromkeyEvent(keyEvent, modifier);
        // Only return the action if it is enabled
        if (action.isPresent()
            && Optional.ofNullable(getAction(action.get())).filter(a -> a.isActionEnabled()).isPresent()) {
            return action;
        }
        return Optional.empty();
    }

    @Override
    public void keyTyped(KeyEvent e) {
        // Do nothing
    }

    @Override
    public void keyPressed(KeyEvent e) {

        int keyEvent = e.getKeyCode();
        int modifiers = e.getModifiers();

        if (keyEvent == KeyEvent.VK_ESCAPE) {
            resetDisplay();
        } else if (keyEvent == ActionW.CINESTART.getKeyCode() && ActionW.CINESTART.getModifier() == modifiers) {
            Optional<SliderCineListener> cineAction = getAction(ActionW.SCROLL_SERIES, SliderCineListener.class);
            if (cineAction.isPresent() && cineAction.get().isActionEnabled()) {
                if (cineAction.get().isCining()) {
                    cineAction.get().stop();
                } else {
                    cineAction.get().start();
                }
            }
            return;
        } else if (keyEvent == KeyEvent.VK_P && modifiers == 0) {
            ImageViewerPlugin<ImageElement> view = getSelectedView2dContainer();
            if (view != null) {
                ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(view);
                PrintDialog<?> dialog = new PrintDialog<>(SwingUtilities.getWindowAncestor(view),
                    Messages.getString("View2dContainer.print_layout"), this); //$NON-NLS-1$
                ColorLayerUI.showCenterScreen(dialog, layer);
            }
        } else {
            triggerDrawingToolKeyEvent(keyEvent, modifiers);
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        // Do nothing
    }

    @Override
    public void setSelectedView2dContainer(ImageViewerPlugin<ImageElement> selectedView2dContainer) {
        if (this.selectedView2dContainer != null) {
            this.selectedView2dContainer.setMouseActions(null);
            getAction(ActionW.SCROLL_SERIES, SliderCineListener.class).ifPresent(a -> a.stop());
        }

        ImageViewerPlugin<ImageElement> oldContainer = this.selectedView2dContainer;
        this.selectedView2dContainer = selectedView2dContainer;
        if (selectedView2dContainer != null) {
            Optional<ComboItemListener> synchAction = getAction(ActionW.SYNCH, ComboItemListener.class);
            Optional<ComboItemListener> layoutAction = getAction(ActionW.LAYOUT, ComboItemListener.class);
            if (oldContainer == null || !oldContainer.getClass().equals(selectedView2dContainer.getClass())) {
                synchAction.ifPresent(
                    a -> a.setDataListWithoutTriggerAction(selectedView2dContainer.getSynchList().toArray()));
                layoutAction.ifPresent(
                    a -> a.setDataListWithoutTriggerAction(selectedView2dContainer.getLayoutList().toArray()));
            }
            if (oldContainer != null) {
                ViewCanvas<ImageElement> pane = oldContainer.getSelectedImagePane();
                if (pane != null) {
                    pane.setFocused(false);
                }
            }
            synchAction.ifPresent(a -> a.setSelectedItemWithoutTriggerAction(selectedView2dContainer.getSynchView()));
            layoutAction.ifPresent(
                a -> a.setSelectedItemWithoutTriggerAction(selectedView2dContainer.getOriginalLayoutModel()));
            updateComponentsListener(selectedView2dContainer.getSelectedImagePane());
            selectedView2dContainer.setMouseActions(mouseActions);
            ViewCanvas<ImageElement> pane = selectedView2dContainer.getSelectedImagePane();
            if (pane != null) {
                fireSeriesViewerListeners(
                    new SeriesViewerEvent(selectedView2dContainer, pane.getSeries(), null, EVENT.SELECT_VIEW));
                pane.setFocused(true);
            }
        }
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
        cinePlay(evt.getActionCommand());
    }

    private void cinePlay(String command) {
        if (command != null) {
            if (command.equals(ActionW.CINESTART.cmd())) {
                getAction(ActionW.SCROLL_SERIES, SliderCineListener.class).ifPresent(a -> a.start());
            } else if (command.equals(ActionW.CINESTOP.cmd())) {
                getAction(ActionW.SCROLL_SERIES, SliderCineListener.class).ifPresent(a -> a.stop());
            }
        }
    }

    @Override
    public void resetDisplay() {
        reset(ResetTools.ALL);
    }

    public void reset(ResetTools action) {
        AuditLog.LOGGER.info("reset action:{}", action.name()); //$NON-NLS-1$
        if (ResetTools.ALL.equals(action)) {
            firePropertyChange(ActionW.SYNCH.cmd(), null,
                new SynchEvent(getSelectedViewPane(), ActionW.RESET.cmd(), true));
        } else if (ResetTools.ZOOM.equals(action)) {
            // Pass the value 0.0 (convention: default value according the zoom type) directly to the property change,
            // otherwise the value is adjusted by the BoundedRangeModel
            firePropertyChange(ActionW.SYNCH.cmd(), null,
                new SynchEvent(getSelectedViewPane(), ActionW.ZOOM.cmd(), 0.0));
        } else if (ResetTools.ROTATION.equals(action)) {
            getAction(ActionW.ROTATION, SliderChangeListener.class).ifPresent(a -> a.setSliderValue(0));
        } else if (ResetTools.WL.equals(action)) {
            if (selectedView2dContainer != null) {
                ViewCanvas<ImageElement> defaultView2d = selectedView2dContainer.getSelectedImagePane();
                if (defaultView2d != null) {
                    ImageElement img = defaultView2d.getImage();
                    if (img != null) {
                        boolean pixelPadding = LangUtil.getNULLtoTrue((Boolean) defaultView2d.getDisplayOpManager()
                            .getParamValue(WindowOp.OP_NAME, ActionW.IMAGE_PIX_PADDING.cmd()));
                        getAction(ActionW.WINDOW, SliderChangeListener.class)
                            .ifPresent(a -> a.setRealValue(img.getDefaultWindow(pixelPadding)));
                        getAction(ActionW.LEVEL, SliderChangeListener.class)
                            .ifPresent(a -> a.setRealValue(img.getDefaultLevel(pixelPadding)));
                    }
                }
            }
        } else if (ResetTools.PAN.equals(action)) {
            if (selectedView2dContainer != null) {
                ViewCanvas viewPane = selectedView2dContainer.getSelectedImagePane();
                if (viewPane != null) {
                    viewPane.resetPan();
                }
            }
        }
    }

    @Override
    public synchronized boolean updateComponentsListener(ViewCanvas<ImageElement> view2d) {
        if (view2d == null) {
            return false;
        }

        if (selectedView2dContainer == null || view2d != selectedView2dContainer.getSelectedImagePane()) {
            return false;
        }

        clearAllPropertyChangeListeners();
        Optional<SliderCineListener> cineAction = getAction(ActionW.SCROLL_SERIES, SliderCineListener.class);

        if (Objects.isNull(view2d.getSourceImage())) {
            enableActions(false);
            if (view2d.getSeries() != null) {
                // Let scrolling if only one image is corrupted in the series
                cineAction.ifPresent(a -> a.enableAction(true));
            }
            return false;
        }
        if (!enabledAction) {
            enableActions(true);
        }
        MediaSeries<ImageElement> series = view2d.getSeries();

        OpManager dispOp = view2d.getDisplayOpManager();
        ImageOpNode node = dispOp.getNode(WindowOp.OP_NAME);
        if (node != null) {
            Optional<SliderChangeListener> windowAction = getAction(ActionW.WINDOW, SliderChangeListener.class);
            Optional<SliderChangeListener> levelAction = getAction(ActionW.LEVEL, SliderChangeListener.class);
            if (windowAction.isPresent() && levelAction.isPresent()) {
                Double windowValue = (Double) node.getParam(ActionW.WINDOW.cmd());
                Double levelValue = (Double) node.getParam(ActionW.LEVEL.cmd());

                double window;
                double minLevel;
                double maxLevel;
                if (windowValue == null) {
                    windowValue = windowAction.get().getRealValue();
                }
                if (levelValue == null) {
                    levelValue = levelAction.get().getRealValue();
                }
                Double levelMin = (Double) node.getParam(ActionW.LEVEL_MIN.cmd());
                Double levelMax = (Double) node.getParam(ActionW.LEVEL_MAX.cmd());
                if (levelMin == null || levelMax == null) {
                    minLevel = levelValue - windowValue / 2.0;
                    maxLevel = levelValue + windowValue / 2.0;
                } else {
                    minLevel = levelMin;
                    maxLevel = levelMax;
                }
                window = Math.max(windowValue, maxLevel - minLevel);

                windowAction.get().setRealMinMaxValue(1.0, window, windowValue, false);
                levelAction.get().setRealMinMaxValue(minLevel, maxLevel, levelValue, false);
            }
        }

        getAction(ActionW.LUT, ComboItemListener.class).ifPresent(a -> a
            .setSelectedItemWithoutTriggerAction(dispOp.getParamValue(PseudoColorOp.OP_NAME, PseudoColorOp.P_LUT)));
        getAction(ActionW.INVERT_LUT, ToggleButtonListener.class).ifPresent(a -> a.setSelectedWithoutTriggerAction(
            (Boolean) dispOp.getParamValue(PseudoColorOp.OP_NAME, PseudoColorOp.P_LUT_INVERSE)));
        getAction(ActionW.FILTER, ComboItemListener.class).ifPresent(
            a -> a.setSelectedItemWithoutTriggerAction(dispOp.getParamValue(FilterOp.OP_NAME, FilterOp.P_KERNEL_DATA)));
        getAction(ActionW.ROTATION, SliderChangeListener.class)
            .ifPresent(a -> a.setSliderValue((Integer) view2d.getActionValue(ActionW.ROTATION.cmd()), false));
        getAction(ActionW.FLIP, ToggleButtonListener.class).ifPresent(a -> a.setSelectedWithoutTriggerAction(
            LangUtil.getNULLtoFalse((Boolean) view2d.getActionValue(ActionW.FLIP.cmd()))));

        getAction(ActionW.ZOOM, SliderChangeListener.class)
            .ifPresent(a -> a.setRealValue(Math.abs((Double) view2d.getActionValue(ActionW.ZOOM.cmd())), false));
        getAction(ActionW.SPATIAL_UNIT, ComboItemListener.class)
            .ifPresent(a -> a.setSelectedItemWithoutTriggerAction(view2d.getActionValue(ActionW.SPATIAL_UNIT.cmd())));
        getAction(ActionW.LENS, ToggleButtonListener.class)
            .ifPresent(a -> a.setSelectedWithoutTriggerAction((Boolean) view2d.getActionValue(ActionW.LENS.cmd())));
        getAction(ActionW.INVERSESTACK, ToggleButtonListener.class)
            .ifPresent(a -> a.setSelected((Boolean) view2d.getActionValue(ActionW.INVERSESTACK.cmd())));

        Double lensZoom = (Double) view2d.getLensActionValue(ActionW.ZOOM.cmd());
        if (lensZoom != null) {
            getAction(ActionW.LENSZOOM, SliderChangeListener.class)
                .ifPresent(a -> a.setRealValue(Math.abs(lensZoom), false));
        }
        cineAction.ifPresent(a -> a.setSliderMinMaxValue(1,
            series.size((Filter<ImageElement>) view2d.getActionValue(ActionW.FILTERED_SERIES.cmd())),
            view2d.getFrameIndex() + 1, false));
        final Integer speed = (Integer) series.getTagValue(TagW.get("CineRate")); //$NON-NLS-1$
        if (speed != null) {
            cineAction.ifPresent(a -> a.setSpeed(speed));
        }
        // register all actions for the selected view and for the other views register according to synchview.
        ComboItemListener synchAtction = getAction(ActionW.SYNCH, ComboItemListener.class).orElse(null);
        updateAllListeners(selectedView2dContainer,
            synchAtction == null ? SynchView.NONE : (SynchView) synchAtction.getSelectedItem());

        view2d.updateGraphicSelectionListener(selectedView2dContainer);

        return true;
    }

    public void savePreferences(BundleContext bundleContext) {
        Preferences prefs = BundlePreferences.getDefaultPreferences(bundleContext);
        zoomSetting.savePreferences(prefs);
        // Mouse buttons preferences
        mouseActions.savePreferences(prefs);
        if (prefs != null) {
            Preferences prefNode = prefs.node("mouse.sensivity"); //$NON-NLS-1$
            setSliderPreference(prefNode, ActionW.WINDOW);
            setSliderPreference(prefNode, ActionW.LEVEL);
            setSliderPreference(prefNode, ActionW.SCROLL_SERIES);
            setSliderPreference(prefNode, ActionW.ROTATION);
            setSliderPreference(prefNode, ActionW.ZOOM);

            Preferences containerNode = prefs.node(View2dContainer.class.getSimpleName().toLowerCase());
            InsertableUtil.savePreferences(View2dContainer.TOOLBARS, containerNode, Type.TOOLBAR);
            InsertableUtil.savePreferences(View2dContainer.TOOLS, containerNode, Type.TOOL);
        }
    }

    private void setSliderPreference(Preferences prefNode, ActionW action) {
        Optional<SliderChangeListener> sliderAction = getAction(action, SliderChangeListener.class);
        if (sliderAction.isPresent()) {
            BundlePreferences.putDoublePreferences(prefNode, action.cmd(), sliderAction.get().getMouseSensivity());
        }
    }

    private void getSliderPreference(Preferences prefNode, ActionW action, double defVal) {
        Optional<SliderChangeListener> sliderAction = getAction(action, SliderChangeListener.class);
        if (sliderAction.isPresent()) {
            sliderAction.get().setMouseSensivity(prefNode.getDouble(action.cmd(), defVal));
        }
    }

    public MediaSeries<ImageElement> getSelectedSeries() {
        ViewCanvas<ImageElement> pane = getSelectedViewPane();
        if (pane != null) {
            return pane.getSeries();
        }
        return null;
    }

    public JMenu getResetMenu(String prop) {
        JMenu menu = null;
        if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty(prop, true)) {
            ButtonGroup group = new ButtonGroup();
            menu = new JMenu(Messages.getString("ResetTools.reset")); //$NON-NLS-1$
            menu.setIcon(new ImageIcon(DefaultView2d.class.getResource("/icon/16x16/reset.png"))); //$NON-NLS-1$
            menu.setEnabled(getSelectedSeries() != null);

            if (menu.isEnabled()) {
                for (final ResetTools action : ResetTools.values()) {
                    final JMenuItem item = new JMenuItem(action.toString());
                    if (ResetTools.ALL.equals(action)) {
                        item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,  0));
                    }
                    item.addActionListener(e -> reset(action));
                    menu.add(item);
                    group.add(item);
                }
            }
        }
        return menu;
    }

    public JMenu getZoomMenu(String prop) {
        JMenu menu = null;
        if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty(prop, true)) {
            Optional<SliderChangeListener> zoomAction = getAction(ActionW.ZOOM, SliderChangeListener.class);
            if (zoomAction.isPresent()) {
                menu = new JMenu(ActionW.ZOOM.getTitle());
                menu.setIcon(ActionW.ZOOM.getSmallIcon());
                menu.setEnabled(zoomAction.get().isActionEnabled());

                if (zoomAction.get().isActionEnabled()) {
                    for (JMenuItem jMenuItem : ZoomToolBar.getZoomListMenuItems(this)) {
                        menu.add(jMenuItem);
                    }
                }
            }
        }
        return menu;
    }

    public JMenu getOrientationMenu(String prop) {
        JMenu menu = null;
        if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty(prop, true)) {
            Optional<SliderChangeListener> rotateAction = getAction(ActionW.ROTATION, SliderChangeListener.class);
            if (rotateAction.isPresent()) {
                menu = new JMenu(Messages.getString("View2dContainer.orientation")); //$NON-NLS-1$
                menu.setIcon(ActionW.ROTATION.getSmallIcon());
                menu.setEnabled(rotateAction.get().isActionEnabled());

                if (rotateAction.get().isActionEnabled()) {
                    JMenuItem menuItem = new JMenuItem(Messages.getString("ResetTools.reset")); //$NON-NLS-1$
                    menuItem.addActionListener(e -> rotateAction.get().setSliderValue(0));
                    menu.add(menuItem);
                    menuItem = new JMenuItem(Messages.getString("View2dContainer.-90")); //$NON-NLS-1$
                    menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.ALT_DOWN_MASK));
                    menuItem.addActionListener(
                        e -> rotateAction.get().setSliderValue((rotateAction.get().getSliderValue() - 90 + 360) % 360));
                    menu.add(menuItem);
                    menuItem = new JMenuItem(Messages.getString("View2dContainer.+90")); //$NON-NLS-1$
                    menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.ALT_DOWN_MASK));
                    menuItem.addActionListener(
                        e -> rotateAction.get().setSliderValue((rotateAction.get().getSliderValue() + 90) % 360));
                    menu.add(menuItem);
                    menuItem = new JMenuItem(Messages.getString("View2dContainer.+180")); //$NON-NLS-1$
                    menuItem.addActionListener(
                        e -> rotateAction.get().setSliderValue((rotateAction.get().getSliderValue() + 180) % 360));
                    menu.add(menuItem);

                    Optional<ToggleButtonListener> flipAction = getAction(ActionW.FLIP, ToggleButtonListener.class);
                    if (flipAction.isPresent()) {
                        menu.add(new JSeparator());
                        menuItem = flipAction.get()
                            .createUnregiteredJCheckBoxMenuItem(Messages.getString("View2dContainer.flip_h")); //$NON-NLS-1$
                        menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.ALT_DOWN_MASK));
                        menu.add(menuItem);
                    }
                }
            }
        }
        return menu;
    }

    // public JMenu getSortStackMenu(String prop) {
    // JMenu menu = null;
    // if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty(prop, true)) {
    // menu = sortStackAction.createUnregisteredRadioMenu(Messages.getString("View2dContainer.sort_stack"));
    // //$NON-NLS-1$
    //
    // menu.add(new JSeparator());
    // menu.add(inverseStackAction.createUnregiteredJCheckBoxMenuItem(Messages
    // .getString("View2dContainer.inv_stack"))); //$NON-NLS-1$
    // }
    // return menu;
    // }

    public JMenu getLutMenu(String prop) {
        JMenu menu = null;
        if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty(prop, true)) {
            Optional<ComboItemListener> lutAction = getAction(ActionW.LUT, ComboItemListener.class);
            if (lutAction.isPresent()) {
                menu = lutAction.get().createUnregisteredRadioMenu(Messages.getString("ImageTool.lut"));//$NON-NLS-1$
            }
        }
        return menu;
    }

    public JCheckBoxMenuItem getLutInverseMenu(String prop) {
        JCheckBoxMenuItem menu = null;
        if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty(prop, true)) {
            Optional<ToggleButtonListener> inverseLutAction = getAction(ActionW.INVERT_LUT, ToggleButtonListener.class);
            if (inverseLutAction.isPresent()) {
                menu = inverseLutAction.get().createUnregiteredJCheckBoxMenuItem(ActionW.INVERT_LUT.getTitle());
            }
        }
        return menu;
    }

    public JMenu getFilterMenu(String prop) {
        JMenu menu = null;
        if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty(prop, true)) {
            Optional<ComboItemListener> filterAction = getAction(ActionW.FILTER, ComboItemListener.class);
            if (filterAction.isPresent()) {
                menu = filterAction.get().createUnregisteredRadioMenu(Messages.getString("ImageTool.filter")); //$NON-NLS-1$
            }
        }
        return menu;
    }
}
