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

import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SpinnerNumberModel;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.TitledBorder;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.GuiUtils.IconColor;
import org.weasis.core.util.MathUtil;
import org.weasis.core.util.StringUtil;

public abstract class SliderCineListener extends SliderChangeListener {

  public enum TIME {
    SECOND,
    MINUTE,
    HOUR
  }

  private static final double DEFAULT_MIN = 0.01;
  private static final double DEFAULT_MAX = 90.0;

  protected volatile boolean sweeping = false;
  private volatile boolean backward = false;

  private volatile long startTime;
  private volatile long spentTime;

  private final AtomicInteger iteration = new AtomicInteger(0);

  private final TIME time;
  private final SpinnerNumberModel speedModel;

  private final Timer timer =
      new Timer(
          1000 / 20,
          _ -> {
            int step = (int) (getSpeed() / 5);
            if (step <= 0) {
              step = 1;
            }
            if (iteration.getAndIncrement() % step == 0) {
              long prevStartTime = startTime;
              startTime = System.currentTimeMillis();
              if (iteration.get() > step) {
                spentTime = (System.currentTimeMillis() - prevStartTime) / 1000;
              } else {
                spentTime = 0;
              }
            }
            int offset = backward ? -1 : 1;
            int frameIndex = getSliderValue() + offset;
            if (frameIndex > getSliderMax()) {
              if (sweeping) {
                backward = true;
                frameIndex = frameIndex - 2;
              } else {
                frameIndex = getSliderMin();
              }
            } else if (frameIndex < getSliderMin()) {
              backward = false;
              frameIndex = getSliderMin();
            }
            setSliderValue(frameIndex);
          });

  protected SliderCineListener(
      Feature<? extends ActionState> action,
      int min,
      int max,
      int value,
      double speed,
      TIME time,
      double mouseSensitivity) {
    this(action, min, max, value, speed, time);
    setMouseSensitivity(mouseSensitivity);
  }

  protected SliderCineListener(
      Feature<? extends ActionState> action, int min, int max, int value, double speed, TIME time) {
    super(action, min, max, value);
    this.time = time;
    speedModel = new SpinnerNumberModel(speed, DEFAULT_MIN, DEFAULT_MAX, 1.0);
    speedModel.addChangeListener(_ -> updateSpeed());
  }

  public void start() {
    if (!timer.isRunning() && getSliderMax() - getSliderMin() > 0) {
      timer.setDelay((int) (1000 / getSpeed()));
      iteration.set(0);
      timer.start();
    }
  }

  public void stop() {
    if (timer.isRunning()) {
      timer.stop();
    }
  }

  public boolean isCining() {
    return timer.isRunning();
  }

  public double getSpeed() {
    return (Double) speedModel.getValue();
  }

  @Override
  public void updateSliderProperties(JSliderW slider) {
    double rate = getCurrentCineRate();
    StringBuilder buffer = new StringBuilder(Messages.getString("SliderCineListener.img"));
    buffer.append(StringUtil.COLON_AND_SPACE);
    buffer.append(getValueToDisplay());

    if (slider.isDisplayValueInTitle() && slider.getBorder() instanceof TitledBorder titledBorder) {
      if (MathUtil.isDifferentFromZero(rate)) {
        buffer.append(" (");
        buffer.append(DecFormatter.twoDecimal(rate));
        if (TIME.SECOND.equals(time)) {
          buffer.append(Messages.getString("SliderCineListener.fps"));
        } else if (TIME.MINUTE.equals(time)) {
          buffer.append(Messages.getString("SliderCineListener.fpm"));
        } else if (TIME.HOUR.equals(time)) {
          buffer.append(Messages.getString("SliderCineListener.fph"));
        }
        buffer.append(")");
      }
      titledBorder.setTitleColor(
          rate > 0 && Math.abs(rate - getSpeed()) > getSpeed() / 20.0
              ? IconColor.ACTIONS_RED.getColor()
              : UIManager.getColor("TitledBorder.titleColor"));
      titledBorder.setTitle(buffer.toString());
      slider.repaint();
    } else {
      slider.setToolTipText(buffer.toString());
    }
  }

  public double getCurrentCineRate() {
    if (isCining()) {
      double time = spentTime;
      if (time == 0) {
        return 1000.0 / timer.getDelay();
      }
      return spentTime;
    }
    return 0.0;
  }

  public void setSpeed(double speed) {
    speedModel.setValue(Math.max(DEFAULT_MIN, Math.min(speed, DEFAULT_MAX)));
  }

  protected void updateSpeed() {
    if (timer.isRunning()) {
      iteration.set(0);
      timer.setDelay((int) (1000 / getSpeed()));
    }
  }

  public SpinnerNumberModel getSpeedModel() {
    return speedModel;
  }

  public void setSweeping(boolean sweep) {
    this.sweeping = sweep;
  }
}
