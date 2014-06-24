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

public class KOSpecialElement extends DicomSpecialElement {

    private Map<String, Map<String, SOPInstanceReferenceAndMAC>> sopInstanceReferenceMapBySeriesUID;
    private Map<String, Map<String, SeriesAndInstanceReference>> seriesAndInstanceReferenceMapByStudyUID;
    private Map<String, HierachicalSOPInstanceReference> hierachicalSOPInstanceReferenceByStudyUID;

    public KOSpecialElement(DicomMediaIO mediaIO) {
        super(mediaIO);
    }

    @Override
    protected void initLabel() {
        /*
         * DICOM PS 3.3 - 2011 - C.17.3 SR Document Content Module
         * 
         * Concept Name Code Sequence: mandatory when type is CONTAINER or the root content item.
         */
        StringBuilder buf = new StringBuilder(getLabelPrefix());

        Attributes dicom = ((DicomMediaIO) mediaIO).getDicomObject();
        Attributes item = dicom.getNestedDataset(Tag.ContentSequence);
        if (item != null) {
            buf.append(item.getString(Tag.TextValue));
        }
        label = buf.toString();
    }

    public Set<String> getReferencedStudyInstanceUIDSet() {
        if (hierachicalSOPInstanceReferenceByStudyUID == null) {
            updateHierachicalSOPInstanceReference();
        }
        return hierachicalSOPInstanceReferenceByStudyUID.keySet();
    }

    public boolean containsStudyInstanceUIDReference(String studyInstanceUIDReference) {
        if (hierachicalSOPInstanceReferenceByStudyUID == null) {
            updateHierachicalSOPInstanceReference();
        }
        return hierachicalSOPInstanceReferenceByStudyUID.containsKey(studyInstanceUIDReference);
    }

    public Set<String> getReferencedSeriesInstanceUIDSet(String studyUID) {
        if (seriesAndInstanceReferenceMapByStudyUID == null) {
            updateHierachicalSOPInstanceReference();
        }
        Map<String, SeriesAndInstanceReference> seriesAndInstanceReferenceBySeriesUID =
            seriesAndInstanceReferenceMapByStudyUID.get(studyUID);
        return seriesAndInstanceReferenceBySeriesUID != null ? seriesAndInstanceReferenceMapByStudyUID.get(studyUID)
            .keySet() : null;
    }

    public Set<String> getReferencedSeriesInstanceUIDSet() {
        if (sopInstanceReferenceMapBySeriesUID == null) {
            updateHierachicalSOPInstanceReference();
        }

        return sopInstanceReferenceMapBySeriesUID.keySet();
    }

    public boolean containsSeriesInstanceUIDReference(String seriesInstanceUIDReference) {
        if (sopInstanceReferenceMapBySeriesUID == null) {
            updateHierachicalSOPInstanceReference();
        }
        return sopInstanceReferenceMapBySeriesUID.containsKey(seriesInstanceUIDReference);
    }

    public Set<String> getReferencedSOPInstanceUIDSet() {
        if (sopInstanceReferenceMapBySeriesUID == null) {
            updateHierachicalSOPInstanceReference();
        }

        Set<String> referencedSOPInstanceUIDSet = new LinkedHashSet<String>();
        for (Map<String, SOPInstanceReferenceAndMAC> sopInstanceReference : sopInstanceReferenceMapBySeriesUID.values()) {
            referencedSOPInstanceUIDSet.addAll(sopInstanceReference.keySet());
        }
        return referencedSOPInstanceUIDSet;
    }

    public Set<String> getReferencedSOPInstanceUIDSet(String seriesUID) {
        if (seriesUID == null) {
            return getReferencedSOPInstanceUIDSet();
        }

        if (sopInstanceReferenceMapBySeriesUID == null) {
            updateHierachicalSOPInstanceReference();
        }

        Map<String, SOPInstanceReferenceAndMAC> sopInstanceReferenceBySOPInstanceUID =
            sopInstanceReferenceMapBySeriesUID.get(seriesUID);
        return sopInstanceReferenceBySOPInstanceUID != null ? sopInstanceReferenceBySOPInstanceUID.keySet() : null;
    }

