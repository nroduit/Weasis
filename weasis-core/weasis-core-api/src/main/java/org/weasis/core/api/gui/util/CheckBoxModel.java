/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
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
