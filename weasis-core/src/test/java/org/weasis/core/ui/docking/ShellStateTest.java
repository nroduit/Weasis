/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.docking;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.StringReader;
import java.util.Arrays;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.docking.ShellState.ShellSide;
import org.weasis.core.ui.docking.SidePanel.Role;
import org.weasis.core.ui.docking.SidePanel.Side;

class ShellStateTest {

  @Test
  void jsonRoundTripKeepsEmptySlotsAndEveryNumber() {
    ShellState state =
        new ShellState(
            new ShellSide(Arrays.asList("dicom-explorer", null), 300, 0.6, false),
            new ShellSide(Arrays.asList("display", "measure"), 320, 0.5, true),
            "mini");
    JsonObject json = state.toJson();
    ShellState parsed = ShellState.fromJson(json);
    assertAll(
        () -> assertEquals(state, parsed),
        () -> assertTrue(json.getJsonObject("explorer").getJsonArray("slots").isNull(1)),
        () -> assertEquals("mini", json.getString("strip")),
        () -> assertEquals("measure", parsed.tools().slot(Slot.BOTTOM)),
        () -> assertNull(parsed.explorer().slot(Slot.BOTTOM)));
  }

  @Test
  void aPartialStateLeavesTheMissingSideAndTheStripOut() {
    ShellState parsed =
        ShellState.fromJson(
            Json.createReader(new StringReader("{\"tools\": {\"slots\": [\"display\"]}}"))
                .readObject());
    assertAll(
        () -> assertNull(parsed.explorer()),
        () -> assertNull(parsed.strip()),
        () -> assertEquals(List.of("display"), parsed.tools().slots()),
        () -> assertEquals(0, parsed.tools().width()),
        () -> assertNull(parsed.tools().slot(Slot.BOTTOM)),
        () -> assertEquals(0, parsed.toJson().getJsonObject("tools").getInt("width")));
  }

  @Test
  void captureAndApplyGoThroughTheShellAndReportWhatIsSkipped() throws Exception {
    Shell shell =
        new Shell(
            new SidePanel(Role.EXPLORER, Side.LEFT),
            new SidePanel(Role.TOOLS, Side.RIGHT),
            new MiniStrip());
    FakeTool mini = new FakeTool("mini", 5);
    SwingUtilities.invokeAndWait(
        () -> {
          shell.explorer().show(new FakeTool("dicom-explorer", 20));
          shell.tools().show(new FakeTool("display", 10));
          shell.strip().show(mini);
        });
    List<String> skipped = new java.util.ArrayList<>();
    SwingUtilities.invokeAndWait(
        () ->
            skipped.addAll(
                ShellState.apply(
                    new ShellState(
                        null,
                        new ShellSide(Arrays.asList("display", "nope"), 0, 0, false),
                        "other"),
                    shell)));
    SwingUtilities.invokeAndWait(() -> {});
    ShellState captured = ShellState.capture(shell);
    assertAll(
        () -> assertEquals(List.of("nope", "other"), skipped),
        () -> assertEquals("dicom-explorer", captured.explorer().slot(Slot.TOP)),
        () -> assertEquals("display", captured.tools().slot(Slot.TOP)),
        () -> assertEquals("mini", captured.strip()),
        () -> assertNull(ShellState.capture(null)),
        () -> assertTrue(ShellState.apply(captured, null).isEmpty()));
  }

  @Test
  void theToolsShownAsViewsTravelAndAreAbsentWhenNone() {
    ShellSide side =
        new ShellSide(Arrays.asList("display", null), 320, 0.5, false, List.of("dicom-info"));
    JsonObject json = side.toJson();
    assertAll(
        () -> assertEquals(List.of("dicom-info"), ShellSide.fromJson(json).views()),
        () -> assertEquals(side, ShellSide.fromJson(json)),
        () -> assertEquals(List.of(), new ShellSide(List.of("display"), 0, 0, false).views()),
        () ->
            assertTrue(
                !new ShellSide(List.of("display"), 0, 0, false).toJson().containsKey("views")));
  }

  @Test
  void theFamilyAndTheBottomOfEachFamilyRoundTripAndStayAbsentWithoutFamilies() {
    java.util.Map<String, String> bottoms = new java.util.LinkedHashMap<>();
    bottoms.put("dicom-explorer", "import");
    bottoms.put("dataset-catalog", null);
    ShellSide side =
        new ShellSide(
            Arrays.asList("file-explorer", null),
            300,
            0.6,
            false,
            List.of(),
            "file-explorer",
            bottoms);
    ShellSide back = ShellSide.fromJson(side.toJson());
    assertAll(
        () -> assertEquals(side, back),
        () -> assertEquals("file-explorer", back.family()),
        () -> assertTrue(back.bottoms().containsKey("dataset-catalog")),
        () -> assertEquals(null, back.bottoms().get("dataset-catalog")),
        () ->
            assertFalse(
                new ShellSide(List.of("display"), 0, 0, false).toJson().containsKey("family")),
        () ->
            assertFalse(
                new ShellSide(List.of("display"), 0, 0, false).toJson().containsKey("bottoms")));
  }
}
