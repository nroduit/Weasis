/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.acquire.explorer.gui.central.meta.model.imp;

import org.weasis.acquire.explorer.core.bean.SeriesGroup;
import org.weasis.acquire.explorer.gui.central.meta.model.AcquireMetadataTableModel;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.core.api.media.data.TagW;

public class AcquireSerieMeta extends AcquireMetadataTableModel {
    private static final long serialVersionUID = 8912202268139591519L;

    private static final TagW[] TAGS_TO_DISPLAY = getTags("weasis.acquire.meta.series.display", //$NON-NLS-1$
        "Modality,OperatorsName,ReferringPhysicianName,BodyPartExamined,SeriesDescription"); //$NON-NLS-1$
    private static final TagW[] TAGS_EDITABLE =
        getTags("weasis.acquire.meta.series.edit", "ReferringPhysicianName,BodyPartExamined,SeriesDescription"); //$NON-NLS-1$ //$NON-NLS-2$
    private static final TagW[] TAGS_TO_PUBLISH =
        getTags("weasis.acquire.meta.series.required", "Modality,SeriesDescription"); //$NON-NLS-1$ //$NON-NLS-2$

    public AcquireSerieMeta(SeriesGroup seriesGroup) {
        super(seriesGroup, TAGS_TO_DISPLAY, TAGS_EDITABLE, TAGS_TO_PUBLISH);
    }

    public static boolean isPublishable(TagReadable tagMaps) {
        return hasNonNullValues(TAGS_TO_PUBLISH, tagMaps);
    }
}
