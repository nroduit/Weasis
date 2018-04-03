/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.acquire.dockable.components.actions.rectify.lib;

import java.util.Dictionary;
import java.util.Hashtable;

import javax.swing.JLabel;

import org.weasis.acquire.Messages;
import org.weasis.acquire.dockable.components.actions.rectify.RectifyPanel;
import org.weasis.acquire.dockable.components.util.AbstractSliderComponent;
import org.weasis.acquire.operations.impl.RectifyOrientationChangeListener;

public class OrientationSliderComponent extends AbstractSliderComponent {
    private static final long serialVersionUID = -4238024766089795426L;

    private static final int RECTIFY_ORIENTATION_MIN = -45;
    private static final int RECTIFY_ORIENTATION_MAX = 45;
    private static final int RECTIFY_ORIENTATION_DEFAULT = 0;

    private static final Hashtable<Integer, JLabel> labels = new Hashtable<>();

    static {
        int div = 7;
        int space = (RECTIFY_ORIENTATION_MAX - RECTIFY_ORIENTATION_MIN) / (div - 1);

        for (int i = 0; i < div; i++) {
            Integer index = i * space + RECTIFY_ORIENTATION_MIN;
            labels.put(index, new JLabel(index.toString()));
        }
    }

    private final RectifyOrientationChangeListener listener;

    public OrientationSliderComponent(RectifyPanel panel) {
        super(panel, Messages.getString("OrientationSliderComponent.orientation")); //$NON-NLS-1$
        listener = new RectifyOrientationChangeListener(panel.getRectifyAction());
        addChangeListener(listener);
    }

    public RectifyOrientationChangeListener getListener() {
        return listener;
    }

    @Override
    public int getDefaultValue() {
        return RECTIFY_ORIENTATION_DEFAULT;
    }

    @Override
    public int getMin() {
        return RECTIFY_ORIENTATION_MIN;
    }

    @Override
    public int getMax() {
        return RECTIFY_ORIENTATION_MAX;
    }

    @Override
    public Dictionary<Integer, JLabel> getLabels() {
        return labels;
    }

    @Override
    public String getDisplayTitle() {
        return super.getDisplayTitle() + Messages.getString("OrientationSliderComponent.deg_symb"); //$NON-NLS-1$
    }
}
