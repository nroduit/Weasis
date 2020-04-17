/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.api.gui.util;

import java.awt.Font;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.Enumeration;
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

import org.weasis.core.api.service.AuditLog;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.util.StringUtil;

public abstract class SliderChangeListener extends MouseActionAdapter implements ChangeListener, ActionState {
    public static final int DEFAULT_SMALLEST = 0;
    public static final int DEFAULT_LARGEST = 4095;

    private final DefaultBoundedRangeModel model;
    protected final BasicActionState basicState;
    protected volatile boolean triggerAction = true;
    protected volatile boolean valueIsAdjusting = true;
    protected Double realMin;
    protected Double realMax;

    public SliderChangeListener(ActionW action, int min, int max, int value) {
        this(action, min, max, value, true);
    }

    public SliderChangeListener(ActionW action, int min, int max, int value, boolean valueIsAdjusting,
        double mouseSensivity) {
        this(action, min, max, value, valueIsAdjusting);
        setMouseSensivity(mouseSensivity);
    }

    public SliderChangeListener(ActionW action, int min, int max, int value, boolean valueIsAdjusting) {
        super();
        this.basicState = new BasicActionState(action);
        this.valueIsAdjusting = valueIsAdjusting;
        model = new DefaultBoundedRangeModel(value, 0, min, max);
        model.addChangeListener(this);
    }

    public SliderChangeListener(ActionW action, double min, double max, double value, boolean valueIsAdjusting,
        double mouseSensivity, int sliderRange) {
        this.basicState = new BasicActionState(action);
        this.valueIsAdjusting = valueIsAdjusting;
        setMouseSensivity(mouseSensivity);
        model = new DefaultBoundedRangeModel(0, 0, 0, sliderRange);
        setRealMinMaxValue(min, max, value, false);
        model.addChangeListener(this);
    }

    @Override
    public void enableAction(boolean enabled) {
        basicState.enableAction(enabled);
    }

    @Override
    public boolean isActionEnabled() {
        return basicState.isActionEnabled();
    }

    public void setSliderMinMax(int min, int max) {
        setSliderMinMaxValue(min, max, model.getValue());
    }

    public void setSliderMinMaxValue(int min, int max, int value) {
        setSliderMinMaxValue(min, max, value, true);
    }

    public void setSliderMinMaxValue(int min, int max, int value, boolean triggerChangedEvent) {
        realMin = null;
        realMax = null;
        minMaxValueAction(min, max, value, triggerChangedEvent);
    }

    public void setRealMinMaxValue(double min, double max, double value) {
        setRealMinMaxValue(min, max, value, true);
    }

    public void setRealMinMaxValue(double min, double max, double value, boolean triggerChangedEvent) {
        // Avoid to get infinity value and lock the slider
        if (max - min == 0) {
            max += 1;
        }
        realMin = min;
        realMax = max;
        minMaxValueAction(toSliderValue(min), toSliderValue(max), toSliderValue(value), triggerChangedEvent);
    }

    private synchronized void minMaxValueAction(int min, int max, int value, boolean trigger) {
        if (min > max) {
            throw new IllegalStateException("min > max"); //$NON-NLS-1$
        }

        // Adjust the value to min and max to avoid the model to change the min and the max
        int v = (value > max) ? max : ((value < min) ? min : value);
        boolean oldTrigger = triggerAction;
        triggerAction = trigger;
        model.setRangeProperties(v, model.getExtent(), min, max, model.getValueIsAdjusting());
        triggerAction = oldTrigger;
        boolean paintThicks = max < 65536;

        for (Object c : basicState.getComponents()) {
            if (c instanceof JSliderW) {
                JSliderW s = (JSliderW) c;
                if (s.isShowLabels()) {
                    // When range becomes big do not display thick (can be very slow) and labels
                    s.setPaintTicks(paintThicks);
                    s.setPaintLabels(paintThicks);
                }
                updateSliderProoperties(s);
                setSliderLabelValues(s, min, max, realMin, realMax);
            }
        }
    }

    public boolean isTriggerAction() {
        return triggerAction;
    }

    @Override
    public ActionW getActionW() {
        return basicState.getActionW();
    }

    public void setSliderValue(int value) {
        model.setValue(value);
    }

