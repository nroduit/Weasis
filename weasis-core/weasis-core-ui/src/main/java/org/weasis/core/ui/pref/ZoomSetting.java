/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.pref;

import java.awt.Color;

import org.osgi.service.prefs.Preferences;
import org.weasis.core.api.service.BundlePreferences;

public class ZoomSetting {

    public static final String PREFERENCE_NODE = "zoom"; //$NON-NLS-1$
    // public static final String P_ZOOM_SYNCH = "zoom.synch"; //$NON-NLS-1$
    // public static final String P_SHOW_DRAWINGS = "show.drawings"; //$NON-NLS-1$
    // public static final String P_ROUND = "round"; //$NON-NLS-1$

    private boolean lensShowDrawings = true;
    private boolean lensSynchronize = false;
    private int lensWidth = 200;
    private int lensHeight = 200;
    private int interpolation = 1;
    private boolean lensRound = false;
    private int lensLineWidth = 2;
    private Color lensLineColor = new Color(195, 109, 254);

    public void applyPreferences(Preferences prefs) {
        if (prefs != null) {
            Preferences p = prefs.node(ZoomSetting.PREFERENCE_NODE);
            interpolation = p.getInt("interpolation", 1); //$NON-NLS-1$
        }
    }

    public void savePreferences(Preferences prefs) {
        if (prefs != null) {
            Preferences p = prefs.node(ZoomSetting.PREFERENCE_NODE);
            BundlePreferences.putIntPreferences(p, "interpolation", interpolation); //$NON-NLS-1$
        }
    }

    public boolean isLensShowDrawings() {
        return lensShowDrawings;
    }

    public void setLensShowDrawings(boolean lensShowDrawings) {
        this.lensShowDrawings = lensShowDrawings;
    }

    public boolean isLensSynchronize() {
        return lensSynchronize;
    }

    public void setLensSynchronize(boolean lensSynchronize) {
        this.lensSynchronize = lensSynchronize;
    }

    public int getLensWidth() {
        return lensWidth;
    }

    public void setLensWidth(int lensWidth) {
        this.lensWidth = lensWidth;
    }

    public int getLensHeight() {
        return lensHeight;
    }

    public void setLensHeight(int lensHeight) {
        this.lensHeight = lensHeight;
    }

    public int getInterpolation() {
        // 0 : nearest neighbors, 1: bilinear, 2 : bicubic, 3 : bicubic2
        return interpolation;
    }

    public void setInterpolation(int interpolation) {
        this.interpolation = interpolation;
    }

    public boolean isLensRound() {
        return lensRound;
    }

    public void setLensRound(boolean lensRound) {
        this.lensRound = lensRound;
    }

    public int getLensLineWidth() {
        return lensLineWidth;
    }

    public void setLensLineWidth(int lensLineWidth) {
        this.lensLineWidth = lensLineWidth;
    }

    public Color getLensLineColor() {
        return lensLineColor;
    }

    public void setLensLineColor(Color lensLineColor) {
        this.lensLineColor = lensLineColor;
    }

}
