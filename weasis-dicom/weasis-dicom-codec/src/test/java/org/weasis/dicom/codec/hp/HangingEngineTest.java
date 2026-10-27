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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.util.LayeredEntries.Origin;
import org.weasis.core.ui.editor.image.HangingLayout;
import org.weasis.dicom.codec.hp.FeatureSelector.Extreme;
import org.weasis.dicom.codec.hp.FeatureSelector.Key;
import org.weasis.dicom.codec.hp.HangingEngine.Candidate;
import org.weasis.dicom.codec.hp.HangingEngine.ProtocolScore;
import org.weasis.dicom.codec.hp.HangingPlan.CellPlan;
import org.weasis.dicom.codec.hp.HangingPlan.Match;
import org.weasis.dicom.codec.hp.HangingPlan.Step;
import org.weasis.dicom.codec.hp.ProtocolJson.Document;
import org.weasis.dicom.codec.hp.WeasisExtensions.Derive;
import org.weasis.dicom.codec.hp.WeasisExtensions.DisplaySet;
import org.weasis.dicom.codec.hp.WeasisExtensions.ImageSet;
import org.weasis.dicom.codec.hp.WeasisExtensions.ViewerType;
import org.weasis.dicom.hp.HPComparatorFactory;
import org.weasis.dicom.hp.HPDefinition;
import org.weasis.dicom.hp.HPDisplaySet;
import org.weasis.dicom.hp.HPImageBox;
import org.weasis.dicom.hp.HPImageSet;
import org.weasis.dicom.hp.HPScrollingGroup;
import org.weasis.dicom.hp.HPSelectorFactory;
import org.weasis.dicom.hp.HPTimeBasedImageSet;
import org.weasis.dicom.hp.HangingProtocol;
import org.weasis.dicom.hp.RelativeTime;
import org.weasis.dicom.hp.enums.ImageBoxLayoutType;
import org.weasis.dicom.hp.enums.RelativeTimeUnits;
import org.weasis.dicom.hp.enums.SortingDirection;

class HangingEngineTest {

  private static final SeriesFeatureExtractor EXTRACTOR =
      new SeriesFeatureExtractor(new DerivationTables(), InterestProvider.NONE);

  /** A CT series of the study: kernel, slice thickness, image count, optionally coronal. */
  private static StudySeries ct(
      String uid, String kernel, double thickness, int images, boolean coronal) {
    List<Attributes> slices = new ArrayList<>();
    for (int i = 1; i <= images; i++) {
      Attributes a = TestSeries.ctSlice(i, i * thickness);
      a.setString(Tag.SeriesInstanceUID, VR.UI, uid);
      a.setString(Tag.ConvolutionKernel, VR.SH, kernel);
      a.setDouble(Tag.SliceThickness, VR.DS, thickness);
      a.setString(Tag.SeriesDescription, VR.LO, kernel + " " + thickness); // NON-NLS
      if (coronal) {
        a.setDouble(Tag.ImageOrientationPatient, VR.DS, 1, 0, 0, 0, 0, -1);
        a.setDouble(Tag.ImagePositionPatient, VR.DS, -100, i * thickness, 100);
      }
      slices.add(a);
    }
    return StudySeries.of(List.of(TestSeries.series(slices.toArray(Attributes[]::new))), EXTRACTOR)
        .getFirst();
  }

  private static StudySeries localizer(String uid) {
    Attributes a = TestSeries.ctSlice(1, 0);
    a.setString(Tag.SeriesInstanceUID, VR.UI, uid);
    a.setString(Tag.ImageType, VR.CS, "ORIGINAL", "PRIMARY", "LOCALIZER"); // NON-NLS
    return StudySeries.of(List.of(TestSeries.series(a)), EXTRACTOR).getFirst();
  }

  private static StudySeries mr(String uid) {
    Attributes a = TestSeries.image("MR", "1.2.840.10008.5.1.4.1.1.4", 1); // NON-NLS
    a.setString(Tag.SeriesInstanceUID, VR.UI, uid);
    return StudySeries.of(List.of(TestSeries.series(a)), EXTRACTOR).getFirst();
  }

