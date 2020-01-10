/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.acquire.dockable.components.util;

import javax.swing.JPanel;
import javax.swing.border.TitledBorder;

import org.weasis.acquire.dockable.components.actions.AbstractAcquireActionPanel;

public abstract class AbstractComponent extends JPanel {
    private static final long serialVersionUID = 5581699214603462715L;

    protected final String title;
    protected TitledBorder borderTitle;
    protected AbstractAcquireActionPanel panel;

    public AbstractComponent(AbstractAcquireActionPanel panel, String title) {
        this.title = title;
        this.borderTitle = new TitledBorder(getDisplayTitle());
        this.panel = panel;
    }

    public String getTitle() {
        return title;
    }

    public void updatePanelTitle() {
        borderTitle.setTitle(getDisplayTitle());
    }

    public abstract String getDisplayTitle();

}
