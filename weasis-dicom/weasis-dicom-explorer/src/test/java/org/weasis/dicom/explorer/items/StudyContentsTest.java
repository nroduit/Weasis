/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.items;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;
import javax.swing.JProgressBar;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.util.UIDUtils;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.command.CommandModel;
import org.weasis.core.api.command.CommandResult;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.SeriesImporter;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.HiddenSeriesManager;
import org.weasis.dicom.codec.HiddenSpecialElement;
import org.weasis.dicom.codec.KOSpecialElement;
import org.weasis.dicom.codec.ObjectKind;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.explorer.DicomModel;

@DisplayNameGeneration(ReplaceUnderscores.class)
class StudyContentsTest {

  /** A patient with one study holding two CT series, plus a KO stored in the study. */
  private static final class Fixture {
    final DicomModel model = new DicomModel();
    final String studyUID = UIDUtils.createUID();
    final String ctUID = UIDUtils.createUID();
    final String ct2UID = UIDUtils.createUID();
    final KOSpecialElement ko;
    final MediaSeriesGroup patient;
    final MediaSeriesGroup study;
    final DicomSeries ct;

    Fixture() throws URISyntaxException {
      Attributes attrs = KoFixtures.koAttributes(studyUID, ctUID, 2);
      ko = element(attrs);
      // The pseudo UID is what the loader computes from the identity; any key works for the index
      String pseudoUID = "PSEUDO-" + UIDUtils.createUID(); // NON-NLS
      patient =
          new MediaSeriesGroupNode(TagW.PatientPseudoUID, pseudoUID, DicomModel.patient.tagView());
      study = studyNode(studyUID);
      model.addHierarchyNode(MediaSeriesGroupNode.rootNode, patient);
      model.addHierarchyNode(patient, study);
      ct = series(ctUID, "CT", 2, "Axial 1mm");
      model.addHierarchyNode(study, ct);
      model.addHierarchyNode(study, series(ct2UID, "CT", 3, "Coronal"));
      // The hidden KO series, as the loader creates it, must not be listed as a series
      model.addHierarchyNode(study, series(attrs.getString(Tag.SeriesInstanceUID), "KO", 99, null));
      register(ko, pseudoUID, ctUID);
    }
  }

  @Test
  void lists_the_image_series_and_leaves_the_hidden_series_out() throws URISyntaxException {
    Fixture f = new Fixture();
    StudyContents contents = StudyContents.of(f.model, f.study);

    assertAll(
        () -> assertEquals(f.studyUID, contents.studyUID()),
        () ->
            assertEquals(
                List.of(f.ctUID, f.ct2UID),
                contents.series().stream().map(SeriesItem::seriesUID).toList()),
        () -> assertEquals("CT", contents.series().getFirst().modality()),
        () -> assertEquals(2, contents.series().getFirst().seriesNumber()),
        () -> assertEquals("Axial 1mm", contents.series().getFirst().label()),
        () -> assertEquals(DownloadState.COMPLETE, contents.series().getFirst().downloadState()),
        () -> assertEquals(3, contents.items().size()));
  }

  @Test
  void lists_the_key_object_of_the_study_with_the_series_it_references() throws URISyntaxException {
    Fixture f = new Fixture();
    StudyContents contents = StudyContents.of(f.model, f.study);

    assertTrue(contents.hasObjects());
    ObjectItem item = contents.objects().getFirst();
    assertAll(
        () -> assertEquals(1, contents.objects().size()),
        () -> assertEquals(ObjectKind.KEY_OBJECT, item.kind()),
        () -> assertSame(f.ko, item.element()),
        () -> assertSame(f.study, item.study()),
        () -> assertEquals("Key images", item.label()),
        () -> assertTrue(item.references(f.ctUID)),
        () -> assertFalse(item.references(f.ct2UID)),
        () -> assertEquals(List.of(item), contents.objectsReferencing(f.ctUID)),
        () -> assertEquals(List.of(), contents.objectsReferencing(f.ct2UID)),
        () -> assertEquals(Map.of(ObjectKind.KEY_OBJECT, 1), contents.countByKind()),
        () -> assertEquals(List.of(item), contents.objects(ObjectKind.KEY_OBJECT)),
        () -> assertEquals(List.of(), contents.objects(ObjectKind.SEGMENTATION)));
  }

