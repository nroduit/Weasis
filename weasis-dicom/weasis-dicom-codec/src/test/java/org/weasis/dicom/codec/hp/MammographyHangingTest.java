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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.util.LayeredEntries.Origin;
import org.weasis.core.ui.editor.image.Justification;
import org.weasis.core.ui.editor.image.ViewState.ViewPresentation;
import org.weasis.dicom.codec.hp.HangingEngine.Candidate;
import org.weasis.dicom.codec.hp.HangingEngine.ProtocolScore;
import org.weasis.dicom.codec.hp.HangingPlan.CellPlan;
import org.weasis.dicom.codec.hp.HangingPlan.Step;
import org.weasis.dicom.hp.AbstractPriorValue;
import org.weasis.dicom.hp.HPTimeBasedImageSet;
import org.weasis.dicom.hp.RelativeTime;
import org.weasis.dicom.hp.enums.RelativeTimeUnits;

/** The built-in mammography protocol on a current screening study and its priors. */
class MammographyHangingTest {

  private static final SeriesFeatureExtractor EXTRACTOR =
      new SeriesFeatureExtractor(new DerivationTables(), InterestProvider.NONE);

  /** One mammogram: laterality, view code and the stored Patient Orientation. */
  private static StudySeries view(
      String study, String date, String lat, String view, String row, String col) {
    Attributes a = TestSeries.image("MG", UID.DigitalMammographyXRayImageStorageForPresentation, 1);
    a.setString(Tag.StudyInstanceUID, VR.UI, study);
    a.setString(Tag.SeriesInstanceUID, VR.UI, study + "." + lat + view);
    a.setString(Tag.StudyDate, VR.DA, date);
    a.setString(Tag.StudyTime, VR.TM, "101500"); // NON-NLS
    a.setString(Tag.ImageLaterality, VR.CS, lat);
    a.setString(Tag.PatientOrientation, VR.CS, row, col);
    Attributes code = TestSeries.item(a, Tag.ViewCodeSequence);
    code.setString(Tag.CodeValue, VR.SH, "CC".equals(view) ? "R-10242" : "R-10226"); // NON-NLS
    code.setString(Tag.CodingSchemeDesignator, VR.SH, "SRT"); // NON-NLS
    return StudySeries.of(List.of(TestSeries.series(a)), EXTRACTOR).getFirst();
  }

  /** A four-view screening, the left views stored like the right ones (chest wall on the right). */
  private static List<StudySeries> screening(String study, String date, boolean leftAsRight) {
    List<StudySeries> series = new ArrayList<>();
    series.add(view(study, date, "R", "CC", "P", "L")); // NON-NLS
    series.add(view(study, date, "L", "CC", leftAsRight ? "P" : "A", "R")); // NON-NLS
    series.add(view(study, date, "R", "MLO", "P", "FL")); // NON-NLS
    series.add(view(study, date, "L", "MLO", leftAsRight ? "P" : "A", "FR")); // NON-NLS
    return series;
  }

  static final List<StudySeries> CURRENT = screening("1.1", "20070119", true); // NON-NLS
  static final List<StudySeries> PRIOR = screening("1.2", "20060118", false); // NON-NLS
  static final List<StudySeries> OLDEST =
      List.of(view("1.3", "20040301", "R", "CC", "P", "L")); // NON-NLS
  static final List<StudySeries> LATER = screening("1.4", "20080120", false); // NON-NLS

  private static List<Candidate> library() {
    return BuiltInProtocols.entries().stream()
        .map(e -> new Candidate(ProtocolJson.fromJson(e), Origin.BUILT_IN, true))
        .toList();
  }

  private static List<PriorStudy> priors() {
    return PriorStudy.of(CURRENT, List.of(LATER, OLDEST, PRIOR, CURRENT));
  }

  private static ViewPresentation viewOf(Step step, int cell) {
    return step.layout().cells().get(cell).panes().getFirst().view();
  }

  @Test
  void thePriorsAreTheOlderStudiesMostRecentFirst() {
    List<PriorStudy> priors = priors();
    assertAll(
        () -> assertEquals(List.of("1.2", "1.3"), priors.stream().map(PriorStudy::uid).toList()),
        () -> assertEquals(List.of(1, 2), priors.stream().map(PriorStudy::rank).toList()),
        () -> assertEquals(2006, priors.getFirst().date().getYear()));
  }

