/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.base.ui.gui;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.weasis.core.api.command.CommandContext;
import org.weasis.core.api.command.CommandModel;
import org.weasis.core.api.command.CommandOrigin;
import org.weasis.core.api.command.CommandResult;

/**
 * {@code weasis:info} is one of the six 4.x command lines 5.0 keeps, so its options of the time
 * still parse; the window verbs that replaced {@code weasis:ui} are checked next to them.
 */
class LegacyCommandsTest {

  private static final List<String> LINES =
      List.of(
          "weasis:info -v",
          "weasis:info -a",
          "weasis:info",
          "weasis:show",
          "weasis:minimize",
          "weasis:quit");

  /** Stopping the application is refused to a remote caller that did not confirm it. */
  @Test
  void quittingNeedsAConfirmationFromARemoteCaller() {
    CommandResult asked =
        CommandContext.runWith(
            new CommandContext(CommandOrigin.REMOTE, false),
            () -> CommandModel.execute(new WeasisCommands(), "quit", new String[0]));
    assertAll(
        () -> assertEquals(CommandResult.FORBIDDEN, asked.status()),
        () -> assertTrue(asked.error().contains("application.shutdown"), asked.error()),
        () -> assertTrue(asked.error().contains("-y"), asked.error()));
  }

  /** {@code weasis:ui} was replaced by the three window verbs and dropped by the 2.0 major. */
  @Test
  void theWindowVerbOfWeasis4IsGone() {
    assertEquals(
        CommandResult.USAGE,
        CommandModel.execute(new WeasisCommands(), "ui", new String[] {"-q"}).status());
  }

  @Test
  void documentedCommandLinesStillParse() {
    Object model = new WeasisCommands();
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
