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

import static org.assertj.core.api.Assertions.assertThat;

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

    assertThat(actual)
        .isInstanceOfAny(
            DefaultUUID.class,
            GraphicModel.class,
            AbstractGraphicModel.class,
            XmlGraphicModel.class);

    assertThat(actual.getReferencedSeries()).hasSize(1);
    assertThat(actual.getReferencedSeries().get(0).getUuid()).isEqualTo(UUID_2);
    assertThat(actual.getReferencedSeries().get(0).getImages()).hasSize(1);
    assertThat(actual.getReferencedSeries().get(0).getImages().get(0).getUuid()).isEqualTo(UUID_1);

    assertThat(actual.getLayers()).isEmpty();
    assertThat(actual.getModels()).isEmpty();
    assertThat(actual.getAllGraphics()).isEmpty();
    assertThat(actual.groupLayerByType()).isEmpty();
    assertThat(actual.getSelectedDraggableGraphics()).isEmpty();
    assertThat(actual.getSelectedGraphics()).isEmpty();
    assertThat(actual.getGraphicSelectionListeners()).isEmpty();

    assertThat(actual.getLayerCount()).isZero();
  }

  @Test
  void test_image_with_no_uuid_and_series_uuid() {
    ImageElement img = mockImage(null, UUID_2);
    GraphicModel actual = new XmlGraphicModel(img);

    assertThat(actual)
        .isInstanceOfAny(
            DefaultUUID.class,
            GraphicModel.class,
            AbstractGraphicModel.class,
            XmlGraphicModel.class);

    assertThat(actual.getReferencedSeries()).hasSize(1);

    ReferencedSeries referencedSeries = actual.getReferencedSeries().get(0);
    assertThat(referencedSeries).isNotNull();
    assertThat(referencedSeries.getUuid()).isEqualTo(UUID_2);
    assertThat(referencedSeries.getImages()).hasSize(1);

    ReferencedImage referencedImage = referencedSeries.getImages().get(0);
    assertThat(referencedImage).isNotNull();
    assertThat(referencedImage.getUuid()).isNotNull().isNotEmpty();

    assertThat(actual.getLayers()).isEmpty();
    assertThat(actual.getModels()).isEmpty();
    assertThat(actual.getAllGraphics()).isEmpty();
    assertThat(actual.groupLayerByType()).isEmpty();
    assertThat(actual.getSelectedDraggableGraphics()).isEmpty();
    assertThat(actual.getSelectedGraphics()).isEmpty();
    assertThat(actual.getGraphicSelectionListeners()).isEmpty();

    assertThat(actual.getLayerCount()).isZero();
  }

  @Test
  void test_image_with_uuid_and_no_series_uuid() throws Exception {
    ImageElement img = mockImage(UUID_1, null);
    GraphicModel actual = new XmlGraphicModel(img);

    assertThat(actual)
        .isInstanceOfAny(
            DefaultUUID.class,
            GraphicModel.class,
            AbstractGraphicModel.class,
            XmlGraphicModel.class);

    ReferencedSeries referencedSeries = actual.getReferencedSeries().get(0);
    assertThat(referencedSeries).isNotNull();
    assertThat(referencedSeries.getUuid()).isNotNull().isNotEmpty();
    assertThat(referencedSeries.getImages()).hasSize(1);

    ReferencedImage referencedImage = referencedSeries.getImages().get(0);
    assertThat(referencedImage).isNotNull();
    assertThat(referencedImage.getUuid()).isEqualTo(UUID_1);

    assertThat(actual.getLayers()).isEmpty();
    assertThat(actual.getModels()).isEmpty();
    assertThat(actual.getAllGraphics()).isEmpty();
    assertThat(actual.groupLayerByType()).isEmpty();
    assertThat(actual.getSelectedDraggableGraphics()).isEmpty();
    assertThat(actual.getSelectedGraphics()).isEmpty();
    assertThat(actual.getGraphicSelectionListeners()).isEmpty();

    assertThat(actual.getLayerCount()).isZero();
  }

  @Test
  void test_image_with_no_uuid_and_no_series_uuid() {
    ImageElement img = mockImage(null, null);
    GraphicModel actual = new XmlGraphicModel(img);

    assertThat(actual)
        .isInstanceOfAny(
            DefaultUUID.class,
            GraphicModel.class,
            AbstractGraphicModel.class,
            XmlGraphicModel.class);

    ReferencedSeries referencedSeries = actual.getReferencedSeries().get(0);
    assertThat(referencedSeries).isNotNull();
    assertThat(referencedSeries.getUuid()).isNotNull().isNotEmpty();
    assertThat(referencedSeries.getImages()).hasSize(1);

    ReferencedImage referencedImage = referencedSeries.getImages().get(0);
    assertThat(referencedImage).isNotNull();
    assertThat(referencedImage.getUuid()).isNotNull().isNotEmpty();

    assertThat(actual.getLayers()).isEmpty();
    assertThat(actual.getModels()).isEmpty();
    assertThat(actual.getAllGraphics()).isEmpty();
    assertThat(actual.groupLayerByType()).isEmpty();
    assertThat(actual.getSelectedDraggableGraphics()).isEmpty();
    assertThat(actual.getSelectedGraphics()).isEmpty();
    assertThat(actual.getGraphicSelectionListeners()).isEmpty();

    assertThat(actual.getLayerCount()).isZero();
  }
}
