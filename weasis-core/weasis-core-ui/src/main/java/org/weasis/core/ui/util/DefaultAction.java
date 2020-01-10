/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.util;

import java.awt.event.ActionEvent;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.Icon;

public class DefaultAction extends AbstractAction {

    private static final long serialVersionUID = -7936036560191191025L;

    private final Consumer<ActionEvent> action;

    public DefaultAction(String name, Consumer<ActionEvent> action) {
        super(name);
        this.action = Objects.requireNonNull(action);
    }

    public DefaultAction(String name, Icon icon, Consumer<ActionEvent> action) {
        super(name, icon);
        this.action = Objects.requireNonNull(action);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        action.accept(e);
    }

}
