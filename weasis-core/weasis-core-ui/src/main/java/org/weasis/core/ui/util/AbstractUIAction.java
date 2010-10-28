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
package org.weasis.core.ui.util;

import javax.swing.AbstractAction;
import javax.swing.Icon;

import org.weasis.core.ui.Messages;

public abstract class AbstractUIAction extends AbstractAction {

    private String description = ""; //$NON-NLS-1$

    public AbstractUIAction() {
        super();
    }

    public AbstractUIAction(String name, Icon icon) {
        super(name, icon);
    }

    public AbstractUIAction(String name) {
        super(name);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

}