  static final StudySeries SOFT_THICK = ct("A", "B30f", 2.5, 10, false); // NON-NLS
  static final StudySeries SOFT_THIN = ct("B", "B30f", 1.25, 20, false); // NON-NLS
  static final StudySeries LUNG = ct("C", "B70f", 1.25, 20, false); // NON-NLS
  static final StudySeries SCOUT = localizer("D"); // NON-NLS
  static final StudySeries CORONAL = ct("E", "B30f", 2.5, 10, true); // NON-NLS
  static final StudySeries MR = mr("F"); // NON-NLS
  static final List<StudySeries> STUDY = List.of(SOFT_THICK, SOFT_THIN, LUNG, SCOUT, CORONAL, MR);

  /** CT chest: soft tissue (thinnest, tiled) and lung side by side, then a prior step. */
  static Document chestCt(String partialData) {
    HangingProtocol hp = new HangingProtocol();
    hp.setHangingProtocolName("CT chest"); // NON-NLS
    hp.setHangingProtocolLevel("SITE"); // NON-NLS
    hp.setPartialDataDisplayHandling(partialData);
    HPDefinition def = new HPDefinition();
    def.setModality("CT"); // NON-NLS
    hp.addHangingProtocolDefinition(def);

    HPImageSet soft = hp.addNewImageSet(null);
    soft.addImageSetSelector(
        HPSelectorFactory.createAttributeValueSelector(
            "MATCH", null, Tag.Modality, 1, VR.CS, new String[] {"CT"})); // NON-NLS
    soft.addImageSetSelector(
        HPSelectorFactory.createAttributeValueSelector(
            "NO_MATCH", null, Tag.ImageType, 3, VR.CS, new String[] {"LOCALIZER"})); // NON-NLS
    HPTimeBasedImageSet now = new HPTimeBasedImageSet();
    now.setImageSetLabel("Current"); // NON-NLS
    now.setRelativeTime(new RelativeTime(0, 0, RelativeTimeUnits.DAYS));
    soft.addTimeBasedImageSet(now);

    HPImageSet lung = hp.addNewImageSet(null);
    lung.addImageSetSelector(
        HPSelectorFactory.createAttributeValueSelector(
            "MATCH", null, Tag.Modality, 1, VR.CS, new String[] {"CT"})); // NON-NLS
    HPTimeBasedImageSet nowToo = new HPTimeBasedImageSet();
    nowToo.setRelativeTime(new RelativeTime(0, 0, RelativeTimeUnits.DAYS));
    lung.addTimeBasedImageSet(nowToo);

    HPImageSet prior = hp.addNewImageSet(null);
    prior.addImageSetSelector(
        HPSelectorFactory.createAttributeValueSelector(
            "MATCH", null, Tag.Modality, 1, VR.CS, new String[] {"CT"})); // NON-NLS
    HPTimeBasedImageSet before = new HPTimeBasedImageSet();
    before.setImageSetLabel("Prior"); // NON-NLS
    before.setRelativeTime(new RelativeTime(1, 2, RelativeTimeUnits.YEARS));
    prior.addTimeBasedImageSet(before);

    HPDisplaySet softSet = displaySet(1, "Soft tissue", soft, 1); // NON-NLS
    HPImageBox box = new HPImageBox();
    box.setImageBoxLayoutType(ImageBoxLayoutType.TILED);
    box.setImageBoxTileHorizontalDimension(2);
    box.setImageBoxTileVerticalDimension(2);
    softSet.addImageBox(box);
    softSet.addSortingOperation(
        HPComparatorFactory.createSortAlongAxis(SortingDirection.DECREASING));
    softSet.setShowGrayscaleInverted("Y"); // NON-NLS
    hp.addDisplaySet(softSet);
    HPDisplaySet lungSet = displaySet(2, "Lung", lung, 1); // NON-NLS
    lungSet.addImageBox(new HPImageBox());
    hp.addDisplaySet(lungSet);
    HPDisplaySet priorSet = displaySet(3, "Prior", prior, 2); // NON-NLS
    priorSet.addImageBox(new HPImageBox());
    hp.addDisplaySet(priorSet);
    HPDisplaySet softAgain = displaySet(4, "Current soft", soft, 2); // NON-NLS
    softAgain.addImageBox(new HPImageBox());
    softAgain.setReformattingOperationType("MPR"); // NON-NLS
    hp.addDisplaySet(softAgain);
    HPScrollingGroup scrolling = new HPScrollingGroup();
    scrolling.addDisplaySet(softSet);
    scrolling.addDisplaySet(lungSet);
    scrolling.updateAttributes();
    hp.addScrollingGroup(scrolling);

    WeasisExtensions weasis =
        new WeasisExtensions(
            Map.of(
                1,
                new ImageSet(
                    "Axial soft tissue", // NON-NLS
                    List.of(
                        FeatureSelector.match(Key.ORIENTATION, "AXIAL"), // NON-NLS
                        FeatureSelector.prefer(Key.CT_KERNEL_FAMILY, 2, "SOFT"), // NON-NLS
                        FeatureSelector.prefer(Key.SLICE_THICKNESS, 1, Extreme.MIN))),
                2,
                new ImageSet(
                    "Axial lung", // NON-NLS
                    List.of(
                        FeatureSelector.match(Key.ORIENTATION, "AXIAL"), // NON-NLS
                        FeatureSelector.noMatch(Key.ROLE, "LOCALIZER"), // NON-NLS
                        FeatureSelector.prefer(Key.CT_KERNEL_FAMILY, 2, "LUNG")))), // NON-NLS
            Map.of(),
            Map.of(),
            null,
            null,
            null,
            null,
            null);
    return new Document("ct-chest", hp, weasis); // NON-NLS
  }

