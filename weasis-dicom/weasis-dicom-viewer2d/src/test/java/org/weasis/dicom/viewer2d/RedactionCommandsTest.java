/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.weasis.core.api.command.CommandModel;
import picocli.CommandLine.ParameterException;

/** The syntax of the {@code redact:} verbs is the public API of the masking surface. */
class RedactionCommandsTest {

  private static final List<String> VALID =
      List.of(
          "redact:list",
          "redact:list --view view_1 --pane 1",
          "redact:add --rect 10,20,100,50",
          "redact:add --ellipse 10,20,100,50 --scope series",
          "redact:add --polygon 0,0,10,0,10,10 --scope image",
          "redact:clear",
          "redact:clear --scope series",
          "redact:library",
          "redact:match",
          "redact:apply --entry device_1",
          "redact:apply --entry device_1 --scope series",
          "redact:save",
          "redact:save --name Scanner1 --entry device_1",
          "redact:delete --entry device_1",
          "redact:export --out masks.json",
          "redact:export --out profile.yml --karnak --complete --masking-profile basic",
          "redact:import --file masks.json",
          "redact:import --file profile.yml --karnak");

  private static final List<String> INVALID =
      List.of(
          "redact:add", // a shape is required
          "redact:add --rect 10,20,100,50 --ellipse 10,20,100,50", // exclusive
          "redact:apply", // an id is required
          "redact:delete", // an id is required
          "redact:export", // an output file is required
          "redact:import", // a file is required
          "redact:clear --scope everything");

  @Test
  void documentedCommandLinesParse() {
    Object model = new RedactionCommands();
    assertAll(
        VALID.stream()
            .map(
                line ->
                    (Executable)
                        () ->
                            assertDoesNotThrow(
                                () -> CommandModel.parse(model, line), "valid: " + line))
            .toList());
  }

  @Test
  void incompleteCommandLinesAreUsageErrors() {
    Object model = new RedactionCommands();
    assertAll(
        INVALID.stream()
            .map(
                line ->
                    (Executable)
                        () ->
                            assertThrows(
                                ParameterException.class,
                                () -> CommandModel.parse(model, line),
                                "invalid: " + line))
            .toList());
  }
}
