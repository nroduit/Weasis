/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.hp;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.util.LayeredEntries.Origin;
import org.weasis.core.ui.editor.image.HangingLayout;
import org.weasis.core.ui.editor.image.ViewState;
import org.weasis.dicom.codec.hp.HangingEngine.Candidate;
import org.weasis.dicom.codec.hp.HangingPlan.CellPlan;
import org.weasis.dicom.codec.hp.HangingPlan.Step;

/**
 * The reading steps of the built-in library beyond the first display: the window of a cell, the
 * comparison with a prior, the series left for further steps and the cine of a run.
 */
class BuiltInReadingStepsTest {

  private static final SeriesFeatureExtractor EXTRACTOR =
      new SeriesFeatureExtractor(new DerivationTables(), InterestProvider.NONE);

  private static List<Candidate> library() {
    return BuiltInProtocols.entries().stream()
        .map(e -> new Candidate(ProtocolJson.fromJson(e), Origin.BUILT_IN, true))
        .toList();
  }

  private static StudySeries seriesOf(Attributes... images) {
    return StudySeries.of(List.of(TestSeries.series(images)), EXTRACTOR).getFirst();
  }

  /** An axial CT stack of a study, of a body part. */
  private static StudySeries ct(String study, String date, String uid, String bodyPart) {
    Attributes[] slices = new Attributes[12];
    for (int i = 0; i < slices.length; i++) {
      Attributes a = TestSeries.ctSlice(i + 1, i * 2.5);
      a.setString(Tag.StudyInstanceUID, VR.UI, study);
      a.setString(Tag.StudyDate, VR.DA, date);
      a.setString(Tag.SeriesInstanceUID, VR.UI, uid);
      a.setString(Tag.BodyPartExamined, VR.CS, bodyPart);
      a.setString(Tag.FrameOfReferenceUID, VR.UI, study + ".9"); // NON-NLS
      slices[i] = a;
    }
    return seriesOf(slices);
  }

  /** One radiograph of a study: its view position and body part. */
  private static StudySeries radiograph(String study, String date, String uid, String view) {
    Attributes a = TestSeries.image("DX", UID.DigitalXRayImageStorageForPresentation, 1); // NON-NLS
    a.setString(Tag.StudyInstanceUID, VR.UI, study);
    a.setString(Tag.StudyDate, VR.DA, date);
    a.setString(Tag.SeriesInstanceUID, VR.UI, uid);
    a.setString(Tag.ViewPosition, VR.CS, view);
    a.setString(Tag.BodyPartExamined, VR.CS, "CHEST"); // NON-NLS
    return seriesOf(a);
  }

  /** A series of a modality no protocol names, or a multi-frame run. */
  private static StudySeries other(String modality, String sopClass, String uid, int frames) {
    Attributes a = TestSeries.image(modality, sopClass, 1);
    a.setString(Tag.SeriesInstanceUID, VR.UI, uid);
    if (frames > 1) {
      a.setInt(Tag.NumberOfFrames, VR.IS, frames);
    }
    return seriesOf(a);
  }

  private static ViewState pane(Step step, int cell) {
    return step.layout().cells().get(cell).panes().getFirst();
  }

  private static List<String> series(Step step) {
    return step.cells().stream().map(CellPlan::series).toList();
  }

  @Test
  void aChestCtWithOneSeriesShowsItTwiceWithItsOwnWindowAndTheLungWindow() {
    HangingPlan plan =
        HangingEngine.match(library(), List.of(ct("1.1", "20240110", "A", "CHEST"))) // NON-NLS
            .orElseThrow();
    Step first = plan.steps().getFirst();
    assertAll(
        () -> assertEquals("weasis.ct.chest", plan.protocolId()), // NON-NLS
        () -> assertEquals(List.of("A", "A"), series(first)), // NON-NLS
        () -> assertNull(pane(first, 0).view().preset(), "the image decides"),
        () -> assertEquals("weasis.ct.lung", pane(first, 1).view().preset()), // NON-NLS
        () -> assertEquals("stack", first.layout().synch()), // NON-NLS
        () -> assertEquals(2, plan.steps().size(), "then the reformats, no comparison"));
  }

  @Test
  void aHeadCtShowsTheBoneWindowBesideTheWindowOfTheImageAndAnotherRegionKeepsTheCtProtocol() {
    HangingPlan head =
        HangingEngine.match(library(), List.of(ct("1.1", "20240110", "A", "HEAD"))) // NON-NLS
            .orElseThrow();
    HangingPlan abdomen =
        HangingEngine.match(library(), List.of(ct("1.1", "20240110", "A", "ABDOMEN"))) // NON-NLS
            .orElseThrow();
    assertAll(
        () -> assertEquals("weasis.ct.head", head.protocolId()), // NON-NLS
        () -> assertNull(pane(head.steps().getFirst(), 0).view().preset(), "the image decides"),
        () -> assertEquals("weasis.ct.bone", pane(head.steps().getFirst(), 1).view().preset()),
        () -> assertEquals("weasis.ct", abdomen.protocolId()), // NON-NLS
        () -> assertEquals(1, abdomen.steps().getFirst().cells().size(), "one series, one cell"));
  }

