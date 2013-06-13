package org.weasis.core.ui.editor.image;

import java.util.HashMap;

public class SynchData implements Cloneable {

    public enum Mode {
        None, Stack, Tile
    }

    protected final HashMap<String, Boolean> actions;
    protected final Mode mode;

    public SynchData(Mode mode, HashMap<String, Boolean> actions) {
        if (actions == null) {
            throw new IllegalArgumentException("A parameter is null!"); //$NON-NLS-1$
        }
        this.actions = actions;
        this.mode = mode;
    }

    public HashMap<String, Boolean> getActions() {
        return actions;
    }

    public boolean isActionEnable(String action) {
        Boolean bool = actions.get(action);
        return (bool != null && bool);
    }

    public Mode getMode() {
        return mode;
    }

    @Override
    public SynchData clone() {
        return new SynchData(mode, new HashMap<String, Boolean>(actions));
    }

}