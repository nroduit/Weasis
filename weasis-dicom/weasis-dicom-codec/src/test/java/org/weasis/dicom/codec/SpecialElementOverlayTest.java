/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.function.Supplier;
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
class SpecialElementOverlayTest {

  private static Graphic square(double x) {
    return new NonEditableGraphic(new Rectangle2D.Double(x, x, 10, 10));
  }

  /** An overlay drawing the given graphics on every image. */
  private static SpecialElementOverlay overlay(
      String name, boolean applies, Supplier<List<Graphic>> graphics) {
    return new SpecialElementOverlay() {
      @Override
      public LayerType getOverlayLayerType() {
        return LayerType.DICOM_SR;
      }

      @Override
      public String getOverlayLayerName() {
        return name;
      }

      @Override
      public boolean appliesTo(DicomImageElement image) {
        return applies;
      }

      @Override
      public List<Graphic> buildOverlayGraphics(DicomImageElement image) {
        return graphics.get();
      }
    };
  }

  private static List<GraphicLayer> srLayers(GraphicModel model) {
    return model.getLayers().stream().filter(l -> l.getType() == LayerType.DICOM_SR).toList();
  }

  @Test
  void a_new_reference_with_the_same_graphic_count_replaces_the_previous_layer() {
    // Issue #922: a graphic count is not an identity
    GraphicModel model = new XmlGraphicModel();
    Graphic first = square(0);
    Graphic second = square(100);

    SpecialElementOverlay.apply(model, null, LayerType.DICOM_SR, List.of());
    assertTrue(model.getModels().isEmpty());

    DicomImageElement image = org.mockito.Mockito.mock(DicomImageElement.class);
    SpecialElementOverlay.apply(
        model, image, LayerType.DICOM_SR, List.of(overlay("A", true, () -> List.of(first))));
    assertEquals(List.of(first), model.getModels());

    SpecialElementOverlay.apply(
        model, image, LayerType.DICOM_SR, List.of(overlay("A", true, () -> List.of(second))));
    assertEquals(List.of(second), model.getModels());
    assertEquals(1, srLayers(model).size());
  }

  @Test
  void each_element_gets_its_own_locked_layer_and_non_matching_elements_are_skipped() {
    GraphicModel model = new XmlGraphicModel();
    DicomImageElement image = org.mockito.Mockito.mock(DicomImageElement.class);

    SpecialElementOverlay.apply(
        model,
        image,
        LayerType.DICOM_SR,
        List.of(
            overlay("Report 1", true, () -> List.of(square(0), square(1))),
            overlay("Other image", false, () -> List.of(square(50))),
            overlay("Empty", true, List::of),
            overlay("Report 2", true, () -> List.of(square(2)))));

    List<GraphicLayer> layers = srLayers(model);
    assertEquals(
        List.of("Report 1", "Report 2"), layers.stream().map(GraphicLayer::getName).toList());
    assertEquals(3, model.getModels().size());
    assertTrue(layers.stream().allMatch(l -> l.getLocked() && !l.getSelectable()));
    assertEquals(
        SpecialElementOverlay.OVERLAY_LAYER_LEVEL + 1, layers.get(1).getLevel().intValue());
  }

  @Test
  void other_layer_types_are_left_alone() {
    GraphicModel model = new XmlGraphicModel();
    Graphic measurement = square(50);
    measurement.setLayer(new DefaultLayer(LayerType.MEASURE));
    model.addGraphic(measurement);
    DicomImageElement image = org.mockito.Mockito.mock(DicomImageElement.class);
    SpecialElementOverlay.apply(
        model, image, LayerType.DICOM_SR, List.of(overlay("A", true, () -> List.of(square(0)))));
    assertEquals(2, model.getModels().size());

    SpecialElementOverlay.apply(model, image, LayerType.DICOM_SR, List.of());

    assertEquals(List.of(measurement), model.getModels());
    assertTrue(srLayers(model).isEmpty());
    assertFalse(model.getLayers().isEmpty());
  }
}
