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
package org.weasis.core.api.gui.util;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.Hashtable;

import javax.swing.BoundedRangeModel;
import javax.swing.BoxLayout;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.weasis.core.api.Messages;
import org.weasis.core.api.util.FontTools;

public abstract class SliderChangeListener extends MouseActionAdapter implements ChangeListener, ActionState {

    protected final ActionW action;
    private final ArrayList<JSliderW> sliders;
    private final DefaultBoundedRangeModel model;
    private boolean enable;
    private boolean triggerAction = true;
    private boolean valueIsAdjusting = true;

    public SliderChangeListener(ActionW action, int min, int max, int value, boolean valueIsAdjusting,
        double mouseSensivity) {
        this(action, min, max, value, valueIsAdjusting);
        setMouseSensivity(mouseSensivity);
    }

    public SliderChangeListener(ActionW action, int min, int max, int value, boolean valueIsAdjusting) {
        super();
        this.action = action;
        this.valueIsAdjusting = valueIsAdjusting;
        enable = true;
        model = new DefaultBoundedRangeModel(value, 0, min, max);
        model.addChangeListener(this);
        sliders = new ArrayList<JSliderW>();

    }

    public SliderChangeListener(ActionW action, int min, int max, int value) {
        this(action, min, max, value, true);
    }

    public void enableAction(boolean enabled) {
        this.enable = enabled;
        for (JSlider slider : sliders) {
            slider.setEnabled(enabled);
        }
    }

    public int getMin() {
        return model.getMinimum();
    }

    public void setMinMax(int min, int max) {
        setMinMaxValue(min, max, model.getValue());
    }

    public synchronized void setMinMaxValue(int min, int max, int value) {
        // Adjust the value to min and max to avoid the model to change the min and the max
        value = value > max ? max : value < min ? min : value;
        model.setRangeProperties(value, model.getExtent(), min, max, model.getValueIsAdjusting());
        for (int i = 0; i < sliders.size(); i++) {
            JSliderW s = sliders.get(i);
            updateSliderProoperties(s);
            setSliderLabelValues(s, min, max);
        }
    }

    public synchronized void setMinMaxValueWithoutTriggerAction(int min, int max, int value) {
        // Adjust the value to min and max to avoid the model to change the min and the max
        value = value > max ? max : value < min ? min : value;
        triggerAction = false;
        model.setRangeProperties(value, model.getExtent(), min, max, model.getValueIsAdjusting());
        triggerAction = true;
        for (int i = 0; i < sliders.size(); i++) {
            JSliderW s = sliders.get(i);
            updateSliderProoperties(s);
            setSliderLabelValues(s, min, max);
        }
    }

    public boolean isTriggerAction() {
        return triggerAction;
    }

    public ActionW getActionW() {
        return action;
    }

    public void setValue(int value) {
        model.setValue(value);
    }

    public void setValueWithoutTriggerAction(int value) {
        boolean ajusting = valueIsAdjusting ? true : !model.getValueIsAdjusting();
        if (ajusting) {
            triggerAction = false;
            model.setValue(value);
            triggerAction = true;
        }
    }

    public boolean isValueIsAdjusting() {
        return valueIsAdjusting;
    }

    public int getMax() {
        return model.getMaximum();
    }

    public int getValue() {
        return model.getValue();
    }

    public DefaultBoundedRangeModel getModel() {
        return model;
    }

    public String getValueToDisplay() {
        return getValue() + ""; //$NON-NLS-1$
    }

    public void stateChanged(ChangeEvent evt) {
        boolean ajusting = valueIsAdjusting ? true : !model.getValueIsAdjusting();
        if (triggerAction && ajusting) {
            stateChanged(model);
        }
        for (int i = 0; i < sliders.size(); i++) {
            updateSliderProoperties(sliders.get(i));
        }
    }

    public abstract void stateChanged(BoundedRangeModel model);

    @Override
    public String toString() {
        return action.getTitle();
    }

