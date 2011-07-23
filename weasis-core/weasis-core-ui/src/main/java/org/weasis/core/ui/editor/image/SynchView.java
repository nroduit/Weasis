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

import java.util.HashMap;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.GUIEntry;
import org.weasis.core.ui.Messages;

public class SynchView implements GUIEntry {
    public enum Mode {
        None, Stack, Tile
    }

    public static final SynchView NONE = new SynchView(
        Messages.getString("SynchView.none"), Mode.None, null, new HashMap<ActionW, Boolean>()); //$NON-NLS-1$
    public static final SynchView DEFAULT_TILE;
    public static final SynchView DEFAULT_STACK;
    static {
        HashMap<ActionW, Boolean> actions = new HashMap<ActionW, Boolean>();
        actions.put(ActionW.SCROLL_SERIES, true);
        actions.put(ActionW.PAN, true);
        actions.put(ActionW.ZOOM, true);
        actions.put(ActionW.ROTATION, true);
        actions.put(ActionW.FLIP, true);
        actions.put(ActionW.WINDOW, true);
        actions.put(ActionW.LEVEL, true);
        actions.put(ActionW.PRESET, true);
        actions.put(ActionW.LUT, true);
        actions.put(ActionW.INVERSELUT, true);
        actions.put(ActionW.FILTER, true);
        actions.put(ActionW.VIEWINGPROTOCOL, true);
        actions.put(ActionW.IMAGE_OVERLAY, true);
        DEFAULT_TILE = new SynchView(Messages.getString("SynchView.def_t"), Mode.Tile, //$NON-NLS-1$
            new ImageIcon(SynchView.class.getResource("/icon/22x22/tile.png")), actions); //$NON-NLS-1$

        actions = new HashMap<ActionW, Boolean>();
        actions.put(ActionW.SCROLL_SERIES, true);
        actions.put(ActionW.PAN, true);
        actions.put(ActionW.ZOOM, true);
        actions.put(ActionW.ROTATION, true);
        actions.put(ActionW.FLIP, true);
        actions.put(ActionW.VIEWINGPROTOCOL, true);
        actions.put(ActionW.IMAGE_OVERLAY, true);
        DEFAULT_STACK = new SynchView(Messages.getString("SynchView.def_s"), Mode.Stack, new ImageIcon(SynchView.class //$NON-NLS-1$
            .getResource("/icon/22x22/sequence.png")), actions); //$NON-NLS-1$
    }

    private final HashMap<ActionW, Boolean> actions;
    private final Mode mode;
    private final String name;
    private Icon icon;

    public SynchView(String name, Mode mode, Icon icon, HashMap<ActionW, Boolean> actions) {
        if (name == null || actions == null) {
            throw new IllegalArgumentException("A parameter is null!"); //$NON-NLS-1$
        }
        this.name = name;
        this.actions = actions;
        this.mode = mode;
        this.icon = icon;
    }

    public HashMap<ActionW, Boolean> getActions() {
        return actions;
    }

    public boolean isActionEnable(ActionW action) {
        Boolean bool = actions.get(action);
        return (bool != null && bool);
    }

    @Override
    public String toString() {
        return name;
    }

    public Mode getMode() {
        return mode;
    }

    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public Icon getIcon() {
        return icon;
    }

    @Override
    public String getUIName() {
        return name;
    }

}
