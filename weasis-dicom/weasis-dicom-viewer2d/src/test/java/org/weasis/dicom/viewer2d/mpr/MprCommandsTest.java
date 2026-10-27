/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr;

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

class MprCommandsTest {

  private static final List<String> VALID =
      List.of(
          "mpr:reformat --plane CORONAL",
          "mpr:reformat --series 1.2.3 --plane sagittal --no-open",
          "mpr:reformat --plane AXIAL --json",
          "mpr:list",
          "mpr:delete --series 1.2.3.4");

  private static final List<String> INVALID =
      List.of("mpr:reformat", "mpr:reformat --series 1.2.3", "mpr:delete", "mpr:list --series x");

  @Test
  void documentedCommandLinesParse() {
    Object model = new MprCommands();
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
    Object model = new MprCommands();
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
    assertEquals(List.of(), CommandVocabulary.violations("mpr", new MprCommands()));
  }
}
