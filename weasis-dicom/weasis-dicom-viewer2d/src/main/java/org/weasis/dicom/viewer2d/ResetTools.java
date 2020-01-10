/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.viewer2d;

public enum ResetTools {
    ALL(Messages.getString("ResetTools.all")), //$NON-NLS-1$

    WL(Messages.getString("ResetTools.wl")), //$NON-NLS-1$

    ZOOM(Messages.getString("ViewerPrefView.zoom")), //$NON-NLS-1$

    ROTATION(Messages.getString("ResetTools.rotation")), //$NON-NLS-1$

    PAN(Messages.getString("ResetTools.pan")); //$NON-NLS-1$

    private final String name;

    private ResetTools(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

}
