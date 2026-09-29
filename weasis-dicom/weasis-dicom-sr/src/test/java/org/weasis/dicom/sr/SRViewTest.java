/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.sr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Rectangle2D;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.imp.NonEditableGraphic;
import org.weasis.core.ui.model.imp.XmlGraphicModel;
import org.weasis.core.ui.model.layer.GraphicLayer;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.layer.imp.DefaultLayer;

@DisplayNameGeneration(ReplaceUnderscores.class)
class SRViewTest {

  private static Graphic square(double x) {
    return new NonEditableGraphic(new Rectangle2D.Double(x, x, 10, 10));
  }

  private static GraphicLayer scoordLayer(GraphicModel model) {
    return model.getLayers().stream()
        .filter(l -> SRReader.SCOORD_LAYER_NAME.equals(l.getName()))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void a_second_reference_with_the_same_graphic_count_replaces_the_first_one() {
    // Issue #922: a graphic count is not an identity
    GraphicModel model = new XmlGraphicModel();
    Graphic first = square(0);
    Graphic second = square(100);

    SRView.updateScoordLayer(model, List.of(first));
    assertEquals(List.of(first), model.getModels());
    GraphicLayer layer = scoordLayer(model);
    assertEquals(LayerType.DICOM_SR, layer.getType());
    assertSame(layer, first.getLayer());

    SRView.updateScoordLayer(model, List.of(second));
    assertEquals(List.of(second), model.getModels());
    assertSame(layer, scoordLayer(model), "the layer instance is reused");
    assertSame(layer, second.getLayer());
  }

  @Test
  void clicking_the_same_reference_twice_keeps_a_single_copy() {
    GraphicModel model = new XmlGraphicModel();
    Graphic g = square(0);

    SRView.updateScoordLayer(model, List.of(g));
    SRView.updateScoordLayer(model, List.of(g));

    assertEquals(List.of(g), model.getModels());
    assertEquals(1, model.getLayers().size());
  }

  @Test
  void an_empty_reference_clears_the_layer_and_leaves_other_layers_alone() {
    GraphicModel model = new XmlGraphicModel();
    Graphic measurement = square(50);
    measurement.setLayer(new DefaultLayer(LayerType.MEASURE));
    model.addGraphic(measurement);
    SRView.updateScoordLayer(model, List.of(square(0), square(1)));
    assertEquals(3, model.getModels().size());

    SRView.updateScoordLayer(model, List.of());

    assertEquals(List.of(measurement), model.getModels());
    assertFalse(
        model.getLayers().stream().anyMatch(l -> SRReader.SCOORD_LAYER_NAME.equals(l.getName())));
    assertTrue(model.getLayers().stream().anyMatch(l -> l.getType() == LayerType.MEASURE));
  }
}