  private static HPDisplaySet displaySet(int number, String label, HPImageSet imageSet, int group) {
    HPDisplaySet set = new HPDisplaySet();
    set.setDisplaySetNumber(number);
    set.setDisplaySetLabel(label);
    set.setImageSet(imageSet);
    set.setDisplaySetPresentationGroup(group);
    return set;
  }

  @Test
  void theFiltersAreHardAndThePreferencesRankTheCandidates() {
    HangingPlan plan = HangingEngine.plan(chestCt("MAINTAIN_LAYOUT"), STUDY); // NON-NLS
    Step first = plan.steps().getFirst();
    CellPlan soft = first.cells().get(0);
    CellPlan lung = first.cells().get(1);
    assertAll(
        () -> assertEquals(2, plan.steps().size()),
        () -> assertEquals("B", soft.series(), "thinnest soft kernel"), // NON-NLS
        () -> assertEquals(Match.EXACT, soft.match()),
        () ->
            assertEquals(
                List.of("A", "C"),
                soft.alternatives().stream().map(a -> a.series()).toList(),
                "soft thick before lung thin"), // NON-NLS
        () -> assertEquals("C", lung.series()), // NON-NLS
        () -> assertEquals(Match.EXACT, lung.match()),
        () -> assertTrue(soft.reasons().contains("match orientation in [AXIAL]")), // NON-NLS
        () -> assertTrue(soft.reasons().contains("not ImageType in [LOCALIZER]")), // NON-NLS
        () ->
            assertTrue(
                soft.reasons().stream()
                    .anyMatch(
                        r -> r.startsWith("prefer smallest sliceThickness: 1.25"))), // NON-NLS
        () -> assertEquals(ViewerType.VIEWER_2D, soft.viewer()),
        () -> assertEquals(List.of(3), plan.unresolved().stream().map(u -> u.imageSet()).toList()),
        () -> assertEquals(HangingEngine.NO_PRIOR, plan.unresolved().getFirst().reason()));
  }

  @Test
  void theLayoutOfAStepCarriesTilingSortInversionAndSynchronization() {
    HangingPlan plan = HangingEngine.plan(chestCt("MAINTAIN_LAYOUT"), STUDY); // NON-NLS
    HangingLayout layout = plan.steps().getFirst().layout();
    HangingLayout.Cell soft = layout.cells().get(0);
    assertAll(
        () -> assertEquals("1x2", layout.grid()), // NON-NLS
        () -> assertEquals("stack", layout.synch(), "scrolling group"), // NON-NLS
        () -> assertEquals(1, layout.selected()),
        () -> assertEquals(new HangingLayout.Tiling(2, 2), soft.tiling()),
        () -> assertEquals("B", soft.seriesId().orElseThrow()), // NON-NLS
        () -> assertEquals("slice-position", soft.panes().getFirst().view().sort()), // NON-NLS
        () -> assertTrue(soft.panes().getFirst().view().inverseStack()),
        () -> assertTrue(soft.panes().getFirst().view().invert()),
        () -> assertNull(layout.cells().get(1).tiling()));
  }

