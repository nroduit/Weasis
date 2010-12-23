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
package org.weasis.core.api.gui.util;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.net.URL;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.Messages;

public enum ActionW {
    SYNCH(Messages.getString("ActionW.synch"), "synch", 0, 0, null), //$NON-NLS-1$ //$NON-NLS-2$

    VIEW_MODE(Messages.getString("ActionW.view_mode"), "viewMode", 0, 0, null), //$NON-NLS-1$ //$NON-NLS-2$

    ZOOM(
         Messages.getString("ActionW.zoom"), "zoom", KeyEvent.VK_Z, 0, getCustomCursor("zoom.png", Messages.getString("ActionW.zoom"), 16, 16)), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

    SCROLL_SERIES(
                  Messages.getString("ActionW.scroll"), "sequence", KeyEvent.VK_S, 0, getCustomCursor("sequence.png", Messages.getString("ActionW.scroll"), 16, 16)), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

    ROTATION(
             Messages.getString("ActionW.rotate"), "rotation", KeyEvent.VK_R, 0, getCustomCursor("rotation.png", Messages.getString("ActionW.rotate"), 16, 16)), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

    CINESPEED(Messages.getString("ActionW.speed"), "cinespeed", 0, 0, null), //$NON-NLS-1$ //$NON-NLS-2$

    CINESTART(Messages.getString("ActionW.start"), "cinestart", 0, 0, null), //$NON-NLS-1$ //$NON-NLS-2$

    CINESTOP(Messages.getString("ActionW.stop"), "cinestop", 0, 0, null), //$NON-NLS-1$ //$NON-NLS-2$

    SKIPBACKWARD(Messages.getString("ActionW.prev"), "skipBackward", 0, 0, null), //$NON-NLS-1$ //$NON-NLS-2$

    SKIPFORWARD(Messages.getString("ActionW.next"), "skipForward", 0, 0, null), //$NON-NLS-1$ //$NON-NLS-2$

    SEEKBACKWARD(Messages.getString("ActionW.fwd"), "seekBackward", 0, 0, null), //$NON-NLS-1$ //$NON-NLS-2$

    SEEKFORWARD(Messages.getString("ActionW.rew"), "seekForward", 0, 0, null), //$NON-NLS-1$ //$NON-NLS-2$

    WINDOW(Messages.getString("ActionW.win"), "window", 0, 0, null), //$NON-NLS-1$ //$NON-NLS-2$

    LEVEL(Messages.getString("ActionW.level"), "level", 0, 0, null), //$NON-NLS-1$ //$NON-NLS-2$

    WINLEVEL(
             Messages.getString("ActionW.wl"), "winLevel", KeyEvent.VK_W, 0, getCustomCursor("winLevel.png", Messages.getString("ActionW.wl"), 16, 16)), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

    FLIP(Messages.getString("ActionW.flip"), "flip", 0, 0, null), //$NON-NLS-1$ //$NON-NLS-2$

    PRESET(Messages.getString("ActionW.preset"), "preset", 0, 0, null), //$NON-NLS-1$ //$NON-NLS-2$

    LUT(Messages.getString("ActionW.lut"), "lut", 0, 0, null), //$NON-NLS-1$ //$NON-NLS-2$

    INVERSELUT("", "inverseLut", 0, 0, null), //$NON-NLS-1$ //$NON-NLS-2$

    RESET(Messages.getString("ActionW.Reset"), "reset", 0, 0, null), //$NON-NLS-1$ //$NON-NLS-2$

    PAN(
        Messages.getString("ActionW.pan"), "pan", KeyEvent.VK_T, 0, getCustomCursor("pan.png", Messages.getString("ActionW.pan"), 16, 16)), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

    DRAW(Messages.getString("ActionW.draw"), "draw", 0, 0, null), //$NON-NLS-1$ //$NON-NLS-2$

    MEASURE(Messages.getString("ActionW.measure"), "measure", KeyEvent.VK_M, 0, null), //$NON-NLS-1$ //$NON-NLS-2$

    SORTSTACK("", "sortStack", 0, 0, null), //$NON-NLS-1$ //$NON-NLS-2$