  @Test
  void theFourViewsAreMirroredChestWallsBackToBack() {
    HangingPlan plan = HangingEngine.match(library(), CURRENT, priors()).orElseThrow();
    Step first = plan.steps().getFirst();
    assertAll(
        () -> assertEquals("weasis.mg", plan.protocolId()), // NON-NLS
        () -> assertEquals("2x4", first.layout().grid(), "the overview with the prior"), // NON-NLS
        () ->
            assertEquals(
                List.of(
                    "RCC", // NON-NLS
                    "LCC", // NON-NLS
                    "RMLO", // NON-NLS
                    "LMLO", // NON-NLS
                    "Prior RCC", // NON-NLS
                    "Prior LCC", // NON-NLS
                    "Prior RMLO", // NON-NLS
                    "Prior LMLO"), // NON-NLS
                first.cells().stream().map(CellPlan::label).toList()),
        () ->
            assertEquals(
                List.of("RCC", "LCC", "RMLO", "LMLO"), // NON-NLS
                HangingEngine.match(library(), CURRENT)
                    .orElseThrow()
                    .steps()
                    .getFirst()
                    .cells()
                    .stream()
                    .map(CellPlan::label)
                    .toList(),
                "the four views alone without a prior"),
        () -> assertFalse(viewOf(first, 0).flip(), "RCC is stored P\\L"),
        () -> assertTrue(viewOf(first, 1).flip(), "LCC stored P\\R shows as A\\R"),
        () -> assertFalse(viewOf(first, 2).flip(), "RMLO is stored P\\FL"),
        () -> assertTrue(viewOf(first, 3).flip(), "LMLO stored P\\FR shows as A\\F"),
        () -> assertEquals(0, viewOf(first, 1).rotation()),
        () ->
            assertEquals(
                Justification.Horizontal.RIGHT,
                viewOf(first, 0).justify().horizontal(),
                "the right breast against the middle"),
        () -> assertEquals(Justification.Horizontal.LEFT, viewOf(first, 1).justify().horizontal()),
        () -> assertEquals("views", viewOf(first, 3).scaleGroup())); // NON-NLS
  }

  @Test
  void currentAndPriorShareTheScaleGroupOfTheirStep() {
    HangingPlan plan = HangingEngine.match(library(), CURRENT, priors()).orElseThrow();
    Step cc = plan.steps().get(1);
    assertAll(
        () ->
            assertEquals(
                List.of("views", "views", "views", "views"), // NON-NLS
                List.of(0, 1, 2, 3).stream().map(i -> viewOf(cc, i).scaleGroup()).toList()),
        () -> assertEquals(Justification.Horizontal.RIGHT, viewOf(cc, 2).justify().horizontal()));
  }

  @Test
  void theComparisonStepsShowTheMostRecentPriorUnderTheCurrentViews() {
    HangingPlan plan = HangingEngine.match(library(), CURRENT, priors()).orElseThrow();
    Step cc = plan.steps().get(1);
    Step mlo = plan.steps().get(2);
    assertAll(
        () -> assertEquals("2x2", cc.layout().grid()), // NON-NLS
        () ->
            assertEquals(
                List.of("1.1.RCC", "1.1.LCC", "1.2.RCC", "1.2.LCC"), // NON-NLS
                cc.cells().stream().map(CellPlan::series).toList()),
        () -> assertEquals(List.of(0, 0, 1, 1), cc.cells().stream().map(CellPlan::prior).toList()),
        () -> assertEquals("1.2", cc.cells().get(2).study()), // NON-NLS
        () -> assertFalse(viewOf(cc, 3).flip(), "the prior LCC is already stored A\\R"),
        () -> assertTrue(viewOf(cc, 1).flip()),
        () ->
            assertEquals(
                List.of("1.1.RMLO", "1.1.LMLO", "1.2.RMLO", "1.2.LMLO"), // NON-NLS
                mlo.cells().stream().map(CellPlan::series).toList()),
        () ->
            assertTrue(
                plan.unresolved().stream().noneMatch(u -> u.imageSet() >= 10),
                "every prior set resolves: " + plan.unresolved()), // NON-NLS
        () ->
            assertEquals(
                1,
                plan.toJson()
                    .getJsonArray("steps")
                    .getJsonObject(1)
                    .getJsonArray("cells")
                    .getJsonObject(2)
                    .getInt("prior"))); // NON-NLS
  }

  /** A prior whose four views are the images of one series, as some vendors store them. */
  private static List<StudySeries> oneSeriesPrior() {
    List<Attributes> images = new ArrayList<>();
    String[][] views = {
      {"L", "CC", "A", "R"}, {"L", "MLO", "A", "FR"}, {"R", "MLO", "P", "FL"}, {"R", "CC", "P", "L"}
    }; // NON-NLS
    for (int i = 0; i < views.length; i++) {
      Attributes a =
          TestSeries.image("MG", UID.DigitalMammographyXRayImageStorageForPresentation, i + 1);
      a.setString(Tag.StudyInstanceUID, VR.UI, "1.5"); // NON-NLS
      a.setString(Tag.SeriesInstanceUID, VR.UI, "1.5.1"); // NON-NLS
      a.setString(Tag.SOPInstanceUID, VR.UI, "1.5.1." + (i + 1)); // NON-NLS
      a.setString(Tag.StudyDate, VR.DA, "20060118"); // NON-NLS
      a.setString(Tag.ImageLaterality, VR.CS, views[i][0]);
      a.setString(Tag.PatientOrientation, VR.CS, views[i][2], views[i][3]);
      Attributes code = TestSeries.item(a, Tag.ViewCodeSequence);
      code.setString(Tag.CodeValue, VR.SH, "CC".equals(views[i][1]) ? "R-10242" : "R-10226");
      code.setString(Tag.CodingSchemeDesignator, VR.SH, "SRT"); // NON-NLS
      images.add(a);
    }
    return StudySeries.of(List.of(TestSeries.series(images.toArray(Attributes[]::new))), EXTRACTOR);
  }

