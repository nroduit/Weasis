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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.geom.Point2D;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.opencv.core.Point3;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.ui.model.graphic.imp.BuiltinGraphicTools;
import org.weasis.core.ui.model.graphic.imp.line.LineGraphic;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;

@DisplayNameGeneration(ReplaceUnderscores.class)
class MeasurementServiceTest {

  private final MeasurementService service =
      new MeasurementService(new GraphicRegistry(property -> false));

  private static List<Point2D> segment() {
    return List.of(new Point2D.Double(0, 0), new Point2D.Double(30, 40));
  }

  @Test
  void create_builds_a_configured_graphic_of_the_tool() throws Exception {
    Graphic graphic = service.create(BuiltinGraphicTools.LINE, segment());
    assertAll(
        () -> assertInstanceOf(LineGraphic.class, graphic),
        () -> assertEquals(LayerType.MEASURE, graphic.getLayerType()),
        () -> assertTrue(graphic.isGraphicComplete()),
        () -> assertEquals(2, graphic.getPts().size()));
  }

  @Test
  void create_with_geometry_anchors_the_graphic_in_patient_space() throws Exception {
    Graphic graphic = service.create(BuiltinGraphicTools.LINE, segment(), SpatialAnchorTest.AXIAL);
    assertAll(
        () -> assertEquals(2, service.patientPoints(graphic).size()),
        () -> assertEquals(new Point3(15, 20, 10), service.patientPoints(graphic).get(1)),
        () ->
            assertTrue(
                service
                    .patientPoints(service.create(BuiltinGraphicTools.LINE, segment()))
                    .isEmpty()));
  }

  @Test
  void create_rejects_unknown_tools_and_invalid_shapes() {
    assertThrows(IllegalArgumentException.class, () -> service.create("nope.tool", segment()));
    assertThrows(
        InvalidShapeException.class,
        () -> service.create(BuiltinGraphicTools.ANGLE, List.of(new Point2D.Double(1, 1))));
  }

  @Test
  void measure_returns_keyed_values_in_the_layer_unit() throws Exception {
    MeasurableLayer layer = mock(MeasurableLayer.class);
    when(layer.hasContent()).thenReturn(true);
    when(layer.getMeasurementAdapter(any()))
        .thenReturn(new MeasurementsAdapter(0.5, 0, 0, false, 100, "mm")); // NON-NLS

    Graphic graphic = service.create(BuiltinGraphicTools.LINE, segment());
    List<MeasureItem> items = service.measure(graphic, layer, Unit.MILLIMETER);
    MeasureItem length =
        items.stream().filter(i -> "length".equals(i.getKey())).findFirst().orElseThrow();
    assertAll(
        () -> assertEquals(25.0, ((Number) length.getValue()).doubleValue(), 1e-9),
        () -> assertEquals("mm", length.getUnit()),
        () -> assertTrue(items.stream().anyMatch(i -> "azimuth".equals(i.getKey()))));
  }
}
