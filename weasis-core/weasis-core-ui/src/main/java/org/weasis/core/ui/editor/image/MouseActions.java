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
package org.weasis.core.ui.editor.image;

import java.awt.event.InputEvent;

import org.osgi.service.prefs.Preferences;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.ui.Messages;

public class MouseActions {

    public final static int SCROLL_MASK = 1 << 1;

    public final static String PREFERENCE_NODE = "mouse.action"; //$NON-NLS-1$
    public final static String P_MOUSE_LEFT = "mouse_left"; //$NON-NLS-1$
    public final static String P_MOUSE_MIDDLE = "mouse_middle"; //$NON-NLS-1$
    public final static String P_MOUSE_RIGHT = "mouse_right"; //$NON-NLS-1$
    public final static String P_MOUSE_WHEEL = "mouse_wheel"; //$NON-NLS-1$

    public final static String LEFT = "left"; //$NON-NLS-1$
    public final static String MIDDLE = "middle"; //$NON-NLS-1$
    public final static String RIGHT = "right"; //$NON-NLS-1$
    public final static String WHEEL = "wheel"; //$NON-NLS-1$

    private String left = ActionW.WINLEVEL.cmd();
    private String middle = ActionW.PAN.cmd();
    private String right = ActionW.CONTEXTMENU.cmd();
    private String wheel = ActionW.SCROLL_SERIES.cmd();
    private int activeButtons =
        InputEvent.BUTTON1_DOWN_MASK | InputEvent.BUTTON2_DOWN_MASK | InputEvent.BUTTON3_DOWN_MASK | SCROLL_MASK;

    public MouseActions(String left, String middle, String right, String wheel) {
        super();
        this.left = left;
        this.middle = middle;
        this.right = right;
        this.wheel = wheel;
    }

    public MouseActions(Preferences prefs) {
        applyPreferences(prefs);
    }

    public String getWheel() {
        return wheel;
    }

    public void setWheel(String wheel) {
        this.wheel = wheel;
    }

    @Override
    public String toString() {
        return left + "/" + right + "/" + wheel; //$NON-NLS-1$ //$NON-NLS-2$
    }

    public String getToolTips() {
        return "<html>Left: " + left + "<br>Right: " + right + "<br>Wheel: " + wheel + "</html>"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    }

    public String getLeft() {
        return left;
    }

    public void setLeft(String left) {
        this.left = left;
    }

    public String getRight() {
        return right;
    }

    public void setRight(String right) {
        this.right = right;
    }

    public String getMiddle() {
        return middle;
    }

    public void setMiddle(String middle) {
        this.middle = middle;
    }

    public String getAction(String type) {
        if (MouseActions.LEFT.equals(type)) {
            return left;
        } else if (MouseActions.MIDDLE.equals(type)) {
            return middle;
        } else if (MouseActions.RIGHT.equals(type)) {
            return right;
        } else if (MouseActions.WHEEL.equals(type)) {
            return wheel;
        }
        return null;
    }

    public int getActiveButtons() {
        return activeButtons;
    }

    public void setActiveButtons(int activeButtons) {
        this.activeButtons = activeButtons;
    }

    public void setAction(String type, String action) {
        if (MouseActions.LEFT.equals(type)) {
            setLeft(action);
        } else if (MouseActions.MIDDLE.equals(type)) {
            setMiddle(action);
        } else if (MouseActions.RIGHT.equals(type)) {
            setRight(action);
        } else if (MouseActions.WHEEL.equals(type)) {
            setWheel(action);
        }
    }

    public static void loadPreferences(Preferences prefs, boolean defaultValue) {
        if (prefs != null) {
            Preferences p = prefs.node(MouseActions.PREFERENCE_NODE);
            p.put(P_MOUSE_LEFT, defaultValue ? ActionW.WINLEVEL.cmd() : p.get(P_MOUSE_LEFT, ActionW.WINLEVEL
                .cmd()));
            p.put(P_MOUSE_MIDDLE, defaultValue ? ActionW.PAN.cmd() : p.get(P_MOUSE_MIDDLE, ActionW.PAN
                .cmd()));
            p.put(P_MOUSE_RIGHT, defaultValue ? ActionW.CONTEXTMENU.cmd() : p.get(P_MOUSE_RIGHT,
                ActionW.CONTEXTMENU.cmd()));
            p.put(P_MOUSE_WHEEL, defaultValue ? ActionW.ZOOM.cmd() : p.get(P_MOUSE_WHEEL, ActionW.ZOOM
                .cmd()));
        }
    }

    public void applyPreferences(Preferences prefs) {
        if (prefs != null) {
            Preferences p = prefs.node(MouseActions.PREFERENCE_NODE);
            left = p.get(P_MOUSE_LEFT, left);
            middle = p.get(P_MOUSE_MIDDLE, middle);
            right = p.get(P_MOUSE_RIGHT, right);
            wheel = p.get(P_MOUSE_WHEEL, wheel);
        }
    }

    public void savePreferences(Preferences prefs) {
        if (prefs != null) {
            Preferences p = prefs.node(MouseActions.PREFERENCE_NODE);
            BundlePreferences.putStringPreferences(p, P_MOUSE_LEFT, left);
            BundlePreferences.putStringPreferences(p, P_MOUSE_MIDDLE, middle);
            BundlePreferences.putStringPreferences(p, P_MOUSE_RIGHT, right);
            BundlePreferences.putStringPreferences(p, P_MOUSE_WHEEL, wheel);
        }
    }
}
