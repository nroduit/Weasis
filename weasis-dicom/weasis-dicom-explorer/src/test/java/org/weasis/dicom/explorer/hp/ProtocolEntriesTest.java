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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.hp.HangingProtocolDocuments;
import org.weasis.core.api.util.LayeredEntries.Origin;
import org.weasis.dicom.codec.hp.BuiltInProtocols;
import org.weasis.dicom.codec.hp.HangingEngine;
import org.weasis.dicom.explorer.hp.ProtocolEntries.Row;

class ProtocolEntriesTest {

  @BeforeEach
  void installLayers(@TempDir Path dir) {
    HangingProtocolDocuments.protocols().setBuiltIn(BuiltInProtocols.entries());
    HangingProtocolDocuments.protocols()
        .configure(Optional.empty(), dir.resolve(HangingProtocolDocuments.PROTOCOLS));
  }

  @AfterEach
  void clear(@TempDir Path dir) {
    HangingProtocolDocuments.protocols().configure(Optional.empty(), dir.resolve("x.json"));
  }

  @Test
  void aBuiltInIsDisabledThroughAUserCopyAndDuplicatedUnderANewId() throws IOException {
    List<Row> rows = ProtocolEntries.rows();
    Row ct = rows.stream().filter(r -> r.id().equals("weasis.ct")).findFirst().orElseThrow();
    assertAll(
        () -> assertEquals(BuiltInProtocols.entries().size(), rows.size()),
        () -> assertEquals(Origin.BUILT_IN, ct.origin()),
        () -> assertEquals("built-in", ct.layer()),
        () -> assertTrue(ct.enabled()));

    assertTrue(ProtocolEntries.setEnabled("weasis.ct", false));
    Row disabled =
        ProtocolEntries.rows().stream()
            .filter(r -> r.id().equals("weasis.ct"))
            .findFirst()
            .orElseThrow();
    assertAll(
        () -> assertFalse(disabled.enabled()),
        () -> assertEquals(Origin.USER, disabled.origin(), "a user copy carries the flag"),
        () -> assertEquals("CT", disabled.name().substring(0, 2)),
        () ->
            assertTrue(
                HangingEngine.candidates().stream()
                    .filter(c -> c.document().id().equals("weasis.ct"))
                    .noneMatch(HangingEngine.Candidate::enabled),
                "the engine sees it disabled"));
    assertTrue(ProtocolEntries.delete("weasis.ct"), "the user copy goes, the built-in is back");
    assertTrue(
        ProtocolEntries.rows().stream()
            .filter(r -> r.id().equals("weasis.ct"))
            .findFirst()
            .orElseThrow()
            .enabled());

    String copy = ProtocolEntries.duplicate("weasis.mr");
    String second = ProtocolEntries.duplicate("weasis.mr");
    assertAll(
        () -> assertEquals("weasis.mr.copy", copy),
        () -> assertEquals("weasis.mr.copy2", second),
        () -> assertEquals(BuiltInProtocols.entries().size() + 2, ProtocolEntries.rows().size()),
        () -> assertFalse(ProtocolEntries.setEnabled("nope", true)),
        () -> assertFalse(ProtocolEntries.delete("weasis.us"), "not a user entry"));
  }
}
