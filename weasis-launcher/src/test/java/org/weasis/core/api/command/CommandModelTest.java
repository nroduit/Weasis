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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** The descriptor and the reference do not depend on the order reflection gives the fields. */
class CommandModelTest {

  @Command(
      name = "shape",
      subcommands = {Shape.Set.class})
  static final class Shape {

    static final class Choice {
      @Option(names = "--zeta", paramLabel = "<n>", description = "Last by name.")
      Integer zeta;

      @Option(names = "--alpha", paramLabel = "<n>", description = "First by name.")
      Integer alpha;
    }

    @Command(name = "set", description = "Sets a shape.")
    static final class Set implements Callable<Integer> {
      @Option(names = "--view", paramLabel = "<view-id>", description = "A view.")
      ViewId view;

      @ArgGroup(exclusive = true)
      Choice choice;

      @Option(names = "--beta", paramLabel = "<n>", description = "Between the two.")
      Integer beta;

      @Override
      public Integer call() {
        return CommandResult.OK;
      }
    }
  }

  @Test
  void groupAlternativesAreSortedInTheSynopsis() {
    assertAll(
        () ->
            assertEquals(
                "x [-h] [--view=<view-id>] [--alpha=<n> | --zeta=<n>]",
                CommandModel.sortedGroups("x [-h] [--view=<view-id>] [--zeta=<n> | --alpha=<n>]")),
        () ->
            assertEquals(
                "x (--c | [--a | --b]) [-h]",
                CommandModel.sortedGroups("x (--c | [--b | --a]) [-h]")),
        () -> assertEquals("x <a,b>", CommandModel.sortedGroups("x <a,b>")));
  }

  @Test
  void theDescriptorAndTheReferenceListOptionsByName() {
    JsonWriter out = new JsonWriter();
    CommandModel.json(out, new Shape(), "set"); // NON-NLS
    String json = out.toString();
    String markdown = CommandModel.markdown("shape", new Shape(), "set").orElseThrow();
    int alpha = json.indexOf("\"--alpha\"");
    int beta = json.indexOf("\"--beta\"");
    int view = json.indexOf("\"--view\"");
    assertAll(
        () -> assertTrue(alpha > 0 && alpha < beta && beta < view, json),
        () -> assertTrue(json.contains("[--alpha=<n> | --zeta=<n>]"), json),
        () ->
            assertTrue(
                markdown.indexOf("| --alpha |") < markdown.indexOf("| --beta |")
                    && markdown.indexOf("| --beta |") < markdown.indexOf("| --view |"),
                markdown));
  }
}
