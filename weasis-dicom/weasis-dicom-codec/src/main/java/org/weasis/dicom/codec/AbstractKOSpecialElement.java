/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
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
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.api.util.ResourceUtil.ResourceIconPath;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.macro.HierarchicalSOPInstanceReference;
import org.weasis.dicom.macro.KODocumentModule;
import org.weasis.dicom.macro.SOPInstanceReferenceAndMAC;
import org.weasis.dicom.macro.SeriesAndInstanceReference;

public class AbstractKOSpecialElement extends HiddenSpecialElement {

  public static class Reference {
    private final String studyInstanceUID;
    private final String seriesInstanceUID;
    private final String sopInstanceUID;
    private final String sopClassUID;
    private final List<Integer> frameList;
    private final Integer instanceNumber;

    public Reference(DicomImageElement dicomImage) {
      studyInstanceUID = TagD.getTagValue(dicomImage, Tag.StudyInstanceUID, String.class);
      seriesInstanceUID = TagD.getTagValue(dicomImage, Tag.SeriesInstanceUID, String.class);
      sopInstanceUID = TagD.getTagValue(dicomImage, Tag.SOPInstanceUID, String.class);
      sopClassUID = TagD.getTagValue(dicomImage, Tag.SOPClassUID, String.class);
      instanceNumber = TagD.getTagValue(dicomImage, Tag.InstanceNumber, Integer.class);

      if (dicomImage.getMediaReader().getMediaElementNumber() > 1) {
        Integer frame = TagD.getTagValue(dicomImage, Tag.InstanceNumber, Integer.class);
        frameList = new ArrayList<>(1);
        frameList.add(frame);
      } else {
        frameList = Collections.emptyList();
      }
    }

    public Reference(
        String studyInstanceUID,
        String seriesInstanceUID,
        String sopInstanceUID,
        String sopClassUID,
        Integer instanceNumber,
        int[] frames) {
      this.studyInstanceUID = studyInstanceUID;
      this.seriesInstanceUID = seriesInstanceUID;
      this.sopInstanceUID = sopInstanceUID;
      this.sopClassUID = sopClassUID;
      this.instanceNumber = instanceNumber;
      this.frameList =
          frames == null
              ? Collections.emptyList()
              : Arrays.stream(frames).boxed().collect(Collectors.toList());
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

    public Integer getInstanceNumber() {
      return instanceNumber;
    }

    public List<Integer> getFrameList() {
      return frameList;
    }
  }

  protected Map<String, Map<String, SOPInstanceReferenceAndMAC>> sopInstanceReferenceMapBySeriesUID;
  protected Map<String, Map<String, SeriesAndInstanceReference>>
      seriesAndInstanceReferenceMapByStudyUID;
  protected Map<String, HierarchicalSOPInstanceReference>
      hierarchicalSOPInstanceReferenceByStudyUID;

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

  @Override
  public ResourceIconPath getIconPath() {
    return OtherIcon.KEY;
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
    if (hierarchicalSOPInstanceReferenceByStudyUID == null) {
      updateHierarchicalSOPInstanceReference();
    }
    return hierarchicalSOPInstanceReferenceByStudyUID.keySet();
  }

  public boolean containsStudyInstanceUIDReference(String studyInstanceUIDReference) {
    if (hierarchicalSOPInstanceReferenceByStudyUID == null) {
      updateHierarchicalSOPInstanceReference();
    }
    return hierarchicalSOPInstanceReferenceByStudyUID.containsKey(studyInstanceUIDReference);
  }

  public Set<String> getReferencedSeriesInstanceUIDSet(String studyUID) {
    if (seriesAndInstanceReferenceMapByStudyUID == null) {
      updateHierarchicalSOPInstanceReference();
    }
    Map<String, SeriesAndInstanceReference> seriesAndInstanceReferenceBySeriesUID =
        seriesAndInstanceReferenceMapByStudyUID.get(studyUID);
    return seriesAndInstanceReferenceBySeriesUID != null
        ? seriesAndInstanceReferenceMapByStudyUID.get(studyUID).keySet()
        : null;
  }

