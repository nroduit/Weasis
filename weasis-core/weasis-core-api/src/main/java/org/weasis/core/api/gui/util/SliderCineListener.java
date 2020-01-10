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

import java.awt.Color;

import javax.swing.JPanel;
import javax.swing.SpinnerNumberModel;
import javax.swing.UIManager;
import javax.swing.border.TitledBorder;

import org.weasis.core.api.Messages;
import org.weasis.core.api.util.StringUtil;

public abstract class SliderCineListener extends SliderChangeListener {
    public enum TIME {
        SECOND, MINUTE, HOUR
    }

    private final TIME time;
    private final SpinnerNumberModel speedModel;

    public SliderCineListener(ActionW action, int min, int max, int value, int speed, TIME time,
        double mouseSensivity) {
        this(action, min, max, value, speed, time);
        setMouseSensivity(mouseSensivity);
    }

    public SliderCineListener(ActionW action, int min, int max, int value, int speed, TIME time) {
        super(action, min, max, value);
        this.time = time;
        speedModel = new SpinnerNumberModel(speed, 1, 200, 1);
        speedModel.addChangeListener(e -> setSpeed((Integer) ((SpinnerNumberModel) e.getSource()).getValue()));
    }

    public abstract void start();

    public abstract void stop();

    public abstract boolean isCining();

    public int getSpeed() {
        return (Integer) speedModel.getValue();
    }

    @Override
    public void updateSliderProoperties(JSliderW slider) {
        JPanel panel = (JPanel) slider.getParent();
        int rate = getCurrentCineRate();
        StringBuilder buffer = new StringBuilder(Messages.getString("SliderCineListener.img")); //$NON-NLS-1$
        buffer.append(StringUtil.COLON_AND_SPACE);
        buffer.append(getValueToDisplay());

        if (slider.isdisplayValueInTitle() && panel != null && panel.getBorder() instanceof TitledBorder) {
            if (rate > 0) {
                buffer.append(" - "); //$NON-NLS-1$
                buffer.append(Messages.getString("SliderCineListener.cine")); //$NON-NLS-1$
                buffer.append(StringUtil.COLON_AND_SPACE);
                buffer.append(rate);
                if (TIME.SECOND.equals(time)) {
                    buffer.append(Messages.getString("SliderCineListener.fps")); //$NON-NLS-1$
                } else if (TIME.MINUTE.equals(time)) {
                    buffer.append(Messages.getString("SliderCineListener.fpm")); //$NON-NLS-1$
                } else if (TIME.HOUR.equals(time)) {
                    buffer.append(Messages.getString("SliderCineListener.fph")); //$NON-NLS-1$
                }
            }
            ((TitledBorder) panel.getBorder()).setTitleColor(
                rate > 0 && rate < (getSpeed() - 1) ? Color.red : UIManager.getColor("TitledBorder.titleColor")); //$NON-NLS-1$
            ((TitledBorder) panel.getBorder()).setTitle(buffer.toString());
            panel.repaint();
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
