/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
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
