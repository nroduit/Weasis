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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * A spelling of an earlier major keeps working, stays out of the help, and a remote caller is told
 * the current one.
 */
class LegacySpellingTest {

  @Command(name = "view2d", description = "Display verbs.", subcommands = Model.Grid.class)
  static final class Model {

    @Command(
        name = "grid",
        aliases = "layout",
        description = "Selects the grid of panes of a view.")
    static final class Grid implements Callable<Integer> {
      GridId grid;

      @Option(names = "--grid", paramLabel = "<grid-id>", description = "Grid id.")
      void setGrid(GridId value) {
        this.grid = value;
      }

      @Option(
          names = {"-i", "--id"},
          hidden = true,
          paramLabel = "<grid-id>")
      @Renamed("--grid")
      void setLegacyGrid(GridId value) {
        this.grid = value;
      }

      @Parameters(arity = "0..1", hidden = true, paramLabel = "<grid>")
      @Renamed("--grid")
      String legacyPositional;

      @Override
      public Integer call() {
        System.out.print(""); // NOSONAR the body is the grid itself
        return CommandResult.OK;
      }
    }
  }

  private static CommandResult run(CommandOrigin origin, String verb, String... args) {
    return CommandContext.runWith(
        new CommandContext(origin), () -> CommandModel.execute(new Model(), verb, args));
  }

  @Test
  void theEarlierVerbAndOptionStillReachTheSameCommand() {
    assertAll(
        () ->
            assertEquals(
                CommandResult.OK, run(CommandOrigin.REMOTE, "grid", "--grid", "1x2").status()),
        () ->
            assertEquals(
                CommandResult.OK, run(CommandOrigin.REMOTE, "layout", "-i", "1x2").status()),
        () ->
            assertEquals(
                CommandResult.OK, run(CommandOrigin.REMOTE, "layout", "--id=1x2").status()));
  }

  @Test
  void aRemoteCallerIsToldTheCurrentSpelling() {
    CommandResult legacy = run(CommandOrigin.REMOTE, "layout", "-i", "1x2");
    CommandResult current = run(CommandOrigin.REMOTE, "grid", "--grid", "1x2");
    CommandResult local = run(CommandOrigin.LAUNCH_ARGS, "layout", "-i", "1x2");
    assertAll(
        () -> assertTrue(legacy.error().contains("layout is now grid"), legacy.error()),
        () -> assertTrue(legacy.error().contains("-i is now --grid"), legacy.error()),
        () -> assertEquals(CommandResult.OK, legacy.status()),
        () -> assertEquals("", current.error()),
        () -> assertEquals("", local.error(), "launch arguments stay silent"));
  }

  @Test
  void theHelpAndTheDescriptorShowOneSpelling() {
    String usage = CommandModel.usage(new Model(), "grid").orElseThrow();
    String markdown = CommandModel.markdown("view2d", new Model());
    JsonWriter out = new JsonWriter();
    CommandModel.json(out, "view2d", new Model());
    String json = out.toString();
    assertAll(
        () -> assertTrue(usage.contains("--grid"), usage),
        () -> assertFalse(usage.contains("--id"), usage),
        () -> assertTrue(markdown.contains("### view2d:grid"), markdown),
        () -> assertFalse(markdown.contains("--id"), markdown),
        () -> assertFalse(markdown.contains("<grid>"), "a hidden positional stays out too"),
        () -> assertFalse(json.contains("\"--id\""), json),
        () -> assertFalse(json.contains("<grid>"), json));
  }
}
