/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.acquire.dockable.components.actions;

import javax.swing.JPanel;

public abstract class AbstractAcquireActionPanel extends JPanel implements AcquireActionPanel {
    private static final long serialVersionUID = -8562722948334410446L;

    private String lastActionCommand;

    public AbstractAcquireActionPanel() {
        super();
    }

    public boolean needValidationPanel() {
        return false;
    }

    @Override
    public String getLastActionCommand() {
        return lastActionCommand;
    }

    @Override
    public void setLastActionCommand(String lastActionCommand) {
        this.lastActionCommand = lastActionCommand;
    }

    public void stopEditing() {
        // Do nothing by default
    }
}
