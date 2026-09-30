/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.weasis.core.api.command.CommandModel;
import org.weasis.core.api.command.CommandResult;

/** Validation of the {@code acquire} model, without loading a patient context. */
class AcquireCommandsTest {

  private static CommandResult run(String verb, String... args) {
    return CommandModel.execute(AcquireManager.getInstance(), verb, args);
  }

  @Test
  void patientNeedsOneEncoding() {
    CommandResult none = run("patient");
    assertAll(
        () -> assertEquals(CommandResult.USAGE, none.status()),
        () -> assertTrue(none.error().contains("--xml"), none.error()));
  }

  /** The encodings each carry the same value, so two of them would silently keep only one. */
  @Test
  void patientTakesOneEncodingOnly() {
    CommandResult twoEncodings = run("patient", "--xml", "<patient/>", "--xml-gzip", "H4sIAAAA");
    CommandResult xmlAndUrl = run("patient", "--xml", "<patient/>", "--url", "https://h/p.xml");
    assertAll(
        () -> assertEquals(CommandResult.USAGE, twoEncodings.status()),
        () -> assertTrue(twoEncodings.error().contains("mutually exclusive"), twoEncodings.error()),
        () -> assertEquals(CommandResult.USAGE, xmlAndUrl.status()),
        () -> assertTrue(xmlAndUrl.error().contains("mutually exclusive"), xmlAndUrl.error()));
  }

  /** The 4.x form gave the value as a positional, which cannot be repeated on an option. */
  @Test
  void patientRefusesTheValueTwice() {
    CommandResult both = run("patient", "--xml", "<patient/>", "<other/>");
    assertAll(
        () -> assertEquals(CommandResult.USAGE, both.status()),
        () -> assertTrue(both.error().contains("once"), both.error()));
  }
}
