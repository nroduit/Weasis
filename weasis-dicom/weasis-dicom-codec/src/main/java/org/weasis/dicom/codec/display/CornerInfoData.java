/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.dicom.codec.display;

public class CornerInfoData {

    public static final int ELEMENT_NUMBER = 7;
    private final CornerDisplay corner;
    private final TagView[] infos;

    public CornerInfoData(CornerDisplay corner, Modality extendModality) {
        this.corner = corner;
        ModalityInfoData mdata = null;
        if (extendModality != null) {
            mdata = ModalityView.MODALITY_VIEW_MAP.get(extendModality);
        }
        this.infos = mdata == null ? new TagView[ELEMENT_NUMBER] : mdata.getCornerInfo(corner).getInfos().clone();
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
