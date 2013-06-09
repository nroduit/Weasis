package org.weasis.core.api.gui.util;

import java.awt.Component;
import java.util.ArrayList;

public class BasicActionState implements ActionState {

    protected final ActionW action;
    protected boolean enabled;
    protected final ArrayList<Object> components;

    public BasicActionState(ActionW action) {
        if (action == null) {
            throw new IllegalArgumentException();
        }
        this.action = action;
        this.components = new ArrayList<Object>();
    }

    @Override
    public void enableAction(boolean enabled) {
        this.enabled = enabled;
        for (Object c : components) {
            if (c instanceof Component) {
                ((Component) c).setEnabled(enabled);
            } else if (c instanceof State) {
                ((State) c).setEnabled(enabled);
            }
        }
    }

    public boolean isActionEnabled() {
        return enabled;
    }

    protected ArrayList<Object> getComponents() {
        return components;
    }

    @Override
    public ActionW getActionW() {
        return action;
    }

    @Override
    public boolean registerActionState(Object c) {
        if (!components.contains(c)) {
            components.add(c);
            if (c instanceof Component) {
                ((Component) c).setEnabled(enabled);
            } else if (c instanceof State) {
                ((State) c).setEnabled(enabled);
            }
            return true;
        }
        return false;
    }

    @Override
    public void unregisterActionState(Object c) {
        components.remove(c);
    }
}
