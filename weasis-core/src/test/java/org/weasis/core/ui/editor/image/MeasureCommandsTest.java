/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.weasis.core.api.command.CommandModel;

/** The syntax of the {@code measure} verbs, pinned without a running viewer. */
class MeasureCommandsTest {

  @Test
  void theVerbsParse() {
    MeasureCommands model = new MeasureCommands();
    assertAll(
        () ->
            assertDoesNotThrow(() -> CommandModel.parse(model, "measure:tools --category measure")),
        () -> assertDoesNotThrow(() -> CommandModel.parse(model, "measure:profiles")),
        () ->
            assertDoesNotThrow(() -> CommandModel.parse(model, "measure:profile --select default")),
        () -> assertDoesNotThrow(() -> CommandModel.parse(model, "measure:profile --automatic")),
        () ->
            assertDoesNotThrow(
                () ->
                    CommandModel.parse(
                        model, "measure:export --format csv --out /tmp/x.csv --force")),
        () ->
            assertDoesNotThrow(
                () ->
                    CommandModel.parse(
                        model, "measure:calibrate --graphic abc --length 12.5 --unit cm --series")),
        () -> assertDoesNotThrow(() -> CommandModel.parse(model, "measure:calibrate --remove")),
        () ->
            assertThrows(
                RuntimeException.class,
                () -> CommandModel.parse(model, "measure:profile --select a --automatic"),
                "one action at a time"),
        () ->
            assertThrows(
                RuntimeException.class,
                () -> CommandModel.parse(model, "measure:export --out /tmp/x")));
  }

  @Test
  void theHelpNamesTheFormatsAndTheHandles() {
    String help = CommandModel.usage(new MeasureCommands(), "export").orElseThrow();
    assertAll(
        () -> assertTrue(help.contains("--format"), help),
        () -> assertTrue(help.contains("<output-path>"), help),
        () -> assertTrue(help.contains("<layer-name>"), help));
  }
}
