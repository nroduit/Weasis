/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.acquire.explorer.gui.central.meta.panel.imp;

import org.weasis.acquire.explorer.Messages;
import org.weasis.acquire.explorer.gui.central.meta.model.AcquireMetadataTableModel;
import org.weasis.acquire.explorer.gui.central.meta.model.imp.AcquireImageMeta;
import org.weasis.acquire.explorer.gui.central.meta.panel.AcquireMetadataPanel;
import org.weasis.core.api.util.StringUtil;

public class AcquireImageMetaPanel extends AcquireMetadataPanel {
    private static final long serialVersionUID = -2751941971479265507L;

    private static final String NO_IMAGE = Messages.getString("AcquireImageMetaPanel.no_img"); //$NON-NLS-1$
    private static final String IMAGE_PREFIX =
        Messages.getString("AcquireImageMetaPanel.img") + StringUtil.COLON_AND_SPACE; //$NON-NLS-1$

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
