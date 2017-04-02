/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.api.explorer;

import java.beans.PropertyChangeEvent;

public class ObservableEvent extends PropertyChangeEvent {

    private static final long serialVersionUID = 2727161739305072870L;

    public enum BasicAction {
        SELECT, ADD, REMOVE, UPDATE, UDPATE_PARENT, NULL_SELECTION, UPDATE_TOOLS, UPDTATE_TOOLBARS, REGISTER, UNREGISTER,
        REPLACE, LOADING_START, LOADING_CANCEL, LOADING_STOP
    }

    private final BasicAction actionCommand;

    public ObservableEvent(BasicAction actionCommand, Object source, Object oldValue, Object newValue) {
        super(source, null, oldValue, newValue);
        if (actionCommand == null) {
            throw new IllegalArgumentException("null source"); //$NON-NLS-1$
        }
        this.actionCommand = actionCommand;
    }

    @Override
    public String getPropertyName() {
        return actionCommand.toString();
    }

    public BasicAction getActionCommand() {
        return actionCommand;
    }

    @Override
    public Object getSource() {
        return source;
    }

}