  @Test
  void anEmptyDisplaySetKeepsItsCellOrIsRemovedDependingOnThePartialDataHandling() {
    Step maintain = HangingEngine.plan(chestCt("MAINTAIN_LAYOUT"), STUDY).steps().get(1); // NON-NLS
    Step adapt = HangingEngine.plan(chestCt("ADAPT_LAYOUT"), STUDY).steps().get(1); // NON-NLS
    assertAll(
        () -> assertEquals(2, maintain.cells().size()),
        () -> assertEquals(Match.EMPTY, maintain.cells().get(0).match()),
        () -> assertTrue(maintain.cells().get(0).isEmpty()),
        () ->
            assertEquals(
                List.of("expected: " + HangingEngine.NO_PRIOR),
                maintain.cells().get(0).reasons()), // NON-NLS
        () -> assertEquals("1x2", maintain.layout().grid()), // NON-NLS
        () -> assertTrue(maintain.layout().cells().get(0).seriesId().isEmpty()),
        () -> assertEquals("B", maintain.cells().get(1).series()), // NON-NLS
        () -> assertEquals(ViewerType.MPR, maintain.cells().get(1).viewer()),
        () -> assertEquals(1, adapt.cells().size()),
        () -> assertEquals("1x1", adapt.layout().grid()), // NON-NLS
        () -> assertEquals(0, adapt.cells().getFirst().cell()));
  }

  @Test
  void aSeriesAlreadyShownInTheStepIsSharedOnlyWhenNothingElseMatches() {
    HangingProtocol hp = new HangingProtocol();
    hp.setHangingProtocolName("Twice"); // NON-NLS
    HPImageSet set = hp.addNewImageSet(null);
    set.addImageSetSelector(
        HPSelectorFactory.createAttributeValueSelector(
            "MATCH", null, Tag.Modality, 1, VR.CS, new String[] {"CT"})); // NON-NLS
    HPDisplaySet one = displaySet(1, "one", set, 1); // NON-NLS
    one.addImageBox(new HPImageBox());
    HPDisplaySet two = displaySet(2, "two", set, 1); // NON-NLS
    two.addImageBox(new HPImageBox());
    hp.addDisplaySet(one);
    hp.addDisplaySet(two);
    Document document = new Document("twice", hp, WeasisExtensions.NONE); // NON-NLS

    Step twoSeries = HangingEngine.plan(document, List.of(SOFT_THIN, LUNG)).steps().getFirst();
    Step oneSeries = HangingEngine.plan(document, List.of(SOFT_THIN)).steps().getFirst();
    assertAll(
        () -> assertEquals("B", twoSeries.cells().get(0).series(), "more images first"), // NON-NLS
        () ->
            assertEquals(
                "C", twoSeries.cells().get(1).series(), "the other one, not shared"), // NON-NLS
        () -> assertEquals(Match.EXACT, twoSeries.cells().get(1).match()),
        () -> assertEquals("B", oneSeries.cells().get(1).series()), // NON-NLS
        () -> assertEquals(Match.SHARED, oneSeries.cells().get(1).match()),
        () -> assertTrue(oneSeries.cells().get(1).alternatives().isEmpty()));
  }

