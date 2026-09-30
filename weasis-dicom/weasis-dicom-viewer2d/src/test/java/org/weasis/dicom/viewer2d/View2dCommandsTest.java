/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.weasis.core.api.command.CommandModel;
import org.weasis.core.api.command.CommandResult;
import org.weasis.core.api.gui.layout.MigLayoutModel;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.dicom.codec.DicomImageElement;

/** Validation and documentation of the {@code view2d} model, without a viewer. */
class View2dCommandsTest {

  private static CommandResult run(String verb, String... args) {
    return CommandModel.execute(new View2dCommands(), verb, args);
  }

  @Test
  void exclusiveAmountsAreEnforced() {
    CommandResult two = run("zoom", "--set", "2", "--by", "1");
    CommandResult gridBoth = run("grid", "--panes", "2", "--grid", "x");
    assertAll(
        () -> assertEquals(CommandResult.USAGE, two.status()),
        () -> assertEquals(CommandResult.USAGE, gridBoth.status()));
  }

  /** A display verb called without an argument reads the state instead of failing. */
  @Test
  void bareDisplayVerbsAskForTheState() {
    assertAll(
        Stream.of(
                "zoom", "window-level", "pan", "scroll", "reset", "rotate", "flip", "grid", "sync")
            .map(
                verb ->
                    (Executable)
                        () ->
                            assertDoesNotThrow(
                                () -> CommandModel.parse(new View2dCommands(), "view2d:" + verb),
                                verb))
            .toList());
  }

  /** Every pane index is 1-based, and the target options are on the display verbs too. */
  @Test
  void displayVerbsTakeAViewAndAPane() {
    assertAll(
        Stream.of(
                "view2d:zoom --view v1 --pane 2 --set 2",
                "view2d:zoom --fit best-fit",
                "view2d:zoom --by -2",
                "view2d:window-level --view v1 --pane 1 --window 400 --level 40",
                "view2d:scroll --pane 2 --image 26",
                "view2d:scroll --by -5",
                "view2d:pan --dx 10 --dy -5",
                "view2d:reset --view v1 --pane 2 --all",
                "view2d:reset --tool window-level --tool zoom",
                "view2d:window-level --window-by 50 --level-by -20",
                "view2d:window-level --level-by -20",
                "view2d:stats --ellipse 326.5,272,12,15",
                "view2d:rotate --pane 1 --degrees 90",
                "view2d:rotate --by -90",
                "view2d:flip --pane 1 on --axis vertical",
                "view2d:preset --pane 2 --preset lung",
                "view2d:grid --view v1 --panes 2",
                "view2d:grid --best-for 3",
                "view2d:mouse-action --button right --action zoom",
                "view2d:capture --out /tmp/a.png --crop 0,0,100,100",
                "view2d:annotate --label-at 100,200 --text caption",
                "view2d:sync --view v1 --mode stack")
            .map(
                line ->
                    (Executable)
                        () ->
                            assertDoesNotThrow(
                                () -> CommandModel.parse(new View2dCommands(), line), line))
            .toList());
  }

  /**
   * The grids answered are those of the viewer {@code --view} names: the LAYOUT action holds the
   * ones of the selected viewer, which is another one as soon as the caller aims elsewhere.
   */
  @Test
  @SuppressWarnings("unchecked")
  void gridsComeFromTheAddressedViewer() {
    MigLayoutModel own = new MigLayoutModel("1x3", "1x3", 1, 3, View2d.class.getName()); // NON-NLS
    ImageViewerPlugin<DicomImageElement> container = mock(ImageViewerPlugin.class);
    when(container.getLayoutList()).thenReturn(List.of(own));
    assertEquals(List.of(own), View2dCommands.Grid.layouts(container));
  }

  @Test
  void windowLevelAcceptsAbsoluteOrStepsOnly() {
    CommandResult windowOnly = run("window-level", "--window", "400");
    CommandResult mixed =
        run("window-level", "--window", "400", "--level", "40", "--window-by", "10");
    assertAll(
        () -> assertEquals(CommandResult.USAGE, windowOnly.status()),
        () -> assertTrue(windowOnly.error().contains("--level"), windowOnly.error()),
        () -> assertEquals(CommandResult.USAGE, mixed.status()));
  }

  @Test
  void resetNamesTheToolsItAccepts() {
    CommandResult unknown = run("reset", "--tool", "rotate");
    assertAll(
        () -> assertEquals(CommandResult.USAGE, unknown.status()),
        () -> assertTrue(unknown.error().contains("window-level"), unknown.error()),
        () -> assertTrue(unknown.error().contains("rotation"), unknown.error()));
  }

