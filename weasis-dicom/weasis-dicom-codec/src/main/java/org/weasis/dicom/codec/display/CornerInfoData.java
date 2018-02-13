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

import java.util.Arrays;

import org.weasis.core.api.media.data.TagView;

public class CornerInfoData {

    public static final int ELEMENT_NUMBER = 7;
    private final CornerDisplay corner;
    private final TagView[] infos;

    public CornerInfoData(CornerDisplay corner, Modality extendModality) {
        this.corner = corner;
        TagView[] extInfos = null;
        if (extendModality != null) {
            ModalityInfoData mdata = ModalityView.MODALITY_VIEW_MAP.get(extendModality);
            if (mdata != null) {
                extInfos = mdata.getCornerInfo(corner).getInfos();
            }
        }
        this.infos = extInfos == null ? new TagView[ELEMENT_NUMBER] : Arrays.copyOf(extInfos, extInfos.length);
    }

    public TagView[] getInfos() {
        return infos;
    }

    public CornerDisplay getCorner() {
        return corner;
    }

    @Override
    public String toString() {
        return corner.toString();
    }
}
