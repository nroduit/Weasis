package org.weasis.core.ui.editor.image;

import java.util.HashMap;

import org.weasis.core.api.gui.util.JMVUtils;

public class SynchData implements Cloneable {

    public enum Mode {
        None, Stack, Tile
    }

    protected final HashMap<String, Boolean> actions;
    protected final Mode mode;

    private boolean original;

    public SynchData(Mode mode, HashMap<String, Boolean> actions) {
        if (actions == null) {
            throw new IllegalArgumentException("A parameter is null!"); //$NON-NLS-1$
        }
        this.actions = actions;
        this.mode = mode;
        this.original = true;
    }

    public HashMap<String, Boolean> getActions() {
        return actions;
    }

    public boolean isActionEnable(String action) {
        return JMVUtils.getNULLtoFalse(actions.get(action));
    }

    public Mode getMode() {
        return mode;
    }

    @Override
    public SynchData clone() {
        return new SynchData(mode, new HashMap<String, Boolean>(actions));
    }

    public boolean isOriginal() {
        return original;
    }

    public void setOriginal(boolean original) {
        this.original = original;
    }

}