  @Test
  void agentVerbsValidateBeforeTouchingTheViewer() {
    CommandResult noOut = run("capture");
    CommandResult badFormat = run("capture", "--out", "view.bmp");
    CommandResult noRegion = run("stats");
    CommandResult twoRegions = run("stats", "--rect", "0,0,10,10", "--ellipse", "0,0,10,10");
    CommandResult shortPolygon = run("stats", "--polygon", "1,2,3,4");
    CommandResult badRect = run("stats", "--rect", "0,0,-5,10");
    CommandResult onePoint = run("measure", "--type", "line", "--points", "1,2");
    CommandResult threePointLine = run("measure", "--type", "line", "--points", "1,2,3,4,5,6");
    CommandResult circleTwo = run("measure", "--type", "circle", "--points", "1,2,3,4");
    CommandResult badType = run("measure", "--type", "diagonal", "--points", "1,2,3,4");
    CommandResult oddCoordinates = run("measure", "--type", "polygon", "--points", "1,2,3,4,5");
    CommandResult notANumber = run("measure", "--type", "line", "--points", "1,2,a,4");
    assertAll(
        () -> assertEquals(CommandResult.USAGE, noOut.status()),
        () -> assertTrue(noOut.error().contains("--out"), noOut.error()),
        () -> assertEquals(CommandResult.USAGE, badFormat.status()),
        () -> assertTrue(badFormat.error().contains(".png"), badFormat.error()),
        () -> assertEquals(CommandResult.USAGE, noRegion.status()),
        () -> assertEquals(CommandResult.USAGE, twoRegions.status()),
        () -> assertEquals(CommandResult.USAGE, shortPolygon.status()),
        () -> assertEquals(CommandResult.USAGE, badRect.status()),
        () -> assertEquals(CommandResult.USAGE, onePoint.status()),
        () -> assertTrue(onePoint.error().contains("two points at least"), onePoint.error()),
        () -> assertTrue(threePointLine.error().contains("line needs 2"), threePointLine.error()),
        () -> assertEquals(CommandResult.USAGE, circleTwo.status()),
        () -> assertEquals(CommandResult.USAGE, badType.status()),
        () -> assertTrue(badType.error().contains("LINE"), badType.error()),
        () -> assertEquals(CommandResult.USAGE, oddCoordinates.status()),
        () -> assertEquals(CommandResult.USAGE, notANumber.status()),
        () -> assertTrue(notANumber.error().contains("image pixels"), notANumber.error()));
  }

  @Test
  void displayAndAnnotationVerbsValidate() {
    CommandResult badSwitch = run("flip", "maybe");
    CommandResult badPane = run("zoom", "--pane", "x", "--set", "2");
    CommandResult noText = run("annotate", "--at", "1,2");
    CommandResult noPlace = run("annotate", "--text", "hello");
    CommandResult blankText = run("annotate", "--at", "1,2", "--text", "  ");
    CommandResult badAnchor = run("annotate", "--at", "1", "--text", "hello");
    CommandResult badLabel = run("annotate", "--at", "1,2", "--text", "hello", "--label-at", "x,y");
    assertAll(
        () -> assertEquals(CommandResult.USAGE, badSwitch.status()),
        () -> assertTrue(badSwitch.error().contains("ON"), badSwitch.error()),
        () -> assertEquals(CommandResult.USAGE, badPane.status()),
        () -> assertEquals(CommandResult.USAGE, noText.status()),
        () -> assertEquals(CommandResult.USAGE, noPlace.status()),
        () -> assertTrue(noPlace.error().contains("--label-at"), noPlace.error()),
        () -> assertEquals(CommandResult.USAGE, blankText.status()),
        () -> assertEquals(CommandResult.USAGE, badAnchor.status()),
        () -> assertTrue(badAnchor.error().contains("--at"), badAnchor.error()),
        () -> assertEquals(CommandResult.USAGE, badLabel.status()));
  }

  /** The 4.x spellings of this namespace were dropped by the 2.0 major; none of them parses. */
  @Test
  void theSpellingsOfWeasis4AreGone() {
    assertAll(
        Stream.of(
                new String[] {"wl", "50", "-20"},
                new String[] {"move", "10", "-5"},
                new String[] {"layout", "-n", "2"},
                new String[] {"mouseLeftAction", "winLevel"},
                new String[] {"synch", "Stack"},
                new String[] {"zoom", "-s", "-200"},
                new String[] {"scroll", "-i", "1"},
                new String[] {"reset", "winLevel"},
                new String[] {"capture", "--roi", "0,0,10,10"},
                new String[] {"preset", "--name", "Lung"})
            .map(
                argv ->
                    (Executable)
                        () -> {
                          CommandResult result =
                              run(argv[0], java.util.Arrays.copyOfRange(argv, 1, argv.length));
                          assertEquals(
                              CommandResult.USAGE, result.status(), String.join(" ", argv));
                        })
            .toList());
  }

  @Test
  void helpAndMarkdownListEveryVerb() {
    String usage = CommandModel.usage(new View2dCommands());
    String markdown = CommandModel.markdown("view2d", new View2dCommands());
    assertAll(
        () -> assertTrue(usage.contains("mouse-action"), usage),
        () -> assertFalse(usage.contains("mouseLeftAction"), "the 4.x verbs are gone"),
        () -> assertTrue(markdown.contains("### view2d:window-level"), markdown),
        () -> assertTrue(markdown.contains("--window"), markdown),
        () -> assertTrue(markdown.contains("### view2d:capture"), markdown),
        () -> assertTrue(markdown.contains("### view2d:stats"), markdown),
        () -> assertTrue(markdown.contains("### view2d:measure"), markdown),
        () -> assertTrue(markdown.contains("### view2d:preset"), markdown),
        () -> assertTrue(markdown.contains("### view2d:graphics"), markdown),
        () -> assertTrue(markdown.contains("### view2d:annotate"), markdown),
        () -> assertFalse(markdown.contains("--roi"), "the 4.x options are gone"),
        () -> assertTrue(run("pan", "--help").body().contains("--pane")),
        () -> assertEquals(CommandResult.OK, run("pan", "--help").status()));
  }
}
