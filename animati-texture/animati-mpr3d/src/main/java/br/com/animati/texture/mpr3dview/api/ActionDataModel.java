/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Informática Ltda.
 * (http://www.animati.com.br)
 */

package br.com.animati.texture.mpr3dview.api;

import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;

/**
 *
 * @author Gabriela Bauermann (gabriela@animati.com.br)
 * @version 
 */
public class ActionDataModel implements ActionState {
    
    private ActionW action;
    private Object actionData;
    private Object actionValue;
    private boolean enable;
    
    public ActionDataModel(ActionW act, Object data, Object value) {
        action = act;
        actionData = data;
        actionValue = value;
    }

    @Override
    public void enableAction(boolean enabled) {
        enable = enabled;
    }

    @Override
    public ActionW getActionW() {
        return action;
    }
    
    public Object getActionValue() {
        return actionValue;
    }

    /**
     * @return the actionData
     */
    public Object getActionData() {
        return actionData;
    }

    /**
     * @param actionData the actionData to set
     */
    public void setActionData(Object actionData) {
        this.actionData = actionData;
    }

    /**
     * @param actionValue the actionValue to set
     */
    public void setActionValue(Object actionValue) {
        this.actionValue = actionValue;
    }

    /**
     * @return the enable
     */
    public boolean isActionEnabled() {
        return enable;
    }

    @Override
    public boolean registerActionState(Object c) { return false; }

    @Override
    public void unregisterActionState(Object c) { /*Empty*/ }
    
}
