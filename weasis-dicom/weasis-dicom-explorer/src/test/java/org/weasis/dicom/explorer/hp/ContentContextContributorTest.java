/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.hp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.dcm4che3.data.Tag;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.ContextFacts;
import org.weasis.core.ui.editor.Relevance;
import org.weasis.core.ui.editor.ViewContext;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomModel;

class ContentContextContributorTest {

  private static Map<String, Object> facts(ContentContextContributor contributor, DicomSeries s) {
    Map<String, Object> facts = new LinkedHashMap<>();
    contributor.contribute(null, s, facts);
    return facts;
  }

  private static ViewContext context(Map<String, Object> facts) {
    return new ViewContext(null, "2d", null, facts);
  }

  private static DicomModel model(MediaSeriesGroup study, DicomSeries... all) {
    DicomModel model = mock(DicomModel.class);
    when(model.getParent(any(), any())).thenReturn(null);
    when(model.getChildren(study)).thenReturn(new ArrayList<>(List.of(all)));
    for (DicomSeries s : all) {
      when(model.getParent(s, DicomModel.study)).thenReturn(study);
    }
    return model;
  }

  @Test
  void aSingleCtImageIsCalibratedButNeitherAVolumeNorTemporal() {
    ContentContextContributor contributor = new ContentContextContributor(Optional::empty);
    Map<String, Object> facts = facts(contributor, ExplorerFixtures.ctSeries("1.1", "B30f"));
    assertEquals(false, facts.get(ContextFacts.VOLUME));
    assertEquals("STATIC", facts.get(ContextFacts.TEMPORAL));
    assertEquals(1, facts.get(ContextFacts.IMAGES));
    assertEquals(false, facts.get(ContextFacts.SUV));
    assertEquals(true, facts.get(ContextFacts.CALIBRATED));
    assertEquals(false, facts.get(ContextFacts.FUSION), "no model: no partner");
    assertNull(facts.get(ContextFacts.objects("SEG")));

    ViewContext context = context(facts);
    assertFalse(Relevance.VOLUME.test(context));
    assertFalse(Relevance.TEMPORAL.test(context));
    assertFalse(Relevance.CALIBRATION.test(context));
  }

  @Test
  void aStackOfSlicesIsAVolumeAndARadiographAtTheDetectorIsNotCalibrated() {
    ContentContextContributor contributor = new ContentContextContributor(Optional::empty);
    Map<String, Object> volume =
        facts(contributor, ExplorerFixtures.ctSeries("2.1", "B30f", 3, "CT"));
    assertEquals(true, volume.get(ContextFacts.VOLUME));
    assertEquals(3, volume.get(ContextFacts.IMAGES));
    assertTrue(Relevance.VOLUME.test(context(volume)));

    Map<String, Object> radiograph = facts(contributor, ExplorerFixtures.crSeries("3.1"));
    assertEquals(false, radiograph.get(ContextFacts.CALIBRATED));
    assertTrue(Relevance.CALIBRATION.test(context(radiograph)));
    assertEquals(false, radiograph.get(ContextFacts.VOLUME));
  }

  @Test
  void aNonDicomSeriesAddsNoFact() {
    Map<String, Object> facts = new LinkedHashMap<>();
    new ContentContextContributor(Optional::empty).contribute(null, null, facts);
    assertTrue(facts.isEmpty());
    new ContentContextContributor(Optional::empty).contribute(null, new DicomSeries("4.1"), facts);
    assertTrue(facts.isEmpty(), "a series without an image");
  }

  @Test
  void theFusionPartnerIsASharedFrameOfReferenceOrAFunctionalAnatomicalPair() {
    MediaSeriesGroup study =
        new MediaSeriesGroupNode(TagD.get(Tag.StudyInstanceUID), "5", DicomModel.study.tagView());
    DicomSeries ct = ExplorerFixtures.ctSeries("5.1", "B30f", 3, "CT");
    DicomSeries pet = ExplorerFixtures.ctSeries("5.2", "PET", 3, "PT");
    DicomSeries bone = ExplorerFixtures.ctSeries("5.3", "B70f", 3, "CT");
    DicomSeries mr = ExplorerFixtures.ctSeries("5.4", "T1", 3, "MR");

    DicomModel withPet = model(study, ct, pet);
    assertEquals(
        true, facts(new ContentContextContributor(() -> Optional.of(withPet)), ct).get(ContextFacts.FUSION));

    DicomModel twoCt = model(study, ct, bone);
    assertEquals(
        false,
        facts(new ContentContextContributor(() -> Optional.of(twoCt)), ct).get(ContextFacts.FUSION),
        "two CT reconstructions with their own frames");
    bone.setTag(TagD.get(Tag.FrameOfReferenceUID), "5.1.frame"); // NON-NLS
    assertEquals(
        true,
        facts(new ContentContextContributor(() -> Optional.of(twoCt)), ct).get(ContextFacts.FUSION),
        "the same frame of reference");

    DicomModel ctMr = model(study, ct, mr);
    assertEquals(
        false,
        facts(new ContentContextContributor(() -> Optional.of(ctMr)), ct).get(ContextFacts.FUSION),
        "CT and MR are both anatomical");
    assertEquals(
        false, facts(new ContentContextContributor(() -> Optional.of(model(study, ct))), ct).get(ContextFacts.FUSION));
  }

  @Test
  void theObjectsReferencingTheSeriesAreCountedByModality() {
    TagW modality = TagD.get(Tag.Modality);
    DicomSpecialElement seg1 = mock(DicomSpecialElement.class);
    when(seg1.getTagValue(modality)).thenReturn("SEG");
    DicomSpecialElement seg2 = mock(DicomSpecialElement.class);
    when(seg2.getTagValue(modality)).thenReturn("SEG");
    DicomSpecialElement rt = mock(DicomSpecialElement.class);
    when(rt.getTagValue(modality)).thenReturn("RTSTRUCT");
    DicomSpecialElement unknown = mock(DicomSpecialElement.class);
    Map<String, Integer> counts =
        ContentContextContributor.countByModality(List.of(seg1, rt, seg2, unknown));
    assertEquals(Map.of("SEG", 2, "RTSTRUCT", 1), counts);

    Map<String, Object> facts = new LinkedHashMap<>();
    counts.forEach((m, n) -> facts.put(ContextFacts.objects(m), n));
    ViewContext context = context(facts);
    assertTrue(Relevance.objects("SEG").test(context));
    assertTrue(Relevance.objects("rtstruct").test(context));
    assertFalse(Relevance.objects("KO").test(context));
    assertEquals(Map.of(), ContentContextContributor.objectsOf(ExplorerFixtures.ctSeries("6.1", "B30f")));
  }
}
