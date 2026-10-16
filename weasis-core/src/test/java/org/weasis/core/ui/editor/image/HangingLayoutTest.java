/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.editor.image.HangingLayout.Cell;
import org.weasis.core.ui.editor.image.HangingLayout.Tiling;

class HangingLayoutTest {

  @Test
  void aLayoutSurvivesJsonWithItsCellsInOrder() {
    HangingLayout layout =
        HangingLayout.builder()
            .grid("2x2") // NON-NLS
            .synch("stack") // NON-NLS
            .select(2)
            .series(3, "1.2.3") // NON-NLS
            .tiled(3, new Tiling(2, 2))
            .series(1, "1.2.1") // NON-NLS
            .build();
    JsonObject json = layout.toJson();
    HangingLayout read = HangingLayout.fromJson(json);
    assertAll(
        () -> assertEquals(layout, read),
        () -> assertEquals(List.of(1, 3), read.cells().stream().map(Cell::position).toList()),
        () ->
            assertEquals(
                "2x2",
                json.getJsonArray("cells").getJsonObject(1).getJsonObject("tiled").getInt("cols")
                    + "x"
                    + json.getJsonArray("cells")
                        .getJsonObject(1)
                        .getJsonObject("tiled")
                        .getInt("rows")), // NON-NLS
        () -> assertEquals("1.2.3", read.cells().get(1).seriesId().orElseThrow()), // NON-NLS
        () -> assertNull(read.cells().getFirst().tiling()),
        () -> assertEquals(2, read.selected()));
  }

  @Test
  void missingFieldsKeepTheViewerAsItIs() {
    HangingLayout read = HangingLayout.fromJson(Json.createObjectBuilder().build());
    assertAll(
        () -> assertNull(read.grid()),
        () -> assertNull(read.synch()),
        () -> assertEquals(0, read.selected()),
        () -> assertTrue(read.cells().isEmpty()),
        () -> assertFalse(read.toJson().containsKey("grid"))); // NON-NLS
  }

  @Test
  void aTilingIsReadFromColsByRowsAndNeverBelowOne() {
    assertAll(
        () -> assertEquals(new Tiling(2, 3), Tiling.parse("2x3")), // NON-NLS
        () -> assertEquals(new Tiling(1, 1), Tiling.parse("0X0")), // NON-NLS
        () -> assertEquals("2x3", new Tiling(2, 3).toString()), // NON-NLS
        () -> assertThrows(IllegalArgumentException.class, () -> Tiling.parse("2")), // NON-NLS
        () -> assertThrows(NumberFormatException.class, () -> Tiling.parse("axb"))); // NON-NLS
  }

  @Test
  void anEmptyCellHasNoSeriesAndASeriesCellCarriesTheDefaultPresentation() {
    Cell empty = new Cell(1, null, List.of(ViewState.EMPTY));
    Cell shown = Cell.of(2, "1.2"); // NON-NLS
    assertAll(
        () -> assertTrue(empty.seriesId().isEmpty()),
        () -> assertEquals("1.2", shown.seriesId().orElseThrow()), // NON-NLS
        () -> assertEquals(ViewState.ViewPresentation.DEFAULT, shown.panes().getFirst().view()),
        () -> assertFalse(shown.toJson().containsKey("tiled"))); // NON-NLS
  }
}
