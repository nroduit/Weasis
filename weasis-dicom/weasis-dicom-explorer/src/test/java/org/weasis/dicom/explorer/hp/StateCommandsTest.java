/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.hp;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.weasis.core.api.command.CommandModel;
import org.weasis.core.api.command.CommandResult;
import org.weasis.core.api.command.CommandVocabulary;

class StateCommandsTest {

  private static final List<String> LINES =
      List.of(
          "state:keep",
          "state:keep --label \"before the call\" --pin --json",
          "state:list",
          "state:restore --session 2026-10-17T10:00:00Z",
          "state:restore --study 1.2.3",
          "state:delete --session 2026-10-17T10:00:00Z",
          "state:export --session 2026-10-17T10:00:00Z --out /tmp/session",
          "state:export --study 1.2.3 --out /tmp/session --dicom --json");

  @Test
  void documentedCommandLinesParse() {
    Object model = new StateCommands();
    assertAll(
        LINES.stream()
            .map(
                line ->
                    (Executable)
                        () -> assertDoesNotThrow(() -> CommandModel.parse(model, line), line))
            .toList());
  }

  @Test
  void theNamespaceFollowsTheVocabulary() {
    assertEquals(List.of(), CommandVocabulary.violations("state", new StateCommands())); // NON-NLS
  }

  @Test
  void restoreNeedsASessionOrAStudy() {
    CommandResult result =
        CommandModel.execute(new StateCommands(), "restore", new String[0]); // NON-NLS
    assertTrue(result.status() != CommandResult.OK, String.valueOf(result.status()));
  }
}
