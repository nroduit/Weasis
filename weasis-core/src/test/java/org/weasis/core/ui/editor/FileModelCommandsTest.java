/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.weasis.core.api.command.CommandModel;
import org.weasis.core.api.command.CommandResult;

/** Validation and result of the {@code image} verbs, on the model the application really fills. */
class FileModelCommandsTest {

  private static CommandResult run(String verb, String... args) {
    return CommandModel.execute(new FileModel(), verb, args);
  }

  @Test
  void closeReportsWhatItClosedAndRefusesUnknownHandles() {
    CommandResult nothing = run("close");
    CommandResult unknown = run("close", "--group", "nope");
    CommandResult unknownSeries = run("close", "--series", "nope");
    assertAll(
        () -> assertEquals(CommandResult.USAGE, nothing.status()),
        () -> assertEquals(CommandResult.ERROR, unknown.status()),
        () -> assertTrue(unknown.error().contains("Nothing closed"), unknown.error()),
        () -> assertEquals(CommandResult.ERROR, unknownSeries.status()),
        () -> assertTrue(unknownSeries.error().contains("nope"), unknownSeries.error()));
  }

  @Test
  void openingAnUnreadableFileIsReportedInsteadOfIgnored() {
    CommandResult noSource = run("get");
    CommandResult missing = run("get", "--file", "/does/not/exist.png");
    assertAll(
        () -> assertEquals(CommandResult.USAGE, noSource.status()),
        () -> assertEquals(CommandResult.OK, missing.status(), missing.error()),
        () ->
            assertEquals(
                "{\"opened\":[],\"failed\":[\"/does/not/exist.png\"]}", missing.body().strip()));
  }
}
