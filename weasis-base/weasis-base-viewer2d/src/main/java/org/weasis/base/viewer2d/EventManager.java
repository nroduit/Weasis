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
import java.util.ArrayList;
import java.util.List;

import org.noos.xing.mydoggy.Content;
import org.osgi.service.prefs.Preferences;
import org.weasis.base.viewer2d.internal.Activator;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.gui.util.SliderCineListener.TIME;
import org.weasis.core.api.image.op.ByteLut;
import org.weasis.core.api.image.op.ByteLutCollection;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagElement;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.PannerListener;
import org.weasis.core.ui.editor.image.SynchView;

/**
 * The event processing center for this application. This class responses for loading data sets, processing the events
 * from the utility menu that includes changing the operation scope, the layout, window/level, rotation angle, zoom
 * factor, starting/stoping the cining-loop and etc.
 * 
 */

public class EventManager extends ImageViewerEventManager<ImageElement> implements ActionListener {

    /** The single instance of this singleton class. */

    private static EventManager instance;

    private final SliderCineListener moveTroughSliceAction;
    private final SliderChangeListener windowAction;
    private final SliderChangeListener levelAction;
    private final SliderChangeListener rotateAction;
    private final SliderChangeListener zoomAction;

    private final ToggleButtonListener flipAction;
    private final ToggleButtonListener inverseLutAction;
    private final ToggleButtonListener inverseStackAction;

    private final ComboItemListener lutAction;
    private final ComboItemListener layoutAction;
    private final ComboItemListener synchAction;

    private final PannerListener panAction;