    public void setSliderValue(int value, boolean triggerChangedEvent) {
        if (triggerChangedEvent) {
            setSliderValue(value);
        } else {
            boolean ajusting = valueIsAdjusting ? true : !model.getValueIsAdjusting();
            if (ajusting) {
                boolean oldTrigger = triggerAction;
                triggerAction = false;
                setSliderValue(value);
                triggerAction = oldTrigger;
            }
        }
    }

    public void setRealValue(double value) {
        model.setValue(toSliderValue(value));
    }

    public void setRealValue(double value, boolean triggerChangedEvent) {
        if (triggerChangedEvent) {
            setRealValue(value);
        } else {
            boolean ajusting = valueIsAdjusting ? true : !model.getValueIsAdjusting();
            if (ajusting) {
                boolean oldTrigger = triggerAction;
                triggerAction = false;
                setRealValue(value);
                triggerAction = oldTrigger;
            }
        }
    }

    public boolean isValueIsAdjusting() {
        return valueIsAdjusting;
    }

    public int getSliderMin() {
        return model.getMinimum();
    }

    public int getSliderMax() {
        return model.getMaximum();
    }

    public int getSliderValue() {
        return model.getValue();
    }

    public double getRealValue() {
        return toModelValue(model.getValue());
    }

    public DefaultBoundedRangeModel getSliderModel() {
        return model;
    }

    public String getValueToDisplay() {
        return getDisplayedModelValue(getSliderValue(), getSliderMax(), realMin, realMax);
    }

    @Override
    public void stateChanged(ChangeEvent evt) {
        boolean ajusting = valueIsAdjusting ? true : !model.getValueIsAdjusting();
        if (triggerAction && ajusting) {
            stateChanged(model);
            AuditLog.LOGGER.info("action:{} val:{} min:{} max:{}", //$NON-NLS-1$
                basicState.getActionW().cmd(), model.getValue(), model.getMinimum(), model.getMaximum());
        }

        for (Object c : basicState.getComponents()) {
            if (c instanceof JSliderW) {
                updateSliderProoperties((JSliderW) c);
            }
        }
    }

    public abstract void stateChanged(BoundedRangeModel model);

    @Override
    public String toString() {
        return basicState.getActionW().getTitle();
    }

    @Override
    public boolean registerActionState(Object c) {
        if (basicState.registerActionState(c)) {
            if (c instanceof JSliderW) {
                JSliderW slider = (JSliderW) c;
                slider.setModel(model);
                updateSliderProoperties(slider);
            }
            return true;
        }
        return false;
    }

    @Override
    public void unregisterActionState(Object c) {
        basicState.unregisterActionState(c);
        if (c instanceof JSliderW) {
            ((JSliderW) c).setModel(new DefaultBoundedRangeModel(0, 0, 0, 100));
        }
    }

    public static void setSliderLabelValues(JSliderW slider, final int min, int max) {
        setSliderLabelValues(slider, min, max, null, null);
    }

    public static void setSliderLabelValues(JSliderW slider, final int min, int max, final Double realMin,
        final Double realMax) {
        final int div = slider.getLabelDivision();
        if (div < 1) {
            return;
        }
        int space = (max - min) / (div - 1);
        final int spacing = space < 1 ? 1 : space;
        if (!slider.getPaintLabels()) {
            return;
        }

        final Hashtable<Integer, JLabel> table = new Hashtable<>();
        GuiExecutor.instance().invokeAndWait(() -> {
            for (int i = 0; i < div; i++) {
                int index = i * spacing + min;
                table.put(index, new JLabel(getDisplayedModelValue(i * spacing + min, max, realMin, realMax)));
            }
        });

        slider.setLabelTable(table);
        SliderChangeListener.setFont(slider, FontTools.getFont10());
        slider.setMajorTickSpacing(spacing);
    }

    private static String getDisplayedModelValue(int sliderValue, int sliderMax, Double modelMin, Double modelMax) {
        if (modelMin == null || modelMax == null) {
            return Integer.toString(sliderValue);
        }
        double realVal = toModelValue(sliderValue, sliderMax, modelMin, modelMax);
        return DecFormater.twoDecimal(realVal);
    }

