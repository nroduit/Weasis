/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.print;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.awt.Color;
import java.io.StringReader;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.weasis.core.api.command.CommandContext;
import org.weasis.core.api.command.CommandModel;
import org.weasis.core.api.command.CommandOrigin;
import org.weasis.core.api.command.CommandResult;
import org.weasis.core.ui.print.PageSpec.Orientation;
import org.weasis.core.ui.util.ExportLayout;
import org.weasis.core.ui.util.PrintOptions;

@DisplayNameGeneration(ReplaceUnderscores.class)
class PrintCommandsTest {

  private static final List<String> LINES =
      List.of(
          "print:destinations",
          "print:destinations --json",
          "print:page",
          "print:page --view v1 --pane 2 --to Fake --paper a4 --orientation LANDSCAPE --text small"
              + " --background black --annotations off --position top-left --masking teaching",
          "print:render --job job-1 --out /tmp/page.png",
          "print:render --job job-1 --to Fake --dpi 300 --gray --page 2 --out /tmp/page.png",
          "print:page --series --grid 3x4 --step 2 --frames 1-40 --header on",
          "print:send --job job-1 --to Fake --copies 2",
          "print:send --job job-1 --to file:pdf --out /tmp/pages.pdf --dpi 200");

  /** A destination that prints nothing. */
  static final class FakeDestination implements PrintDestination {
    @Override
    public String id() {
      return "printer:Fake"; // NON-NLS
    }

    @Override
    public String title() {
      return "Fake"; // NON-NLS
    }

    @Override
    public List<Paper> papers() {
      return List.of(Paper.LETTER, Paper.A4);
    }

    @Override
    public Paper defaultPaper() {
      return Paper.LETTER;
    }

    @Override
    public Color defaultBackground() {
      return Color.WHITE;
    }

    @Override
    public PageSpec pageSpec(Paper paper, Orientation orientation, Color background, Color border) {
      return PageSpec.of(paper, orientation, background);
    }

    @Override
    public OptionalInt resolutionDpi() {
      return OptionalInt.of(300);
    }

    @Override
    public void print(PageSource pages, PageSpec spec, PrintOptions options, int copies) {}
  }

  private PrintCommands commands;

  @BeforeEach
  void setUp() {
    commands =
        new PrintCommands()
            .withDestinations(() -> List.of(new FakeDestination()))
            .withSelectedViewer(Optional::empty)
            .withJobs(new PrintJobs(2));
  }

  private CommandResult run(String verb, String... args) {
    return CommandContext.runWith(
        new CommandContext(CommandOrigin.LAUNCH_ARGS),
        () -> CommandModel.execute(commands, verb, args));
  }

  private static JsonObject json(String text) {
    return Json.createReader(new StringReader(text)).readObject();
  }

  @Test
  void documented_command_lines_parse() {
    assertAll(
        LINES.stream()
            .map(
                line ->
                    (Executable)
                        () -> assertDoesNotThrow(() -> CommandModel.parse(commands, line), line))
            .toList());
  }

  @Test
  void destinations_lists_the_printers_with_their_papers() {
    CommandResult result = run("destinations", "--json");
    JsonObject body = json(result.body());
    JsonObject first = body.getJsonArray("destinations").getJsonObject(0);
    assertAll(
        () -> assertEquals(CommandResult.OK, result.status()),
        () -> assertEquals(1, body.getInt("count")),
        () -> assertEquals("printer:Fake", first.getString("id")),
        () -> assertEquals("printer", first.getString("kind")),
        () -> assertEquals("Letter", first.getString("defaultPaper")),
        () -> assertEquals(300, first.getInt("resolutionDpi")),
        () -> assertEquals(2, first.getJsonArray("papers").size()));
  }

  @Test
  void page_without_a_viewer_and_unknown_handles_fail_with_a_pointer() {
    CommandResult noViewer = run("page");
    CommandResult badDestination = run("page", "--to", "Nowhere");
    CommandResult badJob = run("render", "--job", "job-9", "--out", "/tmp/x.png");
    assertAll(
        () -> assertFalse(noViewer.isOk()),
        () -> assertTrue(noViewer.error().contains("No image viewer"), noViewer.error()),
        () -> assertFalse(badDestination.isOk()),
        () ->
            assertTrue(
                badDestination.error().contains("Unknown print destination"),
                badDestination.error()),
        () -> assertFalse(badJob.isOk()),
        () -> assertTrue(badJob.error().contains("Unknown print job"), badJob.error()));
  }

  @Test
  void a_paper_is_found_by_id_or_film_size_among_the_papers_of_the_destination() {
    FakeDestination fake = new FakeDestination();
    assertAll(
        () -> assertEquals(Paper.A4, PrintCommands.paper("a4", fake)),
        () -> assertEquals(Paper.IN_14X17, PrintCommands.paper("14INX17IN", null)),
        () -> assertEquals(Paper.IN_14X17, PrintCommands.paper("14x17in", null)),
        () ->
            assertThrows(
                IllegalArgumentException.class, () -> PrintCommands.paper("Tabloid", fake)));
  }

  @Test
  void the_registry_keeps_the_newest_jobs_and_disposes_the_oldest() {
    PrintJobs jobs = new PrintJobs(2);
    ExportLayout<?> first = mock(ExportLayout.class);
    ExportLayout<?> second = mock(ExportLayout.class);
    ExportLayout<?> third = mock(ExportLayout.class);
    PrintJob a =
        jobs.create(
            null, PageSource.of(first), Paper.A4, Orientation.PORTRAIT, new PrintOptions(), null);
    jobs.create(
        null, PageSource.of(second), Paper.A4, Orientation.PORTRAIT, new PrintOptions(), null);
    PrintJob c =
        jobs.create(
            "v1", PageSource.of(third), Paper.A4, Orientation.PORTRAIT, new PrintOptions(), null);
    assertAll(
        () -> assertEquals("job-1", a.id()),
        () -> assertEquals(List.of("job-2", "job-3"), jobs.ids()),
        () -> verify(first).dispose(),
        () -> verify(second, never()).dispose(),
        () -> assertTrue(jobs.find("job-1").isEmpty()),
        () -> assertEquals("v1", c.viewId().orElseThrow()),
        () ->
            assertThrows(IllegalStateException.class, () -> a.render(a.spec(null), 72, false, 0)));
    jobs.remove("job-3");
    assertAll(() -> verify(third).dispose(), () -> assertEquals(List.of("job-2"), jobs.ids()));
  }

  @Test
  void grid_and_frame_range_are_parsed_and_checked() {
    assertAll(
        () -> assertArrayEquals(new int[] {3, 4}, PrintCommands.Page.gridSize("3x4")),
        () -> assertArrayEquals(new int[] {2, 2}, PrintCommands.Page.gridSize("2X2")),
        () -> assertArrayEquals(new int[] {1, 0}, PrintCommands.Page.frameRange(null)),
        () -> assertArrayEquals(new int[] {5, 40}, PrintCommands.Page.frameRange("5-40")),
        () -> assertArrayEquals(new int[] {5, 0}, PrintCommands.Page.frameRange("5-")),
        () -> assertArrayEquals(new int[] {7, 7}, PrintCommands.Page.frameRange("7")),
        () ->
            assertThrows(IllegalArgumentException.class, () -> PrintCommands.Page.gridSize("0x3")),
        () ->
            assertThrows(
                IllegalArgumentException.class, () -> PrintCommands.Page.frameRange("9-2")));
  }
}
