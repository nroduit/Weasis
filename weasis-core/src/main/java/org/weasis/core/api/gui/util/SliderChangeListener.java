/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.util;

import java.awt.Font;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import javax.swing.BorderFactory;
import javax.swing.BoundedRangeModel;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.api.util.FontItem;
import org.weasis.core.util.MathUtil;
import org.weasis.core.util.StringUtil;

public abstract class SliderChangeListener extends MouseActionAdapter
    implements ChangeListener, ActionState {

  private final DefaultBoundedRangeModel model;
  protected final BasicActionState basicState;
  protected volatile boolean triggerAction = true;
  protected volatile boolean valueIsAdjusting;
  protected Double realMin;
  protected Double realMax;

  protected SliderChangeListener(
      Feature<? extends ActionState> action, int min, int max, int value) {
    this(action, min, max, value, true);
  }

  protected SliderChangeListener(
      Feature<? extends ActionState> action,
      int min,
      int max,
      int value,
      boolean valueIsAdjusting,
      double mouseSensitivity) {
    this(action, min, max, value, valueIsAdjusting);
    setMouseSensitivity(mouseSensitivity);
  }

  protected SliderChangeListener(
      Feature<? extends ActionState> action,
      int min,
      int max,
      int value,
      boolean valueIsAdjusting) {
    super();
    this.basicState = new BasicActionState(action);
    this.valueIsAdjusting = valueIsAdjusting;
    model = new DefaultBoundedRangeModel(value, 0, min, max);
    model.addChangeListener(this);
  }

  protected SliderChangeListener(
      Feature<? extends ActionState> action,
      double min,
      double max,
      double value,
      boolean valueIsAdjusting,
      double mouseSensitivity,
      int sliderRange) {
    this.basicState = new BasicActionState(action);
    this.valueIsAdjusting = valueIsAdjusting;
    setMouseSensitivity(mouseSensitivity);
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

  public void setRealMinMaxValue(
      double min, double max, double value, boolean triggerChangedEvent) {
    // Avoid getting infinity value and lock the slider
    if (max - min == 0) {
      max += 1;
    }
    realMin = min;
    realMax = max;
    minMaxValueAction(
        toSliderValue(min), toSliderValue(max), toSliderValue(value), triggerChangedEvent);
  }

  private synchronized void minMaxValueAction(int min, int max, int value, boolean trigger) {
    if (min > max) {
      throw new IllegalStateException("min > max");
    }

    // Adjust the value to min and max to avoid the model to change the min and the max
    int v = (value > max) ? max : Math.max(value, min);
    boolean oldTrigger = triggerAction;
    triggerAction = trigger;
    model.setRangeProperties(v, model.getExtent(), min, max, model.getValueIsAdjusting());
    triggerAction = oldTrigger;
    boolean paintThick = max < 65536;

    for (Object c : basicState.getComponents()) {
      if (c instanceof JSliderW s) {
        if (s.isShowLabels()) {
          // When range becomes big do not display thick (can be very slow) and labels
          s.setPaintTicks(paintThick);
          s.setPaintLabels(paintThick);
        }
        updateSliderProperties(s);
        setSliderLabelValues(s, min, max, realMin, realMax);
      }
    }
  }

  public boolean isTriggerAction() {
    return triggerAction;
  }

  @Override
  public Feature<? extends ActionState> getActionW() {
    return basicState.getActionW();
  }

  public void setSliderValue(int value) {
    model.setValue(value);
  }

  public void setSliderValue(int value, boolean triggerChangedEvent) {
    if (triggerChangedEvent) {
      setSliderValue(value);
    } else {
      boolean adjusting = valueIsAdjusting || !model.getValueIsAdjusting();
      if (adjusting) {
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
      boolean adjusting = valueIsAdjusting || !model.getValueIsAdjusting();
      if (adjusting) {
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
    boolean adjusting = valueIsAdjusting || !model.getValueIsAdjusting();
    if (triggerAction && adjusting) {
      stateChanged(model);
      AuditLog.LOGGER.info(
          "action:{} val:{} min:{} max:{}",
          basicState.getActionW().cmd(),
          model.getValue(),
          model.getMinimum(),
          model.getMaximum());
    }

    for (Object c : basicState.getComponents()) {
      if (c instanceof JSliderW slider) {
        updateSliderProperties(slider);
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
      if (c instanceof JSliderW slider) {
        slider.setModel(model);
        updateSliderProperties(slider);
      }
      return true;
    }
    return false;
  }

  @Override
  public void unregisterActionState(Object c) {
    basicState.unregisterActionState(c);
    if (c instanceof JSliderW slider) {
      slider.setModel(new DefaultBoundedRangeModel(0, 0, 0, 100));
    }
  }

  public static void setSliderLabelValues(JSliderW slider, final int min, int max) {
    setSliderLabelValues(slider, min, max, null, null);
  }

  public static void setSliderLabelValues(
      JSliderW slider, final int min, int max, final Double realMin, final Double realMax) {
    final int div = slider.getLabelDivision();
    if (div < 1) {
      return;
    }
    int space = (max - min) / (div - 1);
    final int spacing = Math.max(space, 1);
    if (!slider.getPaintLabels()) {
      return;
    }

    final Hashtable<Integer, JLabel> table = new Hashtable<>();
    GuiExecutor.instance()
        .invokeAndWait(
            () -> {
              for (int i = 0; i < div; i++) {
                int index = i * spacing + min;
                table.put(
                    index,
                    new JLabel(getDisplayedModelValue(i * spacing + min, max, realMin, realMax)));
              }
            });

    slider.setLabelTable(table);
    SliderChangeListener.setFont(slider, FontItem.MINI.getFont());
    slider.setMajorTickSpacing(spacing);
  }

  private static String getDisplayedModelValue(
      int sliderValue, int sliderMax, Double modelMin, Double modelMax) {
    if (modelMin == null || modelMax == null) {
      return Integer.toString(sliderValue);
    }
    double realVal = toModelValue(sliderValue, sliderMax, modelMin, modelMax);
    return DecFormatter.twoDecimal(realVal);
  }

  public void updateSliderProperties(JSliderW slider) {
    String result =
        basicState.getActionW().getTitle() + StringUtil.COLON_AND_SPACE + getValueToDisplay();
    updateSliderProperties(slider, result);
  }

  public static void updateSliderProperties(JSliderW slider, String title) {
    if (slider.isDisplayValueInTitle() && slider.getBorder() instanceof TitledBorder titledBorder) {
      titledBorder.setTitle(title);
      slider.repaint();
    } else {
      slider.setToolTipText(title);
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
       * multi split container)
       */
      if ((modifier & buttonMask) != 0 && MathUtil.isDifferent(dragAccumulator, Double.MAX_VALUE)) {
        int position = isMoveOnX() ? e.getX() : e.getY();
        int mask = InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK;
        // Accelerate the action if ctrl or shift is down
        double acceleratorKey =
            (modifier & mask) == 0 ? 1.0 : (modifier & mask) == mask ? 5.0 : 2.5;
        double val = (position - lastPosition) * getMouseSensitivity() * acceleratorKey;
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
    TitledBorder titledBorder =
        new TitledBorder(
            BorderFactory.createEmptyBorder(),
            basicState.getActionW().getTitle(),
            TitledBorder.LEADING,
            TitledBorder.DEFAULT_POSITION,
            FontItem.MEDIUM.getFont(),
            null);
    JSliderW slider = new JSliderW(model.getMinimum(), model.getMaximum(), model.getValue());
    slider.setLabelDivision(labelDivision);
    slider.setDisplayValueInTitle(displayValueInTitle);
    slider.setPaintTicks(true);
    slider.setShowLabels(labelDivision > 0);
    slider.setBorder(titledBorder);
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

  protected static double toModelValue(
      int sliderValue, int sliderMax, Double modelMin, Double modelMax) {
    if (modelMin == null || modelMax == null) {
      return sliderValue;
    }
    return (sliderValue * (modelMax - modelMin)) / sliderMax + modelMin;
  }

  public static void setFont(JSlider jslider, Font font) {
    @SuppressWarnings("rawtypes")
    Dictionary labelTable = jslider.getLabelTable();
    if (labelTable == null) {
      return;
    }
    Enumeration<?> labels = labelTable.keys();
    while (labels.hasMoreElements()) {
      if (labelTable.get(labels.nextElement()) instanceof JLabel label) {
        label.setFont(font);
      }
    }
  }
}
