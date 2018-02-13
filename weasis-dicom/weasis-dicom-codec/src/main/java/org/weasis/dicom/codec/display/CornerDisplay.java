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
package org.weasis.dicom.codec.display;

import org.weasis.dicom.codec.Messages;

public enum CornerDisplay {
    TOP_LEFT(Messages.getString("CornerDisplay.t_left")), //$NON-NLS-1$

    TOP_RIGHT(Messages.getString("CornerDisplay.t_right")), //$NON-NLS-1$

    BOTTOM_LEFT(Messages.getString("CornerDisplay.b_left")), //$NON-NLS-1$

    BOTTOM_RIGHT(Messages.getString("CornerDisplay.b_right")); //$NON-NLS-1$

    private final String name;

    private CornerDisplay(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
