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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.weasis.core.api.command.CommandModel;

/** The 4.x command lines documented on the site must still parse: the syntax is the public API. */
class LegacyCommandsTest {

  private static final List<String> LINES =
      List.of(
          "acquire:patient -x <patient/>",
          "acquire:patient -i H4sIAAAA",
          "acquire:patient -s H4sIAAAA",
          "acquire:patient -u http://host/patient.xml");

  @Test
  void documentedCommandLinesStillParse() {
    Object model = AcquireManager.getInstance();
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
