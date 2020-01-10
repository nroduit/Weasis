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

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.DataBuffer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.swing.BoundedRangeModel;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import org.dcm4che3.data.Tag;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.command.Option;
import org.weasis.core.api.command.Options;
import org.weasis.core.api.gui.Insertable.Type;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.BasicActionState;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.RadioMenuItem;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.gui.util.SliderCineListener.TIME;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.image.FilterOp;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.image.ImageOpNode;
import org.weasis.core.api.image.LutShape;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.PseudoColorOp;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.image.op.ByteLut;
import org.weasis.core.api.image.op.ByteLutCollection;
import org.weasis.core.api.image.util.KernelData;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeries.MEDIA_POSITION;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.SeriesComparator;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.api.util.LangUtil;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.editor.image.PannerListener;
import org.weasis.core.ui.editor.image.SynchCineEvent;
import org.weasis.core.ui.editor.image.SynchData;
import org.weasis.core.ui.editor.image.SynchData.Mode;
import org.weasis.core.ui.editor.image.SynchEvent;
import org.weasis.core.ui.editor.image.SynchView;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerToolBar;
import org.weasis.core.ui.editor.image.ZoomToolBar;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.utils.bean.PanPoint;
import org.weasis.core.ui.util.ColorLayerUI;
import org.weasis.core.ui.util.PrintDialog;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.PresentationStateReader;
import org.weasis.dicom.codec.SortSeriesStack;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.display.PresetWindowLevel;
import org.weasis.dicom.codec.geometry.ImageOrientation;
import org.weasis.dicom.viewer2d.mip.MipView;
import org.weasis.dicom.viewer2d.mpr.MPRContainer;
import org.weasis.dicom.viewer2d.mpr.MprView;
import org.weasis.opencv.op.ImageConversion;

/**
 * The event processing center for this application. This class responses for loading data sets, processing the events
 * from the utility menu that includes changing the operation scope, the layout, window/level, rotation angle, zoom
 * factor, starting/stoping the cining-loop and etc.
 *
 */

