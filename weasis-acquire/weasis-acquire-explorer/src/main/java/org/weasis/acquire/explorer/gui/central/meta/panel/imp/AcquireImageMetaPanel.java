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
package org.weasis.acquire.explorer.gui.central.meta.panel.imp;

import org.weasis.acquire.explorer.gui.central.meta.model.AcquireMetadataTableModel;
import org.weasis.acquire.explorer.gui.central.meta.model.imp.AcquireImageMeta;
import org.weasis.acquire.explorer.gui.central.meta.panel.AcquireMetadataPanel;

public class AcquireImageMetaPanel extends AcquireMetadataPanel {
    private static final long serialVersionUID = -2751941971479265507L;

    private static final String NO_IMAGE = "No image";
    private static final String IMAGE_PREFIX = "Image : ";

    public AcquireImageMetaPanel(String title) {
        super(title);
    }

    @Override
    public AcquireMetadataTableModel newTableModel() {
        return new AcquireImageMeta(imageInfo);
    }

    @Override
    public String getDisplayText() {
        if (imageInfo != null) {
            return new StringBuilder(IMAGE_PREFIX).append(imageInfo.getImage().getName()).toString();
        } else {
            return NO_IMAGE;
        }
    }
}
