package org.weasis.core.api.gui.util;


public class BasicActionState implements ActionState {

    protected final ActionW action;
    protected boolean enabled;

    public BasicActionState(ActionW action) {
        this.action = action;

    }

    @Override
    public void enableAction(boolean enabled) {
        // Do nothing as it is not graphical component.
    }

    @Override
    public ActionW getActionW() {
        return action;
    }

}
