/*******************************************************************************
 * Copyright (c) 2017 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *     Tomas Skripcak - initial API and implementation
 ******************************************************************************/

package org.weasis.dicom.rt;

import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.layer.imp.DefaultLayer;

import java.util.Objects;

public class IsoDoseLayer {

    private final IsoDose isoDose;
    private final DefaultLayer layer;

    public IsoDoseLayer(IsoDose isoDose) {
        this.isoDose = Objects.requireNonNull(isoDose);
        this.layer = new DefaultLayer(LayerType.DICOM_RT);
        this.layer.setName(isoDose.getLabel());
    }

    public IsoDose getIsoDose() {
        return this.isoDose;
    }

    public boolean isSelected() {
        return layer.getVisible();
    }

    public DefaultLayer getLayer() {
        return layer;
    }

    @Override
    public String toString() {
        return isoDose.toString();
    }
}
