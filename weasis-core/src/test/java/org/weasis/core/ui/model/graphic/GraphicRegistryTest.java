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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.model.graphic.imp.BuiltinGraphicTools;
import org.weasis.core.ui.model.graphic.imp.area.SelectGraphic;
import org.weasis.core.ui.model.graphic.imp.line.LineGraphic;
import org.weasis.core.ui.model.layer.LayerType;

@DisplayNameGeneration(ReplaceUnderscores.class)
class GraphicRegistryTest {

  private static GraphicRegistry registry() {
    return new GraphicRegistry(property -> false);
  }

  private static GraphicToolProvider provider(GraphicToolDescriptor... tools) {
    return () -> List.of(tools);
  }

  @Test
  void built_in_palettes_keep_their_order_and_start_with_selection() {
    GraphicRegistry registry = registry();
    List<Graphic> measure = registry.prototypes(ToolCategory.MEASURE);
    List<Graphic> draw = registry.prototypes(ToolCategory.DRAW);
    assertAll(
        () -> assertEquals(17, measure.size()),
        () -> assertEquals(10, draw.size()),
        () -> assertSame(BuiltinGraphicTools.SELECTION, measure.getFirst()),
        () -> assertSame(BuiltinGraphicTools.SELECTION, draw.getFirst()),
        () -> assertEquals(LayerType.MEASURE, measure.get(1).getLayerType()),
        () -> assertEquals(LayerType.DRAW, draw.get(1).getLayerType()),
        () -> assertFalse(draw.get(1).getLabelVisible()),
        () -> assertEquals(LayerType.ANNOTATION, draw.getLast().getLayerType()));
  }

  @Test
  void hidden_property_removes_the_tool() {
    GraphicRegistry registry = new GraphicRegistry("weasis.measure.line"::equals);
    assertAll(
        () -> assertEquals(16, registry.prototypes(ToolCategory.MEASURE).size()),
        () -> assertEquals(16, registry.descriptors(ToolCategory.MEASURE).size()),
        () -> assertEquals(10, registry.prototypes(ToolCategory.DRAW).size()),
        () ->
            assertTrue(
                registry.prototypes(ToolCategory.MEASURE).stream()
                    .noneMatch(g -> g.getClass() == LineGraphic.class)),
        () -> assertTrue(registry.prototype(BuiltinGraphicTools.LINE).isPresent()),
        () -> assertTrue(registry.xmlTypes().contains(LineGraphic.class)));
  }

  @Test
  void key_of_matches_prototype_then_class_and_layer_type() {
    GraphicRegistry registry = registry();
    Graphic measureLine = registry.prototype(BuiltinGraphicTools.LINE).orElseThrow();
    Graphic drawnCopy = registry.prototype(BuiltinGraphicTools.DRAW_LINE).orElseThrow().copy();
    Graphic other = new LineGraphic();
    other.setLayerType(LayerType.MEASURE);
    assertAll(
        () -> assertEquals(BuiltinGraphicTools.LINE, registry.keyOf(measureLine).orElseThrow()),
        () -> assertEquals(BuiltinGraphicTools.DRAW_LINE, registry.keyOf(drawnCopy).orElseThrow()),
        () -> assertEquals(BuiltinGraphicTools.LINE, registry.keyOf(other).orElseThrow()),
        () ->
            assertEquals(
                BuiltinGraphicTools.SELECT, registry.keyOf(new SelectGraphic()).orElseThrow()),
        () -> assertTrue(registry.keyOf(null).isEmpty()));
  }

  @Test
  void provider_registration_adds_xml_types_and_notifies_listeners() {
    GraphicRegistry registry = registry();
    AtomicInteger notified = new AtomicInteger();
    registry.addListener(notified::incrementAndGet);
    int builtinTypes = registry.xmlTypes().size();
    GraphicToolProvider provider =
        provider(GraphicToolDescriptor.of("test.star", ToolCategory.ADVANCED, StarGraphic::new));

    registry.register(provider);
    assertAll(
        () -> assertEquals(1, notified.get()),
        () -> assertEquals(builtinTypes + 1, registry.xmlTypes().size()),
        () -> assertTrue(registry.xmlTypes().contains(StarGraphic.class)),
        () -> assertEquals("test.star", registry.keyOf(new StarGraphic()).orElseThrow()),
        () -> assertEquals(2, registry.prototypes(ToolCategory.ADVANCED).size()));

    registry.unregister(provider);
    assertAll(
        () -> assertEquals(2, notified.get()),
        () -> assertEquals(builtinTypes, registry.xmlTypes().size()),
        () -> assertTrue(registry.descriptor("test.star").isEmpty()));
  }

  @Test
  void duplicate_key_keeps_the_first_tool() {
    GraphicRegistry registry = registry();
    Graphic first = registry.prototype(BuiltinGraphicTools.LINE).orElseThrow();
    registry.register(
        provider(
            GraphicToolDescriptor.of(
                BuiltinGraphicTools.LINE, ToolCategory.DRAW, StarGraphic::new)));
    assertSame(first, registry.prototype(BuiltinGraphicTools.LINE).orElseThrow());
  }

  @Test
  void descriptor_rejects_invalid_keys_and_derives_defaults() {
    assertThrows(
        IllegalArgumentException.class,
        () -> GraphicToolDescriptor.of("Bad Key", ToolCategory.MEASURE, LineGraphic::new));
    GraphicToolDescriptor d =
        GraphicToolDescriptor.of("x.y", ToolCategory.MEASURE, LineGraphic::new);
    assertAll(
        () -> assertEquals("weasis.tool.x.y", d.hideProperty()),
        () -> assertEquals(LineGraphic.class, d.xmlType()),
        () ->
            assertEquals(LineGraphic.class, GraphicToolDescriptor.xmlTypeOf(new LineGraphic() {})));
  }
}
