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
package org.weasis.acquire.dockable.components.actions.rectify.lib;

import javax.swing.Icon;
import javax.swing.JButton;

import org.weasis.acquire.operations.impl.RotationActionListener;

public abstract class AbstractRectifyButton extends JButton {
    private static final long serialVersionUID = -7409961577578876870L;

    public AbstractRectifyButton(RotationActionListener actionListener) {
        super();
        setIcon(getIcon());
        setToolTipText(getToolTip());
        addActionListener(actionListener);
    }

    @Override
    public abstract Icon getIcon();

    public abstract String getToolTip();

}
