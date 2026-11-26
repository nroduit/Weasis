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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.weasis.core.api.util.LayeredEntries.Origin;
import org.weasis.core.ui.editor.UiProfile;
import org.weasis.core.ui.editor.image.ViewState;
import org.weasis.dicom.codec.hp.HangingEngine.Candidate;
import org.weasis.dicom.codec.hp.HangingPlan.Match;
import org.weasis.dicom.codec.hp.ProtocolJson.Document;
import org.weasis.dicom.hp.HPDisplaySet;
import org.weasis.dicom.hp.HPImageSet;

class BuiltInProtocolsTest {

  private static List<Candidate> library() {
    return BuiltInProtocols.entries().stream()
        .map(e -> new Candidate(ProtocolJson.fromJson(e), Origin.BUILT_IN, true))
        .toList();
  }

  @Test
  void everyProtocolNamesABuiltInUiProfileAndEveryProfileParses() {
    Set<String> profiles = new HashSet<>();
    for (JsonObject entry : BuiltInProtocols.uiProfiles()) {
      UiProfile profile = UiProfile.fromJson(entry);
      assertTrue(profiles.add(profile.id()), "duplicate " + profile.id());
      assertTrue(profile.shell() != null && profile.shell().tools() != null, profile.id());
    }
    assertAll(
        library().stream()
            .map(
                c ->
                    (Executable)
                        () -> {
                          String id = c.document().weasis().uiProfile();
                          assertTrue(
                              id != null && profiles.contains(id),
                              c.document().id() + " names the UI profile " + id);
                        }));
  }

  @Test
  void everyEntryParsesHasAUniqueIdAndConsistentSets() {
    List<JsonObject> entries = BuiltInProtocols.entries();
    assertTrue(entries.size() >= 8, "the library covers the main imaging types");
    Set<String> ids = new HashSet<>();
    assertAll(
        entries.stream()
            .map(
                entry ->
                    (Executable)
                        () -> {
                          Document document = ProtocolJson.fromJson(entry);
                          assertTrue(document.id().startsWith("weasis."), document.id()); // NON-NLS
                          assertTrue(ids.add(document.id()), "duplicate " + document.id());
                          assertEquals(
                              "MANUFACTURER", // NON-NLS
                              document.protocol().getHangingProtocolLevel(),
                              document.id());
                          Set<Integer> sets = new HashSet<>();
                          for (HPImageSet set : document.protocol().getImageSets()) {
                            assertTrue(sets.add(set.getImageSetNumber()), document.id());
                            assertTrue(
                                document.weasis().imageSets().containsKey(set.getImageSetNumber()),
                                document.id() + ": image set without selectors");
                          }
                          for (HPDisplaySet ds : document.protocol().getDisplaySets()) {
                            assertTrue(
                                ds.getImageSet() != null
                                    && sets.contains(ds.getImageSet().getImageSetNumber()),
                                document.id() + ": display set " + ds.getDisplaySetNumber());
                          }
                        })
            .toList());
  }

  @Test
  void theCtProtocolHangsTheThinSoftSeriesAndTheLungSeriesThenTheReformat() {
    HangingPlan plan = HangingEngine.match(library(), HangingEngineTest.STUDY).orElseThrow();
    HangingPlan.Step first = plan.steps().get(0);
    HangingPlan.Step second = plan.steps().get(1);
    assertAll(
        () -> assertEquals("weasis.ct.chest", plan.protocolId(), "the study is a chest"),
        () -> assertEquals(2, plan.steps().size(), "no prior, no comparison step"),
        () -> assertEquals(2, first.cells().size()),
        () -> assertNull(pane(first, 0).view().preset(), "the window of the image"),
        () -> assertEquals("weasis.ct.lung", pane(first, 1).view().preset()), // NON-NLS
        () -> assertEquals("B", first.cells().get(0).series(), "thin soft kernel"), // NON-NLS
        () -> assertEquals("C", first.cells().get(1).series(), "lung kernel"), // NON-NLS
        () -> assertEquals(Match.PREFERRED, first.cells().get(1).match(), "no lung in the name"),
        () -> assertEquals("stack", first.layout().synch()), // NON-NLS
        () -> assertEquals(2, second.cells().size(), "the sagittal cell falls back to a reformat"),
        () -> assertEquals("E", second.cells().get(0).series()), // NON-NLS
        () -> assertNull(second.cells().get(0).derivation(), "the acquired coronal"),
        () -> assertEquals(Match.FALLBACK, second.cells().get(1).match()),
        () -> assertEquals("B", second.cells().get(1).series(), "reformatted from the thin soft"),
        () -> assertEquals("SAGITTAL", second.cells().get(1).derivation().plane())); // NON-NLS
  }

