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
package org.weasis.core.api.gui.util;

import javax.swing.Icon;
import javax.swing.JRadioButtonMenuItem;

@SuppressWarnings("serial")
public class RadioMenuItem extends JRadioButtonMenuItem {

    private final Object userObject;

    public RadioMenuItem(String text, Object userObject) {
        this(text, null, userObject);
    }

    public RadioMenuItem(String text, Icon icon, Object userObject) {
        this(text, icon, userObject, false);
    }

    public RadioMenuItem(String text, Icon icon, Object userObject, boolean selected) {
        super(text, icon, selected);
        this.userObject = userObject;
    }

    public Object getUserObject() {
        return userObject;
    }

}