    /**
     * Register a slider and add at the same the ChangeListener
     * 
     * @param slider
     */
    public void registerSlider(JSliderW slider) {
        if (!sliders.contains(slider)) {
            sliders.add(slider);
            slider.setEnabled(enable);
            slider.setModel(model);
            updateSliderProoperties(slider);
        }
    }

    public void unregisterSlider(JSliderW slider) {
        sliders.remove(slider);
        slider.setModel(new DefaultBoundedRangeModel(0, 0, 0, 100));
    }

    public static void setSliderLabelValues(JSliderW slider, int min, int max) {
        int div = slider.getLabelDivision();
        if (div < 1) {
            return;
        }
        int spacing = (max - min) / (div - 1);
        if (spacing < 1) {
            spacing = 1;
        }
        // TODO générer les subdivisions par rapport à la taille du slider

        if (!slider.getPaintLabels()) {
            return;
        }

        Hashtable<Integer, JLabel> table = new Hashtable<Integer, JLabel>();

        for (int i = 0; i < div; i++) {
            Integer index = i * spacing + min;
            table.put(index, new JLabel("" + index)); //$NON-NLS-1$
        }
        slider.setLabelTable(table);
        FontTools.setFont10(slider);
        slider.setMajorTickSpacing(spacing);
        // slider.setEnabled(max - min > 0);
    }

    public void updateSliderProoperties(JSliderW slider) {
        JPanel panel = (JPanel) slider.getParent();

        String result = action.getTitle() + ": " + getValueToDisplay(); //$NON-NLS-1$
        if (!slider.isDisplayOnlyValue() && panel != null && panel.getBorder() instanceof TitledBorder) {
            ((TitledBorder) panel.getBorder()).setTitle(result);
            panel.repaint();
        } else {
            slider.setToolTipText(result);
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        int buttonMask = getButtonMaskEx();
        if ((e.getModifiersEx() & buttonMask) != 0) {
            lastPosition = isMoveOnX() ? e.getX() : e.getY();
            dragAccumulator = getValue();
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        int buttonMask = getButtonMaskEx();
        int modifier = e.getModifiersEx();
        // dragAccumulator == Double.NaN when the listener did not catch the Pressed MouseEvent (could append in
        // multisplit container)
        if ((modifier & buttonMask) != 0 && dragAccumulator != Double.MAX_VALUE) {
            int position = isMoveOnX() ? e.getX() : e.getY();
            int mask = (InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK);
            // Accelerate the action if ctrl or shift is down
            double acceleratorKey = (modifier & mask) == 0 ? 1.0 : (modifier & mask) == mask ? 5.0 : 2.5;
            double val = (position - lastPosition) * getMouseSensivity() * acceleratorKey;
            if (val == 0.0) {
                return;
            }
            lastPosition = position;
            if (isInverse()) {
                dragAccumulator -= val;
            } else {
                dragAccumulator += val;
            }
            // logger.debug("val:" + val + " accu: " + dragAccumulator);
            if (val < 0.0) {
                setValue((int) Math.ceil(dragAccumulator));
            } else {
                setValue((int) Math.floor(dragAccumulator));
            }
        }
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        setValue(getValue() + e.getWheelRotation() * e.getScrollAmount());
        super.mouseWheelMoved(e);
    }

    public JSliderW createSlider(int labelDivision, boolean displayOnlyValue) {
        final JPanel palenSlider1 = new JPanel();
        palenSlider1.setLayout(new BoxLayout(palenSlider1, BoxLayout.Y_AXIS));
        palenSlider1.setBorder(new TitledBorder(action.getTitle()));
        JSliderW slider = new JSliderW(model.getMinimum(), model.getMaximum(), model.getValue());
        slider.setLabelDivision(labelDivision);
        slider.setDisplayOnlyValue(displayOnlyValue);
        slider.setPaintTicks(true);
        palenSlider1.add(slider);
        registerSlider(slider);
        if (labelDivision > 0) {
            slider.setPaintLabels(true);
            setSliderLabelValues(slider, model.getMinimum(), model.getMaximum());
        }
        return slider;
    }
}