  @Test
  void theCtAxialStepStartsOnTheFirstImageAndTheCoronalAndSagittalOnTheMiddleOne() {
    HangingPlan plan = HangingEngine.match(library(), HangingEngineTest.STUDY).orElseThrow();
    HangingPlan.Step first = plan.steps().get(0);
    HangingPlan.Step second = plan.steps().get(1);
    assertAll(
        () -> assertEquals(0.0, relativePosition(first, 0), "axial soft tissue"),
        () -> assertEquals(0.0, relativePosition(first, 1), "axial lung or bone"),
        () -> assertEquals(0.5, relativePosition(second, 0), "acquired coronal"),
        () -> assertEquals(0.5, relativePosition(second, 1), "reformatted sagittal"));
  }

  @Test
  void theMrCellsStartOnTheFirstAxialImageAndOnTheMiddleSagittalImage() {
    StudySeries axial = mrStack("M1", "T2 TSE AX", 1, 0, 0, 0, 1, 0); // NON-NLS
    StudySeries sagittal = mrStack("M2", "T1 SE SAG", 0, 1, 0, 0, 0, -1); // NON-NLS
    HangingPlan plan = HangingEngine.match(library(), List.of(axial, sagittal)).orElseThrow();
    List<Executable> checks = new ArrayList<>();
    checks.add(() -> assertEquals("weasis.mr", plan.protocolId())); // NON-NLS
    Set<String> seen = new HashSet<>();
    for (HangingPlan.Step step : plan.steps()) {
      for (HangingPlan.CellPlan cell : step.cells()) {
        if (cell.series() == null) {
          continue;
        }
        seen.add(cell.series());
        double expected = "M2".equals(cell.series()) ? 0.5 : 0.0; // NON-NLS
        checks.add(
            () ->
                assertEquals(
                    expected,
                    relativePosition(step, cell.cell()),
                    cell.label() + " " + cell.series()));
      }
    }
    checks.add(() -> assertEquals(Set.of("M1", "M2"), seen, "both series are hung")); // NON-NLS
    assertAll(checks);
  }

  @Test
  void anAutoStartIsTheFirstImageOfAnAxialStackAndTheMiddleOfTheOtherPlanes() {
    WeasisExtensions.Start auto = WeasisExtensions.Start.AUTO;
    assertAll(
        () -> assertEquals(0.0, auto.relativeFor(SeriesFeatures.Orientation.AXIAL)),
        () -> assertEquals(0.5, auto.relativeFor(SeriesFeatures.Orientation.SAGITTAL)),
        () -> assertEquals(0.5, auto.relativeFor(SeriesFeatures.Orientation.CORONAL)),
        () -> assertEquals(0.5, auto.relativeFor(SeriesFeatures.Orientation.OBLIQUE)),
        () -> assertEquals(0.0, auto.relativeFor(SeriesFeatures.Orientation.MIXED)),
        () -> assertEquals(0.0, auto.relativeFor(null)),
        () -> assertEquals(0.5, WeasisExtensions.Start.MIDDLE.relativeFor(null)),
        () -> assertEquals(auto, WeasisExtensions.Start.fromJson(auto.toJson())),
        () ->
            assertEquals(
                new WeasisExtensions.Start(0.25),
                WeasisExtensions.Start.fromJson(new WeasisExtensions.Start(0.25).toJson())));
  }

  /** An MR stack of 12 slices in the plane of the two direction cosines. */
  private static StudySeries mrStack(String uid, String description, double... orientation) {
    double[] normal = {
      orientation[1] * orientation[5] - orientation[2] * orientation[4],
      orientation[2] * orientation[3] - orientation[0] * orientation[5],
      orientation[0] * orientation[4] - orientation[1] * orientation[3]
    };
    Attributes[] slices = new Attributes[12];
    for (int i = 0; i < slices.length; i++) {
      Attributes a = TestSeries.image("MR", "1.2.840.10008.5.1.4.1.1.4", i + 1); // NON-NLS
      a.setString(Tag.SeriesInstanceUID, VR.UI, uid);
      a.setString(Tag.SeriesDescription, VR.LO, description);
      a.setString(Tag.ImageType, VR.CS, "ORIGINAL", "PRIMARY", "M"); // NON-NLS
      a.setDouble(Tag.ImageOrientationPatient, VR.DS, orientation);
      a.setDouble(
          Tag.ImagePositionPatient, VR.DS, normal[0] * i * 4, normal[1] * i * 4, normal[2] * i * 4);
      a.setDouble(Tag.SliceThickness, VR.DS, 4);
      a.setDouble(Tag.PixelSpacing, VR.DS, 0.5, 0.5);
      a.setString(Tag.FrameOfReferenceUID, VR.UI, "1.2.3.4.6"); // NON-NLS
      slices[i] = a;
    }
    return StudySeries.of(List.of(TestSeries.series(slices)), new SeriesFeatureExtractor())
        .getFirst();
  }

