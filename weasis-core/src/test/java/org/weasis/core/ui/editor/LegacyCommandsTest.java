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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.weasis.core.api.command.CommandModel;

/** The 4.x command lines documented on the site must still parse: the syntax is the public API. */
class LegacyCommandsTest {

  private static final List<String> LINES =
      List.of(
          "image:get -f /tmp/a.png",
          "image:get --file=/tmp/a.png -u http://host/b.jpg",
          "image:close -a",
          "image:close -g group1 -s series1",
          "image:get -?",
          "image:close --help",
          // the 5.0 spellings of the same lines
          "image:load --file /tmp/a.png --url http://host/b.jpg",
          "image:unload --all",
          "image:unload --group group1 --series series1",
          "image:list");

  @Test
  void documentedCommandLinesStillParse() {
    Object model = new FileModel();
    assertAll(
        LINES.stream()
            .map(
                line ->
                    (Executable)
                        () ->
                            assertDoesNotThrow(
                                () -> CommandModel.parse(model, line), "legacy: " + line))
            .toList());
  }
}
