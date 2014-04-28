/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.dicom.viewer2d;

import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseWheelEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;

import javax.swing.BoundedRangeModel;
import javax.swing.DefaultComboBoxModel;

import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.prefs.BackingStoreException;
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
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.gui.util.SliderCineListener.TIME;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.image.FilterOp;
import org.weasis.core.api.image.FlipOp;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.image.ImageOpNode;
import org.weasis.core.api.image.LutShape;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.PseudoColorOp;
import org.weasis.core.api.image.RotationOp;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.image.util.KernelData;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeries.MEDIA_POSITION;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.image.CrosshairListener;
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
import org.weasis.core.ui.editor.image.ViewerToolBar;
import org.weasis.core.ui.graphic.AngleToolGraphic;
import org.weasis.core.ui.graphic.Graphic;
import org.weasis.core.ui.graphic.LineGraphic;
import org.weasis.core.ui.graphic.model.AbstractLayer;
import org.weasis.core.ui.graphic.model.GraphicsListener;
import org.weasis.core.ui.pref.ViewSetting;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.PresentationStateReader;
import org.weasis.dicom.codec.SortSeriesStack;
import org.weasis.dicom.codec.display.LutManager;
import org.weasis.dicom.codec.display.PresetWindowLevel;
import org.weasis.dicom.codec.display.ViewingProtocols;
import org.weasis.dicom.codec.geometry.ImageOrientation;
import org.weasis.dicom.viewer2d.mpr.MPRContainer;
import org.weasis.dicom.viewer2d.mpr.MprView;

/**
 * The event processing center for this application. This class responses for loading data sets, processing the events
 * from the utility menu that includes changing the operation scope, the layout, window/level, rotation angle, zoom
 * factor, starting/stoping the cining-loop and etc.
 * 
 */