    public final static ArrayList<SynchView> SYNCH_LIST = new ArrayList<SynchView>();
    static {
        SYNCH_LIST.add(SynchView.NONE);
        SYNCH_LIST.add(SynchView.DEFAULT_STACK);
        SYNCH_LIST.add(SynchView.DEFAULT_TILE);
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

    /**
     * The default private constructor to guarantee the singleton property of this class.
     */

    private EventManager() {
        iniAction(moveTroughSliceAction = getMoveTroughSliceAction(10, TIME.minute, 0.1));
        iniAction(windowAction = getWindowAction());
        iniAction(levelAction = getLevelAction());
        iniAction(rotateAction = getRotateAction());
        iniAction(zoomAction = getZoomAction());

        iniAction(flipAction = getFlipAction());
        iniAction(inverseLutAction = getInverseLutAction());
        iniAction(inverseStackAction = getInverseStackAction());

        iniAction(lutAction = getLutAction());
        iniAction(layoutAction = getLayoutAction(View2dContainer.MODELS));
        iniAction(synchAction = getSynchAction(SYNCH_LIST.toArray(new SynchView[SYNCH_LIST.size()])));

        iniAction(panAction = getPanAction());

        Preferences pref = Activator.PREFERENCES.getDefaultPreferences();
        mouseActions.applyPreferences(pref);
        if (pref != null) {
            Preferences prefNode = pref.node("mouse.sensivity");
            windowAction.setMouseSensivity(prefNode.getDouble(windowAction.getActionW().getCommand(), 1.25));
            levelAction.setMouseSensivity(prefNode.getDouble(levelAction.getActionW().getCommand(), 1.25));
            moveTroughSliceAction.setMouseSensivity(prefNode.getDouble(moveTroughSliceAction.getActionW().getCommand(),
                0.1));
            rotateAction.setMouseSensivity(prefNode.getDouble(rotateAction.getActionW().getCommand(), 0.25));
            zoomAction.setMouseSensivity(prefNode.getDouble(zoomAction.getActionW().getCommand(), 0.1));
        }
        initializeParameters();
    }

    private void iniAction(ActionState action) {
        actions.put(action.getActionW(), action);
    }

    private void initializeParameters() {
        enableActions(false);
    }

    private ComboItemListener getLutAction() {
        List<ByteLut> luts = ByteLutCollection.getLutCollection();
        return new ComboItemListener(ActionW.LUT, luts.toArray(new ByteLut[luts.size()])) {

            @Override
            public void itemStateChanged(Object object) {
                if (object instanceof ByteLut) {
                    // customPreset = false;
                    firePropertyChange(action.getCommand(), null, object);
                }
            }
        };
    }

    @Override
    protected ToggleButtonListener getInverseStackAction() {
        return new ToggleButtonListener(ActionW.INVERSESTACK, false) {

            @Override
            public void actionPerformed(boolean selected) {
                firePropertyChange(action.getCommand(), null, selected);
            }
        };
    }

    @Override
    public void setSelectedView2dContainer(ImageViewerPlugin<ImageElement> selectedView2dContainer) {
        if (this.selectedView2dContainer != null) {
            this.selectedView2dContainer.setMouseActions(null);
            this.selectedView2dContainer.setDrawActions();
            moveTroughSliceAction.stop();

        }
        this.selectedView2dContainer = selectedView2dContainer;
        if (selectedView2dContainer != null) {
            synchAction.setSelectedItemWithoutTriggerAction(selectedView2dContainer.getSynchView());
            layoutAction.setSelectedItemWithoutTriggerAction(selectedView2dContainer.getLayoutModel());
            updateComponentsListener(selectedView2dContainer.getSelectedImagePane());
            selectedView2dContainer.setMouseActions(mouseActions);
            selectedView2dContainer.setDrawActions();
        }
    }

    /** process the action events. */

    public void actionPerformed(ActionEvent evt) {
        String command = evt.getActionCommand();

        if (command.equals(ActionW.CINESTART.getCommand())) {
            // turn cining on.
            moveTroughSliceAction.start();
        } else if (command.equals(ActionW.CINESTOP.getCommand())) {
            // turn cine off.
            moveTroughSliceAction.stop();
        }
    }

    public void resetAllActions() {
        if (selectedView2dContainer != null) {
            DefaultView2d<ImageElement> defaultView2d = selectedView2dContainer.getSelectedImagePane();
            windowAction.setValue(((Float) defaultView2d.getActionValue(ActionW.WINDOW)).intValue());
            levelAction.setValue(((Float) defaultView2d.getActionValue(ActionW.LEVEL)).intValue());
        }
        flipAction.setSelected(false);
        rotateAction.setValue(0);
        inverseLutAction.setSelected(false);
        lutAction.setSelectedItem(ByteLut.defaultLUT);
        firePropertyChange(ActionW.ZOOM.getCommand(), null, 0.0);
    }

    public void reset(ResetTools action) {
        if (ResetTools.All.equals(action)) {
            resetAllActions();
        } else if (ResetTools.Zoom.equals(action)) {
            // Pass the value 0.0 (convention: best fit zoom value) directly to the property change, otherwise the
            // value is adjusted by the BoundedRangeModel
            firePropertyChange(ActionW.ZOOM.getCommand(), null, 0.0);

        } else if (ResetTools.Rotation.equals(action)) {
            rotateAction.setValue(0);
        } else if (ResetTools.WindowLevel.equals(action)) {
            if (selectedView2dContainer != null) {
                DefaultView2d<ImageElement> defaultView2d = selectedView2dContainer.getSelectedImagePane();
                windowAction.setValue(((Float) defaultView2d.getActionValue(ActionW.WINDOW)).intValue());
                levelAction.setValue(((Float) defaultView2d.getActionValue(ActionW.LEVEL)).intValue());
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
    public synchronized boolean updateComponentsListener(DefaultView2d<ImageElement> defaultView2d) {
        if (defaultView2d == null) {
            return false;
        }
        Content selectedContent = UIManager.toolWindowManager.getContentManager().getSelectedContent();
        if (selectedContent == null || selectedContent.getComponent() != selectedView2dContainer) {
            return false;
        }
        if (selectedView2dContainer == null || defaultView2d != selectedView2dContainer.getSelectedImagePane()) {
            return false;
        }
        // System.out.println(v.getId() + ": udpate");
        // selectedView2dContainer.setSelectedImagePane(v);
        clearAllPropertyChangeListeners();
        ImageElement image = defaultView2d.getImage();
        if (image == null || image.getImage() == null) {
            enableActions(false);
            return false;
        }
        if (!enabledAction) {
            enableActions(true);
        }
        MediaSeries<ImageElement> series = defaultView2d.getSeries();
        windowAction.setMinMaxValueWithoutTriggerAction(0, (int) (image.getMaxValue() - image.getMinValue()),
            ((Float) defaultView2d.getActionValue(ActionW.WINDOW)).intValue());
        levelAction.setMinMaxValueWithoutTriggerAction((int) image.getMinValue(), (int) image.getMaxValue(),
            ((Float) defaultView2d.getActionValue(ActionW.LEVEL)).intValue());
        rotateAction.setValueWithoutTriggerAction((Integer) defaultView2d.getActionValue(ActionW.ROTATION));
        flipAction.setSelectedWithoutTriggerAction((Boolean) defaultView2d.getActionValue(ActionW.FLIP));
        zoomAction.setValueWithoutTriggerAction(viewScaleToSliderValue(Math.abs((Double) defaultView2d
            .getActionValue(ActionW.ZOOM))));
        moveTroughSliceAction.setMinMaxValue(1, series.size(), defaultView2d.getFrameIndex() + 1);
        Integer speed = (Integer) series.getTagValue(TagElement.CineRate);
        if (speed != null) {
            moveTroughSliceAction.setSpeed(speed);
        }
        lutAction.setSelectedItemWithoutTriggerAction(defaultView2d.getActionValue(ActionW.LUT));
        inverseLutAction.setSelectedWithoutTriggerAction((Boolean) defaultView2d.getActionValue(ActionW.INVERSELUT));
        inverseStackAction.setSelected((Boolean) defaultView2d.getActionValue(ActionW.INVERSESTACK));
        // register all actions for the selected view and for the other views register according to synchview.
        updateAllListeners(selectedView2dContainer, (SynchView) synchAction.getSelectedItem());
        return true;
    }

    public void savePreferences() {
        Preferences prefs = Activator.PREFERENCES.getDefaultPreferences();
        mouseActions.savePreferences(prefs);
        if (prefs != null) {
            Preferences prefNode = prefs.node("mouse.sensivity");
            BundlePreferences.putDoublePreferences(prefNode, windowAction.getActionW().getCommand(), windowAction
                .getMouseSensivity());
            BundlePreferences.putDoublePreferences(prefNode, levelAction.getActionW().getCommand(), levelAction
                .getMouseSensivity());
            BundlePreferences.putDoublePreferences(prefNode, moveTroughSliceAction.getActionW().getCommand(),
                moveTroughSliceAction.getMouseSensivity());
            BundlePreferences.putDoublePreferences(prefNode, rotateAction.getActionW().getCommand(), rotateAction
                .getMouseSensivity());
            BundlePreferences.putDoublePreferences(prefNode, zoomAction.getActionW().getCommand(), zoomAction
                .getMouseSensivity());
        }
    }
}