  @Test
  void protocolsAreSelectedByDefinitionScoredByResolutionAndLevelAndDisabledOnesSkipped() {
    Document ct = chestCt("MAINTAIN_LAYOUT"); // NON-NLS
    HangingProtocol mrProtocol = new HangingProtocol();
    mrProtocol.setHangingProtocolName("MR brain"); // NON-NLS
    HPDefinition mrDef = new HPDefinition();
    mrDef.setModality("XA"); // NON-NLS
    mrProtocol.addHangingProtocolDefinition(mrDef);
    HangingProtocol any = new HangingProtocol();
    any.setHangingProtocolName("Anything"); // NON-NLS
    List<Candidate> candidates =
        List.of(
            new Candidate(ct, Origin.BUILT_IN, true),
            new Candidate(ct, Origin.USER, true),
            new Candidate(
                new Document("xa", mrProtocol, WeasisExtensions.NONE),
                Origin.SITE,
                true), // NON-NLS
            new Candidate(
                new Document("any", any, WeasisExtensions.NONE), Origin.SITE, true), // NON-NLS
            new Candidate(ct, Origin.SITE, false));

    List<ProtocolScore> scores = HangingEngine.select(candidates, STUDY);
    HangingPlan best = HangingEngine.match(candidates, STUDY).orElseThrow();
    assertAll(
        () -> assertEquals(3, scores.size(), "XA does not apply, the disabled copy is skipped"),
        () -> assertEquals(Origin.USER, scores.get(0).candidate().origin(), "user layer first"),
        () -> assertEquals(Origin.BUILT_IN, scores.get(1).candidate().origin()),
        () ->
            assertEquals(
                "any",
                scores.get(2).document().id(),
                "no definition: lowest specificity"), // NON-NLS
        () -> assertTrue(scores.get(0).score() > scores.get(1).score()),
        () ->
            assertTrue(
                scores.get(0).reasons().contains("2 of 2 image sets resolve"),
                String.valueOf(scores.get(0).reasons())), // NON-NLS
        () -> assertEquals("ct-chest", best.protocolId()), // NON-NLS
        () -> assertEquals(scores.get(0).score(), best.score()),
        () ->
            assertTrue(
                HangingEngine.match(candidates, List.of(MR)).isPresent(), "the catch-all applies"),
        () ->
            assertEquals(
                "any",
                HangingEngine.match(candidates, List.of(MR))
                    .orElseThrow()
                    .protocolId())); // NON-NLS
  }

  /** One image set of every CT series, one display set repeated: a cell per series. */
  private static Document eachSeries(Integer repeat, String partialData) {
    HangingProtocol hp = new HangingProtocol();
    hp.setHangingProtocolName("Each"); // NON-NLS
    hp.setPartialDataDisplayHandling(partialData);
    HPImageSet set = hp.addNewImageSet(null);
    set.addImageSetSelector(
        HPSelectorFactory.createAttributeValueSelector(
            "MATCH", null, Tag.Modality, 1, VR.CS, new String[] {"CT"})); // NON-NLS
    HPDisplaySet one = displaySet(1, "each", set, 1); // NON-NLS
    one.addImageBox(new HPImageBox());
    hp.addDisplaySet(one);
    HPDisplaySet again = displaySet(2, "again", set, 1); // NON-NLS
    again.addImageBox(new HPImageBox());
    hp.addDisplaySet(again);
    WeasisExtensions weasis =
        new WeasisExtensions(
            Map.of(),
            Map.of(1, new WeasisExtensions.DisplaySet(null, null, List.of(), null, repeat)),
            Map.of(),
            null,
            null,
            null,
            null,
            jakarta.json.JsonValue.EMPTY_JSON_OBJECT);
    return new Document("each", hp, weasis); // NON-NLS
  }

  @Test
  void aRepeatedDisplaySetGivesOneCellPerRemainingSeriesAndTheGridGrows() {
    Step all = HangingEngine.plan(eachSeries(0, "ADAPT_LAYOUT"), STUDY).steps().getFirst();
    Step two = HangingEngine.plan(eachSeries(2, "ADAPT_LAYOUT"), STUDY).steps().getFirst();
    Step shared = HangingEngine.plan(eachSeries(0, "MAINTAIN_LAYOUT"), STUDY).steps().getFirst();
    assertAll(
        () -> assertEquals(5, all.cells().size(), "every CT series once, the second set dropped"),
        () ->
            assertEquals(
                5, all.cells().stream().map(CellPlan::series).distinct().count(), "no repeat"),
        () -> assertEquals("2x3", all.layout().grid()), // NON-NLS
        () -> assertEquals(5, all.layout().cells().size()),
        () -> assertEquals(1, all.cells().get(4).displaySet(), "all cells of the repeated set"),
        () -> assertEquals(3, two.cells().size(), "two of the repeated set, one of the other"),
        () -> assertEquals(6, shared.cells().size(), "MAINTAIN keeps the second set, shared"),
        () -> assertEquals(Match.SHARED, shared.cells().get(5).match()));
  }

