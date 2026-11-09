/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.provenance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.JsonObject;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.provenance.Provenance.Kind;

class ProvenanceTest {

  @Test
  void thePrecedenceIsUserExternalHeaderTableClassifierHeuristicComputed() {
    Provenance header = Provenance.header("(0018,0020)", "SE"); // NON-NLS
    Provenance table = Provenance.table("hp-mr-weighting", "TE 90"); // NON-NLS
    Provenance heuristic =
        Provenance.heuristic("desc.tokens.t2", "T2 in the description"); // NON-NLS
    Provenance strong = Provenance.classifier("seqclass", "FLAIR", 0.91, "1.2"); // NON-NLS
    Provenance weak = Provenance.classifier("seqclass", "FLAIR", 0.62, "1.2"); // NON-NLS
    assertAll(
        () -> assertTrue(Provenance.user("drag", null).outranks(header)), // NON-NLS
        () -> assertTrue(header.outranks(table)),
        () -> assertTrue(table.outranks(strong)),
        () -> assertTrue(strong.outranks(heuristic)),
        () -> assertTrue(strong.outranks(weak), "between classifiers the confident one"),
        () -> assertFalse(heuristic.outranks(table)),
        () -> assertTrue(heuristic.outranks(Provenance.computed("mpr:build", null, List.of()))),
        () -> assertEquals(2, Kind.HEADER.rank()),
        () -> assertEquals("classifier seqclass 91%: FLAIR", strong.describe()));
  }

  @Test
  void aProviderFillsAnUnknownValueOrContestsAKnownOne() {
    Sourced<String> fromHeader =
        new Sourced<>("T2", Provenance.header("(0018,0081)", "TE 90")); // NON-NLS
    Sourced<String> fromWeakClassifier =
        new Sourced<>("FLAIR", Provenance.classifier("seqclass", null, 0.62, "1.2")); // NON-NLS
    Sourced<String> fromStrongClassifier =
        new Sourced<>("FLAIR", Provenance.classifier("seqclass", null, 0.95, "1.2")); // NON-NLS
    Sourced<String> fromHeuristic =
        new Sourced<>("PD", Provenance.heuristic("desc.tokens.pd", null)); // NON-NLS

    Sourced<String> contested = fromHeader.merge(fromWeakClassifier, 0.8);
    Sourced<String> stillHeader = fromHeader.merge(fromStrongClassifier, 0.8);
    Sourced<String> filled = Sourced.<String>none().merge(fromStrongClassifier, 0.8);
    Sourced<String> notFilled = Sourced.<String>none().merge(fromWeakClassifier, 0.8);
    Sourced<String> replaced = fromHeuristic.merge(fromStrongClassifier, 0.8);

    assertAll(
        () -> assertEquals("T2", contested.value()),
        () -> assertEquals(1, contested.contests().size()),
        () -> assertEquals("FLAIR", contested.contests().getFirst().value()),
        () -> assertEquals("T2", stillHeader.value(), "a header is never overridden"),
        () -> assertEquals("FLAIR", filled.value()),
        () -> assertNull(notFilled.value(), "below the threshold a classifier only contests"),
        () -> assertEquals(1, notFilled.contests().size()),
        () -> assertEquals("FLAIR", replaced.value(), "a confident classifier beats a heuristic"),
        () -> assertEquals("PD", replaced.contests().getFirst().value()));
  }

  @Test
  void theJsonRoundTripsWithItsInputs() {
    Provenance computed =
        Provenance.computed(
            "mpr:reformat", // NON-NLS
            "coronal from series Z",
            List.of(Provenance.header("(0020,0037)", "Image Orientation (Patient)")));
    JsonObject json = computed.toJson();
    Provenance read = Provenance.fromJson(json);
    assertAll(
        () -> assertEquals("computed", json.getString("kind")),
        () -> assertEquals(1, json.getJsonArray("from").size()),
        () -> assertEquals(computed, read),
        () -> assertNull(Provenance.fromJson(null)),
        () -> assertNull(Provenance.fromJson(JsonObject.EMPTY_JSON_OBJECT)));
  }
}
