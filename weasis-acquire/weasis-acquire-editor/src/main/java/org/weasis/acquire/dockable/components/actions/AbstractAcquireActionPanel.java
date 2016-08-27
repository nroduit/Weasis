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
package org.weasis.acquire.dockable.components.actions;

import javax.swing.JPanel;

public abstract class AbstractAcquireActionPanel extends JPanel implements AcquireActionPanel {
    private static final long serialVersionUID = -8562722948334410446L;

    public AbstractAcquireActionPanel() {
        super();
    }

    public boolean needValidationPanel() {
        return false;
    }
}
