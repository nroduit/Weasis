/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.codec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.codec.macro.HierachicalSOPInstanceReference;
import org.weasis.dicom.codec.macro.KODocumentModule;
import org.weasis.dicom.codec.macro.SOPInstanceReferenceAndMAC;
import org.weasis.dicom.codec.macro.SeriesAndInstanceReference;

public class AbstractKOSpecialElement extends DicomSpecialElement {

    public static class Reference {
        private final String studyInstanceUID;
        private final String seriesInstanceUID;
        private final String sopInstanceUID;
        private final String sopClassUID;
        private final List<Integer> frameList;

        public Reference(DicomImageElement dicomImage) {
            studyInstanceUID = TagD.getTagValue(dicomImage, Tag.StudyInstanceUID, String.class);
            seriesInstanceUID = TagD.getTagValue(dicomImage, Tag.SeriesInstanceUID, String.class);
            sopInstanceUID = TagD.getTagValue(dicomImage, Tag.SOPInstanceUID, String.class);
            sopClassUID = TagD.getTagValue(dicomImage, Tag.SOPClassUID, String.class);

            if (dicomImage.getMediaReader().getMediaElementNumber() > 1) {
                Integer frame = TagD.getTagValue(dicomImage, Tag.InstanceNumber, Integer.class);
                frameList = new ArrayList<>(1);
                frameList.add(frame);
            } else {
                frameList = Collections.emptyList();
            }
        }

        /**
         * @param studyInstanceUID
         * @param seriesInstanceUID
         * @param sopInstanceUID
         * @param sopClassUID
         * @param frames
         *            the list of DICOM Instance Number
         */
        public Reference(String studyInstanceUID, String seriesInstanceUID, String sopInstanceUID, String sopClassUID,
            int[] frames) {
            this.studyInstanceUID = studyInstanceUID;
            this.seriesInstanceUID = seriesInstanceUID;
            this.sopInstanceUID = sopInstanceUID;
            this.sopClassUID = sopClassUID;
            this.frameList =
                frames == null ? Collections.emptyList() : Arrays.stream(frames).boxed().collect(Collectors.toList());
        }

        public String getStudyInstanceUID() {
            return studyInstanceUID;
        }

        public String getSeriesInstanceUID() {
            return seriesInstanceUID;
        }

        public String getSopInstanceUID() {
            return sopInstanceUID;
        }

        public String getSopClassUID() {
            return sopClassUID;
        }

        public List<Integer> getFrameList() {
            return frameList;
        }
    }

    protected Map<String, Map<String, SOPInstanceReferenceAndMAC>> sopInstanceReferenceMapBySeriesUID;
    protected Map<String, Map<String, SeriesAndInstanceReference>> seriesAndInstanceReferenceMapByStudyUID;
    protected Map<String, HierachicalSOPInstanceReference> hierachicalSOPInstanceReferenceByStudyUID;

    public AbstractKOSpecialElement(DicomMediaIO mediaIO) {
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

        String name = getLabelWithoutPrefix();
        if (name != null) {
            buf.append(name);
        }
        label = buf.toString();
    }