    public boolean containsSopInstanceUIDReference(String seriesInstanceUID, String sopInstanceUIDReference) {
        Set<String> sopInstanceUIDSet = getReferencedSOPInstanceUIDSet(seriesInstanceUID);
        return (sopInstanceUIDSet != null && sopInstanceUIDSet.contains(sopInstanceUIDReference));
    }

    public boolean containsSopInstanceUIDReference(String sopInstanceUIDReference) {
        return containsSopInstanceUIDReference(null, sopInstanceUIDReference);
    }

    public boolean isEmpty() {
        if (sopInstanceReferenceMapBySeriesUID == null) {
            updateHierachicalSOPInstanceReference();
        }
        return sopInstanceReferenceMapBySeriesUID.isEmpty();
    }

    /**
     * Extract all the hierarchical SOP Instance References from the CurrentRequestedProcedureEvidences of the root
     * DicomObject into the dedicated Maps. These collections are used to improve access performance for data queries.
     * 
     * @note This method should be called only once since any call to add/remove methods should keep in sync with the
     *       CurrentRequestedProcedureEvidences of the root DicomObject
     */
    private void updateHierachicalSOPInstanceReference() {
        init();

        Attributes dcmItems = getMediaReader().getDicomObject();

        if (dcmItems != null) {
            Collection<HierachicalSOPInstanceReference> referencedStudySequence =
                HierachicalSOPInstanceReference.toHierachicalSOPInstanceReferenceMacros(dcmItems
                    .getSequence(Tag.CurrentRequestedProcedureEvidenceSequence));

            if (referencedStudySequence != null) {

                boolean sopInstanceExist = false;

                for (HierachicalSOPInstanceReference studyRef : referencedStudySequence) {
                    Collection<SeriesAndInstanceReference> referencedSeriesSequence = studyRef.getReferencedSeries();
                    if (referencedSeriesSequence == null) {
                        continue;
                    }

                    String studyUID = studyRef.getStudyInstanceUID();

                    for (SeriesAndInstanceReference serieRef : referencedSeriesSequence) {
                        Collection<SOPInstanceReferenceAndMAC> referencedSOPInstanceSequence =
                            serieRef.getReferencedSOPInstances();
                        if (referencedSOPInstanceSequence == null) {
                            continue;
                        }

                        String seriesUID = serieRef.getSeriesInstanceUID();

                        for (SOPInstanceReferenceAndMAC sopRef : referencedSOPInstanceSequence) {
                            String SOPInstanceUID = sopRef.getReferencedSOPInstanceUID();

                            if (SOPInstanceUID == null || SOPInstanceUID.equals("")) { //$NON-NLS-1$
                                continue;
                            }

                            Map<String, SOPInstanceReferenceAndMAC> sopInstanceReferenceBySOPInstanceUID =
                                sopInstanceReferenceMapBySeriesUID.get(seriesUID);

                            if (sopInstanceReferenceBySOPInstanceUID == null) {
                                sopInstanceReferenceMapBySeriesUID.put(seriesUID, sopInstanceReferenceBySOPInstanceUID =
                                    new LinkedHashMap<String, SOPInstanceReferenceAndMAC>());
                            }

                            sopInstanceReferenceBySOPInstanceUID.put(SOPInstanceUID, sopRef);
                            sopInstanceExist = true;
                        }

                        if (sopInstanceExist) {

                            Map<String, SeriesAndInstanceReference> seriesAndInstanceReferenceBySeriesUID =
                                seriesAndInstanceReferenceMapByStudyUID.get(studyUID);

                            if (seriesAndInstanceReferenceBySeriesUID == null) {
                                seriesAndInstanceReferenceMapByStudyUID.put(studyUID,
                                    seriesAndInstanceReferenceBySeriesUID =
                                        new LinkedHashMap<String, SeriesAndInstanceReference>());
                            }

                            seriesAndInstanceReferenceBySeriesUID.put(seriesUID, serieRef);
                        }
                    }

                    if (sopInstanceExist) {
                        hierachicalSOPInstanceReferenceByStudyUID.put(studyUID, studyRef);
                    }

                }
            }
        }
    }

