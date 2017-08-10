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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dcm4che3.data.Tag;
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
        // TOOD add frameList
        Map<String, Set<DicomImageElement>> dicomImageSetMap = new HashMap<>();

        for (DicomImageElement dicomImage : series.getSortedMedias(null)) {
            String studyInstanceUID = TagD.getTagValue(dicomImage, Tag.StudyInstanceUID, String.class);
            String seriesInstanceUID = TagD.getTagValue(dicomImage, Tag.SeriesInstanceUID, String.class);
            String sopClassUID = TagD.getTagValue(dicomImage, Tag.SOPClassUID, String.class);

            String hashcode = studyInstanceUID + seriesInstanceUID + sopClassUID;

            Set<DicomImageElement> dicomImageSet = dicomImageSetMap.get(hashcode);
            if (dicomImageSet == null) {
                dicomImageSet = new HashSet<>();
                dicomImageSetMap.put(hashcode, dicomImageSet);
            }
            dicomImageSet.add(dicomImage);
        }

        boolean hasDataModelChanged = false;

        for (Set<DicomImageElement> dicomImageSet : dicomImageSetMap.values()) {

            DicomImageElement firstDicomImage = dicomImageSet.iterator().next();

            String studyInstanceUID = TagD.getTagValue(firstDicomImage, Tag.StudyInstanceUID, String.class);
            String seriesInstanceUID = TagD.getTagValue(firstDicomImage, Tag.SeriesInstanceUID, String.class);
            String sopClassUID = TagD.getTagValue(firstDicomImage, Tag.SOPClassUID, String.class);

            Collection<String> sopInstanceUIDs = new ArrayList<>(dicomImageSet.size());
            for (DicomImageElement dicomImage : dicomImageSet) {
                sopInstanceUIDs.add(TagD.getTagValue(dicomImage, Tag.SOPInstanceUID, String.class));
            }

            hasDataModelChanged |=
                setKeyObjectReference(selectedState, studyInstanceUID, seriesInstanceUID, sopInstanceUIDs, sopClassUID);
        }

        return hasDataModelChanged;
    }

    private boolean setKeyObjectReference(boolean selectedState, String studyInstanceUID, String seriesInstanceUID,
        Collection<String> sopInstanceUIDs, String sopClassUID) {

        if (selectedState) {
            return addKeyObjects(studyInstanceUID, seriesInstanceUID, sopInstanceUIDs, sopClassUID);
        } else {
            return removeKeyObjects(studyInstanceUID, seriesInstanceUID, sopInstanceUIDs);
        }
    }

}
