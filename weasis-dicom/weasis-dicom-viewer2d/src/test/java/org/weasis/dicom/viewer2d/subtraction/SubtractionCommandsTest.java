/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.subtraction;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.weasis.core.api.command.CommandModel;
import org.weasis.core.api.command.CommandVocabulary;
import picocli.CommandLine.ParameterException;

/** The syntax of the {@code subtraction:} verbs is the public API of the subtraction. */
class SubtractionCommandsTest {

  private static final List<String> VALID =
      List.of(
          "subtraction:preview",
          "subtraction:preview --mask 1.2.4",
          "subtraction:preview --view view_1 --pane 2 --mask 1.2.4 --factor 0.5",
          "subtraction:preview --off",
          "subtraction:create",
          "subtraction:create --series 1.2.3 --mask 1.2.4",
          "subtraction:create --mask 1.2.4 --factor 0.8 --offset 100 --absolute --signed --no-open",
          "subtraction:create --series series_2 --json",
          "subtraction:create --phase 2 --mask-phase 1",
          "subtraction:create --series 1.2.3 --all-phases --mip --no-open",
          "subtraction:create --mask-frames 2-4 --shift 0.5,-0.25 --no-open",
          "subtraction:create --series 1.2.3 --tid-offset 2",
          "subtraction:preview --motion-correction",
          "subtraction:preview --motion-correction=false --json",
          "subtraction:create --motion-correction --no-open",
          "subtraction:create --normalize --no-open",
          "subtraction:create --normalize=10,10,64,64 --series 1.2.3 --mask 1.2.4",
          "subtraction:mask --frames 3",
          "subtraction:mask --view view_1 --pane 2 --frames 1,3,5",
          "subtraction:mask --tid-offset -1 --json",
          "subtraction:shift --rows 0.5",
          "subtraction:shift --rows -1 --columns 0.25 --json",
          "subtraction:phases",
          "subtraction:phases --series 1.2.3",
          "subtraction:list",
          "subtraction:list --study 1.2",
          "subtraction:info --series 1.2.3.4",
          "subtraction:delete --series 1.2.3.4");

  private static final List<String> INVALID =
      List.of(
          "subtraction:info", // a series is required
          "subtraction:delete", // a series is required
          "subtraction:create --factor abc",
          "subtraction:preview --mask",
          "subtraction:create --phase x",
          "subtraction:preview --pane x",
          "subtraction:create --offset",
          "subtraction:preview --motion-correction=maybe",
          "subtraction:mask --frames",
          "subtraction:mask --tid-offset x",
          "subtraction:shift --rows abc",
          "subtraction:list --study");

  @Test
  void documentedCommandLinesParse() {
    Object model = new SubtractionCommands();
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
    Object model = new SubtractionCommands();
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

  @Test
  void theNamespaceFollowsTheVocabulary() {
    assertEquals(List.of(), CommandVocabulary.violations("subtraction", new SubtractionCommands()));
  }
}
