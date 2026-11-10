/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.JPanel;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.model.graphic.GraphicToolDescriptor.PanelPlacement;
import org.weasis.core.ui.model.graphic.imp.line.LineGraphic;

/** Where the panel of a tool goes: inline by default, a side tool when the tool asks for it. */
class GraphicToolDescriptorPlacementTest {

  @Test
  void aPanelSitsInlineUnlessTheToolAsksForTheSide() {
    GraphicToolDescriptor plain =
        GraphicToolDescriptor.of("test.line", ToolCategory.MEASURE, LineGraphic::new); // NON-NLS
    GraphicToolDescriptor inline = plain.withPanel(ctx -> new JPanel());
    GraphicToolDescriptor side = plain.withSidePanel(ctx -> new JPanel());
    GraphicToolDescriptor nineValues =
        new GraphicToolDescriptor(
            "test.nine",
            ToolCategory.MEASURE,
            LineGraphic::new,
            null,
            null,
            ctx -> new JPanel(),
            0,
            0,
            2); // NON-NLS
    assertAll(
        () -> assertFalse(plain.hasPanel()),
        () -> assertEquals(PanelPlacement.INLINE, plain.placement()),
        () -> assertTrue(inline.hasPanel()),
        () -> assertFalse(inline.hasSidePanel()),
        () -> assertTrue(side.hasPanel()),
        () -> assertTrue(side.hasSidePanel()),
        () -> assertEquals(PanelPlacement.SIDE, side.placement()),
        () ->
            assertEquals(
                PanelPlacement.SIDE,
                side.withShortcut(65, 0).placement(),
                "the other with* copies keep the placement"),
        () -> assertEquals(PanelPlacement.SIDE, side.withMinPoints(3).placement()),
        () -> assertEquals(PanelPlacement.INLINE, nineValues.placement()));
  }
}