  @Test
  void an_object_stored_in_another_study_is_listed_where_its_references_are()
      throws URISyntaxException {
    Fixture f = new Fixture();
    // A second study of the same patient, holding a KO that references the CT of the first
    String otherStudyUID = UIDUtils.createUID();
    MediaSeriesGroup other = studyNode(otherStudyUID);
    f.model.addHierarchyNode(f.patient, other);
    f.model.addHierarchyNode(other, series(UIDUtils.createUID(), "MR", 1, "T2"));
    Attributes attrs = KoFixtures.koAttributes(otherStudyUID, f.ctUID, 1);
    attrs.setString(Tag.PatientID, VR.LO, patientId(f.ko));
    KOSpecialElement crossKo = element(attrs);
    register(crossKo, (String) f.patient.getTagValue(TagW.PatientPseudoUID), f.ctUID);

    StudyContents first = StudyContents.of(f.model, f.study);
    StudyContents second = StudyContents.of(f.model, other);

    assertAll(
        () -> assertEquals(2, first.objects().size(), "own KO and the cross-study one"),
        () -> assertTrue(first.objects().stream().anyMatch(o -> o.element() == crossKo)),
        () -> assertEquals(1, second.objects().size(), "stored there, references elsewhere"),
        () -> assertSame(crossKo, second.objects().getFirst().element()),
        () -> assertSame(other, second.objects().getFirst().study()));
  }

  @Test
  void series_follow_the_explorer_order_and_carry_their_split_index() throws URISyntaxException {
    Fixture f = new Fixture();
    DicomSeries first = series(UIDUtils.createUID(), "CT", 1, "Localizer");
    f.model.addHierarchyNode(f.study, first);
    DicomSeries split = series(UIDUtils.createUID(), "CT", 2, "Axial 1mm");
    split.setTag(TagD.SplitSeriesNumber, 2);
    f.model.addHierarchyNode(f.study, split);

    StudyContents contents = StudyContents.of(f.model, f.study);
    List<SeriesItem> items = contents.series();
    SeriesItem splitItem = items.stream().filter(SeriesItem::isSplit).findFirst().orElseThrow();
    assertAll(
        () ->
            assertEquals(
                List.of(1, 2, 2, 3), items.stream().map(SeriesItem::seriesNumber).toList()),
        () -> assertSame(first, items.getFirst().series()),
        () -> assertEquals(1, items.stream().filter(SeriesItem::isSplit).count()),
        () -> assertEquals(2, splitItem.splitNumber()),
        () ->
            assertEquals(
                String.valueOf(split.getTagValue(TagW.SubseriesInstanceUID)), splitItem.id()),
        () -> assertEquals(List.of(f.study), StudyContents.studiesOf(f.model, f.patient)),
        () -> assertEquals(List.of(f.patient), StudyContents.patientsOf(f.model)));
  }

  @Test
  void download_state_follows_the_series_loader() {
    DicomSeries series = series(UIDUtils.createUID(), "CT", 1, null);
    assertEquals(DownloadState.COMPLETE, SeriesItem.downloadStateOf(series));
    series.setSeriesLoader(loader(true));
    assertEquals(DownloadState.PENDING, SeriesItem.downloadStateOf(series));
    series.setSeriesLoader(loader(false));
    assertEquals(DownloadState.DOWNLOADING, SeriesItem.downloadStateOf(series));
  }

