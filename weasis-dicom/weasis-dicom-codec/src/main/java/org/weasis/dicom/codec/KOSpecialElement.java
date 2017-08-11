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
package org.weasis.dicom.codec;

import java.util.Map;

import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.dicom.codec.macro.SOPInstanceReferenceAndMAC;

public class KOSpecialElement extends AbstractKOSpecialElement {

    public KOSpecialElement(DicomMediaIO mediaIO) {
        super(mediaIO);
    }

    public void toggleKeyObjectReference(DicomImageElement dicomImage) {

        Reference ref = new Reference(dicomImage);

        // Get the SOPInstanceReferenceMap for this seriesUID
        Map<String, SOPInstanceReferenceAndMAC> sopInstanceReferenceBySOPInstanceUID =
            sopInstanceReferenceMapBySeriesUID.get(ref.getSeriesInstanceUID());

        boolean isSelected = sopInstanceReferenceBySOPInstanceUID != null
            && sopInstanceReferenceBySOPInstanceUID.containsKey(ref.getSopInstanceUID());

        setKeyObjectReference(!isSelected, ref);
    }

    public boolean setKeyObjectReference(boolean selectedState, DicomImageElement dicomImage) {
        return setKeyObjectReference(selectedState, new Reference(dicomImage));
    }

    private boolean setKeyObjectReference(boolean selectedState, Reference ref) {
        if (selectedState) {
            return addKeyObject(ref);
        } else {
            return removeKeyObject(ref);
        }
    }

    public boolean setKeyObjectReference(boolean selectedState, MediaSeries<DicomImageElement> series) {
        boolean hasDataModelChanged = false;
        for (DicomImageElement dicomImage : series.getSortedMedias(null)) {
            hasDataModelChanged |= setKeyObjectReference(selectedState, new Reference(dicomImage));
        }
        return hasDataModelChanged;
    }
}