    protected String getLabelWithoutPrefix() {
        Attributes dicom = ((DicomMediaIO) mediaIO).getDicomObject();
        Attributes item = dicom.getNestedDataset(Tag.ContentSequence);
        if (item != null) {
            return item.getString(Tag.TextValue);
        }
        return null;
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
        return seriesAndInstanceReferenceBySeriesUID != null
            ? seriesAndInstanceReferenceMapByStudyUID.get(studyUID).keySet() : null;
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

        Set<String> referencedSOPInstanceUIDSet = new LinkedHashSet<>();
        for (Map<String, SOPInstanceReferenceAndMAC> sopInstanceReference : sopInstanceReferenceMapBySeriesUID
            .values()) {
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

    public Map<String, SOPInstanceReferenceAndMAC> getReferencedSOPInstanceUIDObject(String seriesUID) {
        if (seriesUID == null) {
            return null;
        }

        if (sopInstanceReferenceMapBySeriesUID == null) {
            updateHierachicalSOPInstanceReference();
        }
        return sopInstanceReferenceMapBySeriesUID.get(seriesUID);
    }

    public boolean containsSopInstanceUIDReference(String seriesInstanceUID, String sopInstanceUIDReference,
        Integer frame) {
        return isSopuidInReferencedSeriesSequence(getReferencedSOPInstanceUIDObject(seriesInstanceUID),
            sopInstanceUIDReference, frame);
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
    protected void updateHierachicalSOPInstanceReference() {
        init();

        Attributes dcmItems = getMediaReader().getDicomObject();
        if (dcmItems != null) {
            Collection<HierachicalSOPInstanceReference> referencedStudySequence =
                HierachicalSOPInstanceReference.toHierachicalSOPInstanceReferenceMacros(
                    dcmItems.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence));
            boolean sopInstanceExist = false;

            for (HierachicalSOPInstanceReference studyRef : referencedStudySequence) {
                Collection<SeriesAndInstanceReference> referencedSeriesSequence = studyRef.getReferencedSeries();
                String studyUID = studyRef.getStudyInstanceUID();

                for (SeriesAndInstanceReference serieRef : referencedSeriesSequence) {
                    Collection<SOPInstanceReferenceAndMAC> referencedSOPInstanceSequence =
                        serieRef.getReferencedSOPInstances();
                    String seriesUID = serieRef.getSeriesInstanceUID();

                    for (SOPInstanceReferenceAndMAC sopRef : referencedSOPInstanceSequence) {
                        String sopInstanceUID = sopRef.getReferencedSOPInstanceUID();

                        if (!StringUtil.hasText(sopInstanceUID)) {
                            continue;
                        }

                        Map<String, SOPInstanceReferenceAndMAC> sopInstanceReferenceBySOPInstanceUID =
                            sopInstanceReferenceMapBySeriesUID.get(seriesUID);

                        if (sopInstanceReferenceBySOPInstanceUID == null) {
                            sopInstanceReferenceBySOPInstanceUID = new LinkedHashMap<>();
                            sopInstanceReferenceMapBySeriesUID.put(seriesUID, sopInstanceReferenceBySOPInstanceUID);
                        }
                        sopInstanceReferenceBySOPInstanceUID.put(sopInstanceUID, sopRef);
                        sopInstanceExist = true;
                    }

                    if (sopInstanceExist) {
                        Map<String, SeriesAndInstanceReference> seriesAndInstanceReferenceBySeriesUID =
                            seriesAndInstanceReferenceMapByStudyUID.get(studyUID);

                        if (seriesAndInstanceReferenceBySeriesUID == null) {
                            seriesAndInstanceReferenceBySeriesUID = new LinkedHashMap<>();
                            seriesAndInstanceReferenceMapByStudyUID.put(studyUID,
                                seriesAndInstanceReferenceBySeriesUID);
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

    private void init() {
        if (hierachicalSOPInstanceReferenceByStudyUID == null) {
            hierachicalSOPInstanceReferenceByStudyUID = new LinkedHashMap<>();
        }
        if (seriesAndInstanceReferenceMapByStudyUID == null) {
            seriesAndInstanceReferenceMapByStudyUID = new LinkedHashMap<>();
        }
        if (sopInstanceReferenceMapBySeriesUID == null) {
            sopInstanceReferenceMapBySeriesUID = new LinkedHashMap<>();
        }
    }

    // ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public boolean addKeyObject(DicomImageElement dicomImage) {
        return addKeyObject(new Reference(dicomImage));
    }

    public boolean addKeyObject(Reference ref) {

        if (hierachicalSOPInstanceReferenceByStudyUID == null) {
            updateHierachicalSOPInstanceReference();
        }

        // Get the SOPInstanceReferenceMap for this seriesUID
        Map<String, SOPInstanceReferenceAndMAC> sopInstanceReferenceBySOPInstanceUID =
            sopInstanceReferenceMapBySeriesUID.get(ref.seriesInstanceUID);

        if (sopInstanceReferenceBySOPInstanceUID == null) {
            // the seriesUID is not referenced, create a new SOPInstanceReferenceMap
            sopInstanceReferenceMapBySeriesUID.put(ref.seriesInstanceUID,
                sopInstanceReferenceBySOPInstanceUID = new LinkedHashMap<>());
        } else if (sopInstanceReferenceBySOPInstanceUID.containsKey(ref.sopInstanceUID)) {
            if (ref.frameList == null || ref.frameList.isEmpty()) {
                // the sopInstanceUID is already referenced, skip the job
                return false;
            } else {
                SOPInstanceReferenceAndMAC val = sopInstanceReferenceBySOPInstanceUID.get(ref.sopInstanceUID);
                int[] seqFrame = val == null ? null : val.getReferencedFrameNumber();
                if (seqFrame != null && seqFrame.length != 0) {
                    val.setReferencedFrameNumber(mergeFrames(seqFrame, ref.frameList));
                    // has updated all the frame, skip the job
                    return true;
                }
            }
        }

        // Create the new SOPInstanceReferenceAndMAC and add to the SOPInstanceReferenceMap
        SOPInstanceReferenceAndMAC referencedSOP = new SOPInstanceReferenceAndMAC();
        referencedSOP.setReferencedSOPInstanceUID(ref.sopInstanceUID);
        referencedSOP.setReferencedSOPClassUID(ref.sopClassUID);
        referencedSOP.setReferencedFrameNumber(ref.frameList.stream().mapToInt(i -> i).toArray());
        sopInstanceReferenceBySOPInstanceUID.put(ref.sopInstanceUID, referencedSOP);

        // Get the SeriesAndInstanceReferenceMap for this studyUID
        Map<String, SeriesAndInstanceReference> seriesAndInstanceReferenceBySeriesUID =
            seriesAndInstanceReferenceMapByStudyUID.get(ref.studyInstanceUID);
        if (seriesAndInstanceReferenceBySeriesUID == null) {
            // the studyUID is not referenced, create a new one SeriesAndInstanceReferenceMap
            seriesAndInstanceReferenceMapByStudyUID.put(ref.studyInstanceUID,
                seriesAndInstanceReferenceBySeriesUID = new LinkedHashMap<>());
        }

        // Get the SeriesAndInstanceReference for this seriesUID
        SeriesAndInstanceReference referencedSerie = seriesAndInstanceReferenceBySeriesUID.get(ref.seriesInstanceUID);
        if (referencedSerie == null) {
            // the seriesUID is not referenced, create a new SeriesAndInstanceReference
            referencedSerie = new SeriesAndInstanceReference();
            referencedSerie.setSeriesInstanceUID(ref.seriesInstanceUID);
            seriesAndInstanceReferenceBySeriesUID.put(ref.seriesInstanceUID, referencedSerie);
        }

        // Update the current SeriesAndInstanceReference with the referencedSOPInstance Sequence
        referencedSerie.setReferencedSOPInstances(sopInstanceReferenceBySOPInstanceUID.values());

        // Get the HierachicalSOPInstanceReference for this studyUID
        HierachicalSOPInstanceReference hierachicalDicom =
            hierachicalSOPInstanceReferenceByStudyUID.get(ref.studyInstanceUID);

        if (hierachicalDicom == null) {
            // the studyUID is not referenced, create a new one HierachicalSOPInstanceReference
            hierachicalDicom = new HierachicalSOPInstanceReference();
            hierachicalDicom.setStudyInstanceUID(ref.studyInstanceUID);
            hierachicalSOPInstanceReferenceByStudyUID.put(ref.studyInstanceUID, hierachicalDicom);
        }

        // Update the current HierachicalSOPInstance with the referencedSeries Sequence
        hierachicalDicom.setReferencedSeries(seriesAndInstanceReferenceBySeriesUID.values());

        // Update the CurrentRequestedProcedureEvidences for the root dcmItems
        Attributes dcmItems = getMediaReader().getDicomObject();

        new KODocumentModule(dcmItems)
            .setCurrentRequestedProcedureEvidences(hierachicalSOPInstanceReferenceByStudyUID.values());

        return true;
    }

    public boolean removeKeyObject(DicomImageElement dicomImage) {
        return removeKeyObject(new Reference(dicomImage));
    }

    public boolean removeKeyObject(Reference ref) {

        if (hierachicalSOPInstanceReferenceByStudyUID == null) {
            updateHierachicalSOPInstanceReference();
        }

        // Get the SeriesAndInstanceReferenceMap for this studyUID
        Map<String, SeriesAndInstanceReference> seriesAndInstanceReferenceBySeriesUID =
            seriesAndInstanceReferenceMapByStudyUID.get(ref.studyInstanceUID);

        // Get the SOPInstanceReferenceMap for this seriesUID
        Map<String, SOPInstanceReferenceAndMAC> sopInstanceReferenceBySOPInstanceUID =
            sopInstanceReferenceMapBySeriesUID.get(ref.seriesInstanceUID);

        if (sopInstanceReferenceBySOPInstanceUID == null || seriesAndInstanceReferenceBySeriesUID == null
            || !sopInstanceReferenceBySOPInstanceUID.containsKey(ref.sopInstanceUID)) {
            // UID's parameters were not referenced, skip the job
            return false;
        }

        if (ref.frameList != null && !ref.frameList.isEmpty()) {
            SOPInstanceReferenceAndMAC val = sopInstanceReferenceBySOPInstanceUID.get(ref.sopInstanceUID);
            int[] seqFrame = val == null ? null : val.getReferencedFrameNumber();
            if (seqFrame != null && seqFrame.length != 0) {
                int[] frames = removeFrames(seqFrame, ref.frameList);
                val.setReferencedFrameNumber(frames);
                if (frames.length > 0) {
                    // has updated all the frame, skip the job
                    return true;
                }
            }
        }
        sopInstanceReferenceBySOPInstanceUID.remove(ref.sopInstanceUID);

        if (sopInstanceReferenceBySOPInstanceUID.isEmpty()) {
            sopInstanceReferenceMapBySeriesUID.remove(ref.seriesInstanceUID);
            seriesAndInstanceReferenceBySeriesUID.remove(ref.seriesInstanceUID);

            if (seriesAndInstanceReferenceBySeriesUID.isEmpty()) {
                seriesAndInstanceReferenceMapByStudyUID.remove(ref.studyInstanceUID);
                hierachicalSOPInstanceReferenceByStudyUID.remove(ref.studyInstanceUID);
            } else {
                // Get the HierachicalSOPInstanceReference for this studyUID
                HierachicalSOPInstanceReference hierachicalDicom =
                    hierachicalSOPInstanceReferenceByStudyUID.get(ref.studyInstanceUID);

                // Update the current HierachicalSOPInstance with the referencedSeries Sequence
                hierachicalDicom.setReferencedSeries(seriesAndInstanceReferenceBySeriesUID.values());
            }

        } else {
            // Get the SeriesAndInstanceReference for this seriesUID
            SeriesAndInstanceReference referencedSeries =
                seriesAndInstanceReferenceBySeriesUID.get(ref.seriesInstanceUID);

            // Update the current SeriesAndInstanceReference with the referencedSOPInstance Sequence
            referencedSeries.setReferencedSOPInstances(sopInstanceReferenceBySOPInstanceUID.values());
        }

        // Update the CurrentRequestedProcedureEvidences for the root dcmItems
        Attributes dcmItems = getMediaReader().getDicomObject();
        List<HierachicalSOPInstanceReference> referencedStudies = null;

        if (!hierachicalSOPInstanceReferenceByStudyUID.isEmpty()) {
            referencedStudies = new ArrayList<>(hierachicalSOPInstanceReferenceByStudyUID.values());
        }

        new KODocumentModule(dcmItems).setCurrentRequestedProcedureEvidences(referencedStudies);

        return true;
    }

    public Filter<DicomImageElement> getSOPInstanceUIDFilter() {
        return new Filter<DicomImageElement>() {
            @Override
            public boolean passes(DicomImageElement dicom) {
                String seriesInstanceUID = TagD.getTagValue(dicom, Tag.SeriesInstanceUID, String.class);
                if (dicom == null || seriesInstanceUID == null) {
                    return false;
                }
                String sopInstanceUID = TagD.getTagValue(dicom, Tag.SOPInstanceUID, String.class);
                Integer frame = TagD.getTagValue(dicom, Tag.InstanceNumber, Integer.class);
                return isSopuidInReferencedSeriesSequence(getReferencedSOPInstanceUIDObject(seriesInstanceUID),
                    sopInstanceUID, frame);

            }
        };
    }

    public String getDocumentTitle() {
        Attributes dcmItems = getMediaReader().getDicomObject();
        if (dcmItems != null) {
            Attributes item = dcmItems.getNestedDataset(Tag.ConceptNameCodeSequence);
            if (item != null) {
                return item.getString(Tag.CodeMeaning, null);
            }
        }
        return null;
    }

    private static int[] mergeFrames(int[] arr1, List<Integer> frameList) {
        Set<Integer> result = new HashSet<>();
        for (int i : arr1) {
            result.add(i);
        }
        for (Integer integer : frameList) {
            result.add(integer);
        }
        return result.stream().mapToInt(i -> i).toArray();
    }

    private static int[] removeFrames(int[] arr1, List<Integer> frameList) {
        Set<Integer> result = new HashSet<>();
        for (int i : arr1) {
            result.add(i);
        }
        for (Integer integer : frameList) {
            result.remove(integer);
        }
        return result.stream().mapToInt(i -> i).toArray();
    }
}