  public Set<String> getReferencedSeriesInstanceUIDSet() {
    if (sopInstanceReferenceMapBySeriesUID == null) {
      updateHierarchicalSOPInstanceReference();
    }

    return sopInstanceReferenceMapBySeriesUID.keySet();
  }

  public boolean containsSeriesInstanceUIDReference(String seriesInstanceUIDReference) {
    if (sopInstanceReferenceMapBySeriesUID == null) {
      updateHierarchicalSOPInstanceReference();
    }
    return sopInstanceReferenceMapBySeriesUID.containsKey(seriesInstanceUIDReference);
  }

  public Set<String> getReferencedSOPInstanceUIDSet() {
    if (sopInstanceReferenceMapBySeriesUID == null) {
      updateHierarchicalSOPInstanceReference();
    }

    Set<String> referencedSOPInstanceUIDSet = new LinkedHashSet<>();
    for (Map<String, SOPInstanceReferenceAndMAC> sopInstanceReference :
        sopInstanceReferenceMapBySeriesUID.values()) {
      referencedSOPInstanceUIDSet.addAll(sopInstanceReference.keySet());
    }
    return referencedSOPInstanceUIDSet;
  }

  public Set<String> getReferencedSOPInstanceUIDSet(String seriesUID) {
    if (seriesUID == null) {
      return getReferencedSOPInstanceUIDSet();
    }

    if (sopInstanceReferenceMapBySeriesUID == null) {
      updateHierarchicalSOPInstanceReference();
    }

    Map<String, SOPInstanceReferenceAndMAC> sopInstanceReferenceBySOPInstanceUID =
        sopInstanceReferenceMapBySeriesUID.get(seriesUID);
    return sopInstanceReferenceBySOPInstanceUID != null
        ? sopInstanceReferenceBySOPInstanceUID.keySet()
        : null;
  }

  public Map<String, SOPInstanceReferenceAndMAC> getReferencedSOPInstanceUIDObject(
      String seriesUID) {
    if (seriesUID == null) {
      return null;
    }

    if (sopInstanceReferenceMapBySeriesUID == null) {
      updateHierarchicalSOPInstanceReference();
    }
    return sopInstanceReferenceMapBySeriesUID.get(seriesUID);
  }

  public int getNumberSelectedImages() {
    return sopInstanceReferenceMapBySeriesUID.values().stream()
        .mapToInt(
            m ->
                m.values().stream()
                    .mapToInt(
                        v -> {
                          int[] frames = v.getReferencedFrameNumber();
                          if (frames == null || frames.length == 0) {
                            return 1;
                          } else {
                            return frames.length;
                          }
                        })
                    .sum())
        .sum();
  }

  public boolean containsSopInstanceUIDReference(
      String seriesInstanceUID, String sopInstanceUIDReference, Integer frame) {
    return isSopuidInReferencedSeriesSequence(
        getReferencedSOPInstanceUIDObject(seriesInstanceUID), sopInstanceUIDReference, frame);
  }

  public boolean isEmpty() {
    if (sopInstanceReferenceMapBySeriesUID == null) {
      updateHierarchicalSOPInstanceReference();
    }
    return sopInstanceReferenceMapBySeriesUID.isEmpty();
  }

