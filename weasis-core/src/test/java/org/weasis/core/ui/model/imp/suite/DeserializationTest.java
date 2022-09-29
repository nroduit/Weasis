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

import java.io.InputStream;
import org.assertj.core.api.Fail;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.model.ReferencedImage;
import org.weasis.core.ui.model.ReferencedSeries;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.imp.PointGraphic;
import org.weasis.core.ui.model.imp.XmlGraphicModel;
import org.weasis.core.ui.model.layer.Layer;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.layer.imp.DefaultLayer;
import org.weasis.core.ui.test.utils.ModelListHelper;
import org.xml.sax.SAXParseException;

class DeserializationTest extends ModelListHelper {
  public static final String XML_0 = "/presentation/presentation.0.xml"; // NON-NLS
  public static final String XML_1 = "/presentation/presentation.1.xml"; // NON-NLS
  public static final String XML_2 = "/presentation/presentation.2.xml"; // NON-NLS
  public static final String XML_3 = "/presentation/presentation.3.xml"; // NON-NLS
  public static final String XML_4 = "/presentation/presentation.4.xml"; // NON-NLS
  public static final String XML_5 = "/presentation/presentation.5.xml"; // NON-NLS

  @Test
  void test_empty_xml() {
    InputStream xml_0 = checkXml(XML_0);

    try {
      deserialize(xml_0, XmlGraphicModel.class);
      Fail.fail("Must throws an exception"); // NON-NLS
    } catch (Exception e) {
      assertThat(e).hasCauseExactlyInstanceOf(SAXParseException.class);
    }

    InputStream xml_1 = checkXml(XML_1);

    try {
      deserialize(xml_1, XmlGraphicModel.class);
      Fail.fail("Must throws an exception"); // NON-NLS
    } catch (Exception e) {
      assertThat(e).hasCauseExactlyInstanceOf(SAXParseException.class);
    }
  }

  @Test
  void test_empty_presentation() throws Exception {
    InputStream xml = checkXml(XML_2);

    XmlGraphicModel result = deserialize(xml, XmlGraphicModel.class);

    assertThat(result).isNotNull();
    assertThat(result.getUuid()).isNotNull().isNotEmpty();
    assertThat(result.getReferencedSeries()).isEmpty();
    assertThat(result.getModels()).isEmpty();
    assertThat(result.getLayers()).isEmpty();
    assertThat(result.getLayerCount()).isZero();
    assertThat(result.getAllGraphics()).isEmpty();
  }

  @Test
  void test_basic_presentation() throws Exception {
    InputStream xml = checkXml(XML_3);

    XmlGraphicModel result = deserialize(xml, XmlGraphicModel.class);
    XmlGraphicModel expected = new XmlGraphicModel();

    assertThat(result).isNotNull();
    assertThat(result.getUuid()).isEqualTo(PRESENTATION_UUID_0);

    assertThat(result.getReferencedSeries()).isEqualTo(expected.getReferencedSeries()).isEmpty();
    assertThat(result.getModels()).isEqualTo(expected.getModels()).isEmpty();
    assertThat(result.getLayers()).isEqualTo(expected.getLayers()).isEmpty();
    assertThat(result.getLayerCount()).isEqualTo(expected.getLayerCount()).isEqualTo(0);
    assertThat(result.getAllGraphics()).isEqualTo(expected.getAllGraphics()).isEmpty();
  }

  @Test
  void test_basic_presentation_with_image_reference() throws Exception {
    ImageElement img = mockImage(PRESENTATION_UUID_1, PRESENTATION_UUID_2);
    InputStream xml = checkXml(XML_4);

    XmlGraphicModel result = deserialize(xml, XmlGraphicModel.class);
    XmlGraphicModel expected = new XmlGraphicModel(img);

    assertThat(result).isNotNull();
    assertThat(result.getUuid()).isEqualTo(PRESENTATION_UUID_0);

    assertThat(result.getReferencedSeries()).hasSize(1);
    assertThat(expected.getReferencedSeries()).hasSize(1);

    ReferencedSeries resultRef = result.getReferencedSeries().get(0);
    ReferencedSeries expectedRef = expected.getReferencedSeries().get(0);

    assertThat(resultRef.getUuid()).isEqualTo(expectedRef.getUuid()).isEqualTo(PRESENTATION_UUID_2);
    assertThat(resultRef.getImages()).hasSize(1);
    assertThat(expectedRef.getImages()).hasSize(1);

    ReferencedImage resultImgRef = resultRef.getImages().get(0);
    ReferencedImage expectedImgRef = expectedRef.getImages().get(0);
    assertThat(resultImgRef.getUuid())
        .isEqualTo(expectedImgRef.getUuid())
        .isEqualTo(PRESENTATION_UUID_1);

    assertThat(result.getModels()).isEqualTo(expected.getModels()).isEmpty();
    assertThat(result.getLayers()).isEqualTo(expected.getLayers()).isEmpty();
    assertThat(result.getLayerCount()).isEqualTo(expected.getLayerCount()).isEqualTo(0);
    assertThat(result.getAllGraphics()).isEqualTo(expected.getAllGraphics()).isEmpty();
  }

  @Test
  void test_presentation_with_one_graphic() throws Exception {
    InputStream xml = checkXml(XML_5);
    XmlGraphicModel result = deserialize(xml, XmlGraphicModel.class);

    assertThat(result).isNotNull();
    assertThat(result.getUuid()).isEqualTo(PRESENTATION_UUID_0);

    assertThat(result.getModels()).hasSize(1);
    assertThat(result.getLayers()).hasSize(1);

    Layer layer = result.getLayers().get(0);
    assertThat(layer).isInstanceOf(DefaultLayer.class);
    assertThat(layer.getUuid()).isEqualTo(LAYER_UUID_0);
    assertThat(layer.getLevel()).isEqualTo(40);
    assertThat(layer.getName()).isNull();
    assertThat(layer.getType()).isEqualTo(LayerType.DRAW);
    assertThat(layer.getVisible()).isEqualTo(Boolean.TRUE);

    Graphic graphic = result.getModels().get(0);
    assertThat(graphic).isInstanceOf(PointGraphic.class);
    assertThat(graphic.getUuid()).isEqualTo(GRAPHIC_UUID_0);
  }

  private InputStream checkXml(String path) {
    InputStream xml = getClass().getResourceAsStream(path);
    assertThat(xml).isNotNull();
    return xml;
  }
}
