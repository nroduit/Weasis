/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.rt;

import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.layer.imp.DefaultLayer;

/**
 * 
 * @author Tomas Skripcak
 * @author Nicolas Roduit
 */
public class RtLayer {

    protected final DefaultLayer layer;

    public RtLayer() {
        this.layer = new DefaultLayer(LayerType.DICOM_RT);
    }

    public boolean isSelected() {
        return layer.getVisible();
    }

    public DefaultLayer getLayer() {
        return layer;
    }
    
}
