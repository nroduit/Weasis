/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.Set;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.util.UIDUtils;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.provenance.Provenance;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ObjectDescriptorTest {

  @Test
  void key_object_selection_is_described_with_its_title_and_references() throws URISyntaxException {
    String refSeries = UIDUtils.createUID();
    Attributes ko = base("KO", UID.KeyObjectSelectionDocumentStorage);
    conceptName(ko, "113000", "Of Interest");
    Attributes text = item(ko, Tag.ContentSequence);
    text.setString(Tag.RelationshipType, VR.CS, "CONTAINS");
    text.setString(Tag.ValueType, VR.CS, "TEXT");
    conceptName(text, "113012", "Key Object Description");
    text.setString(Tag.TextValue, VR.UT, "Suspicious nodule");
    evidence(ko, ko.getString(Tag.StudyInstanceUID), refSeries, 3);

    ObjectDescriptor d = ObjectDescriptor.describe(element(ko));

    assertAll(
        () -> assertEquals(ObjectKind.KEY_OBJECT, d.kind()),
        () -> assertEquals("KO", d.modality()),
        () -> assertEquals("Suspicious nodule", d.label()),
        () -> assertEquals(Set.of(refSeries), d.referencedSeriesUIDs()),
        () -> assertTrue(d.references(refSeries)),
        () -> assertEquals(3, d.referencedImages()),
        () -> assertEquals(LocalDateTime.of(2026, 1, 15, 10, 30), d.date()),
        () -> assertEquals("Acme Workstation (3.2)", d.creator()),
        () -> assertEquals(ko.getString(Tag.SeriesInstanceUID), d.seriesUID()),
        () -> assertEquals(ko.getString(Tag.SOPInstanceUID), d.sopInstanceUID()),
        () -> assertEquals(OtherIcon.KEY, d.icon()));
  }

  @Test
  void the_provenance_names_the_algorithm_of_a_segmentation_and_the_author_of_a_key_object()
      throws URISyntaxException {
    Attributes seg = base("SEG", UID.SegmentationStorage);
    Attributes segment = item(seg, Tag.SegmentSequence);
    segment.setString(Tag.SegmentAlgorithmType, VR.CS, "AUTOMATIC");
    segment.setString(Tag.SegmentAlgorithmName, VR.LO, "LungSeg");
    seg.setString(Tag.SoftwareVersions, VR.LO, "4.1");
    ObjectDescriptor automatic = ObjectDescriptor.describe(element(seg));

    Attributes ko = base("KO", UID.KeyObjectSelectionDocumentStorage);
    conceptName(ko, "113000", "Of Interest");
    ko.setString(Tag.ContentCreatorName, VR.PN, "Doe^Jane");
    ko.remove(Tag.ContributingEquipmentSequence);
    ObjectDescriptor personal = ObjectDescriptor.describe(element(ko));

    assertAll(
        () -> assertEquals(Provenance.Kind.EXTERNAL, automatic.provenance().kind()),
        () -> assertEquals("LungSeg", automatic.provenance().source()),
        () -> assertEquals("automatic segmentation", automatic.provenance().detail()),
        () -> assertEquals("4.1", automatic.provenance().version()),
        () -> assertEquals(Provenance.Kind.USER, personal.provenance().kind()),
        () -> assertEquals("Doe^Jane", personal.provenance().source()));
  }

  @Test
  void rejection_note_has_its_own_kind_and_falls_back_on_the_title() throws URISyntaxException {
    Attributes ko = base("KO", UID.KeyObjectSelectionDocumentStorage);
    conceptName(ko, "113001", "Rejected for Quality Reasons");
    evidence(ko, ko.getString(Tag.StudyInstanceUID), UIDUtils.createUID(), 1);

    DicomSpecialElement element = element(ko);
    ObjectDescriptor d = ObjectDescriptor.describe(element);

    assertAll(
        () -> assertTrue(element instanceof RejectedKOSpecialElement, element.getClass().getName()),
        () -> assertEquals(ObjectKind.REJECTION_NOTE, d.kind()),
        () -> assertEquals("Rejected for Quality Reasons", d.label()),
        () -> assertEquals(1, d.referencedImages()));
  }

  @Test
  void presentation_state_uses_the_content_label_and_the_contributing_equipment()
      throws URISyntaxException {
    String refSeries = UIDUtils.createUID();
    Attributes pr = base("PR", UID.GrayscaleSoftcopyPresentationStateStorage);
    pr.setString(Tag.ContentLabel, VR.CS, "CAD MARKS");
    pr.setString(Tag.ContentDescription, VR.LO, "Lung nodule marks");
    Attributes equipment = item(pr, Tag.ContributingEquipmentSequence);
    equipment.setString(Tag.Manufacturer, VR.LO, "AI Corp");
    equipment.setString(Tag.ManufacturerModelName, VR.LO, "Lung CAD");
    equipment.setString(Tag.SoftwareVersions, VR.LO, "2.1");
    Attributes series = item(pr, Tag.ReferencedSeriesSequence);
    series.setString(Tag.SeriesInstanceUID, VR.UI, refSeries);
    Sequence images = series.newSequence(Tag.ReferencedImageSequence, 2);
    for (int i = 0; i < 2; i++) {
      Attributes image = new Attributes();
      image.setString(Tag.ReferencedSOPClassUID, VR.UI, UID.CTImageStorage);
      image.setString(Tag.ReferencedSOPInstanceUID, VR.UI, UIDUtils.createUID());
      images.add(image);
    }

    ObjectDescriptor d = ObjectDescriptor.describe(element(pr));

    assertAll(
        () -> assertEquals(ObjectKind.PRESENTATION_STATE, d.kind()),
        () -> assertEquals("CAD MARKS", d.label()),
        () -> assertEquals("AI Corp Lung CAD (2.1)", d.creator()),
        () -> assertEquals(Set.of(refSeries), d.referencedSeriesUIDs()),
        () -> assertEquals(2, d.referencedImages()),
        () -> assertEquals(0, d.parts()),
        () -> assertEquals(OtherIcon.PRESENTATION, d.icon()));
  }

  @Test
  void segmentation_counts_its_segments_and_referenced_instances() throws URISyntaxException {
    String refSeries = UIDUtils.createUID();
    Attributes seg = base("SEG", UID.SegmentationStorage);
    seg.setString(Tag.SeriesDescription, VR.LO, "Liver segmentation");
    seg.setString(Tag.ContentLabel, VR.CS, "LIVER");
    Sequence segments = seg.newSequence(Tag.SegmentSequence, 3);
    for (int i = 1; i <= 3; i++) {
      Attributes s = new Attributes();
      s.setInt(Tag.SegmentNumber, VR.US, i);
      s.setString(Tag.SegmentLabel, VR.LO, "Segment " + i);
      segments.add(s);
    }
    Attributes series = item(seg, Tag.ReferencedSeriesSequence);
    series.setString(Tag.SeriesInstanceUID, VR.UI, refSeries);
    Sequence instances = series.newSequence(Tag.ReferencedInstanceSequence, 5);
    for (int i = 0; i < 5; i++) {
      Attributes instance = new Attributes();
      instance.setString(Tag.ReferencedSOPClassUID, VR.UI, UID.CTImageStorage);
      instance.setString(Tag.ReferencedSOPInstanceUID, VR.UI, UIDUtils.createUID());
      instances.add(instance);
    }

    ObjectDescriptor d = ObjectDescriptor.describe(element(seg));

    assertAll(
        () -> assertEquals(ObjectKind.SEGMENTATION, d.kind()),
        () -> assertEquals(3, d.parts()),
        () -> assertEquals(5, d.referencedImages()),
        () -> assertEquals(Set.of(refSeries), d.referencedSeriesUIDs()),
        () -> assertEquals("Liver segmentation", d.label()),
        () -> assertEquals(OtherIcon.SEGMENTATION, d.icon()));
  }

  @Test
  void creator_falls_back_on_the_station_name_and_is_null_when_nothing_is_known() {
    Attributes station = new Attributes();
    station.setString(Tag.StationName, VR.SH, "CT01");
    assertEquals("CT01", ObjectDescriptor.creatorOf(station));
    assertNull(ObjectDescriptor.creatorOf(new Attributes()));
    assertNull(ObjectDescriptor.creatorOf(null));
  }

  @Test
  void kinds_round_trip_through_their_command_identifiers() {
    for (ObjectKind kind : ObjectKind.values()) {
      assertEquals(kind, ObjectKind.fromId(kind.getId()));
      assertEquals(kind, ObjectKind.fromId(kind.getId().toUpperCase()));
    }
    assertNull(ObjectKind.fromId("nope"));
    assertNull(ObjectKind.fromId(null));
    assertEquals(ObjectKind.RT_DOSE, ObjectKind.fromModality("RTDOSE"));
    assertEquals(ObjectKind.OTHER, ObjectKind.fromModality("SR"));
    assertNotNull(ObjectKind.REJECTION_NOTE.getModality());
  }

  /**
   * Builds the element as the loader does, with the identifying tags the reader writes on it (the
   * reader itself needs the application context, which a unit test does not have).
   */
  static DicomSpecialElement element(Attributes dataset) throws URISyntaxException {
    DicomMediaIO reader = new DicomMediaIO(dataset);
    String modality = dataset.getString(Tag.Modality);
    DicomSpecialElement element =
        DicomMediaIO.DCM_ELEMENT_FACTORIES.get(modality).buildDicomSpecialElement(reader);
    tag(element, dataset);
    return element;
  }

  static void tag(DicomSpecialElement element, Attributes dataset) {
    for (int tag :
        new int[] {Tag.SOPInstanceUID, Tag.SeriesInstanceUID, Tag.StudyInstanceUID, Tag.Modality}) {
      element.setTag(TagD.get(tag), dataset.getString(tag));
    }
  }

  static Attributes base(String modality, String sopClass) {
    Attributes a = new Attributes();
    a.setString(Tag.SOPClassUID, VR.UI, sopClass);
    a.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
    a.setString(Tag.Modality, VR.CS, modality);
    a.setString(Tag.PatientID, VR.LO, "P-" + UIDUtils.createUID());
    a.setString(Tag.PatientName, VR.PN, "Doe^Jane");
    a.setString(Tag.StudyInstanceUID, VR.UI, UIDUtils.createUID());
    a.setString(Tag.SeriesInstanceUID, VR.UI, UIDUtils.createUID());
    a.setInt(Tag.SeriesNumber, VR.IS, 99);
    a.setInt(Tag.InstanceNumber, VR.IS, 1);
    a.setString(Tag.ContentDate, VR.DA, "20260115");
    a.setString(Tag.ContentTime, VR.TM, "103000");
    a.setString(Tag.Manufacturer, VR.LO, "Acme");
    a.setString(Tag.ManufacturerModelName, VR.LO, "Workstation");
    a.setString(Tag.SoftwareVersions, VR.LO, "3.2");
    return a;
  }

  static void conceptName(Attributes item, String value, String meaning) {
    Attributes code = item(item, Tag.ConceptNameCodeSequence);
    code.setString(Tag.CodeValue, VR.SH, value);
    code.setString(Tag.CodingSchemeDesignator, VR.SH, "DCM");
    code.setString(Tag.CodeMeaning, VR.LO, meaning);
  }

  /** One referenced study with one referenced series of {@code images} CT instances. */
  static void evidence(Attributes ko, String studyUID, String seriesUID, int images) {
    Attributes study = item(ko, Tag.CurrentRequestedProcedureEvidenceSequence);
    study.setString(Tag.StudyInstanceUID, VR.UI, studyUID);
    Attributes series = item(study, Tag.ReferencedSeriesSequence);
    series.setString(Tag.SeriesInstanceUID, VR.UI, seriesUID);
    Sequence sops = series.newSequence(Tag.ReferencedSOPSequence, images);
    for (int i = 0; i < images; i++) {
      Attributes sop = new Attributes();
      sop.setString(Tag.ReferencedSOPClassUID, VR.UI, UID.CTImageStorage);
      sop.setString(Tag.ReferencedSOPInstanceUID, VR.UI, UIDUtils.createUID());
      sops.add(sop);
    }
  }

  /** Adds a one-item sequence to {@code parent} and returns the item. */
  static Attributes item(Attributes parent, int tag) {
    Sequence sequence = parent.newSequence(tag, 1);
    Attributes item = new Attributes();
    sequence.add(item);
    return item;
  }
}
