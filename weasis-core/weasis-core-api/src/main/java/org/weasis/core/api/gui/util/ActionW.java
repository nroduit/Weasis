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

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.net.URL;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.Messages;

public class ActionW implements KeyActionValue {
    public static final String DRAW_CMD_PREFIX = "draw.sub."; //$NON-NLS-1$

    public static final ActionW NO_ACTION =
        new ActionW(Messages.getString("ActionW.no"), "none", KeyEvent.VK_N, 0, null); //$NON-NLS-1$ //$NON-NLS-2$

    public static final ActionW SYNCH = new ActionW(Messages.getString("ActionW.synch"), "synch", 0, 0, null); //$NON-NLS-1$ //$NON-NLS-2$
    public static final ActionW VIEW_MODE =
        new ActionW(Messages.getString("ActionW.view_mode"), "viewMode", 0, 0, null); //$NON-NLS-1$ //$NON-NLS-2$
    public static final ActionW ZOOM = new ActionW(Messages.getString("ActionW.zoom"), "zoom", KeyEvent.VK_Z, 0, //$NON-NLS-1$ //$NON-NLS-2$
        getCustomCursor("zoom.png", Messages.getString("ActionW.zoom"), 16, 16)); //$NON-NLS-1$ //$NON-NLS-2$
    public static final ActionW SCROLL_SERIES = new ActionW(Messages.getString("ActionW.scroll"), "sequence", //$NON-NLS-1$ //$NON-NLS-2$
        KeyEvent.VK_S, 0, getCustomCursor("sequence.png", Messages.getString("ActionW.scroll"), 16, 16)); //$NON-NLS-1$ //$NON-NLS-2$
    public static final ActionW ROTATION = new ActionW(Messages.getString("ActionW.rotate"), "rotation", KeyEvent.VK_R, //$NON-NLS-1$ //$NON-NLS-2$
        0, getCustomCursor("rotation.png", Messages.getString("ActionW.rotate"), 16, 16)); //$NON-NLS-1$ //$NON-NLS-2$

    public static final ActionW CINESPEED = new ActionW(Messages.getString("ActionW.speed"), "cinespeed", 0, 0, null); //$NON-NLS-1$ //$NON-NLS-2$
    public static final ActionW CINESTART =
        new ActionW(Messages.getString("ActionW.start"), "cinestart", KeyEvent.VK_C, 0, null); //$NON-NLS-1$ //$NON-NLS-2$
    public static final ActionW CINESTOP = new ActionW(Messages.getString("ActionW.stop"), "cinestop", 0, 0, null); //$NON-NLS-1$ //$NON-NLS-2$
    public static final ActionW WINDOW = new ActionW(Messages.getString("ActionW.win"), "window", 0, 0, null); //$NON-NLS-1$ //$NON-NLS-2$
    public static final ActionW LEVEL = new ActionW(Messages.getString("ActionW.level"), "level", 0, 0, null); //$NON-NLS-1$ //$NON-NLS-2$
    public static final ActionW WINLEVEL = new ActionW(Messages.getString("ActionW.wl"), "winLevel", KeyEvent.VK_W, 0, //$NON-NLS-1$ //$NON-NLS-2$
        getCustomCursor("winLevel.png", Messages.getString("ActionW.wl"), 16, 16)); //$NON-NLS-1$ //$NON-NLS-2$
    public static final ActionW LEVEL_MIN = new ActionW("", "level_min", 0, 0, null); //$NON-NLS-1$ //$NON-NLS-2$
    public static final ActionW LEVEL_MAX = new ActionW("", "level_max", 0, 0, null); //$NON-NLS-1$ //$NON-NLS-2$

