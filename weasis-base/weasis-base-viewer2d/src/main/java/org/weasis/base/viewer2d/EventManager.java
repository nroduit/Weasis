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
package org.weasis.base.viewer2d;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.List;

import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;
import org.weasis.base.viewer2d.internal.Activator;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.BasicActionState;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.gui.util.SliderCineListener.TIME;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.image.op.ByteLut;
import org.weasis.core.api.image.op.ByteLutCollection;
import org.weasis.core.api.image.util.KernelData;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.editor.image.PannerListener;
import org.weasis.core.ui.editor.image.SynchView;
import org.weasis.core.ui.editor.image.ViewerToolBar;
import org.weasis.core.ui.graphic.AngleToolGraphic;
import org.weasis.core.ui.graphic.Graphic;
import org.weasis.core.ui.graphic.LineGraphic;
import org.weasis.core.ui.graphic.model.GraphicsListener;
import org.weasis.core.ui.pref.ViewSetting;
import org.weasis.core.ui.util.WtoolBar;

/**
 * The event processing center for this application. This class responses for loading data sets, processing the events
 * from the utility menu that includes changing the operation scope, the layout, window/level, rotation angle, zoom
 * factor, starting/stoping the cining-loop and etc.
 * 
 */

public class EventManager extends ImageViewerEventManager<ImageElement> implements ActionListener {

    /** The single instance of this singleton class. */
    private static EventManager instance;
    private static ActionW[] keyEventActions = { ActionW.ZOOM, ActionW.SCROLL_SERIES, ActionW.ROTATION,
        ActionW.WINLEVEL, ActionW.PAN, ActionW.MEASURE, ActionW.CONTEXTMENU, ActionW.NO_ACTION };

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
    private final ToggleButtonListener drawOnceAction;

    private final ComboItemListener lutAction;
    private final ComboItemListener filterAction;
    private final ComboItemListener layoutAction;
    private final ComboItemListener synchAction;
    private final ComboItemListener measureAction;

    private final PannerListener panAction;

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
        iniAction(moveTroughSliceAction = getMoveTroughSliceAction(10, TIME.minute, 0.1));
        iniAction(windowAction = newWindowAction());
        iniAction(levelAction = newLevelAction());
        iniAction(rotateAction = newRotateAction());
        iniAction(zoomAction = newZoomAction());
        iniAction(lensZoomAction = newLensZoomAction());

        iniAction(flipAction = newFlipAction());
        iniAction(inverseLutAction = newInverseLutAction());
        iniAction(inverseStackAction = newInverseStackAction());
        iniAction(showLensAction = newLensAction());
        iniAction(drawOnceAction = newDrawOnlyOnceAction());

        iniAction(lutAction = newLutAction());
        iniAction(filterAction = newFilterAction());
        iniAction(layoutAction =
            newLayoutAction(View2dContainer.LAYOUT_LIST.toArray(new GridBagLayoutModel[View2dContainer.LAYOUT_LIST
                .size()])));
        iniAction(synchAction =
            newSynchAction(View2dContainer.SYNCH_LIST.toArray(new SynchView[View2dContainer.SYNCH_LIST.size()])));
        synchAction.setSelectedItemWithoutTriggerAction(SynchView.DEFAULT_STACK);
        iniAction(measureAction =
            newMeasurementAction(MeasureToolBar.graphicList.toArray(new Graphic[MeasureToolBar.graphicList.size()])));
        iniAction(panAction = newPanAction());
        iniAction(new BasicActionState(ActionW.RESET));