    private void init() {
        if (hierachicalSOPInstanceReferenceByStudyUID == null) {
            hierachicalSOPInstanceReferenceByStudyUID = new LinkedHashMap<String, HierachicalSOPInstanceReference>();
        }
        if (seriesAndInstanceReferenceMapByStudyUID == null) {
            seriesAndInstanceReferenceMapByStudyUID =
                new LinkedHashMap<String, Map<String, SeriesAndInstanceReference>>();
        }
        if (sopInstanceReferenceMapBySeriesUID == null) {
            sopInstanceReferenceMapBySeriesUID = new LinkedHashMap<String, Map<String, SOPInstanceReferenceAndMAC>>();
        }
    }

    // ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

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

        boolean isSelected =
            sopInstanceReferenceBySOPInstanceUID != null
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

    // ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public boolean addKeyObject(DicomImageElement dicomImage) {

        String studyInstanceUID = (String) dicomImage.getTagValue(TagW.StudyInstanceUID);
        String seriesInstanceUID = (String) dicomImage.getTagValue(TagW.SeriesInstanceUID);
        String sopInstanceUID = (String) dicomImage.getTagValue(TagW.SOPInstanceUID);
        String sopClassUID = (String) dicomImage.getTagValue(TagW.SOPClassUID);

        return addKeyObject(studyInstanceUID, seriesInstanceUID, sopInstanceUID, sopClassUID);
    }

    public boolean addKeyObject(String studyInstanceUID, String seriesInstanceUID, String sopInstanceUID,
        String sopClassUID) {

        if (hierachicalSOPInstanceReferenceByStudyUID == null) {
            updateHierachicalSOPInstanceReference();
        }

        // Get the SOPInstanceReferenceMap for this seriesUID
        Map<String, SOPInstanceReferenceAndMAC> sopInstanceReferenceBySOPInstanceUID =
            sopInstanceReferenceMapBySeriesUID.get(seriesInstanceUID);

        if (sopInstanceReferenceBySOPInstanceUID == null) {
            // the seriesUID is not referenced, create a new SOPInstanceReferenceMap
            sopInstanceReferenceMapBySeriesUID.put(seriesInstanceUID, sopInstanceReferenceBySOPInstanceUID =
                new LinkedHashMap<String, SOPInstanceReferenceAndMAC>());
        } else if (sopInstanceReferenceBySOPInstanceUID.containsKey(sopInstanceUID)) {
            // the sopInstanceUID is already referenced, skip the job
            return false;
        }

        // Create the new SOPInstanceReferenceAndMAC and add to the SOPInstanceReferenceMap
        SOPInstanceReferenceAndMAC referencedSOP = new SOPInstanceReferenceAndMAC();
        referencedSOP.setReferencedSOPInstanceUID(sopInstanceUID);
        referencedSOP.setReferencedSOPClassUID(sopClassUID);

        sopInstanceReferenceBySOPInstanceUID.put(sopInstanceUID, referencedSOP);

        // Get the SeriesAndInstanceReferenceMap for this studyUID
        Map<String, SeriesAndInstanceReference> seriesAndInstanceReferenceBySeriesUID =
            seriesAndInstanceReferenceMapByStudyUID.get(studyInstanceUID);

        if (seriesAndInstanceReferenceBySeriesUID == null) {
            // the studyUID is not referenced, create a new one SeriesAndInstanceReferenceMap
            seriesAndInstanceReferenceMapByStudyUID.put(studyInstanceUID, seriesAndInstanceReferenceBySeriesUID =
                new LinkedHashMap<String, SeriesAndInstanceReference>());
        }

        // Get the SeriesAndInstanceReference for this seriesUID
        SeriesAndInstanceReference referencedSerie = seriesAndInstanceReferenceBySeriesUID.get(seriesInstanceUID);

        if (referencedSerie == null) {
            // the seriesUID is not referenced, create a new SeriesAndInstanceReference
            referencedSerie = new SeriesAndInstanceReference();
            referencedSerie.setSeriesInstanceUID(seriesInstanceUID);
            seriesAndInstanceReferenceBySeriesUID.put(seriesInstanceUID, referencedSerie);
        }

        // Update the current SeriesAndInstanceReference with the referencedSOPInstance Sequence
        List<SOPInstanceReferenceAndMAC> referencedSOPInstances =
            new ArrayList<SOPInstanceReferenceAndMAC>(sopInstanceReferenceBySOPInstanceUID.values());

        referencedSerie.setReferencedSOPInstances(referencedSOPInstances);

        // Get the HierachicalSOPInstanceReference for this studyUID
        HierachicalSOPInstanceReference hierachicalDicom =
            hierachicalSOPInstanceReferenceByStudyUID.get(studyInstanceUID);

        if (hierachicalDicom == null) {
            // the studyUID is not referenced, create a new one HierachicalSOPInstanceReference
            hierachicalDicom = new HierachicalSOPInstanceReference();
            hierachicalDicom.setStudyInstanceUID(studyInstanceUID);
            hierachicalSOPInstanceReferenceByStudyUID.put(studyInstanceUID, hierachicalDicom);
        }

        // Update the current HierachicalSOPInstance with the referencedSeries Sequence
        List<SeriesAndInstanceReference> referencedSeries =
            new ArrayList<SeriesAndInstanceReference>(seriesAndInstanceReferenceBySeriesUID.values());

        hierachicalDicom.setReferencedSeries(referencedSeries);

        // Update the CurrentRequestedProcedureEvidences for the root dcmItems
        Attributes dcmItems = getMediaReader().getDicomObject();

        List<HierachicalSOPInstanceReference> referencedStudies =
            new ArrayList<HierachicalSOPInstanceReference>(hierachicalSOPInstanceReferenceByStudyUID.values());

        new KODocumentModule(dcmItems).setCurrentRequestedProcedureEvidences(referencedStudies);

        return true;
    }

    // ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

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

    // ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public boolean addKeyObjects(String studyInstanceUID, String seriesInstanceUID, Collection<String> sopInstanceUIDs,
        String sopClassUID) {

        if (hierachicalSOPInstanceReferenceByStudyUID == null) {
            updateHierachicalSOPInstanceReference();
        }

        // Get the SOPInstanceReferenceMap for this seriesUID
        Map<String, SOPInstanceReferenceAndMAC> sopInstanceReferenceBySOPInstanceUID =
            sopInstanceReferenceMapBySeriesUID.get(seriesInstanceUID);

        boolean newReferenceAdded = false;

        for (String sopInstanceUID : sopInstanceUIDs) {

            if (sopInstanceReferenceBySOPInstanceUID == null) {
                // the seriesUID is not referenced, create a new SOPInstanceReferenceMap
                sopInstanceReferenceMapBySeriesUID.put(seriesInstanceUID, sopInstanceReferenceBySOPInstanceUID =
                    new LinkedHashMap<String, SOPInstanceReferenceAndMAC>());
            } else if (sopInstanceReferenceBySOPInstanceUID.containsKey(sopInstanceUID)) {
                // the sopInstanceUID is already referenced, keep continue
                continue;
            }

            // Create the new SOPInstanceReferenceAndMAC and add to the SOPInstanceReferenceMap
            SOPInstanceReferenceAndMAC referencedSOP = new SOPInstanceReferenceAndMAC();
            referencedSOP.setReferencedSOPInstanceUID(sopInstanceUID);
            referencedSOP.setReferencedSOPClassUID(sopClassUID);

            sopInstanceReferenceBySOPInstanceUID.put(sopInstanceUID, referencedSOP);

            newReferenceAdded = true;
        }

        if (!newReferenceAdded) {
            return false; // UID's parameters were already referenced , skip the job
        }

        // Get the SeriesAndInstanceReferenceMap for this studyUID
        Map<String, SeriesAndInstanceReference> seriesAndInstanceReferenceBySeriesUID =
            seriesAndInstanceReferenceMapByStudyUID.get(studyInstanceUID);

        if (seriesAndInstanceReferenceBySeriesUID == null) {
            // the studyUID is not referenced, create a new one SeriesAndInstanceReferenceMap
            seriesAndInstanceReferenceMapByStudyUID.put(studyInstanceUID, seriesAndInstanceReferenceBySeriesUID =
                new LinkedHashMap<String, SeriesAndInstanceReference>());
        }

        // Get the SeriesAndInstanceReference for this seriesUID
        SeriesAndInstanceReference referencedSerie = seriesAndInstanceReferenceBySeriesUID.get(seriesInstanceUID);

        if (referencedSerie == null) {
            // the seriesUID is not referenced, create a new SeriesAndInstanceReference
            referencedSerie = new SeriesAndInstanceReference();
            referencedSerie.setSeriesInstanceUID(seriesInstanceUID);
            seriesAndInstanceReferenceBySeriesUID.put(seriesInstanceUID, referencedSerie);
        }

        // Update the current SeriesAndInstanceReference with the referencedSOPInstance Sequence
        List<SOPInstanceReferenceAndMAC> referencedSOPInstances =
            new ArrayList<SOPInstanceReferenceAndMAC>(sopInstanceReferenceBySOPInstanceUID.values());

        referencedSerie.setReferencedSOPInstances(referencedSOPInstances);

        // Get the HierachicalSOPInstanceReference for this studyUID
        HierachicalSOPInstanceReference hierachicalDicom =
            hierachicalSOPInstanceReferenceByStudyUID.get(studyInstanceUID);

        if (hierachicalDicom == null) {
            // the studyUID is not referenced, create a new one HierachicalSOPInstanceReference
            hierachicalDicom = new HierachicalSOPInstanceReference();
            hierachicalDicom.setStudyInstanceUID(studyInstanceUID);
            hierachicalSOPInstanceReferenceByStudyUID.put(studyInstanceUID, hierachicalDicom);
        }

        // Update the current HierachicalSOPInstance with the referencedSeries Sequence
        List<SeriesAndInstanceReference> referencedSeries =
            new ArrayList<SeriesAndInstanceReference>(seriesAndInstanceReferenceBySeriesUID.values());

        hierachicalDicom.setReferencedSeries(referencedSeries);

        // Update the CurrentRequestedProcedureEvidences for the root dcmItems
        Attributes dcmItems = getMediaReader().getDicomObject();

        List<HierachicalSOPInstanceReference> referencedStudies =
            new ArrayList<HierachicalSOPInstanceReference>(hierachicalSOPInstanceReferenceByStudyUID.values());

        new KODocumentModule(dcmItems).setCurrentRequestedProcedureEvidences(referencedStudies);

        return true;
    }

    // ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public boolean removeKeyObject(DicomImageElement dicomImage) {

        String studyInstanceUID = (String) dicomImage.getTagValue(TagW.StudyInstanceUID);
        String seriesInstanceUID = (String) dicomImage.getTagValue(TagW.SeriesInstanceUID);
        String sopInstanceUID = (String) dicomImage.getTagValue(TagW.SOPInstanceUID);

        return removeKeyObject(studyInstanceUID, seriesInstanceUID, sopInstanceUID);
    }

    public boolean removeKeyObject(String studyInstanceUID, String seriesInstanceUID, String sopInstanceUID) {

        if (hierachicalSOPInstanceReferenceByStudyUID == null) {
            updateHierachicalSOPInstanceReference();
        }

        // Get the SeriesAndInstanceReferenceMap for this studyUID
        Map<String, SeriesAndInstanceReference> seriesAndInstanceReferenceBySeriesUID =
            seriesAndInstanceReferenceMapByStudyUID.get(studyInstanceUID);

        // Get the SOPInstanceReferenceMap for this seriesUID
        Map<String, SOPInstanceReferenceAndMAC> sopInstanceReferenceBySOPInstanceUID =
            sopInstanceReferenceMapBySeriesUID.get(seriesInstanceUID);

        if (sopInstanceReferenceBySOPInstanceUID == null || seriesAndInstanceReferenceBySeriesUID == null
            || sopInstanceReferenceBySOPInstanceUID.remove(sopInstanceUID) == null) {
            // UID's parameters were not referenced, skip the job
            return false;
        }

        if (sopInstanceReferenceBySOPInstanceUID.isEmpty()) {

            sopInstanceReferenceMapBySeriesUID.remove(seriesInstanceUID);
            seriesAndInstanceReferenceBySeriesUID.remove(seriesInstanceUID);

            if (seriesAndInstanceReferenceBySeriesUID.isEmpty()) {
                seriesAndInstanceReferenceMapByStudyUID.remove(studyInstanceUID);
                hierachicalSOPInstanceReferenceByStudyUID.remove(studyInstanceUID);
            } else {
                // Get the HierachicalSOPInstanceReference for this studyUID
                HierachicalSOPInstanceReference hierachicalDicom =
                    hierachicalSOPInstanceReferenceByStudyUID.get(studyInstanceUID);

                // Update the current HierachicalSOPInstance with the referencedSeries Sequence
                List<SeriesAndInstanceReference> referencedSeries =
                    new ArrayList<SeriesAndInstanceReference>(seriesAndInstanceReferenceBySeriesUID.values());

                hierachicalDicom.setReferencedSeries(referencedSeries);
            }

        } else {
            // Get the SeriesAndInstanceReference for this seriesUID
            SeriesAndInstanceReference referencedSeries = seriesAndInstanceReferenceBySeriesUID.get(seriesInstanceUID);

            // Update the current SeriesAndInstanceReference with the referencedSOPInstance Sequence
            List<SOPInstanceReferenceAndMAC> referencedSOPInstances =
                new ArrayList<SOPInstanceReferenceAndMAC>(sopInstanceReferenceBySOPInstanceUID.values());

            referencedSeries.setReferencedSOPInstances(referencedSOPInstances);
        }

        // Update the CurrentRequestedProcedureEvidences for the root dcmItems
        Attributes dcmItems = getMediaReader().getDicomObject();
        List<HierachicalSOPInstanceReference> referencedStudies = null;

        if (hierachicalSOPInstanceReferenceByStudyUID.isEmpty() == false) {
            referencedStudies =
                new ArrayList<HierachicalSOPInstanceReference>(hierachicalSOPInstanceReferenceByStudyUID.values());
        }

        new KODocumentModule(dcmItems).setCurrentRequestedProcedureEvidences(referencedStudies);

        return true;
    }

    //
    // ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public boolean removeKeyObjects(String studyInstanceUID, String seriesInstanceUID,
        Collection<String> sopInstanceUIDs) {

        if (hierachicalSOPInstanceReferenceByStudyUID == null) {
            updateHierachicalSOPInstanceReference();
        }

        // Get the SeriesAndInstanceReferenceMap for this studyUID
        Map<String, SeriesAndInstanceReference> seriesAndInstanceReferenceBySeriesUID =
            seriesAndInstanceReferenceMapByStudyUID.get(studyInstanceUID);

        // Get the SOPInstanceReferenceMap for this seriesUID
        Map<String, SOPInstanceReferenceAndMAC> sopInstanceReferenceBySOPInstanceUID =
            sopInstanceReferenceMapBySeriesUID.get(seriesInstanceUID);

        if (sopInstanceReferenceBySOPInstanceUID == null || seriesAndInstanceReferenceBySeriesUID == null) {
            return false;
        }

        boolean referenceRemoved = false;

        for (String sopInstanceUID : sopInstanceUIDs) {
            if (sopInstanceReferenceBySOPInstanceUID.remove(sopInstanceUID) != null) {
                referenceRemoved = true;
            }
        }

        if (!referenceRemoved) {
            return false; // UID's parameters were not referenced, skip the job
        }

        if (!sopInstanceReferenceBySOPInstanceUID.isEmpty()) {
            // Get the SeriesAndInstanceReference for this seriesUID
            SeriesAndInstanceReference referencedSeries = seriesAndInstanceReferenceBySeriesUID.get(seriesInstanceUID);

            // Update the current SeriesAndInstanceReference with the referencedSOPInstance Sequence
            List<SOPInstanceReferenceAndMAC> referencedSOPInstances =
                new ArrayList<SOPInstanceReferenceAndMAC>(sopInstanceReferenceBySOPInstanceUID.values());

            referencedSeries.setReferencedSOPInstances(referencedSOPInstances);
        } else {

            sopInstanceReferenceMapBySeriesUID.remove(seriesInstanceUID);
            seriesAndInstanceReferenceBySeriesUID.remove(seriesInstanceUID);

            if (!seriesAndInstanceReferenceBySeriesUID.isEmpty()) {
                // Get the HierachicalSOPInstanceReference for this studyUID
                HierachicalSOPInstanceReference hierachicalDicom =
                    hierachicalSOPInstanceReferenceByStudyUID.get(studyInstanceUID);

                // Update the current HierachicalSOPInstance with the referencedSeries Sequence
                List<SeriesAndInstanceReference> referencedSeries =
                    new ArrayList<SeriesAndInstanceReference>(seriesAndInstanceReferenceBySeriesUID.values());

                hierachicalDicom.setReferencedSeries(referencedSeries);
            } else {
                seriesAndInstanceReferenceMapByStudyUID.remove(studyInstanceUID);
                hierachicalSOPInstanceReferenceByStudyUID.remove(studyInstanceUID);
            }
        }

        // Update the CurrentRequestedProcedureEvidences for the root dcmItems
        Attributes dcmItems = getMediaReader().getDicomObject();
        List<HierachicalSOPInstanceReference> referencedStudies = null;

        if (!hierachicalSOPInstanceReferenceByStudyUID.isEmpty()) {
            referencedStudies =
                new ArrayList<HierachicalSOPInstanceReference>(hierachicalSOPInstanceReferenceByStudyUID.values());
        }

        new KODocumentModule(dcmItems).setCurrentRequestedProcedureEvidences(referencedStudies);

        return true;
    }

    // ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public Filter<DicomImageElement> getSOPInstanceUIDFilter() {
        Filter<DicomImageElement> filter = new Filter<DicomImageElement>() {
            @Override
            public boolean passes(DicomImageElement dicom) {
                if (dicom == null || dicom.getTagValue(TagW.SeriesInstanceUID) == null) {
                    return false;
                }
                String seriesUID = (String) dicom.getTagValue(TagW.SeriesInstanceUID);
                String sopInstanceUID = (String) dicom.getTagValue(TagW.SOPInstanceUID);

                Set<String> referencedSOPInstanceUIDSet = getReferencedSOPInstanceUIDSet(seriesUID);

                return referencedSOPInstanceUIDSet == null ? false : referencedSOPInstanceUIDSet
                    .contains(sopInstanceUID);
            }
        };
        return filter;
    }

    @Override
    public synchronized File getFile() {
        DicomMediaIO reader = getMediaReader();
        if (reader != null && reader.isEditableDicom()) {
            Attributes dcm = reader.getDicomObject();
            if (dcm != null) {
                DicomOutputStream out = null;
                try {
                    File tmpFile = new File(DicomMediaIO.DICOM_EXPORT_DIR, dcm.getString(Tag.SOPInstanceUID));
                    out = new DicomOutputStream(tmpFile);
                    out.writeDataset(dcm.createFileMetaInformation(UID.ImplicitVRLittleEndian), dcm);
                    return tmpFile;
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    FileUtil.safeClose(out);
                }
            }
        }
        return super.getFile();
    }

}
