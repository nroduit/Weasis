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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** One type per concept: it names itself, validates at parse time and reaches the descriptor. */
class SemanticTypeTest {

  enum Side {
    LEFT,
    RIGHT
  }

  @Command(
      name = "types",
      description = "A namespace of typed values.",
      subcommands = TypedModel.Show.class)
  static final class TypedModel {

    @Command(name = "show", description = "Shows a pane of a view.")
    static final class Show implements Callable<Integer> {
      @Option(names = "--view", paramLabel = "<view-id>", description = "View id.")
      ViewId view;

      @Option(names = "--pane", paramLabel = "<pane-index>", description = "Pane index.")
      PaneIndex pane;

      @Option(names = "--out", paramLabel = "<output-path>", description = "File to write.")
      OutputPath out;

      @Option(names = "--ratio", paramLabel = "<split-ratio>", description = "Share.")
      SplitRatio ratio;

      @Option(names = "--count", paramLabel = "<n>", description = "How many.")
      int count;

      @Option(names = "--factor", paramLabel = "<f>", description = "How much.")
      double factor;

      @Option(names = "--side", paramLabel = "<side>", description = "Which side.")
      Side side;

      @Parameters(arity = "0..1", paramLabel = "<screen-rect>", description = "Bounds.")
      ScreenRect bounds;

      @Override
      public Integer call() {
        return CommandResult.OK;
      }
    }
  }

  private static CommandResult run(String... args) {
    return CommandModel.execute(new TypedModel(), "show", args);
  }

  @Test
  void aTypeNamesItselfAndItsLabel() {
    assertAll(
        () -> assertEquals("view-id", SemanticType.nameOf(ViewId.class)),
        () -> assertEquals("<pane-index>", SemanticType.labelOf(PaneIndex.class)),
        () -> assertEquals("<screen-rect>", SemanticType.labelOf(ScreenRect.class)),
        () -> assertEquals("integer", SemanticType.primitiveOf(int.class)),
        () -> assertEquals("number", SemanticType.primitiveOf(Double.class)),
        () -> assertEquals("string", SemanticType.primitiveOf(String.class)),
        () -> assertEquals("enum", SemanticType.primitiveOf(Side.class)));
  }

  @Test
  void aMalformedValueIsAUsageErrorNamingTheConcept() {
    CommandResult zeroPane = run("--pane", "0");
    CommandResult wordPane = run("--pane", "second");
    CommandResult blankView = run("--view", " ");
    CommandResult ratio = run("--ratio", "1");
    CommandResult bounds = run("10,20,30");
    assertAll(
        () -> assertEquals(CommandResult.USAGE, zeroPane.status()),
        () -> assertTrue(zeroPane.error().contains("a pane index from 1"), zeroPane.error()),
        () -> assertEquals(CommandResult.USAGE, wordPane.status()),
        () -> assertEquals(CommandResult.USAGE, blankView.status()),
        () -> assertTrue(blankView.error().contains("workspace:list"), blankView.error()),
        () -> assertEquals(CommandResult.USAGE, ratio.status()),
        () -> assertEquals(CommandResult.USAGE, bounds.status()),
        () -> assertTrue(bounds.error().contains("x,y,width,height"), bounds.error()));
  }

  @Test
  void aWellFormedValueIsConvertedAndCarriesTheCodeIndex() {
    assertAll(
        () -> assertEquals(CommandResult.OK, run("--view", "v1", "--pane", "3").status()),
        () -> assertEquals(2, PaneIndex.parse("3").toOffset()),
        () -> assertEquals(0, ImageIndex.parse(" 1 ").toOffset()),
        () -> assertEquals("1,2,3,4", ScreenRect.parse("1, 2, 3, 4").toString()),
        () -> assertThrows(IllegalArgumentException.class, () -> ImageIndex.parse("0")),
        () -> assertThrows(IllegalArgumentException.class, () -> GraphicId.parse("")));
  }

  @Test
  void theDescriptorCarriesTheLabelTheTypeAndTheEnumValues() {
    JsonWriter out = new JsonWriter();
    CommandModel.json(out, "types", new TypedModel());
    String json = out.toString().replace(" ", "").replace("\n", "");
    assertAll(
        () -> assertTrue(json.contains("\"label\":\"<view-id>\",\"type\":\"view-id\""), json),
        () -> assertTrue(json.contains("\"label\":\"<pane-index>\",\"type\":\"pane-index\""), json),
        () -> assertTrue(json.contains("\"label\":\"<n>\",\"type\":\"integer\""), json),
        () -> assertTrue(json.contains("\"label\":\"<f>\",\"type\":\"number\""), json),
        () -> assertTrue(json.contains("\"type\":\"enum\",\"values\":[\"LEFT\",\"RIGHT\"]"), json),
        () -> assertTrue(json.contains("\"label\":\"<screen-rect>\""), json));
  }

  @Test
  void theVocabularyCheckReportsALabelOrATypeThatDrifted() {
    assertEquals(List.of(), CommandVocabulary.violations("types", new TypedModel()));
    assertEquals(
        List.of(
            "drifted:show --view: label <id> instead of <view-id>",
            "drifted:show --Pane: a long option name is kebab-case"),
        CommandVocabulary.violations("drifted", new DriftedModel()));
  }

  @Command(name = "drifted", subcommands = DriftedModel.Show.class)
  static final class DriftedModel {
    @Command(name = "show")
    static final class Show implements Callable<Integer> {
      @Option(names = "--view", paramLabel = "<id>", description = "View id.")
      ViewId view;

      @Option(names = "--Pane", paramLabel = "<pane-index>", description = "Pane index.")
      PaneIndex pane;

      @Override
      public Integer call() {
        return CommandResult.OK;
      }
    }
  }
}