    public static final ActionW FLIP = new ActionW(Messages.getString("ActionW.flip"), "flip", 0, 0, null); //$NON-NLS-1$ //$NON-NLS-2$
    public static final ActionW PRESET = new ActionW(Messages.getString("ActionW.preset"), "preset", 0, 0, null); //$NON-NLS-1$ //$NON-NLS-2$
    public static final ActionW DEFAULT_PRESET = new ActionW("", "default_preset", 0, 0, null); //$NON-NLS-1$ //$NON-NLS-2$
    public static final ActionW LUT_SHAPE =
        new ActionW(Messages.getString("ActionW.lut_shape"), "lut_shape", 0, 0, null); //$NON-NLS-1$ //$NON-NLS-2$
    public static final ActionW LUT = new ActionW(Messages.getString("ActionW.lut"), "lut", 0, 0, null); //$NON-NLS-1$ //$NON-NLS-2$
    public static final ActionW INVERT_LUT =
        new ActionW(Messages.getString("ActionW.invert_lut"), "inverseLut", 0, 0, null); //$NON-NLS-1$//$NON-NLS-2$
    public static final ActionW RESET = new ActionW(Messages.getString("ActionW.Reset"), "reset", 0, 0, null); //$NON-NLS-1$ //$NON-NLS-2$
    public static final ActionW SHOW_HEADER =
        new ActionW(Messages.getString("ActionW.show_header"), "reset", 0, 0, null); //$NON-NLS-1$//$NON-NLS-2$
    public static final ActionW PAN = new ActionW(Messages.getString("ActionW.pan"), "pan", KeyEvent.VK_T, 0, //$NON-NLS-1$ //$NON-NLS-2$
        getCustomCursor("pan.png", Messages.getString("ActionW.pan"), 16, 16)); //$NON-NLS-1$ //$NON-NLS-2$
    public static final ActionW DRAWINGS = new ActionW(Messages.getString("ActionW.draw"), "drawings", 0, 0, null); //$NON-NLS-1$ //$NON-NLS-2$
    public static final ActionW MEASURE =
        new ActionW(Messages.getString("ActionW.measure"), "measure", KeyEvent.VK_M, 0, null) { //$NON-NLS-1$ //$NON-NLS-2$
            @Override
            public boolean isDrawingAction() {
                return true;
            }
        };
    public static final ActionW DRAW =
        new ActionW(Messages.getString("ActionW.draws"), "draw", KeyEvent.VK_G, 0, null) { //$NON-NLS-1$//$NON-NLS-2$
            @Override
            public boolean isDrawingAction() {
                return true;
            }
        };
    // Starting cmd by "draw.sub." defines a derivative action
    public static final ActionW DRAW_MEASURE =
        new ActionW(Messages.getString("ActionW.measurement"), DRAW_CMD_PREFIX + MEASURE.cmd(), 0, 0, null); //$NON-NLS-1$
    public static final ActionW DRAW_GRAPHICS =
        new ActionW(Messages.getString("ActionW.draw"), DRAW_CMD_PREFIX + DRAW.cmd(), 0, 0, null); //$NON-NLS-1$
    public static final ActionW SPATIAL_UNIT =
        new ActionW(Messages.getString("ActionW.spatial_unit"), "spunit", 0, 0, null); //$NON-NLS-1$//$NON-NLS-2$
    public static final ActionW SORTSTACK = new ActionW("", "sortStack", 0, 0, null); //$NON-NLS-1$ //$NON-NLS-2$
    public static final ActionW CONTEXTMENU =
        new ActionW(Messages.getString("ActionW.context_menu"), "contextMenu", KeyEvent.VK_Q, 0, null); //$NON-NLS-1$ //$NON-NLS-2$
    public static final ActionW VIEWINGPROTOCOL = new ActionW("", "viewingProtocol", 0, 0, null); //$NON-NLS-1$ //$NON-NLS-2$
    public static final ActionW LAYOUT = new ActionW(Messages.getString("ActionW.layout"), "layout", 0, 0, null); //$NON-NLS-1$ //$NON-NLS-2$
    public static final ActionW MODE = new ActionW(Messages.getString("ActionW.switch_mode"), "mode", 0, 0, null); //$NON-NLS-1$ //$NON-NLS-2$
    public static final ActionW IMAGE_OVERLAY =
        new ActionW(Messages.getString("ActionW.overlay"), "overlay", 0, 0, null); //$NON-NLS-1$ //$NON-NLS-2$
    public static final ActionW PR_STATE = new ActionW(Messages.getString("ActionW.PR"), "pr_state", 0, 0, null); //$NON-NLS-1$//$NON-NLS-2$
    public static final ActionW KO_TOOGLE_STATE =
        new ActionW(Messages.getString("ActionW.toggle_ko"), "ko_toogle_state", KeyEvent.VK_K, 0, //$NON-NLS-1$ //$NON-NLS-2$
            null);
    public static final ActionW KO_SELECTION =
        new ActionW(Messages.getString("ActionW.select_ko"), "ko_selection", 0, 0, null); //$NON-NLS-1$ //$NON-NLS-2$
    public static final ActionW KO_FILTER =
        new ActionW(Messages.getString("ActionW.filter_ko"), "ko_filter", 0, 0, null); //$NON-NLS-1$ //$NON-NLS-2$
    public static final ActionW IMAGE_PIX_PADDING =
        new ActionW(Messages.getString("ActionW.pixpad"), "pixel_padding", 0, 0, null); //$NON-NLS-1$//$NON-NLS-2$
    public static final ActionW IMAGE_SHUTTER =
        new ActionW(Messages.getString("ActionW.shutter"), "shutter", 0, 0, null); //$NON-NLS-1$//$NON-NLS-2$
    public static final ActionW INVERSESTACK = new ActionW("", "inverseStack", 0, 0, null); //$NON-NLS-1$ //$NON-NLS-2$
    public static final ActionW STACK_OFFSET = new ActionW("", "stackOffset", 0, 0, null); //$NON-NLS-1$ //$NON-NLS-2$
    public static final ActionW SYNCH_LINK = new ActionW("", "synchLink", 0, 0, null); //$NON-NLS-1$ //$NON-NLS-2$
    public static final ActionW SYNCH_CROSSLINE = new ActionW("", "synchCrossline", 0, 0, null); //$NON-NLS-1$ //$NON-NLS-2$
    public static final ActionW LENS = new ActionW("", "showLens", 0, 0, null); //$NON-NLS-1$ //$NON-NLS-2$
    public static final ActionW FILTER = new ActionW("", "filter", 0, 0, null);//$NON-NLS-1$ //$NON-NLS-2$
    public static final ActionW CROP = new ActionW("", "crop", 0, 0, null);//$NON-NLS-1$ //$NON-NLS-2$
    public static final ActionW PREPROCESSING = new ActionW("", "preprocessing", 0, 0, null);//$NON-NLS-1$ //$NON-NLS-2$
    public static final ActionW LENSZOOM = new ActionW("", "lensZoom", 0, 0, null);//$NON-NLS-1$ //$NON-NLS-2$
    public static final ActionW LENSPAN = new ActionW("", "lensPan", 0, 0, null); //$NON-NLS-1$ //$NON-NLS-2$
    public static final ActionW DRAW_ONLY_ONCE =
        new ActionW(Messages.getString("ActionW.draw_once"), "drawOnce", 0, 0, null); //$NON-NLS-1$//$NON-NLS-2$
    public static final ActionW PROGRESSION = new ActionW("", "img_progress", 0, 0, null); //$NON-NLS-1$//$NON-NLS-2$
    public static final ActionW FILTERED_SERIES = new ActionW("", "filter_series", 0, 0, null); //$NON-NLS-1$//$NON-NLS-2$
    public static final ActionW CROSSHAIR = new ActionW(Messages.getString("ActionW.crosshair"), "crosshair", //$NON-NLS-1$ //$NON-NLS-2$
        KeyEvent.VK_H, 0, new Cursor(Cursor.CROSSHAIR_CURSOR));

