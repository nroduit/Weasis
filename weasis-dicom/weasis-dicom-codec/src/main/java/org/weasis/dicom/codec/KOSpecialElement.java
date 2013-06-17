package org.weasis.dicom.codec;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.dcm4che.data.DicomObject;
import org.dcm4che.data.Tag;
import org.dcm4che.iod.module.sr.HierachicalSOPInstanceReference;
import org.dcm4che.iod.module.sr.KODocumentModule;
import org.dcm4che.iod.module.sr.SOPInstanceReferenceAndMAC;
import org.dcm4che.iod.module.sr.SeriesAndInstanceReference;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.media.data.TagW;

public class KOSpecialElement extends DicomSpecialElement {

    private Map<String, Map<String, SOPInstanceReferenceAndMAC>> sopInstanceReferenceMapBySeriesUID;
    private Map<String, Map<String, SeriesAndInstanceReference>> seriesAndInstanceReferenceMapByStudyUID;
    private Map<String, HierachicalSOPInstanceReference> hierachicalSOPInstanceReferenceByStudyUID;

    public KOSpecialElement(DicomMediaIO mediaIO) {
        super(mediaIO);
    }

    public Set<String> getReferencedStudyInstanceUIDSet() {
        if (hierachicalSOPInstanceReferenceByStudyUID == null) {
            updateHierachicalSOPInstanceReference();
        }
        return hierachicalSOPInstanceReferenceByStudyUID.keySet();
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
        if (seriesAndInstanceReferenceMapByStudyUID == null) {
            updateHierachicalSOPInstanceReference();
        }

        return sopInstanceReferenceMapBySeriesUID.keySet();
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
        if (sopInstanceReferenceMapBySeriesUID == null) {
            updateHierachicalSOPInstanceReference();
        }
        Map<String, SOPInstanceReferenceAndMAC> sopInstanceReferenceBySOPInstanceUID =
            sopInstanceReferenceMapBySeriesUID.get(seriesUID);
        return sopInstanceReferenceBySOPInstanceUID != null ? sopInstanceReferenceBySOPInstanceUID.keySet() : null;
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

        DicomObject dicomObject = getMediaReader().getDicomObject();
        if (dicomObject != null) {
            HierachicalSOPInstanceReference[] referencedStudySequence =
                HierachicalSOPInstanceReference.toSOPInstanceReferenceMacros(dicomObject
                    .get(Tag.CurrentRequestedProcedureEvidenceSequence));

            if (referencedStudySequence != null) {

                boolean sopInstanceExist = false;

                for (HierachicalSOPInstanceReference studyRef : referencedStudySequence) {
                    SeriesAndInstanceReference[] referencedSeriesSequence = studyRef.getReferencedSeries();
                    if (referencedSeriesSequence == null) {
                        continue;
                    }

                    String studyUID = studyRef.getStudyInstanceUID();

                    for (SeriesAndInstanceReference serieRef : referencedSeriesSequence) {
                        SOPInstanceReferenceAndMAC[] referencedSOPInstanceSequence = serieRef.getReferencedInstances();
                        if (referencedSOPInstanceSequence == null) {
                            continue;
                        }

                        String seriesUID = serieRef.getSeriesInstanceUID();

                        for (SOPInstanceReferenceAndMAC sopRef : referencedSOPInstanceSequence) {
                            String SOPInstanceUID = sopRef.getReferencedSOPInstanceUID();

                            if (SOPInstanceUID == null || SOPInstanceUID.equals("")) {
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

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public void toggleKeyObjectReference(String studyUID, String seriesUID, String sopInstanceUID, String sopClassUID) {

        // Get the SOPInstanceReferenceMap for this seriesUID
        Map<String, SOPInstanceReferenceAndMAC> sopInstanceReferenceBySOPInstanceUID =
            sopInstanceReferenceMapBySeriesUID.get(seriesUID);

        if (sopInstanceReferenceBySOPInstanceUID != null
            && sopInstanceReferenceBySOPInstanceUID.containsKey(sopInstanceUID)) {
            // the sopInstanceUID is already referenced, remove this Key Object
            removeKeyObject(studyUID, seriesUID, sopInstanceUID);
        } else {
            // the sopInstanceUID is not referenced, add a new Key Object
            addKeyObject(studyUID, seriesUID, sopClassUID, sopInstanceUID);
        }

    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public boolean addKeyObject(String studyUID, String seriesUID, String sopClassUID, String sopInstanceUID) {

        if (hierachicalSOPInstanceReferenceByStudyUID == null) {
            updateHierachicalSOPInstanceReference();
        }

        // Get the SOPInstanceReferenceMap for this seriesUID
        Map<String, SOPInstanceReferenceAndMAC> sopInstanceReferenceBySOPInstanceUID =
            sopInstanceReferenceMapBySeriesUID.get(seriesUID);

        if (sopInstanceReferenceBySOPInstanceUID == null) {
            // the seriesUID is not referenced, create a new SOPInstanceReferenceMap
            sopInstanceReferenceMapBySeriesUID.put(seriesUID, sopInstanceReferenceBySOPInstanceUID =
                new LinkedHashMap<String, SOPInstanceReferenceAndMAC>());
        } else if (sopInstanceReferenceBySOPInstanceUID.containsKey(sopInstanceUID)) {
            // the sopInstanceUID is already referenced, skip the job
            return false;
        }

        // Create the new SOPInstanceReference and add to the SOPInstanceReferenceMap
        SOPInstanceReferenceAndMAC referencedSOP = new SOPInstanceReferenceAndMAC();
        referencedSOP.setReferencedSOPInstanceUID(sopInstanceUID);
        referencedSOP.setReferencedSOPClassUID(sopClassUID);

        sopInstanceReferenceBySOPInstanceUID.put(sopInstanceUID, referencedSOP);

        // Get the SeriesAndInstanceReferenceMap for this studyUID
        Map<String, SeriesAndInstanceReference> seriesAndInstanceReferenceBySeriesUID =
            seriesAndInstanceReferenceMapByStudyUID.get(studyUID);

        if (seriesAndInstanceReferenceBySeriesUID == null) {
            // the studyUID is not referenced, create a new one SeriesAndInstanceReferenceMap
            seriesAndInstanceReferenceMapByStudyUID.put(studyUID, seriesAndInstanceReferenceBySeriesUID =
                new LinkedHashMap<String, SeriesAndInstanceReference>());
        }

        // Get the SeriesAndInstanceReference for this seriesUID
        SeriesAndInstanceReference referencedSeries = seriesAndInstanceReferenceBySeriesUID.get(seriesUID);

        if (referencedSeries == null) {
            // the seriesUID is not referenced, create a new SeriesAndInstanceReference
            referencedSeries = new SeriesAndInstanceReference();
            referencedSeries.setSeriesInstanceUID(seriesUID);
            seriesAndInstanceReferenceBySeriesUID.put(seriesUID, referencedSeries);
        }

        // Update the current SeriesAndInstanceReference with the referencedSOPInstance Sequence
        SOPInstanceReferenceAndMAC[] referencedSOPInstanceSequence =
            sopInstanceReferenceBySOPInstanceUID.values().toArray(
                new SOPInstanceReferenceAndMAC[sopInstanceReferenceBySOPInstanceUID.size()]);

        referencedSeries.setSOPInstanceReference(referencedSOPInstanceSequence);

        // Get the HierachicalSOPInstanceReference for this studyUID
        HierachicalSOPInstanceReference hierachicalDicom = hierachicalSOPInstanceReferenceByStudyUID.get(studyUID);

        if (hierachicalDicom == null) {
            // the studyUID is not referenced, create a new one HierachicalSOPInstanceReference
            hierachicalDicom = new HierachicalSOPInstanceReference();
            hierachicalDicom.setStudyInstanceUID(studyUID);
            hierachicalSOPInstanceReferenceByStudyUID.put(studyUID, hierachicalDicom);
        }

        // Update the current HierachicalSOPInstance with the referencedSeries Sequence
        SeriesAndInstanceReference[] referencedSeriesSequence =
            seriesAndInstanceReferenceBySeriesUID.values().toArray(
                new SeriesAndInstanceReference[seriesAndInstanceReferenceBySeriesUID.size()]);

        hierachicalDicom.setReferencedSeries(referencedSeriesSequence);

        // Update the CurrentRequestedProcedureEvidences for the root dicomObject
        DicomObject dicomObject = getMediaReader().getDicomObject();

        HierachicalSOPInstanceReference[] referencedStudySequence =
            hierachicalSOPInstanceReferenceByStudyUID.values().toArray(
                new HierachicalSOPInstanceReference[hierachicalSOPInstanceReferenceByStudyUID.size()]);

        new KODocumentModule(dicomObject).setCurrentRequestedProcedureEvidences(referencedStudySequence);

        return true;
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public boolean addKeyObjects(String studyUID, String seriesUID, String sopClassUID,
        Collection<String> sopInstanceUIDs) {

        if (hierachicalSOPInstanceReferenceByStudyUID == null) {
            updateHierachicalSOPInstanceReference();
        }

        // Get the SOPInstanceReferenceMap for this seriesUID
        Map<String, SOPInstanceReferenceAndMAC> sopInstanceReferenceBySOPInstanceUID =
            sopInstanceReferenceMapBySeriesUID.get(seriesUID);

        boolean newReferenceAdded = false;

        for (String sopInstanceUID : sopInstanceUIDs) {

            if (sopInstanceReferenceBySOPInstanceUID == null) {
                // the seriesUID is not referenced, create a new SOPInstanceReferenceMap
                sopInstanceReferenceMapBySeriesUID.put(seriesUID, sopInstanceReferenceBySOPInstanceUID =
                    new LinkedHashMap<String, SOPInstanceReferenceAndMAC>());
            } else if (sopInstanceReferenceBySOPInstanceUID.containsKey(sopInstanceUID)) {
                // the sopInstanceUID is already referenced, keep continue
                continue;
            }

            // Create the new SOPInstanceReference and add to the SOPInstanceReferenceMap
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
            seriesAndInstanceReferenceMapByStudyUID.get(studyUID);

        if (seriesAndInstanceReferenceBySeriesUID == null) {
            // the studyUID is not referenced, create a new one SeriesAndInstanceReferenceMap
            seriesAndInstanceReferenceMapByStudyUID.put(studyUID, seriesAndInstanceReferenceBySeriesUID =
                new LinkedHashMap<String, SeriesAndInstanceReference>());
        }

        // Get the SeriesAndInstanceReference for this seriesUID
        SeriesAndInstanceReference referencedSeries = seriesAndInstanceReferenceBySeriesUID.get(seriesUID);

        if (referencedSeries == null) {
            // the seriesUID is not referenced, create a new SeriesAndInstanceReference
            referencedSeries = new SeriesAndInstanceReference();
            referencedSeries.setSeriesInstanceUID(seriesUID);
            seriesAndInstanceReferenceBySeriesUID.put(seriesUID, referencedSeries);
        }

        // Update the current SeriesAndInstanceReference with the referencedSOPInstance Sequence
        SOPInstanceReferenceAndMAC[] referencedSOPInstanceSequence =
            sopInstanceReferenceBySOPInstanceUID.values().toArray(
                new SOPInstanceReferenceAndMAC[sopInstanceReferenceBySOPInstanceUID.size()]);

        referencedSeries.setSOPInstanceReference(referencedSOPInstanceSequence);

        // Get the HierachicalSOPInstanceReference for this studyUID
        HierachicalSOPInstanceReference hierachicalDicom = hierachicalSOPInstanceReferenceByStudyUID.get(studyUID);

        if (hierachicalDicom == null) {
            // the studyUID is not referenced, create a new one HierachicalSOPInstanceReference
            hierachicalDicom = new HierachicalSOPInstanceReference();
            hierachicalDicom.setStudyInstanceUID(studyUID);
            hierachicalSOPInstanceReferenceByStudyUID.put(studyUID, hierachicalDicom);
        }

        // Update the current HierachicalSOPInstance with the referencedSeries Sequence
        SeriesAndInstanceReference[] referencedSeriesSequence =
            seriesAndInstanceReferenceBySeriesUID.values().toArray(
                new SeriesAndInstanceReference[seriesAndInstanceReferenceBySeriesUID.size()]);

        hierachicalDicom.setReferencedSeries(referencedSeriesSequence);

        // Update the CurrentRequestedProcedureEvidences for the root dicomObject
        DicomObject dicomObject = getMediaReader().getDicomObject();

        HierachicalSOPInstanceReference[] referencedStudySequence =
            hierachicalSOPInstanceReferenceByStudyUID.values().toArray(
                new HierachicalSOPInstanceReference[hierachicalSOPInstanceReferenceByStudyUID.size()]);

        new KODocumentModule(dicomObject).setCurrentRequestedProcedureEvidences(referencedStudySequence);

        return true;
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public boolean removeKeyObject(String studyUID, String seriesUID, String sopInstanceUID) {

        if (hierachicalSOPInstanceReferenceByStudyUID == null) {
            updateHierachicalSOPInstanceReference();
        }

        // Get the SeriesAndInstanceReferenceMap for this studyUID
        Map<String, SeriesAndInstanceReference> seriesAndInstanceReferenceBySeriesUID =
            seriesAndInstanceReferenceMapByStudyUID.get(studyUID);

        // Get the SOPInstanceReferenceMap for this seriesUID
        Map<String, SOPInstanceReferenceAndMAC> sopInstanceReferenceBySOPInstanceUID =
            sopInstanceReferenceMapBySeriesUID.get(seriesUID);

        if (sopInstanceReferenceBySOPInstanceUID == null || seriesAndInstanceReferenceBySeriesUID == null
            || sopInstanceReferenceBySOPInstanceUID.remove(sopInstanceUID) == null) {
            // UID's parameters were not referenced, skip the job
            return false;
        }

        if (!sopInstanceReferenceBySOPInstanceUID.isEmpty()) {
            // Get the SeriesAndInstanceReference for this seriesUID
            SeriesAndInstanceReference referencedSeries = seriesAndInstanceReferenceBySeriesUID.get(seriesUID);

            // Update the current SeriesAndInstanceReference with the referencedSOPInstance Sequence
            SOPInstanceReferenceAndMAC[] referencedSOPInstanceSequence =
                sopInstanceReferenceBySOPInstanceUID.values().toArray(
                    new SOPInstanceReferenceAndMAC[sopInstanceReferenceBySOPInstanceUID.size()]);

            referencedSeries.setSOPInstanceReference(referencedSOPInstanceSequence);
        } else {
            seriesAndInstanceReferenceBySeriesUID.remove(seriesUID);

            if (!seriesAndInstanceReferenceBySeriesUID.isEmpty()) {
                // Get the HierachicalSOPInstanceReference for this studyUID
                HierachicalSOPInstanceReference hierachicalDicom =
                    hierachicalSOPInstanceReferenceByStudyUID.get(studyUID);

                // Update the current HierachicalSOPInstance with the referencedSeries Sequence
                SeriesAndInstanceReference[] referencedSeriesSequence =
                    seriesAndInstanceReferenceBySeriesUID.values().toArray(
                        new SeriesAndInstanceReference[seriesAndInstanceReferenceBySeriesUID.size()]);

                hierachicalDicom.setReferencedSeries(referencedSeriesSequence);
            } else {
                seriesAndInstanceReferenceMapByStudyUID.remove(studyUID);
                hierachicalSOPInstanceReferenceByStudyUID.remove(studyUID);
            }
        }

        // Update the CurrentRequestedProcedureEvidences for the root dicomObject
        DicomObject dicomObject = getMediaReader().getDicomObject();
        HierachicalSOPInstanceReference[] referencedStudySequence = null;

        if (!hierachicalSOPInstanceReferenceByStudyUID.isEmpty()) {
            referencedStudySequence =
                hierachicalSOPInstanceReferenceByStudyUID.values().toArray(
                    new HierachicalSOPInstanceReference[hierachicalSOPInstanceReferenceByStudyUID.size()]);
        }

        new KODocumentModule(dicomObject).setCurrentRequestedProcedureEvidences(referencedStudySequence);

        return true;
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public boolean removeKeyObjects(String studyUID, String seriesUID, Collection<String> sopInstanceUIDs) {

        if (hierachicalSOPInstanceReferenceByStudyUID == null) {
            updateHierachicalSOPInstanceReference();
        }

        // Get the SeriesAndInstanceReferenceMap for this studyUID
        Map<String, SeriesAndInstanceReference> seriesAndInstanceReferenceBySeriesUID =
            seriesAndInstanceReferenceMapByStudyUID.get(studyUID);

        // Get the SOPInstanceReferenceMap for this seriesUID
        Map<String, SOPInstanceReferenceAndMAC> sopInstanceReferenceBySOPInstanceUID =
            sopInstanceReferenceMapBySeriesUID.get(seriesUID);

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
            SeriesAndInstanceReference referencedSeries = seriesAndInstanceReferenceBySeriesUID.get(seriesUID);

            // Update the current SeriesAndInstanceReference with the referencedSOPInstance Sequence
            SOPInstanceReferenceAndMAC[] referencedSOPInstanceSequence =
                sopInstanceReferenceBySOPInstanceUID.values().toArray(
                    new SOPInstanceReferenceAndMAC[sopInstanceReferenceBySOPInstanceUID.size()]);

            referencedSeries.setSOPInstanceReference(referencedSOPInstanceSequence);
        } else {
            seriesAndInstanceReferenceBySeriesUID.remove(seriesUID);

            if (!seriesAndInstanceReferenceBySeriesUID.isEmpty()) {
                // Get the HierachicalSOPInstanceReference for this studyUID
                HierachicalSOPInstanceReference hierachicalDicom =
                    hierachicalSOPInstanceReferenceByStudyUID.get(studyUID);

                // Update the current HierachicalSOPInstance with the referencedSeries Sequence
                SeriesAndInstanceReference[] referencedSeriesSequence =
                    seriesAndInstanceReferenceBySeriesUID.values().toArray(
                        new SeriesAndInstanceReference[seriesAndInstanceReferenceBySeriesUID.size()]);

                hierachicalDicom.setReferencedSeries(referencedSeriesSequence);
            } else {
                seriesAndInstanceReferenceMapByStudyUID.remove(studyUID);
                hierachicalSOPInstanceReferenceByStudyUID.remove(studyUID);
            }
        }

        // Update the CurrentRequestedProcedureEvidences for the root dicomObject
        DicomObject dicomObject = getMediaReader().getDicomObject();
        HierachicalSOPInstanceReference[] referencedStudySequence = null;

        if (!hierachicalSOPInstanceReferenceByStudyUID.isEmpty()) {
            referencedStudySequence =
                hierachicalSOPInstanceReferenceByStudyUID.values().toArray(
                    new HierachicalSOPInstanceReference[hierachicalSOPInstanceReferenceByStudyUID.size()]);
        }

        new KODocumentModule(dicomObject).setCurrentRequestedProcedureEvidences(referencedStudySequence);

        return true;
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public Filter<DicomImageElement> getSOPInstanceUIDFilter() {
        Filter<DicomImageElement> filter = new Filter<DicomImageElement>() {
            @Override
            public boolean passes(DicomImageElement dicom) {
                if (dicom == null || dicom.getTagValue(TagW.SeriesInstanceUID) == null) {
                    return false;
                }
                String seriesUID = (String) dicom.getTagValue(TagW.SeriesInstanceUID);
                String sopInstanceUID = (String) dicom.getTagValue(TagW.SOPInstanceUID);

                return getReferencedSOPInstanceUIDSet(seriesUID).contains(sopInstanceUID);
            }
        };
        return filter;
    }

}