    CONTEXTMENU(Messages.getString("ActionW.context_menu"), "contextMenu", KeyEvent.VK_Q, 0, null), //$NON-NLS-1$ //$NON-NLS-2$

    VIEWINGPROTOCOL("", "viewingProtocol", 0, 0, null), //$NON-NLS-1$ //$NON-NLS-2$

    LAYOUT(Messages.getString("ActionW.layout"), "layout", 0, 0, null), //$NON-NLS-1$ //$NON-NLS-2$

    MODE(Messages.getString("ActionW.switch_mode"), "mode", 0, 0, null), //$NON-NLS-1$ //$NON-NLS-2$

    IMAGE_OVERLAY(Messages.getString("ActionW.overlay"), "overlay", 0, 0, null), //$NON-NLS-1$ //$NON-NLS-2$

    INVERSESTACK("", "inverseStack", 0, 0, null), //$NON-NLS-1$ //$NON-NLS-2$

    STACK_OFFSET("", "stackOffset", 0, 0, null), //$NON-NLS-1$ //$NON-NLS-2$

    SYNCH_LINK("", "synchLink", 0, 0, null), //$NON-NLS-1$ //$NON-NLS-2$

    SYNCH_CROSSLINE("", "synchCrossline", 0, 0, null), //$NON-NLS-1$ //$NON-NLS-2$

    FILTER("", "filter", 0, 0, null); //$NON-NLS-1$ 

    // keep TempLayer in last position

    private final String title;
    private final String command;
    private final Icon icon;
    private final Icon smallIcon;
    private final int keyCode;
    private final int modifier;
    private final Cursor cursor;

    ActionW(String title, String command, int keyEvent, int modifier, Cursor cursor) {
        this.title = title;
        this.command = command;
        this.keyCode = keyEvent;
        this.modifier = modifier;
        this.cursor = cursor;
        URL url = getClass().getResource("/icon/22x22/" + command + ".png"); //$NON-NLS-1$ //$NON-NLS-2$
        icon = url == null ? null : new ImageIcon(url);
        url = getClass().getResource("/icon/16x16/" + command + ".png"); //$NON-NLS-1$ //$NON-NLS-2$
        smallIcon = url == null ? null : new ImageIcon(url);
    }

    public String getTitle() {
        return title;
    }

    public String getCommand() {
        return command;
    }

    @Override
    public String toString() {
        return title;
    }

    public Icon getIcon() {
        return icon;
    }

    public int getKeyCode() {
        return keyCode;
    }

    public Cursor getCursor() {
        return cursor;
    }

    public int getModifier() {
        return modifier;
    }

    public Icon getSmallIcon() {
        return smallIcon;
    }

    public Icon getDropButtonIcon() {
        if (icon == null) {
            return null;
        }
        return new DropButtonIcon(icon);
    }

    public Icon getSmallDropButtonIcon() {
        if (smallIcon == null) {
            return null;
        }
        return new DropButtonIcon(smallIcon);
    }

    public static ActionW getActionFromCommand(String command) {
        for (ActionW action : ActionW.values()) {
            if (action.command.equals(command)) {
                return action;
            }
        }
        return null;
    }

    public static ActionW getActionFromkeyEvent(int keyEvent) {
        for (ActionW action : ActionW.values()) {
            if (action.keyCode == keyEvent) {
                return action;
            }
        }
        return null;
    }

    public static Cursor getCustomCursor(String filename, String cursorName, int hotSpotX, int hotSpotY) {
        Toolkit defaultToolkit = Toolkit.getDefaultToolkit();
        URL path = ActionW.class.getResource("/icon/cursor/" + filename); //$NON-NLS-1$
        if (path == null) {
            return null;
        }
        ImageIcon icon = new ImageIcon(path);
        Dimension bestCursorSize = defaultToolkit.getBestCursorSize(icon.getIconWidth(), icon.getIconHeight());
        Point hotSpot =
            new Point((hotSpotX * bestCursorSize.width) / icon.getIconWidth(), (hotSpotY * bestCursorSize.height)
                / icon.getIconHeight());
        return defaultToolkit.createCustomCursor(icon.getImage(), hotSpot, cursorName);
    }

}
