/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.imp.suite;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.model.AbstractGraphicModel;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.ReferencedImage;
import org.weasis.core.ui.model.ReferencedSeries;
import org.weasis.core.ui.model.imp.XmlGraphicModel;
import org.weasis.core.ui.model.utils.imp.DefaultUUID;
import org.weasis.core.ui.test.utils.ModelListHelper;

class ConstructorImageElementTest extends ModelListHelper {

  @Test
  void test_image_with_uuid_and_series_uuid() {
    ImageElement img = mockImage(UUID_1, UUID_2);
    GraphicModel actual = new XmlGraphicModel(img);

    assertInstanceOf(DefaultUUID.class, actual);
    assertInstanceOf(GraphicModel.class, actual);
    assertInstanceOf(AbstractGraphicModel.class, actual);
    assertInstanceOf(XmlGraphicModel.class, actual);

    assertEquals(1, actual.getReferencedSeries().size());
    assertEquals(UUID_2, actual.getReferencedSeries().getFirst().getUuid());
    assertEquals(1, actual.getReferencedSeries().getFirst().getImages().size());
    assertEquals(UUID_1, actual.getReferencedSeries().getFirst().getImages().getFirst().getUuid());

    assertTrue(actual.getLayers().isEmpty());
    assertTrue(actual.getModels().isEmpty());
    assertTrue(actual.getAllGraphics().isEmpty());
    assertTrue(actual.groupLayerByType().isEmpty());
    assertTrue(actual.getSelectedDraggableGraphics().isEmpty());
    assertTrue(actual.getSelectedGraphics().isEmpty());
    assertTrue(actual.getGraphicSelectionListeners().isEmpty());

    assertEquals(0, actual.getLayerCount());
  }

  @Test
  void test_image_with_no_uuid_and_series_uuid() {
    ImageElement img = mockImage(null, UUID_2);
    GraphicModel actual = new XmlGraphicModel(img);

    assertInstanceOf(DefaultUUID.class, actual);
    assertInstanceOf(GraphicModel.class, actual);
    assertInstanceOf(AbstractGraphicModel.class, actual);
    assertInstanceOf(XmlGraphicModel.class, actual);

    assertEquals(1, actual.getReferencedSeries().size());

    ReferencedSeries referencedSeries = actual.getReferencedSeries().getFirst();
    assertNotNull(referencedSeries);
    assertEquals(UUID_2, referencedSeries.getUuid());
    assertEquals(1, referencedSeries.getImages().size());

    ReferencedImage referencedImage = referencedSeries.getImages().getFirst();
    assertNotNull(referencedImage);
    assertNotNull(referencedImage.getUuid());
    assertFalse(referencedImage.getUuid().isEmpty());

    assertTrue(actual.getLayers().isEmpty());
    assertTrue(actual.getModels().isEmpty());
    assertTrue(actual.getAllGraphics().isEmpty());
    assertTrue(actual.groupLayerByType().isEmpty());
    assertTrue(actual.getSelectedDraggableGraphics().isEmpty());
    assertTrue(actual.getSelectedGraphics().isEmpty());
    assertTrue(actual.getGraphicSelectionListeners().isEmpty());

    assertEquals(0, actual.getLayerCount());
  }

  @Test
  void test_image_with_uuid_and_no_series_uuid() throws Exception {
    ImageElement img = mockImage(UUID_1, null);
    GraphicModel actual = new XmlGraphicModel(img);

    assertInstanceOf(DefaultUUID.class, actual);
    assertInstanceOf(GraphicModel.class, actual);
    assertInstanceOf(AbstractGraphicModel.class, actual);
    assertInstanceOf(XmlGraphicModel.class, actual);

    ReferencedSeries referencedSeries = actual.getReferencedSeries().getFirst();
    assertNotNull(referencedSeries);
    assertNotNull(referencedSeries.getUuid());
    assertFalse(referencedSeries.getUuid().isEmpty());
    assertEquals(1, referencedSeries.getImages().size());

    ReferencedImage referencedImage = referencedSeries.getImages().getFirst();
    assertNotNull(referencedImage);
    assertEquals(UUID_1, referencedImage.getUuid());

    assertTrue(actual.getLayers().isEmpty());
    assertTrue(actual.getModels().isEmpty());
    assertTrue(actual.getAllGraphics().isEmpty());
    assertTrue(actual.groupLayerByType().isEmpty());
    assertTrue(actual.getSelectedDraggableGraphics().isEmpty());
    assertTrue(actual.getSelectedGraphics().isEmpty());
    assertTrue(actual.getGraphicSelectionListeners().isEmpty());

    assertEquals(0, actual.getLayerCount());
  }

  @Test
  void test_image_with_no_uuid_and_no_series_uuid() {
    ImageElement img = mockImage(null, null);
    GraphicModel actual = new XmlGraphicModel(img);

    assertInstanceOf(DefaultUUID.class, actual);
    assertInstanceOf(GraphicModel.class, actual);
    assertInstanceOf(AbstractGraphicModel.class, actual);
    assertInstanceOf(XmlGraphicModel.class, actual);

    ReferencedSeries referencedSeries = actual.getReferencedSeries().getFirst();
    assertNotNull(referencedSeries);
    assertNotNull(referencedSeries.getUuid());
    assertFalse(referencedSeries.getUuid().isEmpty());
    assertEquals(1, referencedSeries.getImages().size());

    ReferencedImage referencedImage = referencedSeries.getImages().getFirst();
    assertNotNull(referencedImage);
    assertNotNull(referencedImage.getUuid());
    assertFalse(referencedImage.getUuid().isEmpty());

    assertTrue(actual.getLayers().isEmpty());
    assertTrue(actual.getModels().isEmpty());
    assertTrue(actual.getAllGraphics().isEmpty());
    assertTrue(actual.groupLayerByType().isEmpty());
    assertTrue(actual.getSelectedDraggableGraphics().isEmpty());
    assertTrue(actual.getSelectedGraphics().isEmpty());
    assertTrue(actual.getGraphicSelectionListeners().isEmpty());

    assertEquals(0, actual.getLayerCount());
  }
}
