/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.acquire.dockable.components.actions.contrast.comp;

import java.util.Dictionary;
import java.util.Hashtable;

import javax.swing.JLabel;

import org.weasis.acquire.Messages;
import org.weasis.acquire.dockable.components.actions.contrast.ContrastPanel;
import org.weasis.acquire.dockable.components.util.AbstractSliderComponent;

public class BrightnessComponent extends AbstractSliderComponent {
    private static final long serialVersionUID = -4387734543272450700L;

    public static final int BRIGHTNESS_VALUE = 0;
    public static final int BRIGHTNESS_MIN = -127;
    public static final int BRIGHTNESS_MAX = 127;

    private static final Hashtable<Integer, JLabel> labels = new Hashtable<>();

    static {
        labels.put(BRIGHTNESS_MIN, new JLabel(Messages.getString("BrightnessComponent.low") + BRIGHTNESS_MIN)); //$NON-NLS-1$
        labels.put(BRIGHTNESS_VALUE, new JLabel(String.valueOf(BRIGHTNESS_VALUE)));
        labels.put(BRIGHTNESS_MAX, new JLabel(Messages.getString("BrightnessComponent.high") + BRIGHTNESS_MAX)); //$NON-NLS-1$
    }

    public BrightnessComponent(ContrastPanel panel) {
        super(panel, Messages.getString("BrightnessComponent.brightness")); //$NON-NLS-1$
        addChangeListener(panel);
    }

    @Override
    public int getDefaultValue() {
        return BRIGHTNESS_VALUE;
    }

    @Override
    public int getMin() {
        return BRIGHTNESS_MIN;
    }

    @Override
    public int getMax() {
        return BRIGHTNESS_MAX;
    }

    @Override
    public Dictionary<Integer, JLabel> getLabels() {
        return labels;
    }

}