  /**
   * Extract all the hierarchical SOP Instance References from the
   * CurrentRequestedProcedureEvidences of the root DicomObject into the dedicated Maps. These
   * collections are used to improve access performance for data queries.
   *
   * <p>This method should be called only once since any call to add/remove methods should keep in
   * sync with the CurrentRequestedProcedureEvidences of the root DicomObject
   */
  protected void updateHierarchicalSOPInstanceReference() {
    init();

    Attributes dcmItems = getMediaReader().getDicomObject();
    if (dcmItems != null) {
      Collection<HierarchicalSOPInstanceReference> referencedStudySequence =
          HierarchicalSOPInstanceReference.toHierarchicalSOPInstanceReferenceMacros(
              dcmItems.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence));
      boolean sopInstanceExist = false;

      for (HierarchicalSOPInstanceReference studyRef : referencedStudySequence) {
        Collection<SeriesAndInstanceReference> referencedSeriesSequence =
            studyRef.getReferencedSeries();
        String studyUID = studyRef.getStudyInstanceUID();

        for (SeriesAndInstanceReference seriesRef : referencedSeriesSequence) {
          Collection<SOPInstanceReferenceAndMAC> referencedSOPInstanceSequence =
              seriesRef.getReferencedSOPInstances();
          String seriesUID = seriesRef.getSeriesInstanceUID();

          for (SOPInstanceReferenceAndMAC sopRef : referencedSOPInstanceSequence) {
            String sopInstanceUID = sopRef.getReferencedSOPInstanceUID();

            if (!StringUtil.hasText(sopInstanceUID)) {
              continue;
            }

            Map<String, SOPInstanceReferenceAndMAC> sopInstanceReferenceBySOPInstanceUID =
                sopInstanceReferenceMapBySeriesUID.computeIfAbsent(
                    seriesUID, k -> new LinkedHashMap<>());

            sopInstanceReferenceBySOPInstanceUID.put(sopInstanceUID, sopRef);
            sopInstanceExist = true;
          }

          if (sopInstanceExist) {
            Map<String, SeriesAndInstanceReference> seriesAndInstanceReferenceBySeriesUID =
                seriesAndInstanceReferenceMapByStudyUID.computeIfAbsent(
                    studyUID, k -> new LinkedHashMap<>());

            seriesAndInstanceReferenceBySeriesUID.put(seriesUID, seriesRef);
          }
        }

        if (sopInstanceExist) {
          hierarchicalSOPInstanceReferenceByStudyUID.put(studyUID, studyRef);
        }
      }
    }
  }

