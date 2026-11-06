/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wsi;

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

class WsiCommandsTest {

  private static final List<String> VALID =
      List.of(
          "wsi:info",
          "wsi:info --view view_1 --pane 1 --json",
          "wsi:goto",
          "wsi:goto --at 32000,8000",
          "wsi:goto --at 32000.5,8000.25 --zoom 0.5 --complete",
          "wsi:goto --objective 20 --complete --json",
          "wsi:goto --view view_1 --pane 2 --zoom 2",
          "wsi:goto --focal-plane 2",
          "wsi:goto --optical-path DAPI --complete",
          "wsi:goto --focal-plane 1 --optical-path FITC --at 100,200 --objective 10",
          "wsi:annotations",
          "wsi:annotations --json",
          "wsi:annotations --hide",
          "wsi:annotations --group 1,2 --show",
          "wsi:annotations --group 1.2.3 --hide --view view_1 --pane 1",
          "wsi:export --out /tmp/weasis/slide-measurements.dcm",
          "wsi:export --out /tmp/weasis/report.dcm --force --view view_1 --pane 1 --json");

  private static final List<String> INVALID =
      List.of(
          "wsi:goto --at 32000",
          "wsi:goto --at x,y",
          "wsi:goto --zoom 0",
          "wsi:goto --zoom",
          "wsi:goto --objective abc",
          "wsi:info --pane x",
          "wsi:goto --focal-plane x",
          "wsi:goto --optical-path",
          "wsi:annotations --group",
          "wsi:export",
          "wsi:level");

  @Test
  void documentedCommandLinesParse() {
    Object model = new WsiCommands();
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
    Object model = new WsiCommands();
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
    assertEquals(List.of(), CommandVocabulary.violations("wsi", new WsiCommands()));
  }
}
