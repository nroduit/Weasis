/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic.profile;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.GraphicRegistry;
import org.weasis.core.ui.model.graphic.ToolCategory;
import org.weasis.core.ui.model.graphic.imp.BuiltinGraphicTools;

@DisplayNameGeneration(ReplaceUnderscores.class)
class MeasurementProfilesTest {

  private final GraphicRegistry registry = new GraphicRegistry(property -> false);

  @Test
  void a_profile_orders_its_tools_with_selection_first_and_drops_unknown_keys() {
    MeasurementProfile profile =
        new MeasurementProfile(
            "p", // NON-NLS
            "P", // NON-NLS
            null,
            List.of(
                BuiltinGraphicTools.ANGLE,
                "nope.tool", // NON-NLS
                BuiltinGraphicTools.LINE,
                BuiltinGraphicTools.ARROW,
                BuiltinGraphicTools.TEXT),
            null,
            null,
            null,
            false);
    List<Graphic> measure = MeasurementProfiles.tools(profile, registry, ToolCategory.MEASURE);
    List<Graphic> draw = MeasurementProfiles.tools(profile, registry, ToolCategory.DRAW);
    assertAll(
        () -> assertEquals(3, measure.size()),
        () -> assertSame(BuiltinGraphicTools.SELECTION, measure.getFirst()),
        () ->
            assertSame(registry.prototype(BuiltinGraphicTools.ANGLE).orElseThrow(), measure.get(1)),
        () ->
            assertSame(registry.prototype(BuiltinGraphicTools.LINE).orElseThrow(), measure.get(2)),
        () -> assertEquals(3, draw.size()),
        () -> assertSame(BuiltinGraphicTools.SELECTION, draw.getFirst()),
        () ->
            assertEquals(
                List.of(BuiltinGraphicTools.ARROW, BuiltinGraphicTools.TEXT),
                MeasurementProfiles.keysOf(draw.subList(1, 3))));
  }

  @Test
  void a_profile_without_tools_shows_the_whole_palette() {
    MeasurementProfile all =
        new MeasurementProfile("all", "All", null, null, null, null, null, true); // NON-NLS
    assertEquals(
        registry.prototypes(ToolCategory.MEASURE),
        MeasurementProfiles.tools(all, registry, ToolCategory.MEASURE));
    assertEquals(
        registry.prototypes(ToolCategory.DRAW),
        MeasurementProfiles.tools(null, registry, ToolCategory.DRAW));
  }
}
