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
