/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.api.gui.util;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.Hashtable;

import javax.swing.BoundedRangeModel;
import javax.swing.BoxLayout;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.weasis.core.api.service.AuditLog;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.api.util.StringUtil;

public abstract class SliderChangeListener extends MouseActionAdapter implements ChangeListener, ActionState {

    private final BasicActionState basicState;
    private final DefaultBoundedRangeModel model;
    private boolean triggerAction = true;
    private boolean valueIsAdjusting = true;

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

    public SliderChangeListener(ActionW action, int min, int max, int value) {
        this(action, min, max, value, true);
    }

    @Override
    public void enableAction(boolean enabled) {
        basicState.enableAction(enabled);
    }

    @Override
    public boolean isActionEnabled() {
        return basicState.isActionEnabled();
    }

    public int getMin() {
        return model.getMinimum();
    }

    public void setMinMax(int min, int max) {
        setMinMaxValue(min, max, model.getValue());
    }

    public void setMinMaxValue(int min, int max, int value) {
        minMaxValueAction(min, max, value, true);
    }

    public void setMinMaxValueWithoutTriggerAction(int min, int max, int value) {
        minMaxValueAction(min, max, value, false);
    }

    private synchronized void minMaxValueAction(int min, int max, int value, boolean trigger) {
        if (min <= max) {
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
                    setSliderLabelValues(s, min, max);
                }
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
        return Integer.toString(getValue());
    }

    @Override
    public void stateChanged(ChangeEvent evt) {
        boolean ajusting = valueIsAdjusting ? true : !model.getValueIsAdjusting();
        if (triggerAction && ajusting) {
            stateChanged(model);
            AuditLog.LOGGER.info("action:{} val:{} min:{} max:{}", //$NON-NLS-1$
                new Object[] { basicState.getActionW().cmd(), model.getValue(), model.getMinimum(),
                    model.getMaximum() });
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
        final int div = slider.getLabelDivision();
        if (div < 1) {
            return;
        }
        int space = (max - min) / (div - 1);
        // TODO spacing related to the silder size
        final int spacing = space < 1 ? 1 : space;
        if (!slider.getPaintLabels()) {
            return;
        }

        final Hashtable<Integer, JLabel> table = new Hashtable<>();
        GuiExecutor.instance().invokeAndWait(() -> {
            for (int i = 0; i < div; i++) {
                Integer index = i * spacing + min;
                table.put(index, new JLabel(index.toString()));
            }
        });

        slider.setLabelTable(table);
        FontTools.setFont10(slider);
        slider.setMajorTickSpacing(spacing);
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
                dragAccumulator = getValue();
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
                if (dragAccumulator < getMin()) {
                    dragAccumulator = getMin();
                }
                if (dragAccumulator > getMax()) {
                    dragAccumulator = getMax();
                }

                if (val < 0.0) {
                    setValue((int) Math.ceil(dragAccumulator));
                } else {
                    setValue((int) Math.floor(dragAccumulator));
                }
            }
        }
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        if (basicState.isActionEnabled() && !e.isConsumed()) {
            setValue(getValue() + e.getWheelRotation() * e.getScrollAmount());
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
            setSliderLabelValues(slider, model.getMinimum(), model.getMaximum());
        }
        return slider;
    }
}
