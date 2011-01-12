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
package org.weasis.core.api.explorer;

import java.beans.PropertyChangeEvent;

public class ObservableEvent extends PropertyChangeEvent {

    private static final long serialVersionUID = 2727161739305072870L;

    public enum BasicAction {
        Select, Add, Remove, Update, UpdateParent, UpdateIcon, Register, Unregister, Replace
    };

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