  private void init() {
    if (hierarchicalSOPInstanceReferenceByStudyUID == null) {
      hierarchicalSOPInstanceReferenceByStudyUID = new LinkedHashMap<>();
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

    if (hierarchicalSOPInstanceReferenceByStudyUID == null) {
      updateHierarchicalSOPInstanceReference();
    }

    // Get the SOPInstanceReferenceMap for this seriesUID
    Map<String, SOPInstanceReferenceAndMAC> sopInstanceReferenceBySOPInstanceUID =
        sopInstanceReferenceMapBySeriesUID.get(ref.seriesInstanceUID);

    if (sopInstanceReferenceBySOPInstanceUID == null) {
      // the seriesUID is not referenced, create a new SOPInstanceReferenceMap
      sopInstanceReferenceBySOPInstanceUID = new LinkedHashMap<>();
      sopInstanceReferenceMapBySeriesUID.put(
          ref.seriesInstanceUID, sopInstanceReferenceBySOPInstanceUID);
    } else if (sopInstanceReferenceBySOPInstanceUID.containsKey(ref.sopInstanceUID)) {
      if (ref.frameList == null || ref.frameList.isEmpty()) {
        // the sopInstanceUID is already referenced, skip the job
        return false;
      } else {
        SOPInstanceReferenceAndMAC val =
            sopInstanceReferenceBySOPInstanceUID.get(ref.sopInstanceUID);
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
    referencedSOP.setInstanceNumber(ref.getInstanceNumber());
    referencedSOP.setReferencedFrameNumber(ref.frameList.stream().mapToInt(i -> i).toArray());
    sopInstanceReferenceBySOPInstanceUID.put(ref.sopInstanceUID, referencedSOP);

    // Get the SeriesAndInstanceReferenceMap for this studyUID
    Map<String, SeriesAndInstanceReference> seriesAndInstanceReferenceBySeriesUID =
        seriesAndInstanceReferenceMapByStudyUID.computeIfAbsent(
            ref.studyInstanceUID, k -> new LinkedHashMap<>());
    // the studyUID is not referenced, create a new one SeriesAndInstanceReferenceMap

    // Get the SeriesAndInstanceReference for this seriesUID
    SeriesAndInstanceReference referencedSeries =
        seriesAndInstanceReferenceBySeriesUID.get(ref.seriesInstanceUID);
    if (referencedSeries == null) {
      // the seriesUID is not referenced, create a new SeriesAndInstanceReference
      referencedSeries = new SeriesAndInstanceReference();
      referencedSeries.setSeriesInstanceUID(ref.seriesInstanceUID);
      seriesAndInstanceReferenceBySeriesUID.put(ref.seriesInstanceUID, referencedSeries);
    }

    // Update the current SeriesAndInstanceReference with the referencedSOPInstance Sequence
    referencedSeries.setReferencedSOPInstances(sopInstanceReferenceBySOPInstanceUID.values());

    // Get the HierarchicalSOPInstanceReference for this studyUID
    HierarchicalSOPInstanceReference hierarchicalDicom =
        hierarchicalSOPInstanceReferenceByStudyUID.get(ref.studyInstanceUID);

    if (hierarchicalDicom == null) {
      // the studyUID is not referenced, create a new one HierarchicalSOPInstanceReference
      hierarchicalDicom = new HierarchicalSOPInstanceReference();
      hierarchicalDicom.setStudyInstanceUID(ref.studyInstanceUID);
      hierarchicalSOPInstanceReferenceByStudyUID.put(ref.studyInstanceUID, hierarchicalDicom);
    }

    // Update the current HierarchicalSOPInstance with the referencedSeries Sequence
    hierarchicalDicom.setReferencedSeries(seriesAndInstanceReferenceBySeriesUID.values());

    // Update the CurrentRequestedProcedureEvidences for the root dcmItems
    Attributes dcmItems = getMediaReader().getDicomObject();

    new KODocumentModule(dcmItems)
        .setCurrentRequestedProcedureEvidences(hierarchicalSOPInstanceReferenceByStudyUID.values());

    return true;
  }

  public boolean removeKeyObject(DicomImageElement dicomImage) {
    return removeKeyObject(new Reference(dicomImage));
  }

  public boolean removeKeyObject(Reference ref) {

    if (hierarchicalSOPInstanceReferenceByStudyUID == null) {
      updateHierarchicalSOPInstanceReference();
    }

    // Get the SeriesAndInstanceReferenceMap for this studyUID
    Map<String, SeriesAndInstanceReference> seriesAndInstanceReferenceBySeriesUID =
        seriesAndInstanceReferenceMapByStudyUID.get(ref.studyInstanceUID);

    // Get the SOPInstanceReferenceMap for this seriesUID
    Map<String, SOPInstanceReferenceAndMAC> sopInstanceReferenceBySOPInstanceUID =
        sopInstanceReferenceMapBySeriesUID.get(ref.seriesInstanceUID);

    if (sopInstanceReferenceBySOPInstanceUID == null
        || seriesAndInstanceReferenceBySeriesUID == null
        || !sopInstanceReferenceBySOPInstanceUID.containsKey(ref.sopInstanceUID)) {
      // UIDs parameters were not referenced, skip the job
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
        hierarchicalSOPInstanceReferenceByStudyUID.remove(ref.studyInstanceUID);
      } else {
        // Get the HierarchicalSOPInstanceReference for this studyUID
        HierarchicalSOPInstanceReference hierarchicalDicom =
            hierarchicalSOPInstanceReferenceByStudyUID.get(ref.studyInstanceUID);

        // Update the current HierarchicalSOPInstance with the referencedSeries Sequence
        hierarchicalDicom.setReferencedSeries(seriesAndInstanceReferenceBySeriesUID.values());
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
    List<HierarchicalSOPInstanceReference> referencedStudies = null;

    if (!hierarchicalSOPInstanceReferenceByStudyUID.isEmpty()) {
      referencedStudies = new ArrayList<>(hierarchicalSOPInstanceReferenceByStudyUID.values());
    }

    new KODocumentModule(dcmItems).setCurrentRequestedProcedureEvidences(referencedStudies);

    return true;
  }

  public Filter<DicomImageElement> getSOPInstanceUIDFilter() {
    return new Filter<>() {
      @Override
      public boolean passes(DicomImageElement dicom) {
        String seriesInstanceUID = TagD.getTagValue(dicom, Tag.SeriesInstanceUID, String.class);
        if (dicom == null || seriesInstanceUID == null) {
          return false;
        }
        String sopInstanceUID = TagD.getTagValue(dicom, Tag.SOPInstanceUID, String.class);
        Integer frame = TagD.getTagValue(dicom, Tag.InstanceNumber, Integer.class);
        return isSopuidInReferencedSeriesSequence(
            getReferencedSOPInstanceUIDObject(seriesInstanceUID), sopInstanceUID, frame);
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
    result.addAll(frameList);
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

  /**
   * @param seriesUID the Series Instance UID
   * @param specialElements the list of DicomSpecialElement
   * @return the KOSpecialElement collection for the given parameters, if the referenced seriesUID
   *     is null all the KOSpecialElement from specialElements collection are returned. In any case
   *     all the KOSpecialElement that are writable will be added to the returned collection
   *     whatever is the seriesUID. These KO are part of the new created one's by users of the
   *     application
   */
  public static Collection<KOSpecialElement> getKoSpecialElements(
      Collection<KOSpecialElement> specialElements, String seriesUID) {

    if (specialElements == null) {
      return Collections.emptySet();
    }

    SortedSet<KOSpecialElement> koElementSet = null;
    for (KOSpecialElement koElement : specialElements) {
      Set<String> referencedSeriesInstanceUIDSet = koElement.getReferencedSeriesInstanceUIDSet();
      if (seriesUID == null
          || referencedSeriesInstanceUIDSet.contains(seriesUID)
          || koElement.getMediaReader().isEditableDicom()) {

        if (koElementSet == null) {
          koElementSet = new TreeSet<>(ORDER_BY_DATE);
        }
        koElementSet.add(koElement);
      }
    }
    return koElementSet == null ? Collections.emptySet() : koElementSet;
  }

  public static Collection<RejectedKOSpecialElement> getRejectionKoSpecialElements(
      Collection<RejectedKOSpecialElement> specialElements, String seriesUID) {

    if (specialElements == null) {
      return Collections.emptySet();
    }

    SortedSet<RejectedKOSpecialElement> sortedSet = null;
    for (RejectedKOSpecialElement element : specialElements) {
      Set<String> referencedSeriesInstanceUIDSet = element.getReferencedSeriesInstanceUIDSet();

      if (seriesUID == null
          || referencedSeriesInstanceUIDSet.contains(seriesUID)
          || element.getMediaReader().isEditableDicom()) {

        if (sortedSet == null) {
          sortedSet = new TreeSet<>(ORDER_BY_DATE);
        }
        sortedSet.add(element);
      }
    }
    return sortedSet == null ? Collections.emptySet() : sortedSet;
  }

  public static RejectedKOSpecialElement getRejectionKoSpecialElement(
      Collection<RejectedKOSpecialElement> specialElements,
      String seriesUID,
      String sopUID,
      Integer dicomFrameNumber) {

    if (specialElements == null) {
      return null;
    }
    List<RejectedKOSpecialElement> koList = null;

    for (RejectedKOSpecialElement koElement : specialElements) {
      if (isSopuidInReferencedSeriesSequence(
          koElement.getReferencedSOPInstanceUIDObject(seriesUID), sopUID, dicomFrameNumber)) {
        if (koList == null) {
          koList = new ArrayList<>();
        }
        koList.add(koElement);
      }
    }

    if (koList != null && !koList.isEmpty()) {
      // return the most recent Rejection Object
      koList.sort(ORDER_BY_DATE);
      return koList.getFirst();
    }
    return null;
  }
}
