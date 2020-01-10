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
        this.components = new ArrayList<>();
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

    @Override
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