  @Test
  void theComparisonStepOfACtTakesTheMostRecentPriorOfTheSameAnatomy() {
    List<StudySeries> current = List.of(ct("1.3", "20240110", "A", "CHEST")); // NON-NLS
    List<PriorStudy> priors =
        PriorStudy.of(
            current,
            List.of(
                List.of(ct("1.2", "20231005", "H", "HEAD")), // NON-NLS
                List.of(ct("1.1", "20230301", "P", "CHEST")))); // NON-NLS
    HangingPlan plan = HangingEngine.match(library(), current, priors).orElseThrow();
    Step comparison = plan.steps().getLast();
    assertAll(
        () -> assertEquals(3, plan.steps().size()),
        () -> assertEquals(List.of("A", "P"), series(comparison), "current left, prior right"),
        () -> assertEquals("1x2", comparison.layout().grid()), // NON-NLS
        () -> assertEquals(2, comparison.cells().get(1).prior(), "the head CT is skipped"),
        () -> assertEquals("1.1", comparison.cells().get(1).study()), // NON-NLS
        () -> assertEquals("stack", comparison.layout().synch())); // NON-NLS
  }

  @Test
  void aRadiographIsPairedWithTheSameViewOfThePriorAndAViewWithoutPriorHasNoStep() {
    List<StudySeries> current =
        List.of(
            radiograph("1.2", "20240110", "F", "PA"), // NON-NLS
            radiograph("1.2", "20240110", "L", "LL")); // NON-NLS
    List<PriorStudy> priors =
        PriorStudy.of(
            current, List.of(List.of(radiograph("1.1", "20230301", "PF", "AP")))); // NON-NLS
    HangingPlan alone = HangingEngine.match(library(), current).orElseThrow();
    HangingPlan compared = HangingEngine.match(library(), current, priors).orElseThrow();
    assertAll(
        () -> assertEquals("weasis.xr", alone.protocolId()), // NON-NLS
        () -> assertEquals(1, alone.steps().size(), "no prior, no comparison step"),
        () -> assertEquals(List.of("F", "L"), series(alone.steps().getFirst())), // NON-NLS
        () -> assertEquals(2, compared.steps().size(), "the lateral view has no prior"),
        () -> assertEquals(List.of("F", "PF"), series(compared.steps().get(1))), // NON-NLS
        () -> assertEquals(1, compared.steps().get(1).cells().get(1).prior()));
  }

  @Test
  void theSeriesBeyondTheGridOfTheLastResortContinueOverFurtherSteps() {
    List<StudySeries> study = new ArrayList<>();
    for (int i = 1; i <= 8; i++) {
      study.add(other("OT", UID.SecondaryCaptureImageStorage, "S" + i, 1)); // NON-NLS
    }
    HangingPlan plan = HangingEngine.match(library(), study).orElseThrow();
    List<String> shown = new ArrayList<>();
    plan.steps().forEach(step -> shown.addAll(series(step)));
    assertAll(
        () -> assertEquals("weasis.default", plan.protocolId()), // NON-NLS
        () -> assertEquals(2, plan.steps().size()),
        () -> assertEquals("2x3", plan.steps().get(0).layout().grid()), // NON-NLS
        () -> assertEquals(2, plan.steps().get(1).cells().size()),
        () -> assertEquals(8, shown.stream().distinct().count(), "every series once"));
  }

  @Test
  void aStepOfRunsStartsTheCineAndAStepOfStacksDoesNot() {
    HangingPlan runs =
        HangingEngine.match(
                library(),
                List.of(other("XA", UID.XRayAngiographicImageStorage, "R", 40))) // NON-NLS
            .orElseThrow();
    HangingPlan stack =
        HangingEngine.match(library(), List.of(ct("1.1", "20240110", "A", "ABDOMEN"))) // NON-NLS
            .orElseThrow();
    HangingLayout layout = runs.steps().getFirst().layout();
    assertAll(
        () -> assertEquals("weasis.xa", runs.protocolId()), // NON-NLS
        () -> assertTrue(layout.cine()),
        () -> assertTrue(HangingLayout.fromJson(layout.toJson()).cine(), "kept by the JSON form"),
        () -> assertFalse(stack.steps().getFirst().layout().cine()));
  }
}
