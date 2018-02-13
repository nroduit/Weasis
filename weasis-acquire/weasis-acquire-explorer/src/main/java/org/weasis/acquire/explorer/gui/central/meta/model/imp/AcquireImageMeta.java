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
package org.weasis.acquire.explorer.gui.central.meta.model.imp;

import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.gui.central.meta.model.AcquireMetadataTableModel;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.core.api.media.data.TagW;

public class AcquireImageMeta extends AcquireMetadataTableModel {
    private static final long serialVersionUID = 8912202268139591519L;

    private static final TagW[] TAGS_TO_DISPLAY =
        getTags("weasis.acquire.meta.image.display", "ImageComments,ContentDate,ContentTime"); //$NON-NLS-1$ //$NON-NLS-2$
    private static final TagW[] TAGS_EDITABLE =
        getTags("weasis.acquire.meta.image.edit", "ImageComments,ContentDate,ContentTime"); //$NON-NLS-1$ //$NON-NLS-2$
    private static final TagW[] TAGS_TO_PUBLISH = getTags("weasis.acquire.meta.image.required", "ContentDate"); //$NON-NLS-1$ //$NON-NLS-2$

    public AcquireImageMeta(AcquireImageInfo imageInfo) {
        super((imageInfo == null) ? null : imageInfo.getImage(), TAGS_TO_DISPLAY, TAGS_EDITABLE, TAGS_TO_PUBLISH);
    }

    public static boolean isPublishable(TagReadable tagMaps) {
        return hasNonNullValues(TAGS_TO_PUBLISH, tagMaps);
    }
}