    private final String title;
    private final String command;
    private final Icon icon;
    private final Icon smallIcon;
    private final int keyCode;
    private final int modifier;
    private final Cursor cursor;

    public ActionW(String title, String command, int keyEvent, int modifier, Cursor cursor) {
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

    public String cmd() {
        return command;
    }

    @Override
    public String toString() {
        return title;
    }

    public Icon getIcon() {
        return icon;
    }

    @Override
    public int getKeyCode() {
        return keyCode;
    }

    public Cursor getCursor() {
        return cursor;
    }

    @Override
    public int getModifier() {
        return modifier;
    }

    public boolean isDrawingAction() {
        return false;
    }

    public boolean isGraphicListAction() {
        return command.startsWith(DRAW_CMD_PREFIX);
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

    public static Cursor getCustomCursor(String filename, String cursorName, int hotSpotX, int hotSpotY) {
        Toolkit defaultToolkit = Toolkit.getDefaultToolkit();
        URL path = ActionW.class.getResource("/icon/cursor/" + filename); //$NON-NLS-1$
        if (path == null) {
            return null;
        }
        ImageIcon icon = new ImageIcon(path);
        Dimension bestCursorSize = defaultToolkit.getBestCursorSize(icon.getIconWidth(), icon.getIconHeight());
        Point hotSpot = new Point((hotSpotX * bestCursorSize.width) / icon.getIconWidth(),
            (hotSpotY * bestCursorSize.height) / icon.getIconHeight());
        return defaultToolkit.createCustomCursor(icon.getImage(), hotSpot, cursorName);
    }

}