  @Test
  void objects_verb_lists_the_objects_of_a_study_as_json() throws URISyntaxException {
    Fixture f = new Fixture();
    Object commands = commandsOf(f.model);

    CommandResult all =
        CommandModel.execute(commands, "objects", new String[] {"--study", f.studyUID});
    CommandResult byKind =
        CommandModel.execute(
            commands, "objects", new String[] {"--series", f.ctUID, "--type", "ko"});
    CommandResult none =
        CommandModel.execute(commands, "objects", new String[] {"--series", f.ct2UID});
    CommandResult bad = CommandModel.execute(commands, "objects", new String[] {"--type", "nope"});

    assertAll(
        () -> assertEquals(CommandResult.OK, all.status(), all.error()),
        () -> assertTrue(all.body().contains("\"kind\":\"ko\""), all.body()),
        () -> assertTrue(all.body().contains("\"label\":\"Key images\""), all.body()),
        () ->
            assertTrue(
                all.body().contains("\"referencedSeries\":[\"" + f.ctUID + "\"]"), all.body()),
        () -> assertTrue(all.body().contains("\"seriesCount\":2"), all.body()),
        () -> assertTrue(byKind.body().contains("\"kind\":\"ko\""), byKind.body()),
        () -> assertEquals("{\"studies\":[]}", none.body().strip()),
        () -> assertFalse(bad.status() == CommandResult.OK, bad.body()));
  }

  // ---------------------------------------------------------------------------------------------

  private static Object commandsOf(DicomModel model) {
    return org.weasis.dicom.explorer.DicomModelCommandsAccess.commands(model);
  }

  /**
   * Builds the element as the loader does, with the identifying tags the reader writes on it (the
   * reader itself needs the application context, which a unit test does not have).
   */
  private static KOSpecialElement element(Attributes dataset) throws URISyntaxException {
    KOSpecialElement element = new KOSpecialElement(new DicomMediaIO(dataset));
    for (int tag :
        new int[] {Tag.SOPInstanceUID, Tag.SeriesInstanceUID, Tag.StudyInstanceUID, Tag.Modality}) {
      element.setTag(TagD.get(tag), dataset.getString(tag));
    }
    return element;
  }

  private static MediaSeriesGroup studyNode(String studyUID) {
    MediaSeriesGroup study =
        new MediaSeriesGroupNode(TagD.getUID(Level.STUDY), studyUID, DicomModel.study.tagView());
    study.setTag(TagD.get(Tag.StudyInstanceUID), studyUID);
    return study;
  }

  private static DicomSeries series(String uid, String modality, int number, String description) {
    DicomSeries series = new DicomSeries(uid);
    series.setTag(TagD.get(Tag.SeriesInstanceUID), uid);
    series.setTag(TagD.get(Tag.Modality), modality);
    series.setTag(TagD.get(Tag.SeriesNumber), number);
    if (description != null) {
      series.setTag(TagD.get(Tag.SeriesDescription), description);
    }
    return series;
  }

  private static String patientId(KOSpecialElement ko) {
    return ko.getMediaReader().getDicomObject().getString(Tag.PatientID);
  }

  /** Registers the element as {@code DicomModel.addSpecialModality} does when it loads one. */
  private static void register(HiddenSpecialElement element, String pseudoUID, String refSeries) {
    HiddenSeriesManager manager = HiddenSeriesManager.getInstance();
    String seriesUID = element.getMediaReader().getDicomObject().getString(Tag.SeriesInstanceUID);
    manager
        .series2Elements
        .computeIfAbsent(seriesUID, _ -> new CopyOnWriteArraySet<>())
        .add(element);
    manager
        .patient2Series
        .computeIfAbsent(pseudoUID, _ -> new CopyOnWriteArraySet<>())
        .add(seriesUID);
    manager
        .reference2Series
        .computeIfAbsent(refSeries, _ -> new CopyOnWriteArraySet<>())
        .add(seriesUID);
  }

  private static SeriesImporter loader(boolean stopped) {
    return new SeriesImporter() {
      @Override
      public boolean isStopped() {
        return stopped;
      }

      @Override
      public JProgressBar getProgressBar() {
        return null;
      }

      @Override
      public boolean stop() {
        return true;
      }

      @Override
      public void resume() {}

      @Override
      public void setPriority() {}
    };
  }
}
