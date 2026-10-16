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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.util.LayeredEntries.Origin;
import org.weasis.dicom.codec.hp.HangingEngine.Candidate;
import org.weasis.dicom.codec.hp.HangingPlan.Match;
import org.weasis.dicom.codec.hp.ValidationReport.Input;
import org.weasis.dicom.codec.hp.ValidationReport.ProtocolLine;
import org.weasis.dicom.codec.hp.ValidationReport.StudyLine;

class ValidationReportTest {

  @Test
  void theReportCountsCellsPerProtocolAndNamesTheProtocolOfEachStudy() {
    List<Candidate> candidates =
        List.of(
            new Candidate(
                HangingEngineTest.chestCt("MAINTAIN_LAYOUT"), Origin.SITE, true)); // NON-NLS
    List<Input> inputs =
        List.of(
            new Input("1", "chest", HangingEngineTest.STUDY), // NON-NLS
            new Input("2", "mr only", List.of(HangingEngineTest.MR))); // NON-NLS

    ValidationReport report = ValidationReport.run(candidates, inputs);
    ProtocolLine protocol = report.protocols().getFirst();
    StudyLine chest = report.studies().get(0);
    StudyLine mr = report.studies().get(1);
    JsonObject json = report.toJson();
    assertAll(
        () -> assertEquals(1, protocol.applies()),
        () -> assertEquals(1, protocol.chosen()),
        () -> assertEquals(3, protocol.cells().get(Match.EXACT), "soft, lung, soft again"),
        () -> assertEquals(1, protocol.cells().get(Match.EMPTY), "the prior cell"),
        () -> assertEquals(1, protocol.unresolved()),
        () -> assertEquals(0.75, protocol.filledShare()),
        () -> assertEquals(1, protocol.failures().size()),
        () -> assertEquals("ct-chest", chest.protocol()), // NON-NLS
        () -> assertNull(mr.protocol(), "no protocol applies to an MR study"),
        () ->
            assertEquals(
                1,
                json.getJsonArray("protocols")
                    .getJsonObject(0)
                    .getJsonObject("cells")
                    .getInt("empty")), // NON-NLS
        () -> assertEquals(2, json.getJsonArray("studies").size()), // NON-NLS
        () -> assertTrue(report.text().contains("mr only: no protocol applies")), // NON-NLS
        () -> assertTrue(report.text().startsWith("protocol"))); // NON-NLS
  }
}
