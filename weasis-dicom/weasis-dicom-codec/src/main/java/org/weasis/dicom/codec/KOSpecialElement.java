package org.weasis.dicom.codec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.weasis.dicom.codec.macro.SOPInstanceReferenceAndMAC;

public class KOSpecialElement extends AbstractKOSpecialElement {


    public KOSpecialElement(DicomMediaIO mediaIO) {
        super(mediaIO);
    }

    public void toggleKeyObjectReference(DicomImageElement dicomImage) {

        String studyInstanceUID = TagD.getTagValue(dicomImage, Tag.StudyInstanceUID, String.class);
        String seriesInstanceUID = TagD.getTagValue(dicomImage, Tag.SeriesInstanceUID, String.class);
        String sopInstanceUID = TagD.getTagValue(dicomImage, Tag.SOPInstanceUID, String.class);
        String sopClassUID = TagD.getTagValue(dicomImage, Tag.SOPClassUID, String.class);

        toggleKeyObjectReference(studyInstanceUID, seriesInstanceUID, sopInstanceUID, sopClassUID);
    }

    /**
     * If the sopInstanceUID is not referenced, add a new Key Object reference<br>
     * If the sopInstanceUID is already referenced, remove this Key Object reference
     *
     * @param studyInstanceUID
     * @param seriesInstanceUID
     * @param sopInstanceUID
     * @param sopClassUID
     */
    private void toggleKeyObjectReference(String studyInstanceUID, String seriesInstanceUID, String sopInstanceUID,
        String sopClassUID) {

        // Get the SOPInstanceReferenceMap for this seriesUID
        Map<String, SOPInstanceReferenceAndMAC> sopInstanceReferenceBySOPInstanceUID =
            sopInstanceReferenceMapBySeriesUID.get(seriesInstanceUID);

        boolean isSelected = sopInstanceReferenceBySOPInstanceUID != null
            && sopInstanceReferenceBySOPInstanceUID.containsKey(sopInstanceUID);

        setKeyObjectReference(!isSelected, studyInstanceUID, seriesInstanceUID, sopInstanceUID, sopClassUID);
    }

    // ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public boolean setKeyObjectReference(boolean selectedState, DicomImageElement dicomImage) {
        String studyInstanceUID = TagD.getTagValue(dicomImage, Tag.StudyInstanceUID, String.class);
        String seriesInstanceUID = TagD.getTagValue(dicomImage, Tag.SeriesInstanceUID, String.class);
        String sopInstanceUID = TagD.getTagValue(dicomImage, Tag.SOPInstanceUID, String.class);
        String sopClassUID = TagD.getTagValue(dicomImage, Tag.SOPClassUID, String.class);
        
        return setKeyObjectReference(selectedState, studyInstanceUID, seriesInstanceUID, sopInstanceUID, sopClassUID);
    }

    private boolean setKeyObjectReference(boolean selectedState, String studyInstanceUID, String seriesInstanceUID,
        String sopInstanceUID, String sopClassUID) {

        if (selectedState) {
            return addKeyObject(studyInstanceUID, seriesInstanceUID, sopInstanceUID, sopClassUID);
        } else {
            return removeKeyObject(studyInstanceUID, seriesInstanceUID, sopInstanceUID);
        }
    }


    public boolean setKeyObjectReference(boolean selectedState, List<DicomImageElement> dicomImageList) {

        Map<String, Set<DicomImageElement>> dicomImageSetMap = new HashMap<>();

        for (DicomImageElement dicomImage : dicomImageList) {
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
