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
package org.weasis.acquire.dockable.components;

import java.util.Optional;

import javax.swing.BorderFactory;
import javax.swing.JButton;

import org.weasis.acquire.dockable.components.actions.AcquireAction;
import org.weasis.acquire.dockable.components.actions.AcquireAction.Cmd;
import org.weasis.acquire.dockable.components.actions.AcquireActionPanel;

public class AcquireActionButton extends JButton {
    private static final long serialVersionUID = -4757730607905567863L;

    private AcquireAction action;

    public AcquireActionButton(String title, Cmd cmd) {
        super(title);
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        setActionCommand(cmd.name());
    }

    public AcquireActionButton(String title, AcquireAction action) {
        this(title, Cmd.INIT);
        setAcquireAction(action);
    }

    public AcquireActionPanel getCentralPanel() {
        return action.getCentralPanel();
    }

    public AcquireAction getAcquireAction() {
        return this.action;
    }

    public void setAcquireAction(AcquireAction action) {
        Optional.ofNullable(this.action).ifPresent(a -> removeActionListener(a));
        this.action = action;
        addActionListener(this.action);
    }
}