  @Test
  void aProtocolWithoutDefinitionIsALastResortAndOneResolvingNothingIsSkipped() {
    HangingProtocol any = new HangingProtocol();
    any.setHangingProtocolName("Anything"); // NON-NLS
    HangingProtocol ctNothing = new HangingProtocol();
    ctNothing.setHangingProtocolName("CT with an impossible set"); // NON-NLS
    HPDefinition def = new HPDefinition();
    def.setModality("CT"); // NON-NLS
    ctNothing.addHangingProtocolDefinition(def);
    HPImageSet impossible = ctNothing.addNewImageSet(null);
    impossible.addImageSetSelector(
        HPSelectorFactory.createAttributeValueSelector(
            "MATCH", null, Tag.Modality, 1, VR.CS, new String[] {"NM"})); // NON-NLS
    HPTimeBasedImageSet now = new HPTimeBasedImageSet();
    now.setRelativeTime(new RelativeTime(0, 0, RelativeTimeUnits.DAYS));
    impossible.addTimeBasedImageSet(now);
    List<Candidate> candidates =
        List.of(
            new Candidate(new Document("any", any, WeasisExtensions.NONE), Origin.SITE, true),
            new Candidate(chestCt("MAINTAIN_LAYOUT"), Origin.BUILT_IN, true), // NON-NLS
            new Candidate(
                new Document("nothing", ctNothing, WeasisExtensions.NONE), Origin.SITE, true));
    List<ProtocolScore> scores = HangingEngine.select(candidates, STUDY);
    assertAll(
        () -> assertEquals(2, scores.size(), "the one hanging nothing is out"),
        () -> assertEquals("ct-chest", scores.get(0).document().id()), // NON-NLS
        () -> assertEquals("any", scores.get(1).document().id()), // NON-NLS
        () -> assertTrue(scores.get(1).score() <= 1.5, "a last resort scores below any match"));
  }

  /** The round trip through the protocol JSON keeps the image set numbers the plan relies on. */
  @Test
  void aProtocolReadBackFromJsonGivesTheSamePlan() {
    Document document = chestCt("MAINTAIN_LAYOUT"); // NON-NLS
    Document read = ProtocolJson.fromJson(ProtocolJson.toJson(document));
    HangingPlan expected = HangingEngine.plan(document, STUDY);
    HangingPlan actual = HangingEngine.plan(read, STUDY);
    assertAll(
        () ->
            assertEquals(
                List.of(1, 2, 3),
                read.protocol().getImageSets().stream().map(s -> s.getImageSetNumber()).toList()),
        () -> assertEquals(expected.toJson(), actual.toJson()),
        () -> assertEquals(1, actual.unresolved().size(), "the prior stays unresolved"));
  }

  @Test
  void thePlanIsReportedAsJsonWithCellsFromOne() {
    JsonObject json = HangingEngine.plan(chestCt("MAINTAIN_LAYOUT"), STUDY).toJson(); // NON-NLS
    JsonObject cell =
        json.getJsonArray("steps")
            .getJsonObject(0)
            .getJsonArray("cells")
            .getJsonObject(0); // NON-NLS
    assertAll(
        () -> assertEquals("ct-chest", json.getString("protocol")), // NON-NLS
        () -> assertEquals("SITE", json.getString("level")), // NON-NLS
        () -> assertEquals(1, cell.getInt("cell")), // NON-NLS
        () -> assertEquals("B", cell.getString("series")), // NON-NLS
        () -> assertEquals("exact", cell.getString("match")), // NON-NLS
        () -> assertEquals("viewer_2d", cell.getString("viewer")), // NON-NLS
        () -> assertEquals(2, cell.getJsonArray("alternatives").size()), // NON-NLS
        () ->
            assertEquals(
                "1x2",
                json.getJsonArray("steps")
                    .getJsonObject(0)
                    .getJsonObject("layout")
                    .getString("grid")), // NON-NLS
        () ->
            assertEquals(
                3, json.getJsonArray("unresolved").getJsonObject(0).getInt("imageSet"))); // NON-NLS
  }