        Preferences pref = Activator.PREFERENCES.getDefaultPreferences();
        zoomSetting.applyPreferences(pref);
        mouseActions.applyPreferences(pref);
        if (pref != null) {
            Preferences prefNode = pref.node("mouse.sensivity"); //$NON-NLS-1$
            windowAction.setMouseSensivity(prefNode.getDouble(windowAction.getActionW().cmd(), 1.25));
            levelAction.setMouseSensivity(prefNode.getDouble(levelAction.getActionW().cmd(), 1.25));
            moveTroughSliceAction.setMouseSensivity(prefNode.getDouble(moveTroughSliceAction.getActionW().cmd(), 0.1));
            rotateAction.setMouseSensivity(prefNode.getDouble(rotateAction.getActionW().cmd(), 0.25));
            zoomAction.setMouseSensivity(prefNode.getDouble(zoomAction.getActionW().cmd(), 0.1));
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
                    firePropertyChange(action.cmd(), null, object);
                }
            }
        };
    }

    private ComboItemListener newLutAction() {
        List<ByteLut> luts = ByteLutCollection.getLutCollection();
        return new ComboItemListener(ActionW.LUT, luts.toArray(new ByteLut[luts.size()])) {

            @Override
            public void itemStateChanged(Object object) {
                if (object instanceof ByteLut) {
                    // customPreset = false;
                    firePropertyChange(action.cmd(), null, object);
                }
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
        ActionW action = super.getActionFromkeyEvent(keyEvent, modifier);

        if (action == null && keyEvent != 0) {
            for (ActionW a : keyEventActions) {
                if (a.getKeyCode() == keyEvent && a.getModifier() == modifier) {
                    return a;
                }
            }
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
                }
            }
        }

        return action;
    }

    private void setMeasurement(Object obj) {
        ImageViewerPlugin<ImageElement> view = getSelectedView2dContainer();
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
    public void setSelectedView2dContainer(ImageViewerPlugin<ImageElement> selectedView2dContainer) {
        if (this.selectedView2dContainer != null) {
            this.selectedView2dContainer.setMouseActions(null);
            this.selectedView2dContainer.setDrawActions(null);
            moveTroughSliceAction.stop();

        }
        ImageViewerPlugin<ImageElement> oldContainer = this.selectedView2dContainer;
        this.selectedView2dContainer = selectedView2dContainer;
        if (selectedView2dContainer != null) {
            if (oldContainer != null) {
                if (!oldContainer.getClass().equals(selectedView2dContainer.getClass())) {
                    synchAction.setDataListWithoutTriggerAction(selectedView2dContainer.getSynchList().toArray());
                    layoutAction.setDataListWithoutTriggerAction(selectedView2dContainer.getLayoutList().toArray());
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
        }
    }

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
            firePropertyChange(ActionW.RESET.cmd(), null, true);
        } else if (ResetTools.Zoom.equals(action)) {
            // Pass the value 0.0 (convention: best fit zoom value) directly to the property change, otherwise the
            // value is adjusted by the BoundedRangeModel
            firePropertyChange(ActionW.ZOOM.cmd(), null, 0.0);
        } else if (ResetTools.Rotation.equals(action)) {
            rotateAction.setValue(0);
        } else if (ResetTools.WindowLevel.equals(action)) {
            if (selectedView2dContainer != null) {
                DefaultView2d<ImageElement> defaultView2d = selectedView2dContainer.getSelectedImagePane();
                if (defaultView2d != null) {
                    ImageElement img = defaultView2d.getImage();
                    if (img != null) {
                        boolean pixelPadding =
                            JMVUtils.getNULLtoTrue((Boolean) defaultView2d.getActionValue(ActionW.IMAGE_PIX_PADDING
                                .cmd()));
                        windowAction.setValue((int) img.getDefaultWindow(pixelPadding));
                        levelAction.setValue((int) img.getDefaultLevel(pixelPadding));
                    }
                }
            }
        } else if (ResetTools.Pan.equals(action)) {
            if (selectedView2dContainer != null) {
                DefaultView2d viewPane = selectedView2dContainer.getSelectedImagePane();
                if (viewPane != null) {
                    viewPane.center();
                }
            }
        }
    }

    @Override
    public synchronized boolean updateComponentsListener(DefaultView2d<ImageElement> view2d) {
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
            return false;
        }
        if (!enabledAction) {
            enableActions(true);
        }
        ImageElement image = view2d.getImage();
        MediaSeries<ImageElement> series = view2d.getSeries();
        boolean pixelPadding = JMVUtils.getNULLtoTrue((Boolean) view2d.getActionValue(ActionW.IMAGE_PIX_PADDING.cmd()));

        windowAction.setMinMaxValueWithoutTriggerAction(0,
            (int) (image.getMaxValue(pixelPadding) - image.getMinValue(pixelPadding)),
            ((Float) view2d.getActionValue(ActionW.WINDOW.cmd())).intValue());
        levelAction.setMinMaxValueWithoutTriggerAction((int) image.getMinValue(pixelPadding),
            (int) image.getMaxValue(pixelPadding), ((Float) view2d.getActionValue(ActionW.LEVEL.cmd())).intValue());
        rotateAction.setValueWithoutTriggerAction((Integer) view2d.getActionValue(ActionW.ROTATION.cmd()));
        flipAction.setSelectedWithoutTriggerAction((Boolean) view2d.getActionValue(ActionW.FLIP.cmd()));
        zoomAction.setValueWithoutTriggerAction(viewScaleToSliderValue(Math.abs((Double) view2d
            .getActionValue(ActionW.ZOOM.cmd()))));
        showLensAction.setSelectedWithoutTriggerAction((Boolean) view2d.getActionValue(ActionW.LENS.cmd()));
        Double lensZoom = (Double) view2d.getLensActionValue(ActionW.ZOOM.cmd());
        if (lensZoom != null) {
            lensZoomAction.setValueWithoutTriggerAction(viewScaleToSliderValue(Math.abs(lensZoom)));
        }
        moveTroughSliceAction.setMinMaxValue(1,
            series.size((Filter<ImageElement>) view2d.getActionValue(ActionW.FILTERED_SERIES.cmd())),
            view2d.getFrameIndex() + 1);
        Integer speed = (Integer) series.getTagValue(TagW.CineRate);
        if (speed != null) {
            moveTroughSliceAction.setSpeed(speed);
        }
        lutAction.setSelectedItemWithoutTriggerAction(view2d.getActionValue(ActionW.LUT.cmd()));
        inverseLutAction.setSelectedWithoutTriggerAction((Boolean) view2d.getActionValue(ActionW.INVERSELUT.cmd()));
        filterAction.setSelectedItemWithoutTriggerAction(view2d.getActionValue(ActionW.FILTER.cmd()));
        inverseStackAction.setSelected((Boolean) view2d.getActionValue(ActionW.INVERSESTACK.cmd()));
        // register all actions for the selected view and for the other views register according to synchview.
        updateAllListeners(selectedView2dContainer, (SynchView) synchAction.getSelectedItem());

        for (DockableTool p : selectedView2dContainer.getToolPanel()) {
            if (p instanceof GraphicsListener) {
                view2d.getLayerModel().addGraphicSelectionListener((GraphicsListener) p);
            }
        }

        return true;
    }

    public void savePreferences() {
        Preferences prefs = Activator.PREFERENCES.getDefaultPreferences();
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

            WtoolBar.savePreferences(View2dContainer.TOOLBARS,
                prefs.node(View2dContainer.class.getSimpleName().toLowerCase()));
        }
    }
}