    public void updateSliderProoperties(JSliderW slider) {
        JPanel panel = (JPanel) slider.getParent();

        String result = basicState.getActionW().getTitle() + StringUtil.COLON_AND_SPACE + getValueToDisplay();
        if (slider.isdisplayValueInTitle() && panel != null && panel.getBorder() instanceof TitledBorder) {
            ((TitledBorder) panel.getBorder()).setTitle(result);
            panel.repaint();
        } else {
            slider.setToolTipText(result);
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (basicState.isActionEnabled() && !e.isConsumed()) {
            int buttonMask = getButtonMaskEx();
            if ((e.getModifiersEx() & buttonMask) != 0) {
                lastPosition = isMoveOnX() ? e.getX() : e.getY();
                dragAccumulator = getSliderValue();
            }
        } else {
            // Ensure to not enter in drag event when the mouse event is consumed
            dragAccumulator = Double.MAX_VALUE;
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (basicState.isActionEnabled() && !e.isConsumed()) {
            int buttonMask = getButtonMaskEx();
            int modifier = e.getModifiersEx();
            /*
             * dragAccumulator == Double.NaN when the listener did not catch the Pressed MouseEvent (could append in
             * multisplit container)
             */
            if ((modifier & buttonMask) != 0 && MathUtil.isDifferent(dragAccumulator, Double.MAX_VALUE)) {
                int position = isMoveOnX() ? e.getX() : e.getY();
                int mask = InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK;
                // Accelerate the action if ctrl or shift is down
                double acceleratorKey = (modifier & mask) == 0 ? 1.0 : (modifier & mask) == mask ? 5.0 : 2.5;
                double val = (position - lastPosition) * getMouseSensivity() * acceleratorKey;
                if (MathUtil.isEqualToZero(val)) {
                    return;
                }
                lastPosition = position;
                if (isInverse()) {
                    dragAccumulator -= val;
                } else {
                    dragAccumulator += val;
                }
                if (dragAccumulator < getSliderMin()) {
                    dragAccumulator = getSliderMin();
                }
                if (dragAccumulator > getSliderMax()) {
                    dragAccumulator = getSliderMax();
                }

                if (val < 0.0) {
                    setSliderValue((int) Math.ceil(dragAccumulator));
                } else {
                    setSliderValue((int) Math.floor(dragAccumulator));
                }
            }
        }
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        if (basicState.isActionEnabled() && !e.isConsumed()) {
            setSliderValue(getSliderValue() + e.getWheelRotation() * e.getScrollAmount());
        }
    }

    public JSliderW createSlider(int labelDivision, boolean displayValueInTitle) {
        final JPanel palenSlider1 = new JPanel();
        palenSlider1.setLayout(new BoxLayout(palenSlider1, BoxLayout.Y_AXIS));
        palenSlider1.setBorder(new TitledBorder(basicState.getActionW().getTitle()));
        JSliderW slider = new JSliderW(model.getMinimum(), model.getMaximum(), model.getValue());
        slider.setLabelDivision(labelDivision);
        slider.setdisplayValueInTitle(displayValueInTitle);
        slider.setPaintTicks(true);
        slider.setShowLabels(labelDivision > 0);
        palenSlider1.add(slider);
        registerActionState(slider);
        if (slider.isShowLabels()) {
            slider.setPaintLabels(true);
            setSliderLabelValues(slider, model.getMinimum(), model.getMaximum(), realMin, realMax);
        }
        return slider;
    }

    public int toSliderValue(double modelValue) {
        Double modelMin = realMin;
        Double modelMax = realMax;
        if (modelMin == null || modelMax == null) {
            return (int) modelValue;
        }
        return (int) Math.round((modelValue - modelMin) * (model.getMaximum() / (modelMax - modelMin)));
    }

    public double toModelValue(int sliderValue) {
        return toModelValue(sliderValue, model.getMaximum(), realMin, realMax);
    }

    protected static double toModelValue(int sliderValue, int sliderMax, Double modelMin, Double modelMax) {
        if (modelMin == null || modelMax == null) {
            return sliderValue;
        }
        return (sliderValue * (modelMax - modelMin)) / sliderMax + modelMin;
    }

    public static void setFont(JSlider jslider, Font font) {
        Enumeration<?> enumVal = jslider.getLabelTable().elements();
        while (enumVal.hasMoreElements()) {
            Object el = enumVal.nextElement();
            if (el instanceof JLabel) {
                ((JLabel) el).setFont(font);
            }
        }
    }

}
