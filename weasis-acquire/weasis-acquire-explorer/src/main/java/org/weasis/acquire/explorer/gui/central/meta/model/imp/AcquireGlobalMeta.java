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

import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.acquire.explorer.gui.central.meta.model.AcquireMetadataTableModel;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.core.api.media.data.TagW;

public class AcquireGlobalMeta extends AcquireMetadataTableModel {
    private static final long serialVersionUID = 8912202268139591519L;

    private static final TagW[] TAGS_TO_DISPLAY = getTags("weasis.acquire.meta.global.display", //$NON-NLS-1$
        "PatientID,PatientName,PatientBirthDate,PatientSex,AccessionNumber,StudyDescription"); //$NON-NLS-1$
    private static final TagW[] TAGS_EDITABLE = getTags("weasis.acquire.meta.global.edit", "StudyDescription"); //$NON-NLS-1$ //$NON-NLS-2$
    private static final TagW[] TAGS_TO_PUBLISH = getTags("weasis.acquire.meta.global.required", //$NON-NLS-1$
        "PatientID,PatientName,StudyDescription"); //$NON-NLS-1$

    public AcquireGlobalMeta() {
        super(AcquireManager.GLOBAL, TAGS_TO_DISPLAY, TAGS_EDITABLE, TAGS_TO_PUBLISH);
    }

    public static boolean isPublishable(TagReadable tagMaps) {
        return hasNonNullValues(TAGS_TO_PUBLISH, tagMaps);
    }
}
