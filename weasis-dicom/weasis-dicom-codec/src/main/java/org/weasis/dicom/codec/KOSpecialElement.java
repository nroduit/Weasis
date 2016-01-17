package org.weasis.dicom.codec;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Code;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.io.DicomOutputStream;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.codec.macro.HierachicalSOPInstanceReference;
import org.weasis.dicom.codec.macro.KODocumentModule;
import org.weasis.dicom.codec.macro.SOPInstanceReferenceAndMAC;
import org.weasis.dicom.codec.macro.SeriesAndInstanceReference;

public class KOSpecialElement extends AbstractKOSpecialElement {


    public KOSpecialElement(DicomMediaIO mediaIO) {
        super(mediaIO);
    }

    public void toggleKeyObjectReference(DicomImageElement dicomImage) {

        String studyInstanceUID = (String) dicomImage.getTagValue(TagW.StudyInstanceUID);
        String seriesInstanceUID = (String) dicomImage.getTagValue(TagW.SeriesInstanceUID);
        String sopInstanceUID = (String) dicomImage.getTagValue(TagW.SOPInstanceUID);
        String sopClassUID = (String) dicomImage.getTagValue(TagW.SOPClassUID);

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
        String studyInstanceUID = (String) dicomImage.getTagValue(TagW.StudyInstanceUID);
        String seriesInstanceUID = (String) dicomImage.getTagValue(TagW.SeriesInstanceUID);
        String sopInstanceUID = (String) dicomImage.getTagValue(TagW.SOPInstanceUID);
        String sopClassUID = (String) dicomImage.getTagValue(TagW.SOPClassUID);

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

        Map<String, Set<DicomImageElement>> dicomImageSetMap = new HashMap<String, Set<DicomImageElement>>();

        for (DicomImageElement dicomImage : dicomImageList) {
            String studyInstanceUID = (String) dicomImage.getTagValue(TagW.StudyInstanceUID);
            String seriesInstanceUID = (String) dicomImage.getTagValue(TagW.SeriesInstanceUID);
            String sopClassUID = (String) dicomImage.getTagValue(TagW.SOPClassUID);

            String hashcode = studyInstanceUID + seriesInstanceUID + sopClassUID;

            Set<DicomImageElement> dicomImageSet = dicomImageSetMap.get(hashcode);
            if (dicomImageSet == null) {
                dicomImageSetMap.put(hashcode, dicomImageSet = new HashSet<DicomImageElement>());
            }
            dicomImageSet.add(dicomImage);
        }

        boolean hasDataModelChanged = false;

        for (Set<DicomImageElement> dicomImageSet : dicomImageSetMap.values()) {

            DicomImageElement firstDicomImage = dicomImageSet.iterator().next();

            String studyInstanceUID = (String) firstDicomImage.getTagValue(TagW.StudyInstanceUID);
            String seriesInstanceUID = (String) firstDicomImage.getTagValue(TagW.SeriesInstanceUID);
            String sopClassUID = (String) firstDicomImage.getTagValue(TagW.SOPClassUID);

            Collection<String> sopInstanceUIDs = new ArrayList<String>(dicomImageSet.size());
            for (DicomImageElement dicomImage : dicomImageSet) {
                sopInstanceUIDs.add((String) dicomImage.getTagValue(TagW.SOPInstanceUID));
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
