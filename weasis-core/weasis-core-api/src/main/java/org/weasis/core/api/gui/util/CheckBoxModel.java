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

import java.util.Objects;

public class CheckBoxModel {
    private final Object object;
    private boolean selected;

    public CheckBoxModel(Object object, boolean selected) {
        this.object = Objects.requireNonNull(object);
        this.selected = selected;
    }

    public Object getObject() {
        return object;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

}