public class EventManager extends ImageViewerEventManager<DicomImageElement> implements ActionListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventManager.class);

    public static final List<String> functions = Collections
        .unmodifiableList(Arrays.asList("zoom", "wl", "move", "scroll", "layout", "mouseLeftAction", "synch", "reset")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$

    /** The single instance of this singleton class. */
    private static EventManager instance;

    /**
     * Return the single instance of this class. This method guarantees the singleton property of this class.
     */
    public static synchronized EventManager getInstance() {
        if (instance == null) {
            instance = new EventManager();
        }
        return instance;
    }

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

        setAction(getMoveTroughSliceAction(20, TIME.SECOND, 0.1));
        setAction(newWindowAction());
        setAction(newLevelAction());
        setAction(newRotateAction());
        setAction(newZoomAction());

        setAction(newFlipAction());
        setAction(newInverseLutAction());
        setAction(newInverseStackAction());
        setAction(newLensAction());
        setAction(newLensZoomAction());
        setAction(newDrawOnlyOnceAction());
        setAction(newDefaulPresetAction());

        setAction(newPresetAction());
        setAction(newLutShapeAction());
        setAction(newLutAction());
        setAction(newFilterAction());
        setAction(newSortStackAction());
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
        setAction(newCrosshairAction());
        setAction(new BasicActionState(ActionW.RESET));
        setAction(new BasicActionState(ActionW.SHOW_HEADER));

        setAction(newKOToggleAction());
        setAction(newKOFilterAction());
        setAction(newKOSelectionAction());

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

            /*
             * Get first the local value if exist, otherwise try to get the default server configuration and finally if
             * no value take the default value in parameter.
             */
            prefNode = prefs.node("other"); //$NON-NLS-1$
            WProperties.setProperty(options, WindowOp.P_APPLY_WL_COLOR, prefNode, Boolean.TRUE.toString());
            WProperties.setProperty(options, WindowOp.P_INVERSE_LEVEL, prefNode, Boolean.TRUE.toString());
            WProperties.setProperty(options, PRManager.PR_APPLY, prefNode, Boolean.FALSE.toString());
        }

        initializeParameters();
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

    @Override
    protected SliderCineListener getMoveTroughSliceAction(int speed, TIME time, double mouseSensivity) {
        return new SliderCineListener(ActionW.SCROLL_SERIES, 1, 2, 1, speed, time, mouseSensivity) {

            protected CineThread currentCine;

            @Override
            public void stateChanged(BoundedRangeModel model) {

                ViewCanvas<DicomImageElement> view2d = null;
                Series<DicomImageElement> series = null;
                SynchCineEvent mediaEvent = null;
                DicomImageElement image = null;
                Optional<ToggleButtonListener> defaultPresetAction =
                    getAction(ActionW.DEFAULT_PRESET, ToggleButtonListener.class);
                boolean isDefaultPresetSelected =
                    defaultPresetAction.isPresent() ? defaultPresetAction.get().isSelected() : true;

                if (selectedView2dContainer != null) {
                    view2d = selectedView2dContainer.getSelectedImagePane();
                }

                if (view2d != null && view2d.getSeries() instanceof Series) {
                    series = (Series<DicomImageElement>) view2d.getSeries();
                    if (series != null) {
                        // Model contains display value, value-1 is the index value of a sequence
                        int index = model.getValue() - 1;
                        image = series.getMedia(index,
                            (Filter<DicomImageElement>) view2d.getActionValue(ActionW.FILTERED_SERIES.cmd()),
                            view2d.getCurrentSortComparator());
                        mediaEvent = new SynchCineEvent(view2d, image, index);
                        // Ensure to load image before calling the default preset (requires pixel min and max)
                        if (image != null && !image.isImageAvailable()) {
                            image.getImage();
                        }
                    }
                }

                Optional<ComboItemListener> layoutAction = getAction(ActionW.LAYOUT, ComboItemListener.class);
                Optional<ComboItemListener> synchAction = getAction(ActionW.SYNCH, ComboItemListener.class);

                if (image != null && layoutAction.isPresent() && View2dFactory
                    .getViewTypeNumber((GridBagLayoutModel) layoutAction.get().getSelectedItem(), ViewCanvas.class) > 1
                    && synchAction.isPresent()) {

                    SynchView synchview = (SynchView) synchAction.get().getSelectedItem();
                    if (synchview.getSynchData().isActionEnable(ActionW.SCROLL_SERIES.cmd())) {
                        double[] val = (double[]) image.getTagValue(TagW.SlicePosition);
                        if (val != null) {
                            mediaEvent.setLocation(val[0] + val[1] + val[2]);
                        }
                    } else {
                        if (selectedView2dContainer != null) {
                            final List<ViewCanvas<DicomImageElement>> panes = selectedView2dContainer.getImagePanels();
                            for (ViewCanvas<DicomImageElement> p : panes) {
                                Boolean cutlines = (Boolean) p.getActionValue(ActionW.SYNCH_CROSSLINE.cmd());
                                if (cutlines != null && cutlines) {
                                    double[] val = (double[]) image.getTagValue(TagW.SlicePosition);
                                    if (val != null) {
                                        mediaEvent.setLocation(val[0] + val[1] + val[2]);
                                    } else {
                                        return; // Do not throw event
                                    }
                                    break;
                                }
                            }
                        }
                    }
                }

                Optional<SliderChangeListener> windowAction = getAction(ActionW.WINDOW, SliderChangeListener.class);
                Optional<SliderChangeListener> levelAction = getAction(ActionW.LEVEL, SliderChangeListener.class);
                if (view2d != null && image != view2d.getImage() && image != null && windowAction.isPresent()
                    && levelAction.isPresent()) {
                    Optional<ComboItemListener> presetAction = getAction(ActionW.PRESET, ComboItemListener.class);
                    PresetWindowLevel oldPreset =
                        presetAction.isPresent() ? (PresetWindowLevel) presetAction.get().getSelectedItem() : null;
                    PresetWindowLevel newPreset = null;
                    boolean pixelPadding = LangUtil.getNULLtoTrue((Boolean) view2d.getDisplayOpManager()
                        .getParamValue(WindowOp.OP_NAME, ActionW.IMAGE_PIX_PADDING.cmd()));

                    List<PresetWindowLevel> newPresetList = image.getPresetList(pixelPadding);

                    // Assume the image cannot display when win =1 and level = 0
                    if (oldPreset != null
                        || (windowAction.get().getSliderValue() <= 1 && levelAction.get().getSliderValue() == 0)) {
                        if (isDefaultPresetSelected) {
                            newPreset = image.getDefaultPreset(pixelPadding);
                        } else {
                            if (oldPreset != null) {
                                for (PresetWindowLevel preset : newPresetList) {
                                    if (preset.getName().equals(oldPreset.getName())) {
                                        newPreset = preset;
                                        break;
                                    }
                                }
                            }
                            // set default preset when the old preset is not available any more
                            if (newPreset == null) {
                                newPreset = image.getDefaultPreset(pixelPadding);
                                isDefaultPresetSelected = true;
                            }
                        }
                    }

                    Optional<ComboItemListener> lutShapeAction = getAction(ActionW.LUT_SHAPE, ComboItemListener.class);
                    Double windowValue = newPreset == null ? windowAction.get().getRealValue() : newPreset.getWindow();
                    Double levelValue = newPreset == null ? levelAction.get().getRealValue() : newPreset.getLevel();
                    LutShape lutShapeItem = newPreset == null
                        ? lutShapeAction.isPresent() ? (LutShape) lutShapeAction.get().getSelectedItem() : null
                        : newPreset.getLutShape();

                    Double levelMin =
                        (Double) view2d.getDisplayOpManager().getParamValue(WindowOp.OP_NAME, ActionW.LEVEL_MIN.cmd());
                    Double levelMax =
                        (Double) view2d.getDisplayOpManager().getParamValue(WindowOp.OP_NAME, ActionW.LEVEL_MAX.cmd());

                    PresentationStateReader prReader =
                        (PresentationStateReader) view2d.getActionValue(PresentationStateReader.TAG_PR_READER);
                    if (levelMin == null || levelMax == null) {
                        levelMin = Math.min(levelValue - windowValue / 2.0, image.getMinValue(prReader, pixelPadding));
                        levelMax = Math.max(levelValue + windowValue / 2.0, image.getMaxValue(prReader, pixelPadding));
                    } else {
                        levelMin = Math.min(levelMin, image.getMinValue(prReader, pixelPadding));
                        levelMax = Math.max(levelMax, image.getMaxValue(prReader, pixelPadding));
                    }

                    // FIX : setting actionInView here without firing a propertyChange avoid another call to
                    // imageLayer.updateImageOperation(WindowOp.name.....
                    // TODO pass to mediaEvent with PR and KO

                    ImageOpNode node = view2d.getDisplayOpManager().getNode(WindowOp.OP_NAME);
                    if (node != null) {
                        node.setParam(ActionW.PRESET.cmd(), newPreset);
                        node.setParam(ActionW.DEFAULT_PRESET.cmd(), isDefaultPresetSelected);
                        node.setParam(ActionW.WINDOW.cmd(), windowValue);
                        node.setParam(ActionW.LEVEL.cmd(), levelValue);
                        node.setParam(ActionW.LEVEL_MIN.cmd(), levelMin);
                        node.setParam(ActionW.LEVEL_MAX.cmd(), levelMax);
                        node.setParam(ActionW.LUT_SHAPE.cmd(), lutShapeItem);
                    }
                    updateWindowLevelComponentsListener(image, view2d);

                }

                firePropertyChange(ActionW.SYNCH.cmd(), null, mediaEvent);
                if (image != null) {
                    fireSeriesViewerListeners(
                        new SeriesViewerEvent(selectedView2dContainer, series, image, EVENT.SELECT));
                }

                updateKeyObjectComponentsListener(view2d);

            }

            @Override
            public void setSpeed(int speed) {
                super.setSpeed(speed);
                if (currentCine != null) {
                    currentCine.iniSpeed();
                }
            }

            /** Create a thread to cine the images. */

            class CineThread extends Thread {

                private AtomicInteger iteration;
                private volatile int waitTimeMillis;
                private volatile int currentCineRate;
                private volatile long start;
                private volatile boolean cining = true;

                @Override
                public void run() {
                    iniSpeed();
                    while (cining) {
                        long startFrameTime = System.currentTimeMillis();
                        // Set the value to SliderCineListener, must be in EDT for refreshing UI correctly
                        GuiExecutor.instance().invokeAndWait(() -> {
                            if (cining) {
                                int frameIndex = getSliderValue() + 1;
                                setSliderValue(frameIndex > getSliderMax() ? 0 : frameIndex);
                            }
                        });
                        // Time to set the new frame index
                        long elapsedFrame = System.currentTimeMillis() - startFrameTime;
                        /*
                         * If this time is smaller than the time to wait according to the cine speed (fps), then wait
                         * the time left, otherwise continue (that means the cine speed cannot be reached)
                         */
                        if (elapsedFrame < waitTimeMillis) {
                            try {
                                Thread.sleep(waitTimeMillis - elapsedFrame);
                            } catch (Exception e) {
                            }
                        }

                        // Check the speed every 3 images
                        if (iteration.incrementAndGet() > 2) {
                            // Get the speed rate (fps) on the last 3 images
                            currentCineRate = (int) (iteration.get() * 1000 / (System.currentTimeMillis() - start));
                            // reinitialize the parameters for computing speed next time
                            iteration.set(0);
                            waitTimeMillis = 1000 / getSpeed();
                            start = System.currentTimeMillis();
                        }
                    }
                }

                public void iniSpeed() {
                    iteration = new AtomicInteger(0);
                    currentCineRate = getSpeed();
                    waitTimeMillis = 1000 / currentCineRate;
                    start = System.currentTimeMillis();
                }

                public int getCurrentCineRate() {
                    return currentCineRate;
                }
            }

            /** Start the cining. */

            @Override
            public synchronized void start() {
                if (currentCine != null) {
                    stop();
                }
                if (getSliderMax() - getSliderMin() > 0) {
                    currentCine = new CineThread();
                    currentCine.start();
                }
            }

            /** Stop the cining. */

            @Override
            public synchronized void stop() {
                CineThread moribund = currentCine;
                currentCine = null;
                if (moribund != null) {
                    moribund.cining = false;
                    moribund.interrupt();
                }
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (isActionEnabled()) {
                    setSliderValue(getSliderValue() + e.getWheelRotation());
                }
            }

            @Override
            public int getCurrentCineRate() {
                if (currentCine != null) {
                    return currentCine.getCurrentCineRate();
                }
                return 0;
            }

            @Override
            public boolean isCining() {
                return currentCine != null;
            }

        };
    }

    @Override
    protected SliderChangeListener newWindowAction() {

        return new SliderChangeListener(ActionW.WINDOW, WINDOW_SMALLEST, WINDOW_LARGEST, WINDOW_DEFAULT, true, 1.25) {

            @Override
            public void stateChanged(BoundedRangeModel model) {
                updatePreset(getActionW().cmd(), toModelValue(model.getValue()));
            }
        };
    }

    @Override
    protected SliderChangeListener newLevelAction() {
        return new SliderChangeListener(ActionW.LEVEL, LEVEL_SMALLEST, LEVEL_LARGEST, LEVEL_DEFAULT, true, 1.25) {

            @Override
            public void stateChanged(BoundedRangeModel model) {
                updatePreset(getActionW().cmd(), toModelValue(model.getValue()));
            }
        };
    }

    protected void updatePreset(String cmd, Object object) {
        String command = cmd;
        boolean isDefaultPresetSelected = false;
        Optional<ComboItemListener> presetAction = getAction(ActionW.PRESET, ComboItemListener.class);
        if (ActionW.PRESET.cmd().equals(command) && object instanceof PresetWindowLevel) {
            PresetWindowLevel preset = (PresetWindowLevel) object;
            getAction(ActionW.WINDOW, SliderChangeListener.class)
                .ifPresent(a -> a.setSliderValue(a.toSliderValue(preset.getWindow()), false));
            getAction(ActionW.LEVEL, SliderChangeListener.class)
                .ifPresent(a -> a.setSliderValue(a.toSliderValue(preset.getLevel()), false));
            getAction(ActionW.LUT_SHAPE, ComboItemListener.class)
                .ifPresent(a -> a.setSelectedItemWithoutTriggerAction(preset.getLutShape()));

            PresetWindowLevel defaultPreset =
                presetAction.isPresent() ? (PresetWindowLevel) presetAction.get().getFirstItem() : null;
            isDefaultPresetSelected = defaultPreset == null ? false : preset.equals(defaultPreset);
        } else {
            presetAction.ifPresent(
                a -> a.setSelectedItemWithoutTriggerAction(object instanceof PresetWindowLevel ? object : null));
        }

        Optional<ToggleButtonListener> defaultPresetAction =
            getAction(ActionW.DEFAULT_PRESET, ToggleButtonListener.class);
        if (defaultPresetAction.isPresent()) {
            defaultPresetAction.get().setSelectedWithoutTriggerAction(isDefaultPresetSelected);
            SynchEvent evt = new SynchEvent(getSelectedViewPane(), ActionW.DEFAULT_PRESET.cmd(),
                defaultPresetAction.get().isSelected());
            evt.put(command, object);
            firePropertyChange(ActionW.SYNCH.cmd(), null, evt);
        }

        if (selectedView2dContainer != null) {
            fireSeriesViewerListeners(
                new SeriesViewerEvent(selectedView2dContainer, null, null, EVENT.WIN_LEVEL));
        }
    }

    private ComboItemListener<PresetWindowLevel> newPresetAction() {
        return new ComboItemListener<PresetWindowLevel>(ActionW.PRESET, null) {

            @Override
            public void itemStateChanged(Object object) {
                updatePreset(getActionW().cmd(), object);
            }
        };
    }

    private ComboItemListener<LutShape> newLutShapeAction() {
        return new ComboItemListener<LutShape>(ActionW.LUT_SHAPE,
            LutShape.DEFAULT_FACTORY_FUNCTIONS.toArray(new LutShape[LutShape.DEFAULT_FACTORY_FUNCTIONS.size()])) {

            @Override
            public void itemStateChanged(Object object) {
                updatePreset(action.cmd(), object);
            }
        };
    }

    private ToggleButtonListener newDefaulPresetAction() {
        return new ToggleButtonListener(ActionW.DEFAULT_PRESET, true) {
            @Override
            public void actionPerformed(boolean selected) {
                firePropertyChange(ActionW.SYNCH.cmd(), null,
                    new SynchEvent(getSelectedViewPane(), action.cmd(), selected));
            }
        };
    }

    private ToggleButtonListener newKOToggleAction() {
        return new ToggleButtonListener(ActionW.KO_TOOGLE_STATE, false) {
            @Override
            public void actionPerformed(boolean newSelectedState) {

                boolean hasKeyObjectReferenceChanged =
                    KOManager.setKeyObjectReference(newSelectedState, getSelectedViewPane());

                if (!hasKeyObjectReferenceChanged) {
                    // If KO Toogle State hasn't changed this action should be reset to its previous state, that is the
                    // current view's actionValue
                    this.setSelectedWithoutTriggerAction(
                        (Boolean) getSelectedViewPane().getActionValue(ActionW.KO_TOOGLE_STATE.cmd()));
                }
            }
        };
    }

    private ComboItemListener<Object> newKOSelectionAction() {
        return new ComboItemListener<Object>(ActionW.KO_SELECTION,
            new ActionState.NoneLabel[] { ActionState.NoneLabel.NONE }) {
            @Override
            public void itemStateChanged(Object object) {
                koAction(action, object);
            }
        };
    }

    private ToggleButtonListener newKOFilterAction() {
        return new ToggleButtonListener(ActionW.KO_FILTER, false) {
            @Override
            public void actionPerformed(boolean selected) {
                koAction(action, selected);
            }
        };
    }

    private void koAction(ActionW action, Object selected) {
        Optional<ComboItemListener> synchAction = getAction(ActionW.SYNCH, ComboItemListener.class);
        SynchView synchView = synchAction.isPresent() ? (SynchView) synchAction.get().getSelectedItem() : null;
        boolean tileMode = synchView != null && SynchData.Mode.TILE.equals(synchView.getSynchData().getMode());
        ViewCanvas<DicomImageElement> selectedView = getSelectedViewPane();
        if (tileMode) {
            if (selectedView2dContainer instanceof View2dContainer && selectedView != null) {
                View2dContainer container = (View2dContainer) selectedView2dContainer;
                boolean filterSelection = selected instanceof Boolean;
                Object selectedKO =
                    filterSelection ? selectedView.getActionValue(ActionW.KO_SELECTION.cmd()) : selected;
                Boolean enableFilter =
                    (Boolean) (filterSelection ? selected : selectedView.getActionValue(ActionW.KO_FILTER.cmd()));
                ViewCanvas<DicomImageElement> viewPane = container.getSelectedImagePane();
                int frameIndex =
                    LangUtil.getNULLtoFalse(enableFilter) ? 0 : viewPane.getFrameIndex() - viewPane.getTileOffset();

                for (ViewCanvas<DicomImageElement> view : container.getImagePanels(true)) {
                    if (!(view.getSeries() instanceof DicomSeries) || !(view instanceof View2d)) {
                        continue;
                    }

                    KOManager.updateKOFilter(view, selectedKO, enableFilter, frameIndex, false);
                }

                container.updateTileOffset();
                if (!(selectedView.getSeries() instanceof DicomSeries)) {
                    List<ViewCanvas<DicomImageElement>> panes = selectedView2dContainer.getImagePanels(false);
                    if (!panes.isEmpty()) {
                        selectedView2dContainer.setSelectedImagePane(panes.get(0));
                        return;
                    }
                }
            }
        }

        firePropertyChange(ActionW.SYNCH.cmd(), null, new SynchEvent(selectedView, action.cmd(), selected));

        if (tileMode) {
            Optional<SliderCineListener> cineAction = getAction(ActionW.SCROLL_SERIES, SliderCineListener.class);
            if (cineAction.isPresent() && cineAction.get().isActionEnabled()) {
                SliderCineListener moveTroughSliceAction = cineAction.get();
                if (moveTroughSliceAction.getSliderValue() == 1) {
                    moveTroughSliceAction.stateChanged(moveTroughSliceAction.getSliderModel());
                } else {
                    moveTroughSliceAction.setSliderValue(1);
                }
            }
        }
    }

    private ComboItemListener<ByteLut> newLutAction() {
        List<ByteLut> luts = new ArrayList<>();
        luts.add(ByteLutCollection.Lut.GRAY.getByteLut());
        ByteLutCollection.readLutFilesFromResourcesDir(luts, ResourceUtil.getResource("luts"));//$NON-NLS-1$
        // Set default first as the list has been sorted
        luts.add(0, ByteLutCollection.Lut.IMAGE.getByteLut());

        return new ComboItemListener<ByteLut>(ActionW.LUT, luts.toArray(new ByteLut[luts.size()])) {

            @Override
            public void itemStateChanged(Object object) {
                firePropertyChange(ActionW.SYNCH.cmd(), null,
                    new SynchEvent(getSelectedViewPane(), action.cmd(), object));
                if (selectedView2dContainer != null) {
                    fireSeriesViewerListeners(
                        new SeriesViewerEvent(selectedView2dContainer, null, null, EVENT.LUT));
                }
            }
        };
    }

    private ComboItemListener<SeriesComparator<DicomImageElement>> newSortStackAction() {
        return new ComboItemListener<SeriesComparator<DicomImageElement>>(ActionW.SORTSTACK,
            SortSeriesStack.getValues()) {

            @Override
            public void itemStateChanged(Object object) {
                firePropertyChange(ActionW.SYNCH.cmd(), null,
                    new SynchEvent(getSelectedViewPane(), action.cmd(), object));
            }
        };
    }

    @Override
    public Optional<ActionW> getLeftMouseActionFromkeyEvent(int keyEvent, int modifier) {

        Optional<ActionW> action = super.getLeftMouseActionFromkeyEvent(keyEvent, modifier);
        if (!action.isPresent()) {
            return action;
        }
        // Only return the action if it is enabled
        if (Optional.ofNullable(getAction(action.get())).filter(ActionState::isActionEnabled).isPresent()) {
            return action;
        } else if (ActionW.KO_TOOGLE_STATE.equals(action.get()) && keyEvent == ActionW.KO_TOOGLE_STATE.getKeyCode()) {
            Optional<ToggleButtonListener> koToggleAction =
                getAction(ActionW.KO_TOOGLE_STATE, ToggleButtonListener.class);
            if (koToggleAction.isPresent()) {
                koToggleAction.get().setSelected(!koToggleAction.get().isSelected());
            }
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
            ImageViewerPlugin<DicomImageElement> view = getSelectedView2dContainer();
            if (view != null) {
                ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(view);
                PrintDialog dialog = new PrintDialog(SwingUtilities.getWindowAncestor(view),
                    Messages.getString("View2dContainer.print_layout"), this); //$NON-NLS-1$
                ColorLayerUI.showCenterScreen(dialog, layer);
            }
        } else {
            Optional<ComboItemListener> presetAction = getAction(ActionW.PRESET, ComboItemListener.class);
            if (modifiers == 0 && presetAction.isPresent() && presetAction.get().isActionEnabled()) {
                ComboItemListener presetComboListener = presetAction.get();
                DefaultComboBoxModel model = presetComboListener.getModel();
                for (int i = 0; i < model.getSize(); i++) {
                    PresetWindowLevel val = (PresetWindowLevel) model.getElementAt(i);
                    if (val.getKeyCode() == keyEvent) {
                        presetComboListener.setSelectedItem(val);
                        return;
                    }
                }
            }

            triggerDrawingToolKeyEvent(keyEvent, modifiers);
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        // Do nothing
    }

    @Override
    public void setSelectedView2dContainer(ImageViewerPlugin<DicomImageElement> selectedView2dContainer) {
        if (this.selectedView2dContainer != null) {
            this.selectedView2dContainer.setMouseActions(null);
            getAction(ActionW.SCROLL_SERIES, SliderCineListener.class).ifPresent(SliderCineListener::stop);
        }

        ImageViewerPlugin<DicomImageElement> oldContainer = this.selectedView2dContainer;
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
                ViewCanvas<DicomImageElement> pane = oldContainer.getSelectedImagePane();
                if (pane != null) {
                    pane.setFocused(false);
                }
            }
            synchAction.ifPresent(a -> a.setSelectedItemWithoutTriggerAction(selectedView2dContainer.getSynchView()));
            layoutAction.ifPresent(
                a -> a.setSelectedItemWithoutTriggerAction(selectedView2dContainer.getOriginalLayoutModel()));
            updateComponentsListener(selectedView2dContainer.getSelectedImagePane());
            selectedView2dContainer.setMouseActions(mouseActions);
            ViewCanvas<DicomImageElement> pane = selectedView2dContainer.getSelectedImagePane();
            if (pane != null) {
                pane.setFocused(true);
                fireSeriesViewerListeners(
                    new SeriesViewerEvent(selectedView2dContainer, pane.getSeries(), null, EVENT.SELECT_VIEW));
            }
        }
    }

    /** process the action events. */

    @Override
    public void actionPerformed(ActionEvent evt) {
        cinePlay(evt.getActionCommand());
    }

    private void cinePlay(String command) {
        if (command != null) {
            if (command.equals(ActionW.CINESTART.cmd())) {
                getAction(ActionW.SCROLL_SERIES, SliderCineListener.class).ifPresent(SliderCineListener::start);
            } else if (command.equals(ActionW.CINESTOP.cmd())) {
                getAction(ActionW.SCROLL_SERIES, SliderCineListener.class).ifPresent(SliderCineListener::stop);
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
            getAction(ActionW.PRESET, ComboItemListener.class).ifPresent(a -> a.setSelectedItem(a.getFirstItem()));
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
    public synchronized boolean updateComponentsListener(ViewCanvas<DicomImageElement> view2d) {
        if (view2d == null) {
            return false;
        }

        if (selectedView2dContainer == null || view2d != selectedView2dContainer.getSelectedImagePane()) {
            return false;
        }

        clearAllPropertyChangeListeners();
        Optional<SliderCineListener> cineAction = getAction(ActionW.SCROLL_SERIES, SliderCineListener.class);

        if (view2d.getSourceImage() == null) {
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

        OpManager dispOp = view2d.getDisplayOpManager();

        updateWindowLevelComponentsListener(view2d.getImage(), view2d);

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
        Double lensZoom = (Double) view2d.getLensActionValue(ActionW.ZOOM.cmd());
        if (lensZoom != null) {
            getAction(ActionW.LENSZOOM, SliderChangeListener.class)
                .ifPresent(a -> a.setRealValue(Math.abs(lensZoom), false));
        }

        MediaSeries<DicomImageElement> series = view2d.getSeries();
        cineAction.ifPresent(a -> a.setSliderMinMaxValue(1,
            series.size((Filter<DicomImageElement>) view2d.getActionValue(ActionW.FILTERED_SERIES.cmd())),
            view2d.getFrameIndex() + 1, false));
        Integer cineRate = TagD.getTagValue(series, Tag.CineRate, Integer.class);
        final Integer speed =
            cineRate == null ? TagD.getTagValue(series, Tag.RecommendedDisplayFrameRate, Integer.class) : cineRate;
        if (speed != null) {
            cineAction.ifPresent(a -> a.setSpeed(speed));
        }

        getAction(ActionW.SORTSTACK, ComboItemListener.class)
            .ifPresent(a -> a.setSelectedItemWithoutTriggerAction(view2d.getActionValue(ActionW.SORTSTACK.cmd())));
        getAction(ActionW.INVERSESTACK, ToggleButtonListener.class).ifPresent(
            a -> a.setSelectedWithoutTriggerAction((Boolean) view2d.getActionValue(ActionW.INVERSESTACK.cmd())));

        updateKeyObjectComponentsListener(view2d);

        // register all actions for the selected view and for the other views register according to synchview.
        ComboItemListener synchAtction = getAction(ActionW.SYNCH, ComboItemListener.class).orElse(null);
        updateAllListeners(selectedView2dContainer,
            synchAtction == null ? SynchView.NONE : (SynchView) synchAtction.getSelectedItem());

        view2d.updateGraphicSelectionListener(selectedView2dContainer);
        return true;
    }

    public void updateKeyObjectComponentsListener(ViewCanvas<DicomImageElement> view2d) {
        if (view2d != null) {
            Optional<ComboItemListener> koSelectionAction = getAction(ActionW.KO_SELECTION, ComboItemListener.class);
            Optional<ToggleButtonListener> koToggleAction =
                getAction(ActionW.KO_TOOGLE_STATE, ToggleButtonListener.class);
            Optional<ToggleButtonListener> koFilterAction = getAction(ActionW.KO_FILTER, ToggleButtonListener.class);
            if (LangUtil.getNULLtoFalse((Boolean) view2d.getActionValue("no.ko"))) { //$NON-NLS-1$
                koToggleAction.ifPresent(a -> a.enableAction(false));
                koFilterAction.ifPresent(a -> a.enableAction(false));
                koSelectionAction.ifPresent(a -> a.enableAction(false));
            } else {
                koToggleAction.ifPresent(a -> a
                    .setSelectedWithoutTriggerAction((Boolean) view2d.getActionValue(ActionW.KO_TOOGLE_STATE.cmd())));
                koFilterAction.ifPresent(
                    a -> a.setSelectedWithoutTriggerAction((Boolean) view2d.getActionValue(ActionW.KO_FILTER.cmd())));

                Object[] kos = KOManager.getKOElementListWithNone(view2d).toArray();
                boolean enable = kos.length > 1;
                if (enable) {
                    koSelectionAction.ifPresent(a -> a.setDataListWithoutTriggerAction(kos));
                    koSelectionAction.ifPresent(
                        a -> a.setSelectedItemWithoutTriggerAction(view2d.getActionValue(ActionW.KO_SELECTION.cmd())));
                }
                koFilterAction.ifPresent(a -> a.enableAction(enable));
                koSelectionAction.ifPresent(a -> a.enableAction(enable));
            }
        }
    }

    private void updateWindowLevelComponentsListener(DicomImageElement image, ViewCanvas<DicomImageElement> view2d) {

        ImageOpNode node = view2d.getDisplayOpManager().getNode(WindowOp.OP_NAME);
        if (node != null) {
            int imageDataType = ImageConversion.convertToDataType(image.getImage().type());
            PresetWindowLevel preset = (PresetWindowLevel) node.getParam(ActionW.PRESET.cmd());
            boolean defaultPreset = LangUtil.getNULLtoTrue((Boolean) node.getParam(ActionW.DEFAULT_PRESET.cmd()));
            Double windowValue = (Double) node.getParam(ActionW.WINDOW.cmd());
            Double levelValue = (Double) node.getParam(ActionW.LEVEL.cmd());
            LutShape lutShapeItem = (LutShape) node.getParam(ActionW.LUT_SHAPE.cmd());
            boolean pixelPadding = LangUtil.getNULLtoTrue((Boolean) node.getParam(ActionW.IMAGE_PIX_PADDING.cmd()));
            PresentationStateReader prReader =
                (PresentationStateReader) view2d.getActionValue(PresentationStateReader.TAG_PR_READER);

            getAction(ActionW.DEFAULT_PRESET, ToggleButtonListener.class)
                .ifPresent(a -> a.setSelectedWithoutTriggerAction(defaultPreset));

            Optional<SliderChangeListener> windowAction = getAction(ActionW.WINDOW, SliderChangeListener.class);
            Optional<SliderChangeListener> levelAction = getAction(ActionW.LEVEL, SliderChangeListener.class);
            if (windowAction.isPresent() && levelAction.isPresent()) {
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
                    minLevel = Math.min(levelValue - windowValue / 2.0, image.getMinValue(prReader, pixelPadding));
                    maxLevel = Math.max(levelValue + windowValue / 2.0, image.getMaxValue(prReader, pixelPadding));
                } else {
                    minLevel = Math.min(levelMin, image.getMinValue(prReader, pixelPadding));
                    maxLevel = Math.max(levelMax, image.getMaxValue(prReader, pixelPadding));
                }
                window = Math.max(windowValue, maxLevel - minLevel);

                windowAction.get().setRealMinMaxValue(imageDataType >= DataBuffer.TYPE_FLOAT ? 0.00001 : 1.0, window,
                    windowValue, false);
                levelAction.get().setRealMinMaxValue(minLevel, maxLevel, levelValue, false);
            }

            List<PresetWindowLevel> presetList = image.getPresetList(pixelPadding);
            if (prReader != null) {
                List<PresetWindowLevel> prPresets =
                    (List<PresetWindowLevel>) view2d.getActionValue(PRManager.PR_PRESETS);
                if (prPresets != null && !prPresets.isEmpty()) {
                    presetList = prPresets;
                }
            }

            Optional<ComboItemListener> presetAction = getAction(ActionW.PRESET, ComboItemListener.class);
            if (presetAction.isPresent()) {
                presetAction.get().setDataListWithoutTriggerAction(presetList == null ? null : presetList.toArray());
                presetAction.get().setSelectedItemWithoutTriggerAction(preset);
            }

            Optional<ComboItemListener> lutShapeAction = getAction(ActionW.LUT_SHAPE, ComboItemListener.class);
            if (lutShapeAction.isPresent()) {
                Collection<LutShape> lutShapeList = imageDataType >= DataBuffer.TYPE_INT
                    ? Arrays.asList(LutShape.LINEAR) : image.getLutShapeCollection(pixelPadding);
                if (prReader != null && lutShapeList != null && lutShapeItem != null
                    && !lutShapeList.contains(lutShapeItem)) {
                    // Make a copy of the image list
                    ArrayList<LutShape> newList = new ArrayList<>(lutShapeList.size() + 1);
                    newList.add(lutShapeItem);
                    newList.addAll(lutShapeList);
                    lutShapeList = newList;
                }
                lutShapeAction.get()
                    .setDataListWithoutTriggerAction(lutShapeList == null ? null : lutShapeList.toArray());
                lutShapeAction.get().setSelectedItemWithoutTriggerAction(lutShapeItem);
            }
        }
    }

    @Override
    protected boolean isCompatible(MediaSeries<DicomImageElement> series1, MediaSeries<DicomImageElement> series2) {
        // Have the two series the same image plane orientation
        return ImageOrientation.hasSameOrientation(series1, series2);
    }

    @Override
    public void updateAllListeners(ImageViewerPlugin<DicomImageElement> viewerPlugin, SynchView synchView) {
        clearAllPropertyChangeListeners();

        if (viewerPlugin != null) {
            ViewCanvas<DicomImageElement> viewPane = viewerPlugin.getSelectedImagePane();
            if (viewPane == null) {
                return;
            }
            SynchData synch = synchView.getSynchData();
            MediaSeries<DicomImageElement> series = viewPane.getSeries();
            if (series != null) {
                SynchData oldSynch = (SynchData) viewPane.getActionValue(ActionW.SYNCH_LINK.cmd());
                if (oldSynch == null || !oldSynch.getMode().equals(synch.getMode())) {
                    oldSynch = synch;
                }
                viewPane.setActionsInView(ActionW.SYNCH_LINK.cmd(), null);
                addPropertyChangeListener(ActionW.SYNCH.cmd(), viewPane);

                Optional<SliderCineListener> cineAction = getAction(ActionW.SCROLL_SERIES, SliderCineListener.class);
                // if (viewPane instanceof MipView) {
                // // Handle special case with MIP view, do not let scroll the series
                // moveTroughSliceAction.enableAction(false);
                // synchView = SynchView.NONE;
                // synch = synchView.getSynchData();
                // } else {
                cineAction.ifPresent(a -> a.enableAction(true));
                // }
                final List<ViewCanvas<DicomImageElement>> panes = viewerPlugin.getImagePanels();
                panes.remove(viewPane);
                viewPane.setActionsInView(ActionW.SYNCH_CROSSLINE.cmd(), false);

                if (SynchView.NONE.equals(synchView)) {
                    for (int i = 0; i < panes.size(); i++) {
                        ViewCanvas<DicomImageElement> pane = panes.get(i);
                        pane.getGraphicManager().deleteByLayerType(LayerType.CROSSLINES);

                        MediaSeries<DicomImageElement> s = pane.getSeries();
                        String fruid = TagD.getTagValue(series, Tag.FrameOfReferenceUID, String.class);
                        boolean specialView = pane instanceof MipView;
                        if (s != null && fruid != null && !specialView) {
                            if (fruid.equals(TagD.getTagValue(s, Tag.FrameOfReferenceUID))) {
                                if (!ImageOrientation.hasSameOrientation(series, s)) {
                                    pane.setActionsInView(ActionW.SYNCH_CROSSLINE.cmd(), true);
                                    propertySupport.addPropertyChangeListener(ActionW.SCROLL_SERIES.cmd(), pane);
                                }
                                // Force to draw crosslines without changing the slice position
                                cineAction.ifPresent(a -> a.stateChanged(a.getSliderModel()));
                            }
                        }
                        oldSynch = (SynchData) pane.getActionValue(ActionW.SYNCH_LINK.cmd());
                        if (oldSynch == null || !oldSynch.getMode().equals(synch.getMode())) {
                            oldSynch = synch;
                        }
                        pane.setActionsInView(ActionW.SYNCH_LINK.cmd(), oldSynch);
                        // pane.updateSynchState();
                    }
                } else {
                    // TODO if Pan is activated than rotation is required
                    if (Mode.STACK.equals(synch.getMode())) {
                        String fruid = TagD.getTagValue(series, Tag.FrameOfReferenceUID, String.class);
                        DicomImageElement img = series.getMedia(MEDIA_POSITION.MIDDLE, null, null);
                        double[] val = img == null ? null : (double[]) img.getTagValue(TagW.SlicePosition);

                        for (int i = 0; i < panes.size(); i++) {
                            ViewCanvas<DicomImageElement> pane = panes.get(i);
                            pane.getGraphicManager().deleteByLayerType(LayerType.CROSSLINES);

                            MediaSeries<DicomImageElement> s = pane.getSeries();
                            boolean specialView = pane instanceof MipView;
                            if (s != null && fruid != null && val != null && !specialView) {
                                boolean synchByDefault = fruid.equals(TagD.getTagValue(s, Tag.FrameOfReferenceUID));
                                oldSynch = (SynchData) pane.getActionValue(ActionW.SYNCH_LINK.cmd());
                                if (synchByDefault) {
                                    if (ImageOrientation.hasSameOrientation(series, s)) {
                                        pane.setActionsInView(ActionW.SYNCH_CROSSLINE.cmd(), false);
                                        // Only fully synch if no PR is applied (because can change pixel size)
                                        if (pane.getActionValue(PresentationStateReader.TAG_PR_READER) == null
                                            && hasSameSize(series, s)) {
                                            // If the image has the same reference and the same spatial calibration, all
                                            // the actions are synchronized
                                            if (oldSynch == null || oldSynch.isOriginal()
                                                || !oldSynch.getMode().equals(synch.getMode())) {
                                                oldSynch = synch.copy();
                                            }
                                        } else {
                                            if (oldSynch == null || oldSynch.isOriginal()
                                                || !oldSynch.getMode().equals(synch.getMode())) {
                                                oldSynch = synch.copy();
                                                for (Entry<String, Boolean> a : oldSynch.getActions().entrySet()) {
                                                    a.setValue(false);
                                                }
                                                oldSynch.getActions().put(ActionW.SCROLL_SERIES.cmd(), true);
                                            }
                                        }
                                    } else {
                                        pane.setActionsInView(ActionW.SYNCH_CROSSLINE.cmd(), true);
                                        if (pane instanceof MprView) {
                                            if (oldSynch == null || oldSynch.isOriginal()
                                                || !oldSynch.getMode().equals(synch.getMode())) {
                                                oldSynch = synch.copy();
                                            }
                                        } else {
                                            if (oldSynch == null || oldSynch.isOriginal()
                                                || !oldSynch.getMode().equals(synch.getMode())) {
                                                oldSynch = synch.copy();
                                                for (Entry<String, Boolean> a : oldSynch.getActions().entrySet()) {
                                                    a.setValue(false);
                                                }
                                                oldSynch.getActions().put(ActionW.SCROLL_SERIES.cmd(), true);
                                            }
                                        }
                                    }
                                    addPropertyChangeListener(ActionW.SYNCH.cmd(), pane);
                                }
                                pane.setActionsInView(ActionW.SYNCH_LINK.cmd(), oldSynch);
                                // pane.updateSynchState();
                            }
                        }
                        // Force to draw crosslines without changing the slice position
                        cineAction.ifPresent(a -> a.stateChanged(a.getSliderModel()));

                    } else if (Mode.TILE.equals(synch.getMode())) {
                        // Limit the scroll
                        final int maxShift = series
                            .size((Filter<DicomImageElement>) viewPane.getActionValue(ActionW.FILTERED_SERIES.cmd()))
                            - panes.size();
                        cineAction.ifPresent(a -> a.setSliderMinMaxValue(1, maxShift < 1 ? 1 : maxShift,
                            viewPane.getFrameIndex() + 1, false));

                        Object selectedKO = viewPane.getActionValue(ActionW.KO_SELECTION.cmd());
                        Boolean enableFilter = (Boolean) viewPane.getActionValue(ActionW.KO_FILTER.cmd());
                        int frameIndex = LangUtil.getNULLtoFalse(enableFilter) ? 0
                            : viewPane.getFrameIndex() - viewPane.getTileOffset();
                        for (int i = 0; i < panes.size(); i++) {
                            ViewCanvas<DicomImageElement> pane = panes.get(i);
                            oldSynch = (SynchData) pane.getActionValue(ActionW.SYNCH_LINK.cmd());
                            if (oldSynch == null || !oldSynch.getMode().equals(synch.getMode())) {
                                oldSynch = synch.copy();
                            }
                            oldSynch.getActions().put(ActionW.KO_SELECTION.cmd(), true);
                            oldSynch.getActions().put(ActionW.KO_FILTER.cmd(), true);
                            KOManager.updateKOFilter(pane, selectedKO, enableFilter, frameIndex);

                            pane.setActionsInView(ActionW.SYNCH_LINK.cmd(), oldSynch);
                            pane.setActionsInView(ActionW.SYNCH_CROSSLINE.cmd(), false);
                            addPropertyChangeListener(ActionW.SYNCH.cmd(), pane);
                            // pane.updateSynchState();
                        }

                        if (LangUtil.getNULLtoFalse(enableFilter)) {
                            KOManager.updateKOFilter(viewPane, selectedKO, enableFilter, frameIndex);
                        }
                    }
                }
            }

            // viewPane.updateSynchState();
        }
    }

    public static boolean hasSameSize(MediaSeries<DicomImageElement> series1, MediaSeries<DicomImageElement> series2) {
        // Test if the two series has the same size
        if (series1 != null && series2 != null) {
            DicomImageElement image1 = series1.getMedia(MEDIA_POSITION.MIDDLE, null, null);
            DicomImageElement image2 = series2.getMedia(MEDIA_POSITION.MIDDLE, null, null);
            if (image1 != null && image2 != null) {
                return image1.hasSameSize(image2);
            }
        }
        return false;
    }

    public void savePreferences(BundleContext bundleContext) {
        Preferences prefs = BundlePreferences.getDefaultPreferences(bundleContext);
        zoomSetting.savePreferences(prefs);
        // Mouse buttons preferences
        mouseActions.savePreferences(prefs);
        if (prefs != null) {
            // Mouse sensitivity
            Preferences prefNode = prefs.node("mouse.sensivity"); //$NON-NLS-1$
            setSliderPreference(prefNode, ActionW.WINDOW);
            setSliderPreference(prefNode, ActionW.LEVEL);
            setSliderPreference(prefNode, ActionW.SCROLL_SERIES);
            setSliderPreference(prefNode, ActionW.ROTATION);
            setSliderPreference(prefNode, ActionW.ZOOM);

            prefNode = prefs.node("other"); //$NON-NLS-1$
            BundlePreferences.putBooleanPreferences(prefNode, WindowOp.P_APPLY_WL_COLOR,
                options.getBooleanProperty(WindowOp.P_APPLY_WL_COLOR, true));
            BundlePreferences.putBooleanPreferences(prefNode, WindowOp.P_INVERSE_LEVEL,
                options.getBooleanProperty(WindowOp.P_INVERSE_LEVEL, true));
            BundlePreferences.putBooleanPreferences(prefNode, PRManager.PR_APPLY,
                options.getBooleanProperty(PRManager.PR_APPLY, false));

            Preferences containerNode = prefs.node(View2dContainer.class.getSimpleName().toLowerCase());
            InsertableUtil.savePreferences(View2dContainer.TOOLBARS, containerNode, Type.TOOLBAR);
            InsertableUtil.savePreferences(View2dContainer.TOOLS, containerNode, Type.TOOL);

            InsertableUtil.savePreferences(MPRContainer.TOOLBARS,
                prefs.node(MPRContainer.class.getSimpleName().toLowerCase()), Type.TOOLBAR);
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

    public MediaSeries<DicomImageElement> getSelectedSeries() {
        ViewCanvas<DicomImageElement> pane = getSelectedViewPane();
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
                        item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
                    }
                    item.addActionListener(e -> reset(action));
                    menu.add(item);
                    group.add(item);
                }
            }
        }
        return menu;
    }

    public JMenu getPresetMenu(String prop) {
        JMenu menu = null;
        if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty(prop, true)) {
            Optional<ComboItemListener> presetAction = getAction(ActionW.PRESET, ComboItemListener.class);
            if (presetAction.isPresent()) {
                menu = presetAction.get().createUnregisteredRadioMenu(Messages.getString("View2dContainer.presets"));//$NON-NLS-1$
                menu.setIcon(ActionW.WINLEVEL.getSmallIcon());
                for (Component mitem : menu.getMenuComponents()) {
                    RadioMenuItem ritem = (RadioMenuItem) mitem;
                    PresetWindowLevel preset = (PresetWindowLevel) ritem.getUserObject();
                    if (preset.getKeyCode() > 0) {
                        ritem.setAccelerator(KeyStroke.getKeyStroke(preset.getKeyCode(), 0));
                    }
                }
            }
        }
        return menu;
    }

    public JMenu getLutShapeMenu(String prop) {
        JMenu menu = null;
        if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty(prop, true)) {
            Optional<ComboItemListener> lutShapeAction = getAction(ActionW.LUT_SHAPE, ComboItemListener.class);
            if (lutShapeAction.isPresent()) {
                menu = lutShapeAction.get().createUnregisteredRadioMenu(ActionW.LUT_SHAPE.getTitle());
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
                        e -> rotateAction.get().setSliderValue((rotateAction.get().getSliderValue() + 270) % 360));
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

    public JMenu getSortStackMenu(String prop) {
        JMenu menu = null;
        if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty(prop, true)) {
            Optional<ComboItemListener> sortStackAction = getAction(ActionW.SORTSTACK, ComboItemListener.class);
            if (sortStackAction.isPresent()) {
                menu =
                    sortStackAction.get().createUnregisteredRadioMenu(Messages.getString("View2dContainer.sort_stack")); //$NON-NLS-1$
                Optional<ToggleButtonListener> inverseStackAction =
                    getAction(ActionW.INVERSESTACK, ToggleButtonListener.class);
                if (inverseStackAction.isPresent()) {
                    menu.add(new JSeparator());
                    menu.add(inverseStackAction.get()
                        .createUnregiteredJCheckBoxMenuItem(Messages.getString("View2dContainer.inv_stack"))); //$NON-NLS-1$
                }
            }
        }
        return menu;
    }

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

    // ***** OSGI commands: dcmview2d:cmd ***** //

    public void zoom(String[] argv) throws IOException {
        final String[] usage = { "Change the zoom value of the selected image", //$NON-NLS-1$
            "Usage: dcmview2d:zoom (set VALUE | increase NUMBER | decrease NUMBER)", //$NON-NLS-1$
            "  -s --set=VALUE        [decimal value]  set a new value from 0.0 to 12.0 (zoom magnitude, 0.0 => default, -200.0 => best fit, -100.0 => real size)", //$NON-NLS-1$
            "  -i --increase=NUMBER  increase of some amount", //$NON-NLS-1$
            "  -d --decrease=NUMBER  decrease of some amount", //$NON-NLS-1$
            "  -? --help             show help" }; //$NON-NLS-1$
        final Option opt = Options.compile(usage).parse(argv);
        final List<String> args = opt.args();

        if (opt.isSet("help") || !opt.isOnlyOneOptionActivate("set", "increase", "decrease")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            opt.usage();
            return;
        }

        GuiExecutor.instance().execute(() -> zoomCommand(opt, args));
    }

    private void zoomCommand(Option opt, List<String> args) {
        try {
            Optional<SliderChangeListener> zoomAction = getAction(ActionW.ZOOM, SliderChangeListener.class);
            if (zoomAction.isPresent()) {
                if (opt.isSet("increase")) { //$NON-NLS-1$
                    zoomAction.get().setSliderValue(zoomAction.get().getSliderValue() + opt.getNumber("increase")); //$NON-NLS-1$
                } else if (opt.isSet("decrease")) { //$NON-NLS-1$
                    zoomAction.get().setSliderValue(zoomAction.get().getSliderValue() - opt.getNumber("decrease")); //$NON-NLS-1$
                } else if (opt.isSet("set")) { //$NON-NLS-1$
                    double val3 = Double.parseDouble(opt.get("set")); //$NON-NLS-1$
                    if (val3 <= 0.0) {
                        firePropertyChange(ActionW.SYNCH.cmd(), null,
                            new SynchEvent(getSelectedViewPane(), ActionW.ZOOM.cmd(), val3));
                    } else {
                        zoomAction.get().setRealValue(val3);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Zoom command: {}", args.get(0), e); //$NON-NLS-1$
        }
    }

    public void wl(String[] argv) throws IOException {
        final String[] usage = {
            "Change the window/level values of the selected image (increase or decrease into a normalized range of 4096)", //$NON-NLS-1$
            "Usage: dcmview2d:wl -- WIN LEVEL", //$NON-NLS-1$
            "WIN and LEVEL are Integer. It is mandatory to have '--' (end of options) for negative values", //$NON-NLS-1$
            "  -? --help       show help" }; //$NON-NLS-1$
        final Option opt = Options.compile(usage).parse(argv);
        final List<String> args = opt.args();

        if (opt.isSet("help") || args.size() != 2) { //$NON-NLS-1$
            opt.usage();
            return;
        }

        GuiExecutor.instance().execute(() -> {
            try {
                Optional<SliderChangeListener> windowAction = getAction(ActionW.WINDOW, SliderChangeListener.class);
                Optional<SliderChangeListener> levelAction = getAction(ActionW.LEVEL, SliderChangeListener.class);
                if (windowAction.isPresent() && levelAction.isPresent()) {
                    int win = windowAction.get().getSliderValue() + Integer.parseInt(args.get(0));
                    int level = levelAction.get().getSliderValue() + Integer.parseInt(args.get(1));
                    windowAction.get().setSliderValue(win);
                    levelAction.get().setSliderValue(level);
                }
            } catch (Exception e) {
                LOGGER.error("Window/level command: {} {}", args.get(0), args.get(1), e); //$NON-NLS-1$
            }
        });
    }

    public void move(String[] argv) throws IOException {
        final String[] usage = { "Pan the selected image", //$NON-NLS-1$
            "Usage: dcmview2d:move -- X Y", //$NON-NLS-1$
            "X and Y are Integer. It is mandatory to have '--' (end of options) for negative values", //$NON-NLS-1$
            "  -? --help       show help" }; //$NON-NLS-1$
        final Option opt = Options.compile(usage).parse(argv);
        final List<String> args = opt.args();

        if (opt.isSet("help") || args.size() != 2) { //$NON-NLS-1$
            opt.usage();
            return;
        }

        GuiExecutor.instance().execute(() -> {
            try {
                int valx = Integer.parseInt(args.get(0));
                int valy = Integer.parseInt(args.get(1));
                getAction(ActionW.PAN, PannerListener.class)
                    .ifPresent(a -> a.setPoint(new PanPoint(PanPoint.State.MOVE, valx, valy)));

            } catch (Exception e) {
                LOGGER.error("Move (x,y) command: {} {}", args.get(0), args.get(1), e); //$NON-NLS-1$
            }
        });
    }

    public void scroll(String[] argv) throws IOException {
        final String[] usage = { "Scroll into the images of the selected series", //$NON-NLS-1$
            "Usage: dcmview2d:scroll ( -s NUMBER | -i NUMBER | -d NUMBER)", //$NON-NLS-1$
            "  -s --set=NUMBER       set a new value from 1 to series size", //$NON-NLS-1$
            "  -i --increase=NUMBER  increase of some amount", //$NON-NLS-1$
            "  -d --decrease=NUMBER  decrease of some amount", //$NON-NLS-1$
            "  -? --help             show help" }; //$NON-NLS-1$
        final Option opt = Options.compile(usage).parse(argv);

        if (opt.isSet("help") || !opt.isOnlyOneOptionActivate("set", "increase", "decrease")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            opt.usage();
            return;
        }

        GuiExecutor.instance().execute(() -> {
            try {
                Optional<SliderCineListener> cineAction = getAction(ActionW.SCROLL_SERIES, SliderCineListener.class);
                if (cineAction.isPresent() && cineAction.get().isActionEnabled()) {
                    SliderCineListener moveTroughSliceAction = cineAction.get();
                    if (opt.isSet("increase")) { //$NON-NLS-1$
                        moveTroughSliceAction
                            .setSliderValue(moveTroughSliceAction.getSliderValue() + opt.getNumber("increase")); //$NON-NLS-1$
                    } else if (opt.isSet("decrease")) { //$NON-NLS-1$
                        moveTroughSliceAction
                            .setSliderValue(moveTroughSliceAction.getSliderValue() - opt.getNumber("decrease")); //$NON-NLS-1$
                    } else if (opt.isSet("set")) { //$NON-NLS-1$
                        moveTroughSliceAction.setSliderValue(opt.getNumber("set")); //$NON-NLS-1$
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Scroll command error:", e); //$NON-NLS-1$
            }
        });
    }

    public void layout(String[] argv) throws IOException {
        final String[] usage = { "Select a split-screen layout", //$NON-NLS-1$
            "Usage: dcmview2d:layout ( -n NUMBER | -i ID )", //$NON-NLS-1$
            "  -n --number=NUMBER  select the best matching number of views", //$NON-NLS-1$
            "  -i --id=ID          select the layout from its identifier", //$NON-NLS-1$
            "  -? --help           show help" }; //$NON-NLS-1$
        final Option opt = Options.compile(usage).parse(argv);

        if (opt.isSet("help") || !opt.isOnlyOneOptionActivate("number", "id")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            opt.usage();
            return;
        }
        GuiExecutor.instance().execute(() -> {
            try {
                if (opt.isSet("number")) { //$NON-NLS-1$
                    if (selectedView2dContainer != null) {
                        GridBagLayoutModel val1 =
                            selectedView2dContainer.getBestDefaultViewLayout(opt.getNumber("number")); //$NON-NLS-1$
                        getAction(ActionW.LAYOUT, ComboItemListener.class).ifPresent(a -> a.setSelectedItem(val1));
                    }
                } else if (opt.isSet("id")) { //$NON-NLS-1$
                    if (selectedView2dContainer != null) {
                        GridBagLayoutModel val2 = selectedView2dContainer.getViewLayout(opt.get("id")); //$NON-NLS-1$
                        if (val2 != null) {
                            getAction(ActionW.LAYOUT, ComboItemListener.class).ifPresent(a -> a.setSelectedItem(val2));
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Layout command error", e); //$NON-NLS-1$
            }
        });
    }

    public void mouseLeftAction(String[] argv) throws IOException {
        final String[] usage = { "Change the mouse left action", //$NON-NLS-1$
            "Usage: dcmview2d:mouseLeftAction COMMAND", //$NON-NLS-1$
            "COMMAND is (sequence|winLevel|zoom|pan|rotation|crosshair|measure|draw|contextMenu|none)", //$NON-NLS-1$
            "  -? --help       show help" }; //$NON-NLS-1$
        final Option opt = Options.compile(usage).parse(argv);
        final List<String> args = opt.args();

        if (opt.isSet("help") || args.size() != 1) { //$NON-NLS-1$
            opt.usage();
            return;
        }

        GuiExecutor.instance().execute(() -> {
            String command = args.get(0);
            if (command != null) {
                try {
                    if (command.startsWith("session")) { //$NON-NLS-1$
                        AuditLog.LOGGER.info("source:telnet {}", command); //$NON-NLS-1$
                    } else {
                        AuditLog.LOGGER.info("source:telnet mouse:{} action:{}", MouseActions.T_LEFT, command); //$NON-NLS-1$
                        excecuteMouseAction(command);
                    }
                } catch (Exception e) {
                    LOGGER.error("Mouse command: {}", command, e); //$NON-NLS-1$
                }
            }
        });
    }

    private void excecuteMouseAction(String command) {
        if (!command.equals(mouseActions.getAction(MouseActions.T_LEFT))) {
            mouseActions.setAction(MouseActions.T_LEFT, command);
            ImageViewerPlugin<DicomImageElement> view = getSelectedView2dContainer();
            if (view != null) {
                view.setMouseActions(mouseActions);
                final ViewerToolBar toolBar = view.getViewerToolBar();
                if (toolBar != null) {
                    // Test if mouse action exist and if not NO_ACTION is set
                    ActionW action = toolBar.getAction(ViewerToolBar.actionsButtons, command);
                    if (action == null) {
                        command = ActionW.NO_ACTION.cmd();
                    }
                    toolBar.changeButtonState(MouseActions.T_LEFT, command);
                }
            }
        }
    }

    public void synch(String[] argv) throws IOException {
        final String[] usage = { "Set a synchronization mode", //$NON-NLS-1$
            "Usage: dcmview2d:synch VALUE", //$NON-NLS-1$
            "VALUE is " + View2dContainer.DEFAULT_SYNCH_LIST.stream().map(SynchView::getCommand) //$NON-NLS-1$
                .collect(Collectors.joining("|", "(", ")")), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            "  -? --help       show help" }; //$NON-NLS-1$
        final Option opt = Options.compile(usage).parse(argv);
        final List<String> args = opt.args();

        if (opt.isSet("help") || args.size() != 1) { //$NON-NLS-1$
            opt.usage();
            return;
        }
        GuiExecutor.instance().execute(() -> {
            String command = args.get(0);
            if (command != null) {
                ImageViewerPlugin<DicomImageElement> view = getSelectedView2dContainer();
                if (view != null) {
                    try {
                        Optional<ComboItemListener> synchAction = getAction(ActionW.SYNCH, ComboItemListener.class);
                        if (synchAction.isPresent()) {
                            for (SynchView synch : view.getSynchList()) {
                                if (synch.getCommand().equals(command)) {
                                    synchAction.get().setSelectedItem(synch);
                                    return;
                                }
                            }
                            throw new IllegalArgumentException(command + " not found!"); //$NON-NLS-1$
                        }
                    } catch (Exception e) {
                        LOGGER.error("Synch command: {}", command, e); //$NON-NLS-1$
                    }
                }
            }
        });
    }

    public void reset(String[] argv) throws IOException {
        final String[] usage = { "Reset image display", //$NON-NLS-1$
            "Usage: dcmview2d:reset (-a | COMMAND...)", //$NON-NLS-1$
            "COMMAND is (winLevel|zoom|pan|rotation)", //$NON-NLS-1$
            "  -a --all        reset to original display", //$NON-NLS-1$
            "  -? --help       show help" }; //$NON-NLS-1$
        final Option opt = Options.compile(usage).parse(argv);
        final List<String> args = opt.args();

        if (opt.isSet("help") || args.isEmpty() && !opt.isSet("all")) { //$NON-NLS-1$ //$NON-NLS-2$
            opt.usage();
            return;
        }

        GuiExecutor.instance().execute(() -> {
            if (opt.isSet("all")) { //$NON-NLS-1$
                reset(ResetTools.ALL);
            } else {
                for (String command : args) {
                    try {
                        if (ActionW.WINLEVEL.cmd().equals(command)) {
                            reset(ResetTools.WL);
                        } else if (ActionW.ZOOM.cmd().equals(command)) {
                            reset(ResetTools.ZOOM);
                        } else if (ActionW.PAN.cmd().equals(command)) {
                            reset(ResetTools.PAN);
                        } else if (ActionW.ROTATION.cmd().equals(command)) {
                            reset(ResetTools.ROTATION);
                        } else {
                            LOGGER.warn("Reset command not found: {}", command); //$NON-NLS-1$
                        }
                    } catch (Exception e) {
                        LOGGER.error("Reset command: {}", command, e); //$NON-NLS-1$
                    }
                }
            }
        });
    }

}
