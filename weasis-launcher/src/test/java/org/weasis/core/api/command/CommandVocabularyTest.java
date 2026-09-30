/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.command;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** The rules a namespace test enforces, checked here on a model that breaks every one of them. */
class CommandVocabularyTest {

  @Command(
      name = "bad",
      subcommands = {Model.Sloppy.class})
  static final class Model {

    @Command(name = "sloppy", description = "Breaks the rules on purpose.")
    @Since("5.0")
    static final class Sloppy implements Callable<Integer> {

      @Option(
          names = {"-f", "--file"},
          paramLabel = "<path>",
          description = "Read from here.")
      Path file;

      @Option(names = "--out", paramLabel = "<path>", description = "Write there.")
      Path out;

      @Option(names = "--viewId", paramLabel = "<view-id>", description = "A view.")
      ViewId view;

      @Option(names = "--pane", description = "A pane.")
      PaneIndex pane;

      @Override
      public Integer call() {
        return CommandResult.OK;
      }
    }
  }

  /** The namespace the manager registers itself follows the same rules as a plugin's. */
  @Test
  void theScriptNamespaceFollowsTheRules() {
    List<String> violations =
        CommandVocabulary.violations("script", new ScriptCommands(null)); // NON-NLS
    assertTrue(violations.isEmpty(), violations.toString());
  }

  @Test
  void everyRuleIsReported() {
    List<String> violations = CommandVocabulary.violations("bad", new Model());
    assertAll(
        () -> assertTrue(contains(violations, "-f is a short option"), violations.toString()),
        () -> assertTrue(contains(violations, "--file: reads a file"), violations.toString()),
        () -> assertTrue(contains(violations, "--out: writes a file"), violations.toString()),
        () -> assertTrue(contains(violations, "kebab-case"), violations.toString()),
        () ->
            assertTrue(
                contains(violations, "--pane: label <pane> instead of <pane-index>"),
                violations.toString()));
  }

  private static boolean contains(List<String> violations, String text) {
    return violations.stream().anyMatch(v -> v.contains(text));
  }
}