  /** An axial stack of 12 slices of an emission modality on the frame of reference of the CT. */
  private static StudySeries emission(
      String modality, String sopClass, String uid, String corrected, String... imageType) {
    Attributes[] slices = new Attributes[12];
    for (int i = 0; i < slices.length; i++) {
      Attributes a = TestSeries.image(modality, sopClass, i + 1);
      a.setString(Tag.SeriesInstanceUID, VR.UI, uid);
      a.setString(Tag.ImageType, VR.CS, imageType);
      if (corrected != null) {
        a.setString(Tag.CorrectedImage, VR.CS, corrected);
      }
      a.setDouble(Tag.ImageOrientationPatient, VR.DS, 1, 0, 0, 0, 1, 0);
      a.setDouble(Tag.ImagePositionPatient, VR.DS, -200, -200, i * 4.0);
      a.setDouble(Tag.SliceThickness, VR.DS, 4);
      a.setDouble(Tag.PixelSpacing, VR.DS, 4, 4);
      a.setString(Tag.FrameOfReferenceUID, VR.UI, "1.2.3.4.5"); // NON-NLS
      slices[i] = a;
    }
    return StudySeries.of(List.of(TestSeries.series(slices)), new SeriesFeatureExtractor())
        .getFirst();
  }

  private static StudySeries spect(String uid, String corrected, String type) {
    return emission(
        "NM",
        UID.NuclearMedicineImageStorage,
        uid,
        corrected,
        "ORIGINAL",
        "PRIMARY",
        type,
        "EMISSION"); // NON-NLS
  }

  private static StudySeries pet(String uid, String corrected) {
    return emission(
        "PT",
        UID.PositronEmissionTomographyImageStorage,
        uid,
        corrected,
        "ORIGINAL",
        "PRIMARY"); // NON-NLS
  }

  private static ViewState pane(HangingPlan.Step step, int cell) {
    return step.layout().cells().get(cell).panes().getFirst();
  }

  @Test
  void aSpectWithCtHangsTheAxialTripleThenTheCtAndFusedPlanesThenTheNineCells() {
    List<StudySeries> study =
        List.of(
            HangingEngineTest.SOFT_THIN,
            HangingEngineTest.LUNG,
            HangingEngineTest.SCOUT,
            spect("P", null, "TOMO"), // NON-NLS
            spect("N", null, "RECON TOMO"), // NON-NLS
            spect("S", "ATTN", "RECON TOMO")); // NON-NLS
    HangingPlan plan = HangingEngine.match(library(), study).orElseThrow();
    HangingPlan.Step axial = plan.steps().get(0);
    HangingPlan.Step planes = plan.steps().get(1);
    HangingPlan.Step all = plan.steps().get(2);
    assertAll(
        () -> assertEquals("weasis.nm.ct", plan.protocolId()), // NON-NLS
        () -> assertTrue(plan.score() >= 7, "above any CT protocol: " + plan.score()),
        () -> assertEquals(3, plan.steps().size()),
        () -> assertEquals("2x2", axial.layout().grid()), // NON-NLS
        () -> assertEquals("B", axial.cells().get(0).series(), "soft kernel CT"), // NON-NLS
        () -> assertEquals("B", axial.cells().get(1).series(), "the fused cell keeps the CT"),
        () -> assertEquals("S", axial.cells().get(2).series(), "attenuation corrected"), // NON-NLS
        () -> assertNull(pane(axial, 0).blend()),
        () -> assertEquals("S", pane(axial, 1).blend().overlay()), // NON-NLS
        () ->
            assertEquals(
                "weasis.turbo", pane(axial, 1).blend().params().getString("colorMap")), // NON-NLS
        () -> assertEquals("S", axial.cells().get(3).series(), "the projection of the SPECT"),
        () -> assertEquals("CORONAL", axial.cells().get(3).derivation().plane()), // NON-NLS
        () -> assertEquals("MAX", pane(axial, 3).view().projection().type()), // NON-NLS
        () -> assertEquals(2000.0, pane(axial, 3).view().projection().thickness()),
        () -> assertNull(pane(axial, 3).blend(), "the projection is not fused"),
        () -> assertEquals("2x3", planes.layout().grid()), // NON-NLS
        () -> assertEquals(6, planes.cells().size()),
        () -> assertEquals("CORONAL", planes.cells().get(1).derivation().plane()), // NON-NLS
        () -> assertEquals("B", planes.cells().get(1).derivation().source()), // NON-NLS
        () -> assertEquals("SAGITTAL", planes.cells().get(5).derivation().plane()), // NON-NLS
        () -> assertEquals("S", pane(planes, 4).blend().overlay(), "fused coronal"), // NON-NLS
        () -> assertEquals("S", pane(planes, 5).blend().overlay(), "fused sagittal"), // NON-NLS
        () -> assertEquals("3x3", all.layout().grid()), // NON-NLS
        () -> assertEquals(9, all.cells().size()),
        () -> assertEquals("S", all.cells().get(4).derivation().source(), "SPECT reformat"),
        () -> assertNull(pane(all, 4).blend(), "the emission row is not fused"),
        () -> assertEquals(0.5, relativePosition(all, 7), "fused coronal starts in the middle"),
        () ->
            assertTrue(
                plan.steps().stream()
                    .flatMap(s -> s.cells().stream())
                    .noneMatch(c -> Set.of("P", "N", "D").contains(c.series())),
                "no projection, uncorrected reconstruction or localizer"));
  }