public class EventManager extends ImageViewerEventManager<DicomImageElement> implements ActionListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventManager.class);

    public static final String[] functions = {
        "zoom", "wl", "move", "scroll", "layout", "mouseLeftAction", "synch", "reset" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$

    private static ActionW[] keyEventActions = { ActionW.ZOOM, ActionW.SCROLL_SERIES, ActionW.ROTATION,
        ActionW.WINLEVEL, ActionW.PAN, ActionW.MEASURE, ActionW.CONTEXTMENU, ActionW.NO_ACTION };

    /** The single instance of this singleton class. */

    private static EventManager instance;

    private final SliderCineListener moveTroughSliceAction;
    private final SliderChangeListener windowAction;
    private final SliderChangeListener levelAction;
    private final SliderChangeListener rotateAction;
    private final SliderChangeListener zoomAction;
    private final SliderChangeListener lensZoomAction;

    private final ToggleButtonListener flipAction;
    private final ToggleButtonListener inverseLutAction;
    private final ToggleButtonListener inverseStackAction;
    private final ToggleButtonListener showLensAction;
    // private final ToggleButtonListener imageOverlayAction;
    private final ToggleButtonListener drawOnceAction;
    private final ToggleButtonListener defaultPresetAction;

    private final ComboItemListener presetAction;
    private final ComboItemListener lutShapeAction;
    private final ComboItemListener lutAction;
    private final ComboItemListener filterAction;
    private final ComboItemListener sortStackAction;
    private final ComboItemListener viewingProtocolAction;
    private final ComboItemListener layoutAction;
    private final ComboItemListener synchAction;
    private final ComboItemListener measureAction;
    private final ComboItemListener spUnitAction;

    private final ToggleButtonListener koToggleAction;
    private final ToggleButtonListener koFilterAction;
    private final ComboItemListener koSelectionAction;

    private final PannerListener panAction;
    private final CrosshairListener crosshairAction;

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
        iniAction(moveTroughSliceAction = getMoveTroughSliceAction(20, TIME.second, 0.1));
        iniAction(windowAction = newWindowAction());
        iniAction(levelAction = newLevelAction());
        iniAction(rotateAction = newRotateAction());
        iniAction(zoomAction = newZoomAction());

        iniAction(flipAction = newFlipAction());
        iniAction(inverseLutAction = newInverseLutAction());
        iniAction(inverseStackAction = newInverseStackAction());
        iniAction(showLensAction = newLensAction());
        iniAction(lensZoomAction = newLensZoomAction());
        // iniAction(imageOverlayAction = newImageOverlayAction());
        iniAction(drawOnceAction = newDrawOnlyOnceAction());
        iniAction(defaultPresetAction = newDefaulPresetAction());

        iniAction(presetAction = newPresetAction());
        iniAction(lutShapeAction = newLutShapeAction());
        iniAction(lutAction = newLutAction());
        iniAction(filterAction = newFilterAction());
        iniAction(sortStackAction = newSortStackAction());
        iniAction(viewingProtocolAction = newViewingProtocolAction());
        iniAction(layoutAction =
            newLayoutAction(View2dContainer.LAYOUT_LIST.toArray(new GridBagLayoutModel[View2dContainer.LAYOUT_LIST
                .size()])));
        iniAction(synchAction =
            newSynchAction(View2dContainer.SYNCH_LIST.toArray(new SynchView[View2dContainer.SYNCH_LIST.size()])));
        synchAction.setSelectedItemWithoutTriggerAction(SynchView.DEFAULT_STACK);
        iniAction(measureAction =
            newMeasurementAction(MeasureToolBar.graphicList.toArray(new Graphic[MeasureToolBar.graphicList.size()])));
        iniAction(spUnitAction = newSpatialUnit(Unit.values()));
        iniAction(panAction = newPanAction());
        iniAction(crosshairAction = newCrosshairAction());
        iniAction(new BasicActionState(ActionW.RESET));

        iniAction(koToggleAction = newKOToggleAction());
        iniAction(koFilterAction = newKOFilterAction());
        iniAction(koSelectionAction = newKOSelectionAction());

        final BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
        Preferences prefs = BundlePreferences.getDefaultPreferences(context);
        zoomSetting.applyPreferences(prefs);
        mouseActions.applyPreferences(prefs);

        if (prefs != null) {
            Preferences prefNode = prefs.node("mouse.sensivity"); //$NON-NLS-1$
            windowAction.setMouseSensivity(prefNode.getDouble(windowAction.getActionW().cmd(), 1.25));
            levelAction.setMouseSensivity(prefNode.getDouble(levelAction.getActionW().cmd(), 1.25));
            moveTroughSliceAction.setMouseSensivity(prefNode.getDouble(moveTroughSliceAction.getActionW().cmd(), 0.1));
            rotateAction.setMouseSensivity(prefNode.getDouble(rotateAction.getActionW().cmd(), 0.25));
            zoomAction.setMouseSensivity(prefNode.getDouble(zoomAction.getActionW().cmd(), 0.1));

            prefNode = prefs.node("other"); //$NON-NLS-1$
            WProperties.setProperty(options, WindowOp.P_APPLY_WL_COLOR, prefNode, Boolean.FALSE.toString());
        }

        initializeParameters();
    }

    private void iniAction(ActionState action) {
        actions.put(action.getActionW(), action);
    }

    private void initializeParameters() {
        enableActions(false);
    }

    private ComboItemListener newFilterAction() {
        return new ComboItemListener(ActionW.FILTER, KernelData.ALL_FILTERS) {

            @Override
            public void itemStateChanged(Object object) {
                if (object instanceof KernelData) {
                    firePropertyChange(ActionW.SYNCH.cmd(), null, new SynchEvent(getSelectedViewPane(), action.cmd(),
                        object));
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

                DefaultView2d<DicomImageElement> view2d = null;
                Series<DicomImageElement> series = null;
                SynchCineEvent mediaEvent = null;
                DicomImageElement image = null;
                boolean isDefaultPresetSelected = defaultPresetAction.isSelected();

                if (selectedView2dContainer != null) {
                    view2d = selectedView2dContainer.getSelectedImagePane();
                }

                if (view2d != null && view2d.getSeries() instanceof Series) {
                    series = (Series<DicomImageElement>) view2d.getSeries();
                    if (series != null) {
                        // Model contains display value, value-1 is the index value of a sequence
                        int index = model.getValue() - 1;
                        image =
                            series.getMedia(index,
                                (Filter<DicomImageElement>) view2d.getActionValue(ActionW.FILTERED_SERIES.cmd()),
                                view2d.getCurrentSortComparator());
                        mediaEvent = new SynchCineEvent(view2d, image, index);
                        // Ensure to load image before calling the default preset (requires pixel min and max)
                        if (image != null && !image.isImageAvailable()) {
                            image.getImage();
                        }
                    }
                }

                GridBagLayoutModel layout = (GridBagLayoutModel) layoutAction.getSelectedItem();
                ActionState synch = getAction(ActionW.SYNCH);

                if (image != null && View2dFactory.getViewTypeNumber(layout, DefaultView2d.class) > 1
                    && synch instanceof ComboItemListener) {

                    SynchView synchview = (SynchView) ((ComboItemListener) synch).getSelectedItem();
                    if (synchview.getSynchData().isActionEnable(ActionW.SCROLL_SERIES.cmd())) {
                        double[] val = (double[]) image.getTagValue(TagW.SlicePosition);
                        if (val != null) {
                            mediaEvent.setLocation(val[0] + val[1] + val[2]);
                        }
                    } else {
                        if (selectedView2dContainer != null) {
                            final ArrayList<DefaultView2d<DicomImageElement>> panes =
                                selectedView2dContainer.getImagePanels();
                            for (DefaultView2d<DicomImageElement> p : panes) {
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

                if (view2d != null && image != view2d.getImage()) {
                    PresetWindowLevel oldPreset = (PresetWindowLevel) presetAction.getSelectedItem();
                    PresetWindowLevel newPreset = null;
                    boolean pixelPadding =
                        JMVUtils.getNULLtoTrue(view2d.getDisplayOpManager().getParamValue(WindowOp.OP_NAME,
                            ActionW.IMAGE_PIX_PADDING.cmd()));
                    List<PresetWindowLevel> newPresetList = image.getPresetList(pixelPadding);
                    // Assume the image cannot display when win =1 and level = 0
                    if (oldPreset != null || (windowAction.getValue() <= 1 && levelAction.getValue() == 0)) {
                        if (isDefaultPresetSelected) {
                            newPreset = image.getDefaultPreset(pixelPadding);
                        } else {
                            for (PresetWindowLevel preset : newPresetList) {
                                if (preset.getName().equals(oldPreset.getName())) {
                                    newPreset = preset;
                                    break;
                                }
                            }
                            // set default preset when the old preset is not available any more
                            if (newPreset == null) {
                                newPreset = image.getDefaultPreset(pixelPadding);
                                isDefaultPresetSelected = true;
                            }
                        }
                    }

                    Float windowValue = newPreset == null ? windowAction.getValue() : newPreset.getWindow();
                    Float levelValue = newPreset == null ? levelAction.getValue() : newPreset.getLevel();
                    LutShape lutShapeItem =
                        newPreset == null ? (LutShape) lutShapeAction.getSelectedItem() : newPreset.getLutShape();

                    Float levelMin =
                        (Float) view2d.getDisplayOpManager().getParamValue(WindowOp.OP_NAME, ActionW.LEVEL_MIN.cmd());
                    Float levelMax =
                        (Float) view2d.getDisplayOpManager().getParamValue(WindowOp.OP_NAME, ActionW.LEVEL_MAX.cmd());
                    if (newPreset == null) {
                        if (levelMin == null || levelMax == null) {
                            levelMin = Math.min(levelValue - windowValue / 2.0f, image.getMinValue(pixelPadding));
                            levelMax = Math.max(levelValue + windowValue / 2.0f, image.getMaxValue(pixelPadding));
                        } else {
                            levelMin = Math.min(levelMin, image.getMinValue(pixelPadding));
                            levelMax = Math.max(levelMax, image.getMaxValue(pixelPadding));
                        }
                    } else {
                        levelMin = newPreset.getMinBox();
                        levelMax = newPreset.getMaxBox();
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
                    fireSeriesViewerListeners(new SeriesViewerEvent(selectedView2dContainer, series, image,
                        EVENT.SELECT));
                }

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

                private volatile int iteration;
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
                        GuiExecutor.instance().invokeAndWait(new Runnable() {

                            @Override
                            public void run() {
                                if (cining) {
                                    int frameIndex = getValue() + 1;
                                    setValue(frameIndex > getMax() ? 0 : frameIndex);
                                }
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

                        iteration++;
                        // Check the speed every 3 images
                        if (iteration > 2) {
                            // Get the speed rate (fps) on the last 3 images
                            currentCineRate = (int) (iteration * 1000 / (System.currentTimeMillis() - start));
                            // reinitialize the parameters for computing speed next time
                            iteration = 0;
                            waitTimeMillis = 1000 / getSpeed();
                            start = System.currentTimeMillis();
                        }
                    }
                }

                public void iniSpeed() {
                    iteration = 0;
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
                if (getMax() - getMin() > 0) {
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
                    setValue(getValue() + e.getWheelRotation());
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
                updatePreset(getActionW().cmd(), model.getValue());
            }
        };
    }

    @Override
    protected SliderChangeListener newLevelAction() {
        return new SliderChangeListener(ActionW.LEVEL, LEVEL_SMALLEST, LEVEL_LARGEST, LEVEL_DEFAULT, true, 1.25) {

            @Override
            public void stateChanged(BoundedRangeModel model) {
                updatePreset(getActionW().cmd(), model.getValue());
            }
        };
    }

    protected void updatePreset(String cmd, Object object) {
        String command = cmd;
        boolean isDefaultPresetSelected = false;
        if (ActionW.PRESET.cmd().equals(command) && object instanceof PresetWindowLevel) {
            PresetWindowLevel preset = (PresetWindowLevel) object;

            int minLevel = (int) preset.getMinBox();
            int maxLevel = (int) preset.getMaxBox();
            int window = preset.getWindow().intValue();

            windowAction.setMinMaxValueWithoutTriggerAction(1, window, window);
            levelAction.setMinMaxValueWithoutTriggerAction(minLevel, maxLevel, preset.getLevel().intValue());
            lutShapeAction.setSelectedItemWithoutTriggerAction(preset.getLutShape());

            PresetWindowLevel defaultPreset = (PresetWindowLevel) presetAction.getFirstItem();
            isDefaultPresetSelected = defaultPreset == null ? false : preset.equals(defaultPreset);
        } else {
            presetAction.setSelectedItemWithoutTriggerAction(object instanceof PresetWindowLevel ? object : null);
        }
        defaultPresetAction.setSelectedWithoutTriggerAction(isDefaultPresetSelected);
        SynchEvent evt =
            new SynchEvent(getSelectedViewPane(), ActionW.DEFAULT_PRESET.cmd(), defaultPresetAction.isSelected());
        evt.put(command, object);
        firePropertyChange(ActionW.SYNCH.cmd(), null, evt);
    };

    private ComboItemListener newPresetAction() {
        return new ComboItemListener(ActionW.PRESET, null) {

            @Override
            public void itemStateChanged(Object object) {
                updatePreset(getActionW().cmd(), object);
            }
        };
    }

    private ComboItemListener newLutShapeAction() {
        return new ComboItemListener(ActionW.LUT_SHAPE, LutShape.DEFAULT_FACTORY_FUNCTIONS.toArray()) {

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
                firePropertyChange(ActionW.SYNCH.cmd(), null, new SynchEvent(getSelectedViewPane(), action.cmd(),
                    selected));
            }
        };
    }

    private ToggleButtonListener newKOToggleAction() {
        return new ToggleButtonListener(ActionW.KO_TOOGLE_STATE, false) {
            @Override
            public void actionPerformed(boolean selected) {
                firePropertyChange(ActionW.SYNCH.cmd(), null, new SynchEvent(getSelectedViewPane(), action.cmd(),
                    selected));
            }
        };
    }

    private ToggleButtonListener newKOFilterAction() {
        return new ToggleButtonListener(ActionW.KO_FILTER, false) {
            @Override
            public void actionPerformed(boolean selected) {
                firePropertyChange(ActionW.SYNCH.cmd(), null, new SynchEvent(getSelectedViewPane(), action.cmd(),
                    selected));
            }
        };
    }

    private ComboItemListener newKOSelectionAction() {
        return new ComboItemListener(ActionW.KO_SELECTION, new String[] { ActionState.NONE }) {
            @Override
            public void itemStateChanged(Object object) {
                firePropertyChange(ActionW.SYNCH.cmd(), null, new SynchEvent(getSelectedViewPane(), action.cmd(),
                    object));
            }
        };
    }

    private ComboItemListener newLutAction() {
        return new ComboItemListener(ActionW.LUT, LutManager.getLutCollection()) {

            @Override
            public void itemStateChanged(Object object) {
                firePropertyChange(ActionW.SYNCH.cmd(), null, new SynchEvent(getSelectedViewPane(), action.cmd(),
                    object));
            }
        };
    }

    private ComboItemListener newSortStackAction() {
        return new ComboItemListener(ActionW.SORTSTACK, SortSeriesStack.getValues()) {

            @Override
            public void itemStateChanged(Object object) {
                firePropertyChange(ActionW.SYNCH.cmd(), null, new SynchEvent(getSelectedViewPane(), action.cmd(),
                    object));
            }
        };
    }

    private ComboItemListener newViewingProtocolAction() {
        return new ComboItemListener(ActionW.VIEWINGPROTOCOL, ViewingProtocols.getValues()) {

            @Override
            public void itemStateChanged(Object object) {
                firePropertyChange(ActionW.SYNCH.cmd(), null, new SynchEvent(getSelectedViewPane(), action.cmd(),
                    object));
            }
        };
    }

    @Override
    public ActionW getActionFromCommand(String command) {
        ActionW action = super.getActionFromCommand(command);

        if (action == null && command != null) {
            for (ActionW a : keyEventActions) {
                if (a.cmd().equals(command)) {
                    return a;
                }
            }
        }

        return action;
    }

    @Override
    public ActionW getActionFromkeyEvent(int keyEvent, int modifier) {
        // TODO this method should be renamed getLeftMouseActionFromkeyEvent

        ActionW action = super.getActionFromkeyEvent(keyEvent, modifier);

        if (action == null && keyEvent != 0) {
            for (ActionW a : keyEventActions) {
                if (a.getKeyCode() == keyEvent && a.getModifier() == modifier) {
                    return a;
                }
            }

            // TODO - following key action handling do not return nothing and execute code instead
            // should be called from within an outer method like keyPressed(KeyEvent e)

            if (keyEvent == ActionW.CINESTART.getKeyCode() && ActionW.CINESTART.getModifier() == modifier) {
                if (moveTroughSliceAction.isCining()) {
                    moveTroughSliceAction.stop();
                } else {
                    moveTroughSliceAction.start();
                }
            } else if (modifier == 0) {
                // No modifier, otherwise it will conflict with other shortcuts like ctrl+a and ctrl+d
                if (keyEvent == KeyEvent.VK_D) {
                    for (Object obj : measureAction.getAllItem()) {
                        if (obj instanceof LineGraphic) {
                            setMeasurement(obj);
                            break;
                        }
                    }
                } else if (keyEvent == KeyEvent.VK_A) {
                    for (Object obj : measureAction.getAllItem()) {
                        if (obj instanceof AngleToolGraphic) {
                            setMeasurement(obj);
                            break;
                        }
                    }
                } else if (keyEvent == ActionW.KO_TOOGLE_STATE.getKeyCode()) {
                    koToggleAction.setSelected(!koToggleAction.isSelected());
                } else {
                    DefaultComboBoxModel model = presetAction.getModel();
                    for (int i = 0; i < model.getSize(); i++) {
                        PresetWindowLevel val = (PresetWindowLevel) model.getElementAt(i);
                        if (val.getKeyCode() == keyEvent) {
                            presetAction.setSelectedItem(val);
                            break;
                        }
                    }
                }
            }
        }

        return action;
    }

    private void setMeasurement(Object obj) {
        ImageViewerPlugin<DicomImageElement> view = getSelectedView2dContainer();
        if (view != null) {
            final ViewerToolBar toolBar = view.getViewerToolBar();
            if (toolBar != null) {
                String cmd = ActionW.MEASURE.cmd();
                if (!toolBar.isCommandActive(cmd)) {
                    mouseActions.setAction(MouseActions.LEFT, cmd);
                    if (view != null) {
                        view.setMouseActions(mouseActions);
                    }
                    toolBar.changeButtonState(MouseActions.LEFT, cmd);
                }
            }
        }
        measureAction.setSelectedItem(obj);
    }

    @Override
    public void setSelectedView2dContainer(ImageViewerPlugin<DicomImageElement> selectedView2dContainer) {
        if (this.selectedView2dContainer != null) {
            this.selectedView2dContainer.setMouseActions(null);
            this.selectedView2dContainer.setDrawActions(null);
            moveTroughSliceAction.stop();

        }
        ImageViewerPlugin<DicomImageElement> oldContainer = this.selectedView2dContainer;
        this.selectedView2dContainer = selectedView2dContainer;

        if (selectedView2dContainer != null) {
            if (oldContainer != null) {
                if (!oldContainer.getClass().equals(selectedView2dContainer.getClass())) {
                    synchAction.setDataListWithoutTriggerAction(selectedView2dContainer.getSynchList().toArray());
                    layoutAction.setDataListWithoutTriggerAction(selectedView2dContainer.getLayoutList().toArray());
                }
                DefaultView2d<DicomImageElement> pane = oldContainer.getSelectedImagePane();
                if (pane != null) {
                    pane.setFocused(false);
                }
            }
            synchAction.setSelectedItemWithoutTriggerAction(selectedView2dContainer.getSynchView());
            layoutAction.setSelectedItemWithoutTriggerAction(selectedView2dContainer.getOriginalLayoutModel());
            updateComponentsListener(selectedView2dContainer.getSelectedImagePane());
            selectedView2dContainer.setMouseActions(mouseActions);
            Graphic graphic = null;
            ActionState action = getAction(ActionW.DRAW_MEASURE);
            if (action instanceof ComboItemListener) {
                graphic = (Graphic) ((ComboItemListener) action).getSelectedItem();
            }
            selectedView2dContainer.setDrawActions(graphic);
            DefaultView2d<DicomImageElement> pane = selectedView2dContainer.getSelectedImagePane();
            if (pane != null) {
                pane.setFocused(true);
                fireSeriesViewerListeners(new SeriesViewerEvent(selectedView2dContainer, pane.getSeries(), null,
                    EVENT.SELECT_VIEW));
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
                moveTroughSliceAction.start();
            } else if (command.equals(ActionW.CINESTOP.cmd())) {
                moveTroughSliceAction.stop();
            }
        }
    }

    @Override
    public void resetDisplay() {
        reset(ResetTools.All);
    }

    public void reset(ResetTools action) {
        AuditLog.LOGGER.info("reset action:{}", action.name()); //$NON-NLS-1$
        if (ResetTools.All.equals(action)) {
            firePropertyChange(ActionW.SYNCH.cmd(), null, new SynchEvent(getSelectedViewPane(), ActionW.RESET.cmd(),
                true));
        } else if (ResetTools.Zoom.equals(action)) {
            // Pass the value 0.0 (convention: default value according the zoom type) directly to the property change,
            // otherwise the value is adjusted by the BoundedRangeModel
            firePropertyChange(ActionW.SYNCH.cmd(), null,
                new SynchEvent(getSelectedViewPane(), ActionW.ZOOM.cmd(), 0.0));

        } else if (ResetTools.Rotation.equals(action)) {
            rotateAction.setValue(0);
        } else if (ResetTools.WindowLevel.equals(action)) {
            presetAction.setSelectedItem(presetAction.getFirstItem());
        } else if (ResetTools.Pan.equals(action)) {
            if (selectedView2dContainer != null) {
                DefaultView2d viewPane = selectedView2dContainer.getSelectedImagePane();
                if (viewPane != null) {
                    viewPane.resetPan();
                }
            }
        }
    }

    @Override
    public synchronized boolean updateComponentsListener(DefaultView2d<DicomImageElement> view2d) {
        if (view2d == null) {
            return false;
        }

        // TODO docking
        // Content selectedContent = UIManager.toolWindowManager.getContentManager().getSelectedContent();
        // if (selectedContent == null || selectedContent.getComponent() != selectedView2dContainer) {
        // return false;
        // }

        if (selectedView2dContainer == null || view2d != selectedView2dContainer.getSelectedImagePane()) {
            return false;
        }

        clearAllPropertyChangeListeners();

        if (view2d.getSourceImage() == null) {
            enableActions(false);
            if (view2d.getSeries() != null) {
                // Let scrolling if only one image is corrupted in the series
                moveTroughSliceAction.enableAction(true);
            }
            return false;
        }

        if (!enabledAction) {
            enableActions(true);
        }

        OpManager dispOp = view2d.getDisplayOpManager();

        updateWindowLevelComponentsListener(view2d.getImage(), view2d);

        lutAction.setSelectedItemWithoutTriggerAction(dispOp.getParamValue(PseudoColorOp.OP_NAME, PseudoColorOp.P_LUT));
        inverseLutAction.setSelectedWithoutTriggerAction((Boolean) dispOp.getParamValue(PseudoColorOp.OP_NAME,
            PseudoColorOp.P_LUT_INVERSE));
        filterAction
            .setSelectedItemWithoutTriggerAction(dispOp.getParamValue(FilterOp.OP_NAME, FilterOp.P_KERNEL_DATA));
        rotateAction.setValueWithoutTriggerAction((Integer) dispOp.getParamValue(RotationOp.OP_NAME,
            RotationOp.P_ROTATE));
        flipAction.setSelectedWithoutTriggerAction((Boolean) dispOp.getParamValue(FlipOp.OP_NAME, FlipOp.P_FLIP));

        zoomAction.setValueWithoutTriggerAction(viewScaleToSliderValue(Math.abs((Double) view2d
            .getActionValue(ActionW.ZOOM.cmd()))));
        spUnitAction.setSelectedItemWithoutTriggerAction(view2d.getActionValue(ActionW.SPATIAL_UNIT.cmd()));

        showLensAction.setSelectedWithoutTriggerAction((Boolean) view2d.getActionValue(ActionW.LENS.cmd()));
        Double lensZoom = (Double) view2d.getLensActionValue(ActionW.ZOOM.cmd());
        if (lensZoom != null) {
            lensZoomAction.setValueWithoutTriggerAction(viewScaleToSliderValue(Math.abs(lensZoom)));
        }

        MediaSeries<DicomImageElement> series = view2d.getSeries();
        moveTroughSliceAction.setMinMaxValueWithoutTriggerAction(1,
            series.size((Filter<DicomImageElement>) view2d.getActionValue(ActionW.FILTERED_SERIES.cmd())),
            view2d.getFrameIndex() + 1);
        Integer speed = (Integer) series.getTagValue(TagW.CineRate);
        if (speed != null) {
            moveTroughSliceAction.setSpeed(speed);
        }

        sortStackAction.setSelectedItemWithoutTriggerAction(view2d.getActionValue(ActionW.SORTSTACK.cmd()));
        viewingProtocolAction.setSelectedItemWithoutTriggerAction(view2d.getActionValue(ActionW.VIEWINGPROTOCOL.cmd()));
        inverseStackAction.setSelectedWithoutTriggerAction((Boolean) view2d.getActionValue(ActionW.INVERSESTACK.cmd()));

        koToggleAction.setSelectedWithoutTriggerAction((Boolean) view2d.getActionValue(ActionW.KO_TOOGLE_STATE.cmd()));
        koFilterAction.setSelectedWithoutTriggerAction((Boolean) view2d.getActionValue(ActionW.KO_FILTER.cmd()));

        koSelectionAction.setDataListWithoutTriggerAction(KOManager.getKOElementListWithNone(view2d).toArray());
        koSelectionAction.setSelectedItemWithoutTriggerAction(view2d.getActionValue(ActionW.KO_SELECTION.cmd()));

        // register all actions for the selected view and for the other views register according to synchview.
        updateAllListeners(selectedView2dContainer, (SynchView) synchAction.getSelectedItem());

        List<DockableTool> tools = selectedView2dContainer.getToolPanel();
        synchronized (tools) {
            for (DockableTool p : tools) {
                if (p instanceof GraphicsListener) {
                    view2d.getLayerModel().addGraphicSelectionListener((GraphicsListener) p);
                }
            }
        }
        return true;
    }

    private void updateWindowLevelComponentsListener(DicomImageElement image, DefaultView2d<DicomImageElement> view2d) {

        ImageOpNode node = view2d.getDisplayOpManager().getNode(WindowOp.OP_NAME);
        if (node != null) {
            PresetWindowLevel preset = (PresetWindowLevel) node.getParam(ActionW.PRESET.cmd());
            boolean defaultPreset = JMVUtils.getNULLtoTrue(node.getParam(ActionW.DEFAULT_PRESET.cmd()));
            Float windowValue = (Float) node.getParam(ActionW.WINDOW.cmd());
            Float levelValue = (Float) node.getParam(ActionW.LEVEL.cmd());
            LutShape lutShapeItem = (LutShape) node.getParam(ActionW.LUT_SHAPE.cmd());
            boolean pixelPadding = JMVUtils.getNULLtoTrue(node.getParam(ActionW.IMAGE_PIX_PADDING.cmd()));
            boolean pr = view2d.getActionValue(ActionW.PR_STATE.cmd()) != null;

            defaultPresetAction.setSelectedWithoutTriggerAction(defaultPreset);

            int window;
            int minLevel;
            int maxLevel;
            if (preset == null) {
                if (windowValue == null) {
                    windowValue = (float) windowAction.getValue();
                }
                if (levelValue == null) {
                    levelValue = (float) levelAction.getValue();
                }
                Float levelMin = (Float) node.getParam(ActionW.LEVEL_MIN.cmd());
                Float levelMax = (Float) node.getParam(ActionW.LEVEL_MAX.cmd());
                if (levelMin == null || levelMax == null) {
                    minLevel = (int) Math.min(levelValue - windowValue / 2.0f, image.getMinValue(pixelPadding));
                    maxLevel = (int) Math.max(levelValue + windowValue / 2.0f, image.getMaxValue(pixelPadding));
                } else {
                    minLevel = levelMin.intValue();
                    maxLevel = levelMax.intValue();
                }
                window = (int) Math.max(windowValue, image.getFullDynamicWidth(pixelPadding));
            } else {
                minLevel = (int) preset.getMinBox();
                maxLevel = (int) preset.getMaxBox();
                window = preset.getWindow().intValue();
            }

            windowAction.setMinMaxValueWithoutTriggerAction(1, window, windowValue.intValue());
            levelAction.setMinMaxValueWithoutTriggerAction(minLevel, maxLevel, levelValue.intValue());

            List<PresetWindowLevel> presetList = image.getPresetList(pixelPadding);
            if (pr) {
                List<PresetWindowLevel> prPresets =
                    (List<PresetWindowLevel>) view2d.getActionValue(PresentationStateReader.PR_PRESETS);
                if (prPresets != null) {
                    presetList = prPresets;
                }
            }
            presetAction.setDataListWithoutTriggerAction(presetList == null ? null : presetList.toArray());
            presetAction.setSelectedItemWithoutTriggerAction(preset);

            Collection<LutShape> lutShapeList = image.getLutShapeCollection(pixelPadding);
            if (pr && lutShapeList != null && lutShapeItem != null && !lutShapeList.contains(lutShapeItem)) {
                // Make a copy of the image list
                ArrayList<LutShape> newList = new ArrayList<LutShape>(lutShapeList.size() + 1);
                newList.add(lutShapeItem);
                newList.addAll(lutShapeList);
                lutShapeList = newList;
            }
            lutShapeAction.setDataListWithoutTriggerAction(lutShapeList.toArray());
            lutShapeAction.setSelectedItemWithoutTriggerAction(lutShapeItem);
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
            DefaultView2d<DicomImageElement> viewPane = viewerPlugin.getSelectedImagePane();
            // if (viewPane == null || viewPane.getSeries() == null) {
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
                if (viewPane instanceof MipView) {
                    // Handle special case with MIP view, do not let scroll the series
                    moveTroughSliceAction.enableAction(false);
                    synchView = SynchView.NONE;
                    synch = synchView.getSynchData();
                } else {
                    moveTroughSliceAction.enableAction(true);
                }
                final ArrayList<DefaultView2d<DicomImageElement>> panes = viewerPlugin.getImagePanels();
                panes.remove(viewPane);
                viewPane.setActionsInView(ActionW.SYNCH_CROSSLINE.cmd(), false);

                if (SynchView.NONE.equals(synchView)) {
                    for (int i = 0; i < panes.size(); i++) {
                        DefaultView2d<DicomImageElement> pane = panes.get(i);
                        AbstractLayer layer = pane.getLayerModel().getLayer(AbstractLayer.CROSSLINES);
                        if (layer != null) {
                            layer.deleteAllGraphic();
                        }
                        MediaSeries<DicomImageElement> s = pane.getSeries();
                        String fruid = (String) series.getTagValue(TagW.FrameOfReferenceUID);
                        boolean specialView = pane instanceof MipView;
                        if (s != null && fruid != null && !specialView) {
                            if (fruid.equals(s.getTagValue(TagW.FrameOfReferenceUID))) {
                                if (!ImageOrientation.hasSameOrientation(series, s)) {
                                    pane.setActionsInView(ActionW.SYNCH_CROSSLINE.cmd(), true);
                                    propertySupport.addPropertyChangeListener(ActionW.SCROLL_SERIES.cmd(), pane);
                                }
                                // Force to draw crosslines without changing the slice position
                                moveTroughSliceAction.stateChanged(moveTroughSliceAction.getModel());
                            }
                        }
                        oldSynch = (SynchData) pane.getActionValue(ActionW.SYNCH_LINK.cmd());
                        if (oldSynch == null || !oldSynch.getMode().equals(synch.getMode())) {
                            oldSynch = synch;
                        }
                        pane.setActionsInView(ActionW.SYNCH_LINK.cmd(), oldSynch);
                        pane.updateSynchState();
                    }
                } else {
                    // TODO if Pan is activated than rotation is required
                    if (Mode.Stack.equals(synch.getMode())) {
                        String fruid = (String) series.getTagValue(TagW.FrameOfReferenceUID);
                        DicomImageElement img = series.getMedia(MEDIA_POSITION.MIDDLE, null, null);
                        double[] val = img == null ? null : (double[]) img.getTagValue(TagW.SlicePosition);

                        for (int i = 0; i < panes.size(); i++) {
                            DefaultView2d<DicomImageElement> pane = panes.get(i);
                            AbstractLayer layer = pane.getLayerModel().getLayer(AbstractLayer.CROSSLINES);
                            if (layer != null) {
                                layer.deleteAllGraphic();
                            }
                            MediaSeries<DicomImageElement> s = pane.getSeries();
                            boolean specialView = pane instanceof MipView;
                            if (s != null && fruid != null && val != null && !specialView) {
                                boolean synchByDefault = fruid.equals(s.getTagValue(TagW.FrameOfReferenceUID));
                                oldSynch = (SynchData) pane.getActionValue(ActionW.SYNCH_LINK.cmd());
                                if (synchByDefault) {
                                    if (ImageOrientation.hasSameOrientation(series, s)) {
                                        pane.setActionsInView(ActionW.SYNCH_CROSSLINE.cmd(), false);
                                        // Only fully synch if no PR is applied (because can change pixel size)
                                        if (pane.getActionValue(ActionW.PR_STATE.cmd()) == null
                                            && hasSameSize(series, s)) {
                                            // If the image has the same reference and the same spatial calibration, all
                                            // the actions are synchronized
                                            if (oldSynch == null || !oldSynch.getMode().equals(synch.getMode())) {
                                                oldSynch = synch.clone();
                                            }
                                        } else {
                                            if (oldSynch == null || !oldSynch.getMode().equals(synch.getMode())) {
                                                oldSynch = synch.clone();
                                                for (Entry<String, Boolean> a : synch.getActions().entrySet()) {
                                                    a.setValue(false);
                                                }
                                                synch.getActions().put(ActionW.SCROLL_SERIES.cmd(), true);
                                            }
                                        }
                                    } else {
                                        pane.setActionsInView(ActionW.SYNCH_CROSSLINE.cmd(), true);
                                        if (pane instanceof MprView) {
                                            if (oldSynch == null || !oldSynch.getMode().equals(synch.getMode())) {
                                                oldSynch = synch.clone();
                                            }
                                        } else {
                                            if (oldSynch == null || !oldSynch.getMode().equals(synch.getMode())) {
                                                oldSynch = synch.clone();
                                                for (Entry<String, Boolean> a : synch.getActions().entrySet()) {
                                                    a.setValue(false);
                                                }
                                                synch.getActions().put(ActionW.SCROLL_SERIES.cmd(), true);
                                            }
                                        }
                                    }
                                    addPropertyChangeListener(ActionW.SYNCH.cmd(), pane);
                                }
                                pane.setActionsInView(ActionW.SYNCH_LINK.cmd(), oldSynch);
                                pane.updateSynchState();
                            }
                        }
                        // Force to draw crosslines without changing the slice position
                        moveTroughSliceAction.stateChanged(moveTroughSliceAction.getModel());

                    } else if (Mode.Tile.equals(synch.getMode())) {
                        for (int i = 0; i < panes.size(); i++) {
                            DefaultView2d<DicomImageElement> pane = panes.get(i);
                            oldSynch = (SynchData) pane.getActionValue(ActionW.SYNCH_LINK.cmd());
                            if (oldSynch == null || !oldSynch.getMode().equals(synch.getMode())) {
                                oldSynch = synch.clone();
                            }
                            pane.setActionsInView(ActionW.SYNCH_LINK.cmd(), oldSynch);
                            pane.setActionsInView(ActionW.SYNCH_CROSSLINE.cmd(), false);
                            addPropertyChangeListener(ActionW.SYNCH.cmd(), pane);
                            pane.updateSynchState();
                        }
                    }
                }
            }

            viewPane.updateSynchState();
        }
    }

    public static boolean hasSameSize(MediaSeries<DicomImageElement> series1, MediaSeries<DicomImageElement> series2) {
        // Test if the two series has the same orientation
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
        // Remove prefs used in Weasis 1.1.0 RC2, has moved to core.ui
        try {
            if (prefs.nodeExists(ViewSetting.PREFERENCE_NODE)) {
                Preferences oldPref = prefs.node(ViewSetting.PREFERENCE_NODE);
                oldPref.removeNode();
            }
        } catch (BackingStoreException e) {
            // Do nothing
        }
        zoomSetting.savePreferences(prefs);
        // Mouse buttons preferences
        mouseActions.savePreferences(prefs);
        if (prefs != null) {
            // Mouse sensitivity
            Preferences prefNode = prefs.node("mouse.sensivity"); //$NON-NLS-1$
            BundlePreferences.putDoublePreferences(prefNode, windowAction.getActionW().cmd(),
                windowAction.getMouseSensivity());
            BundlePreferences.putDoublePreferences(prefNode, levelAction.getActionW().cmd(),
                levelAction.getMouseSensivity());
            BundlePreferences.putDoublePreferences(prefNode, moveTroughSliceAction.getActionW().cmd(),
                moveTroughSliceAction.getMouseSensivity());
            BundlePreferences.putDoublePreferences(prefNode, rotateAction.getActionW().cmd(),
                rotateAction.getMouseSensivity());
            BundlePreferences.putDoublePreferences(prefNode, zoomAction.getActionW().cmd(),
                zoomAction.getMouseSensivity());

            prefNode = prefs.node("other"); //$NON-NLS-1$
            BundlePreferences.putBooleanPreferences(prefNode, WindowOp.P_APPLY_WL_COLOR,
                options.getBooleanProperty(WindowOp.P_APPLY_WL_COLOR, false));

            Preferences containerNode = prefs.node(View2dContainer.class.getSimpleName().toLowerCase());
            InsertableUtil.savePreferences(View2dContainer.TOOLBARS, containerNode, Type.TOOLBAR);
            InsertableUtil.savePreferences(View2dContainer.TOOLS, containerNode, Type.TOOL);

            InsertableUtil.savePreferences(MPRContainer.TOOLBARS,
                prefs.node(MPRContainer.class.getSimpleName().toLowerCase()), Type.TOOLBAR);
        }
    }

    public void zoom(String[] argv) throws IOException {
        final String[] usage =
            {
                "Change the zoom value of the selected image (0.0 is the best fit value in the window", //$NON-NLS-1$
                "Usage: dcmview2d:zoom [set | increase | decrease] [VALUE]", //$NON-NLS-1$
                "  -s --set [decimal value]  set a new value from 0.0 to 12.0 (zoom magnitude, 0.0 => default, -200.0 => best fit, -100.0 => real size)", //$NON-NLS-1$
                "  -i --increase [integer value]  increase of some amount", //$NON-NLS-1$
                "  -d --decrease [integer value]  decrease of some amount", //$NON-NLS-1$
                "  -? --help       show help" }; //$NON-NLS-1$ 
        final Option opt = Options.compile(usage).parse(argv);
        final List<String> args = opt.args();

        if (opt.isSet("help") || args.isEmpty()) { //$NON-NLS-1$
            opt.usage();
            return;
        }

        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {
                try {
                    if (opt.isSet("increase")) { //$NON-NLS-1$
                        int val = Integer.parseInt(args.get(0));
                        zoomAction.setValue(zoomAction.getValue() + val);
                    } else if (opt.isSet("decrease")) { //$NON-NLS-1$
                        int val = Integer.parseInt(args.get(0));
                        zoomAction.setValue(zoomAction.getValue() - val);
                    } else if (opt.isSet("set")) { //$NON-NLS-1$
                        double val = Double.parseDouble(args.get(0));
                        if (val <= 0.0) {
                            firePropertyChange(ActionW.SYNCH.cmd(), null, new SynchEvent(getSelectedViewPane(),
                                ActionW.ZOOM.cmd(), val));
                        } else {
                            zoomAction.setValue(viewScaleToSliderValue(val));
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void wl(String[] argv) throws IOException {
        final String[] usage =
            {
                "Change the window/level values of the selected image", //$NON-NLS-1$
                "Usage: dcmview2d:wl -- [window integer value] [level integer value] (it is mandatory to have '--' for negative values)", //$NON-NLS-1$
                "  -? --help       show help" }; //$NON-NLS-1$ 
        final Option opt = Options.compile(usage).parse(argv);
        final List<String> args = opt.args();

        if (opt.isSet("help") || args.size() != 2) { //$NON-NLS-1$
            opt.usage();
            return;
        }

        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {
                try {
                    int win = windowAction.getValue() + Integer.parseInt(args.get(0));
                    int level = levelAction.getValue() + Integer.parseInt(args.get(1));
                    windowAction.setValue(win);
                    levelAction.setValue(level);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void move(String[] argv) throws IOException {
        final String[] usage =
            {
                "Change the pan value of the selected image", //$NON-NLS-1$
                "Usage: dcmview2d:move -- [x integer value] [y integer value] (it is mandatory to have '--' for negative values)", //$NON-NLS-1$
                "  -? --help       show help" }; //$NON-NLS-1$ 
        final Option opt = Options.compile(usage).parse(argv);
        final List<String> args = opt.args();

        if (opt.isSet("help") || args.size() != 2) { //$NON-NLS-1$
            opt.usage();
            return;
        }

        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {
                try {
                    int valx = Integer.parseInt(args.get(0));
                    int valy = Integer.parseInt(args.get(1));
                    panAction.setPoint(new Point(valx, valy));

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void scroll(String[] argv) throws IOException {
        final String[] usage = { "Scroll into the images of the selected series", //$NON-NLS-1$
            "Usage: dcmview2d:scroll [set | increase | decrease] [VALUE]", //$NON-NLS-1$
            "  -s --set [integer value]  set a new value from 0 to series size less one", //$NON-NLS-1$
            "  -i --increase [integer value]  increase of some amount", //$NON-NLS-1$
            "  -d --decrease [integer value]  decrease of some amount", //$NON-NLS-1$
            "  -? --help       show help" }; //$NON-NLS-1$ 
        final Option opt = Options.compile(usage).parse(argv);
        final List<String> args = opt.args();

        if (opt.isSet("help") || args.isEmpty()) { //$NON-NLS-1$
            opt.usage();
            return;
        }

        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {
                try {
                    if (opt.isSet("increase")) { //$NON-NLS-1$
                        int val = Integer.parseInt(args.get(0));
                        moveTroughSliceAction.setValue(moveTroughSliceAction.getValue() + val);
                    } else if (opt.isSet("decrease")) { //$NON-NLS-1$
                        int val = Integer.parseInt(args.get(0));
                        moveTroughSliceAction.setValue(moveTroughSliceAction.getValue() - val);
                    } else if (opt.isSet("set")) { //$NON-NLS-1$
                        int val = Integer.parseInt(args.get(0));
                        moveTroughSliceAction.setValue(val);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void layout(String[] argv) throws IOException {
        final String[] usage = { "Select a split-screen layout", //$NON-NLS-1$
            "Usage: dcmview2d:layout [number | id] [VALUE]", //$NON-NLS-1$
            "  -n --number [integer value]  select the best matching number of views", //$NON-NLS-1$
            "  -i --id  select the layout from its identifier", //$NON-NLS-1$
            "  -? --help       show help" }; //$NON-NLS-1$ 
        final Option opt = Options.compile(usage).parse(argv);
        final List<String> args = opt.args();

        if (opt.isSet("help") || args.isEmpty()) { //$NON-NLS-1$
            opt.usage();
            return;
        }
        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {
                try {
                    if (opt.isSet("number")) { //$NON-NLS-1$
                        if (selectedView2dContainer != null) {
                            GridBagLayoutModel val =
                                selectedView2dContainer.getBestDefaultViewLayout(Integer.parseInt(args.get(0)));
                            layoutAction.setSelectedItem(val);
                        }
                    } else if (opt.isSet("id")) { //$NON-NLS-1$
                        if (selectedView2dContainer != null) {
                            GridBagLayoutModel val = selectedView2dContainer.getViewLayout(args.get(0));
                            if (val != null) {
                                layoutAction.setSelectedItem(val);
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void mouseLeftAction(String[] argv) throws IOException {
        final String[] usage = { "Change the mouse left action", //$NON-NLS-1$
            "Usage: dcmview2d:mouseLeftAction [action String value]", //$NON-NLS-1$
            "  -? --help       show help" }; //$NON-NLS-1$ 
        final Option opt = Options.compile(usage).parse(argv);
        final List<String> args = opt.args();

        if (opt.isSet("help") || args.size() != 1) { //$NON-NLS-1$
            opt.usage();
            return;
        }

        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {
                String command = args.get(0);
                if (command != null) {
                    try {
                        if (command.startsWith("session")) { //$NON-NLS-1$
                            AuditLog.LOGGER.info("source:telnet {}", command); //$NON-NLS-1$
                        } else {
                            AuditLog.LOGGER.info("source:telnet mouse:{} action:{}", MouseActions.LEFT, command); //$NON-NLS-1$

                            if (!command.equals(mouseActions.getAction(MouseActions.LEFT))) {

                                ImageViewerPlugin<DicomImageElement> view = getSelectedView2dContainer();
                                if (view != null) {
                                    final ViewerToolBar toolBar = view.getViewerToolBar();
                                    if (toolBar != null) {
                                        // Test if mouse action exist and if not NO_ACTION is set
                                        ActionW action = toolBar.getAction(ViewerToolBar.actionsButtons, command);
                                        if (action == null) {
                                            command = ActionW.NO_ACTION.cmd();
                                        }
                                        toolBar.changeButtonState(MouseActions.LEFT, command);
                                    }
                                    view.setMouseActions(mouseActions);
                                }
                                mouseActions.setAction(MouseActions.LEFT, command);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    public void synch(String[] argv) throws IOException {
        StringBuilder buffer = new StringBuilder("{"); //$NON-NLS-1$
        for (SynchView synch : View2dContainer.SYNCH_LIST) {
            buffer.append(synch.getCommand());
            buffer.append(" "); //$NON-NLS-1$
        }
        buffer.append("}"); //$NON-NLS-1$

        final String[] usage = { "Set a synchronization mode " + buffer.toString(), //$NON-NLS-1$
            "Usage: dcmview2d:synch [VALUE]", //$NON-NLS-1$
            "  -? --help       show help" }; //$NON-NLS-1$ 
        final Option opt = Options.compile(usage).parse(argv);
        final List<String> args = opt.args();

        if (opt.isSet("help") || args.isEmpty()) { //$NON-NLS-1$
            opt.usage();
            return;
        }
        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {
                String command = args.get(0);
                if (command != null) {
                    ImageViewerPlugin<DicomImageElement> view = getSelectedView2dContainer();
                    if (view != null) {
                        try {
                            for (SynchView synch : view.getSynchList()) {
                                if (synch.getCommand().equals(command)) {
                                    synchAction.setSelectedItem(synch);
                                    return;
                                }
                            }
                            throw new IllegalArgumentException("Synch command '" + command + "' not found!"); //$NON-NLS-1$ //$NON-NLS-2$
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
    }

    public void reset(String[] argv) throws IOException {
        final String[] usage = { "Reset a tool or all the tools", //$NON-NLS-1$
            "Usage: dcmview2d:reset [action String value | all]", //$NON-NLS-1$
            "  -? --help       show help" }; //$NON-NLS-1$ 
        final Option opt = Options.compile(usage).parse(argv);
        final List<String> args = opt.args();

        if (opt.isSet("help") || args.size() != 1) { //$NON-NLS-1$
            opt.usage();
            return;
        }

        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {
                String command = args.get(0);
                if (command != null) {
                    try {
                        if (ActionW.WINLEVEL.cmd().equals(command)) {
                            reset(ResetTools.WindowLevel);
                        } else if (ActionW.ZOOM.cmd().equals(command)) {
                            reset(ResetTools.Zoom);
                        } else if (ActionW.PAN.cmd().equals(command)) {
                            reset(ResetTools.Pan);
                        } else if (ActionW.ROTATION.cmd().equals(command)) {
                            reset(ResetTools.Rotation);
                        } else {
                            reset(ResetTools.All);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

}
