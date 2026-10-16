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
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.JsonObject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.weasis.core.api.util.LayeredEntries.Origin;
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
        () -> assertEquals("weasis.ct", plan.protocolId()), // NON-NLS
        () -> assertEquals(2, first.cells().size()),
        () -> assertEquals("B", first.cells().get(0).series(), "thin soft kernel"), // NON-NLS
        () -> assertEquals("C", first.cells().get(1).series(), "lung kernel"), // NON-NLS
        () -> assertEquals(Match.PREFERRED, first.cells().get(1).match(), "no lung in the name"),
        () -> assertEquals("stack", first.layout().synch()), // NON-NLS
        () -> assertEquals(1, second.cells().size(), "the sagittal cell is dropped"),
        () -> assertEquals("E", second.cells().get(0).series())); // NON-NLS
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