  /** Pre and post contrast T1, and a third cell deriving their subtraction. */
  private static Document subtractionProtocol() {
    HangingProtocol hp = new HangingProtocol();
    hp.setHangingProtocolName("MR subtraction"); // NON-NLS
    hp.setHangingProtocolLevel("SITE"); // NON-NLS
    HPDefinition def = new HPDefinition();
    def.setModality("MR"); // NON-NLS
    hp.addHangingProtocolDefinition(def);
    HPImageSet pre = hp.addNewImageSet(null);
    pre.addImageSetSelector(
        HPSelectorFactory.createAttributeValueSelector(
            "MATCH", null, Tag.SeriesDescription, 1, VR.LO, new String[] {"pre"})); // NON-NLS
    HPImageSet post = hp.addNewImageSet(null);
    post.addImageSetSelector(
        HPSelectorFactory.createAttributeValueSelector(
            "MATCH", null, Tag.SeriesDescription, 1, VR.LO, new String[] {"post"})); // NON-NLS
    for (HPImageSet set : List.of(pre, post)) {
      HPTimeBasedImageSet now = new HPTimeBasedImageSet();
      now.setRelativeTime(new RelativeTime(0, 0, RelativeTimeUnits.DAYS));
      set.addTimeBasedImageSet(now);
    }
    hp.addDisplaySet(displaySet(1, "pre", pre, 1)); // NON-NLS
    hp.addDisplaySet(displaySet(2, "post", post, 1)); // NON-NLS
    hp.addDisplaySet(displaySet(3, "subtraction", post, 1)); // NON-NLS
    WeasisExtensions weasis =
        new WeasisExtensions(
            Map.of(),
            Map.of(
                3,
                new DisplaySet(
                    null, null, List.of(), null, null, null, new Derive("subtraction", 1, null))),
            Map.of(),
            null,
            null,
            null,
            null,
            null);
    return new Document("mr-sub", hp, weasis); // NON-NLS
  }

  private static StudySeries mr(String seriesUid, String description) {
    Attributes a = TestSeries.image("MR", "1.2.840.10008.5.1.4.1.1.4", 1); // NON-NLS
    a.setString(Tag.SeriesInstanceUID, VR.UI, seriesUid);
    a.setString(Tag.SeriesDescription, VR.LO, description);
    return StudySeries.of(List.of(TestSeries.series(a)), EXTRACTOR).getFirst();
  }

  @Test
  void aDisplaySetDerivingItsSeriesPlansTheSubtractionOfTheTwoMatchedSeries() {
    HangingPlan plan =
        HangingEngine.plan(
            subtractionProtocol(), List.of(mr("1.2.3.4", "pre"), mr("1.2.3.5", "post"))); // NON-NLS
    CellPlan cell = plan.steps().getFirst().cells().get(2);
    HangingPlan.Derivation derivation = cell.derivation();
    assertAll(
        () -> assertEquals("1.2.3.5", cell.series()),
        () -> assertEquals("subtraction", derivation.kind()),
        () -> assertEquals("1.2.3.5", derivation.source()),
        () -> assertEquals("1.2.3.4", derivation.mask()),
        () -> assertEquals(1, derivation.maskImageSet()),
        () -> assertTrue(cell.reasons().stream().anyMatch(r -> r.startsWith("derived"))),
        () -> assertNull(plan.steps().getFirst().cells().get(1).derivation()));
  }

  @Test
  void withoutAMaskTheCellShowsTheAcquiredSeriesAndSaysWhy() {
    HangingPlan plan =
        HangingEngine.plan(subtractionProtocol(), List.of(mr("1.2.3.5", "post"))); // NON-NLS
    CellPlan cell =
        plan.steps().getFirst().cells().stream()
            .filter(c -> c.displaySet() == 3)
            .findFirst()
            .orElseThrow();
    assertAll(
        () -> assertEquals("1.2.3.5", cell.series()),
        () -> assertNull(cell.derivation()),
        () -> assertTrue(cell.reasons().stream().anyMatch(r -> r.contains("skipped"))));
  }
}
