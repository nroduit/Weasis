/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.codec.display;

import org.weasis.core.util.StringUtil;

public class ModalityInfoData {

    private final Modality modality;
    private final Modality extendModality;
    private final CornerInfoData[] cornerInfo;

    public ModalityInfoData(Modality modality, Modality extendModality) {
        this.modality = modality;
        this.extendModality = extendModality;
        CornerDisplay[] corners = CornerDisplay.values();
        this.cornerInfo = new CornerInfoData[corners.length];
        for (int i = 0; i < corners.length; i++) {
            cornerInfo[i] = new CornerInfoData(corners[i], extendModality);
        }
    }

    public Modality getModality() {
        return modality;
    }

    public Modality getExtendModality() {
        return extendModality;
    }

    public CornerInfoData[] getCornerInfo() {
        return cornerInfo;
    }

    public CornerInfoData getCornerInfo(CornerDisplay corner) {
        for (int i = 0; i < cornerInfo.length; i++) {
            if (cornerInfo[i].getCorner().equals(corner)) {
                return cornerInfo[i];
            }
        }
        return null;
    }

    @Override
    public String toString() {
        String desc = StringUtil.hasText(modality.getDescription()) ? " (" + modality.getDescription() + ")" : ""; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        return modality.toString() + desc;
    }
}
