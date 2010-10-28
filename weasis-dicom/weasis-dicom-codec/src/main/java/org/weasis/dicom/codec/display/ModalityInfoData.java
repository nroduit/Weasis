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

import java.io.Serializable;

import org.weasis.dicom.codec.Messages;

public class ModalityInfoData implements Serializable {

    private static final long serialVersionUID = -3493098593501958898L;
    private final Modality modality;

    private CornerInfoData[] cornerInfo;

    public ModalityInfoData(Modality modality) {
        this.modality = modality;
        CornerDisplay[] corners = CornerDisplay.values();
        this.cornerInfo = new CornerInfoData[corners.length];
        for (int i = 0; i < corners.length; i++) {
            cornerInfo[i] = new CornerInfoData(corners[i]);
        }
    }

    public Modality getModality() {
        return modality;
    }

    public void setCornerInfo(CornerInfoData[] cornerInfo) {
        this.cornerInfo = cornerInfo;
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
        String desc = modality.getDescription().equals("") ? "" : " (" + modality.getDescription() + ")"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        return modality.toString() + desc;
    }
}