  @Test
  void eachViewOfAMultiViewSeriesIsACandidateStartingOnItsImage() {
    List<StudySeries> prior = oneSeriesPrior();
    HangingPlan plan =
        HangingEngine.match(library(), CURRENT, PriorStudy.of(CURRENT, List.of(prior)))
            .orElseThrow();
    Step cc = plan.steps().get(1);
    var rcc = cc.layout().cells().get(2).panes().getFirst().data();
    var lcc = cc.layout().cells().get(3).panes().getFirst().data();
    assertAll(
        () -> assertEquals(4, prior.size(), "one entry per view"),
        () ->
            assertEquals(
                List.of("L CC", "L MLO", "R MLO", "R CC"), // NON-NLS
                prior.stream()
                    .map(s -> s.features().laterality() + " " + s.features().view())
                    .toList()),
        () -> assertEquals("1.5.1", cc.cells().get(2).series()), // NON-NLS
        () -> assertEquals("1.5.1", cc.cells().get(3).series()), // NON-NLS
        () -> assertEquals("1.5.1.4", rcc.instance(), "the prior RCC is the fourth image"),
        () -> assertEquals(4, rcc.image()),
        () -> assertEquals("1.5.1.1", lcc.instance()), // NON-NLS
        () -> assertEquals(1, prior.getFirst().features().imageCount()));
  }

  @Test
  void withoutPriorTheComparisonStepsKeepTheCurrentViewsOnly() {
    HangingPlan plan = HangingEngine.match(library(), CURRENT).orElseThrow();
    Step cc = plan.steps().get(1);
    assertAll(
        () -> assertEquals(2, cc.cells().size(), "ADAPT_LAYOUT drops the prior cells"),
        () -> assertEquals("1x2", cc.layout().grid()), // NON-NLS
        () ->
            assertTrue(
                plan.unresolved().stream()
                    .filter(u -> u.label() != null && u.label().startsWith("Prior")) // NON-NLS
                    .allMatch(u -> HangingEngine.NO_PRIOR.equals(u.reason()))));
  }

  @Test
  void availablePriorsRaiseTheScoreOfAComparisonProtocol() {
    ProtocolScore with = HangingEngine.select(library(), CURRENT, priors()).getFirst();
    ProtocolScore without = HangingEngine.select(library(), CURRENT).getFirst();
    assertAll(
        () -> assertTrue(with.score() > without.score()),
        () -> assertTrue(with.reasons().contains("4 of 4 prior image sets resolve")), // NON-NLS
        () -> assertTrue(without.reasons().contains("0 of 4 prior image sets resolve"))); // NON-NLS
  }

  @Test
  void aTimeBasedItemAcceptsPriorsByRankOrByWindow() {
    List<PriorStudy> priors = priors();
    PriorStudy recent = priors.get(0);
    PriorStudy oldest = priors.get(1);
    var now = PriorStudy.dateOf(CURRENT);

    HPTimeBasedImageSet first = new HPTimeBasedImageSet();
    first.setAbstractPriorValue(new AbstractPriorValue(1, 1));
    HPTimeBasedImageSet last = new HPTimeBasedImageSet();
    last.setAbstractPriorValue(new AbstractPriorValue(-1, -1));
    HPTimeBasedImageSet twoYears = new HPTimeBasedImageSet();
    twoYears.setRelativeTime(new RelativeTime(1, 2, RelativeTimeUnits.YEARS));
    HPTimeBasedImageSet days = new HPTimeBasedImageSet();
    days.setRelativeTime(new RelativeTime(1, 400, RelativeTimeUnits.DAYS));

    assertAll(
        () -> assertTrue(HangingEngine.accepts(first.getAttributes(), recent, 2, now)),
        () -> assertFalse(HangingEngine.accepts(first.getAttributes(), oldest, 2, now)),
        () -> assertTrue(HangingEngine.accepts(last.getAttributes(), oldest, 2, now)),
        () -> assertFalse(HangingEngine.accepts(last.getAttributes(), recent, 2, now)),
        () ->
            assertTrue(
                HangingEngine.accepts(twoYears.getAttributes(), recent, 2, now),
                "one year and one day ago: inside [1, 2] years"),
        () -> assertFalse(HangingEngine.accepts(twoYears.getAttributes(), oldest, 2, now)),
        () -> assertTrue(HangingEngine.accepts(days.getAttributes(), recent, 2, now)),
        () -> assertFalse(HangingEngine.accepts(days.getAttributes(), oldest, 2, now)));
  }
}
