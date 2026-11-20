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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.dcm4che3.data.Tag;
import org.dcm4che3.util.UIDUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.api.util.ResourceUtil.ResourceIconPath;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.DicomWebOrigin;
import org.weasis.dicom.explorer.items.ExplorerActions.Offer;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ExplorerActionsTest {

  /** A study of three CT series. */
  private static final class Fixture {
    final DicomModel model = new DicomModel();
    final MediaSeriesGroup study;
    final List<DicomSeries> series = new ArrayList<>();

    Fixture() {
      MediaSeriesGroup patient =
          new MediaSeriesGroupNode(
              TagW.PatientPseudoUID, UIDUtils.createUID(), DicomModel.patient.tagView());
      String studyUID = UIDUtils.createUID();
      study =
          new MediaSeriesGroupNode(TagD.getUID(Level.STUDY), studyUID, DicomModel.study.tagView());
      study.setTag(TagD.get(Tag.StudyInstanceUID), studyUID);
      model.addHierarchyNode(MediaSeriesGroupNode.rootNode, patient);
      model.addHierarchyNode(patient, study);
      for (int i = 1; i <= 3; i++) {
        String uid = UIDUtils.createUID();
        DicomSeries s = new DicomSeries(uid);
        s.setTag(TagD.get(Tag.SeriesInstanceUID), uid);
        s.setTag(TagD.get(Tag.Modality), "CT");
        s.setTag(TagD.get(Tag.SeriesNumber), i);
        model.addHierarchyNode(study, s);
        series.add(s);
      }
    }
  }

  /** Offers one action that records the targets it was run on. */
  private static final class Recorder implements ExplorerActionProvider {
    final List<ExplorerTarget> ran = new ArrayList<>();

    @Override
    public String getId() {
      return "recorder"; // NON-NLS
    }

    @Override
    public String getTitle() {
      return "Recorder"; // NON-NLS
    }

    @Override
    public List<ExplorerAction> getActions(ExplorerTarget target) {
      return List.of(ExplorerAction.of("run", "Run", () -> ran.add(target))); // NON-NLS
    }

    @Override
    public Optional<SeriesBadge> getBadge(DicomSeries series) {
      Integer number = TagD.getTagValue(series, Tag.SeriesNumber, Integer.class);
      return number != null && number == 2
          ? Optional.of(new SeriesBadge(OtherIcon.AI_TASK, "AI")) // NON-NLS
          : Optional.empty();
    }
  }

  private static final class Failing implements ExplorerActionProvider {
    @Override
    public String getId() {
      return "failing"; // NON-NLS
    }

    @Override
    public String getTitle() {
      return "Failing"; // NON-NLS
    }

    @Override
    public List<ExplorerAction> getActions(ExplorerTarget target) {
      throw new IllegalStateException("broken plugin"); // NON-NLS
    }

    @Override
    public Optional<SeriesBadge> getBadge(DicomSeries series) {
      throw new IllegalStateException("broken plugin"); // NON-NLS
    }
  }

  @AfterEach
  void restore() {
    ExplorerActions.setSource(null);
  }

  @Test
  void a_part_of_the_study_is_offered_before_the_whole_study() {
    Fixture f = new Fixture();
    Recorder recorder = new Recorder();
    ExplorerActions.setSource(() -> List.of(recorder));

    List<Offer> offers =
        ExplorerActions.offers(f.model, f.study, List.of(f.series.get(2), f.series.get(0)));

    assertEquals(1, offers.size());
    Offer offer = offers.getFirst();
    ExplorerTarget part = offer.selection().orElseThrow().target();
    ExplorerTarget whole = offer.study().orElseThrow().target();
    assertFalse(part.wholeStudy());
    // in the order of the explorer, whatever the order of the clicks
    assertEquals(List.of(f.series.get(0), f.series.get(2)), part.series());
    assertEquals(3, part.studySeriesCount());
    assertTrue(whole.wholeStudy());
    assertEquals(f.series, whole.series());

    offer.selection().orElseThrow().actions().getFirst().run().run();
    assertSame(part, recorder.ran.getFirst());
  }

  @Test
  void a_selection_of_every_series_or_of_another_study_is_the_whole_study_only() {
    Fixture f = new Fixture();
    Fixture other = new Fixture();
    ExplorerActions.setSource(() -> List.of(new Recorder()));

    assertTrue(ExplorerActions.offers(f.model, f.study, f.series).getFirst().selection().isEmpty());
    assertTrue(
        ExplorerActions.offers(f.model, f.study, other.series).getFirst().selection().isEmpty());
    assertTrue(ExplorerActions.offers(f.model, f.study, List.of()).getFirst().study().isPresent());
  }

  @Test
  void a_provider_that_throws_is_left_out_and_the_others_still_answer() {
    Fixture f = new Fixture();
    ExplorerActions.setSource(() -> List.of(new Failing(), new Recorder()));

    List<Offer> offers = ExplorerActions.offers(f.model, f.study, List.of());
    assertEquals(List.of("recorder"), offers.stream().map(o -> o.provider().getId()).toList());

    Set<ResourceIconPath> icons =
        ExplorerActions.withBadgeIcons(Set.of(OtherIcon.KEY_IMAGE), f.series.get(1));
    assertEquals(Set.of(OtherIcon.KEY_IMAGE, OtherIcon.AI_TASK), icons);
    assertTrue(ExplorerActions.badges(f.series.get(0)).isEmpty());
  }

  @Test
  void without_a_provider_nothing_is_offered_and_the_icons_are_untouched() {
    Fixture f = new Fixture();
    ExplorerActions.setSource(List::of);
    Set<ResourceIconPath> icons = Set.of(OtherIcon.KEY_IMAGE);

    assertTrue(ExplorerActions.offers(f.model, f.study, f.series).isEmpty());
    assertSame(icons, ExplorerActions.withBadgeIcons(icons, f.series.get(0)));
  }

  @Test
  void the_target_names_its_series_and_the_server_the_study_came_from() {
    Fixture f = new Fixture();
    DicomWebOrigin.attach(f.study, new DicomWebOrigin("https://pacs/dicomweb/", null, "node1"));

    ExplorerTarget target = ExplorerTarget.ofStudy(f.model, f.study);

    assertEquals(3, target.seriesUIDs().size());
    assertEquals(TagD.getTagValue(f.study, Tag.StudyInstanceUID, String.class), target.studyUID());
    assertEquals("https://pacs/dicomweb", target.origin().orElseThrow().baseUrl());
  }
}
