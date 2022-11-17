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

import javax.swing.SpinnerNumberModel;
import javax.swing.UIManager;
import javax.swing.border.TitledBorder;
import org.weasis.core.api.Messages;
import org.weasis.core.api.gui.util.GuiUtils.IconColor;
import org.weasis.core.util.StringUtil;

public abstract class SliderCineListener extends SliderChangeListener {
  public enum TIME {
    SECOND,
    MINUTE,
    HOUR
  }

  private final TIME time;
  private final SpinnerNumberModel speedModel;

  protected SliderCineListener(
      Feature<? extends ActionState> action,
      int min,
      int max,
      int value,
      int speed,
      TIME time,
      double mouseSensitivity) {
    this(action, min, max, value, speed, time);
    setMouseSensitivity(mouseSensitivity);
  }

  protected SliderCineListener(
      Feature<? extends ActionState> action, int min, int max, int value, int speed, TIME time) {
    super(action, min, max, value);
    this.time = time;
    speedModel = new SpinnerNumberModel(speed, 1, 200, 1);
    speedModel.addChangeListener(
        e -> setSpeed((Integer) ((SpinnerNumberModel) e.getSource()).getValue()));
  }

  public abstract void start();

  public abstract void stop();

  public abstract boolean isCining();

  public int getSpeed() {
    return (Integer) speedModel.getValue();
  }

  @Override
  public void updateSliderProperties(JSliderW slider) {
    int rate = getCurrentCineRate();
    StringBuilder buffer = new StringBuilder(Messages.getString("SliderCineListener.img"));
    buffer.append(StringUtil.COLON_AND_SPACE);
    buffer.append(getValueToDisplay());

    if (slider.isDisplayValueInTitle() && slider.getBorder() instanceof TitledBorder titledBorder) {
      if (rate > 0) {
        buffer.append(" (");
        buffer.append(rate);
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
          rate > 0 && rate < (getSpeed() - 1)
              ? IconColor.ACTIONS_RED.getColor()
              : UIManager.getColor("TitledBorder.titleColor"));
      titledBorder.setTitle(buffer.toString());
      slider.repaint();
    } else {
      slider.setToolTipText(buffer.toString());
    }
  }

  public int getCurrentCineRate() {
    return 0;
  }

  public void setSpeed(int speed) {
    speedModel.setValue(speed);
  }

  public SpinnerNumberModel getSpeedModel() {
    return speedModel;
  }
}