  @Test
  void aPetWithCtHangsTheHybridProtocolAndAPetAloneOrWithMrKeepsThePetProtocol() {
    StudySeries corrected = pet("AC", "ATTN"); // NON-NLS
    StudySeries uncorrected = pet("NAC", "DECY"); // NON-NLS
    HangingPlan hybrid =
        HangingEngine.match(library(), List.of(HangingEngineTest.SOFT_THIN, uncorrected, corrected))
            .orElseThrow();
    HangingPlan alone = HangingEngine.match(library(), List.of(corrected)).orElseThrow();
    HangingPlan withMr =
        HangingEngine.match(library(), List.of(corrected, HangingEngineTest.MR)).orElseThrow();
    assertAll(
        () -> assertEquals("weasis.pt.ct", hybrid.protocolId()), // NON-NLS
        () -> assertEquals("AC", hybrid.steps().getFirst().cells().get(2).series()), // NON-NLS
        () -> assertEquals("AC", pane(hybrid.steps().getFirst(), 1).blend().overlay()), // NON-NLS
        () ->
            assertEquals(
                "weasis.dicom.hot-iron", // NON-NLS
                pane(hybrid.steps().getFirst(), 1).blend().params().getString("colorMap")),
        () -> assertEquals("weasis.pt", alone.protocolId()), // NON-NLS
        () ->
            assertEquals(
                List.of("PET", "MIP"), // NON-NLS
                alone.steps().getFirst().cells().stream().map(HangingPlan.CellPlan::label).toList(),
                "a PET alone hangs beside its projection"),
        () -> assertEquals("weasis.pt", withMr.protocolId())); // NON-NLS
  }

  @Test
  void projectionsWithCtHangAsACtAndASpectAloneAsAnyStudy() {
    HangingPlan projections =
        HangingEngine.match(
                library(), List.of(HangingEngineTest.SOFT_THIN, spect("P", null, "TOMO")))
            .orElseThrow();
    HangingPlan alone =
        HangingEngine.match(library(), List.of(spect("S", "ATTN", "RECON TOMO"))).orElseThrow();
    assertAll(
        () -> assertEquals("weasis.ct.chest", projections.protocolId()), // NON-NLS
        () -> assertEquals("weasis.default", alone.protocolId())); // NON-NLS
  }

  private static double relativePosition(HangingPlan.Step step, int cell) {
    return step.layout().cells().get(cell).panes().getFirst().view().position().relative();
  }

  @Test
  void theDefaultProtocolIsTheLastResortWithOneCellPerSeries() {
    Attributes nm = TestSeries.image("NM", "1.2.840.10008.5.1.4.1.1.20", 1); // NON-NLS
    nm.setString(Tag.SeriesInstanceUID, VR.UI, "N"); // NON-NLS
    List<StudySeries> study =
        StudySeries.of(List.of(TestSeries.series(nm)), new SeriesFeatureExtractor());
    HangingPlan plan = HangingEngine.match(library(), study).orElseThrow();
    HangingPlan mr = HangingEngine.match(library(), List.of(HangingEngineTest.MR)).orElseThrow();
    assertAll(
        () -> assertEquals("weasis.default", plan.protocolId()), // NON-NLS
        () -> assertEquals(1, plan.steps().getFirst().cells().size()),
        () -> assertEquals("N", plan.steps().getFirst().cells().getFirst().series()), // NON-NLS
        () -> assertEquals("weasis.mr", mr.protocolId(), "an MR of unknown weighting"), // NON-NLS
        () -> assertEquals(1, mr.steps().size(), "the empty first step is skipped"),
        () -> assertEquals("F", mr.steps().getFirst().cells().getFirst().series())); // NON-NLS
  }
}
