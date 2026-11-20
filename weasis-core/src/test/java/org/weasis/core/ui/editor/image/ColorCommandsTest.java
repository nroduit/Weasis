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

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.weasis.core.api.command.CommandModel;

/** The syntax of the {@code color:} verbs is public API. */
class ColorCommandsTest {

  private static final List<String> LINES =
      List.of(
          "color:status",
          "color:status --view v1 --pane 2 --json",
          "color:set --enabled false",
          "color:set --display-profile /tmp/display.icc",
          "color:set --srgb --enabled true",
          "color:set --screen :0.1 --display-profile /tmp/wide.icc",
          "color:set --screen :0.1 --auto");

  @Test
  void documentedCommandLinesParse() {
    Object model = new ColorCommands();
    assertAll(
        LINES.stream()
            .map(
                line ->
                    (Executable)
                        () -> assertDoesNotThrow(() -> CommandModel.parse(model, line), line))
            .toList());
  }

  @Test
  void displayProfileAndSrgbExcludeEachOther() {
    assertThrows(
        Exception.class,
        () ->
            CommandModel.parse(
                new ColorCommands(), "color:set --srgb --display-profile /tmp/display.icc"));
  }
}
