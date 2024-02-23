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

import java.io.InputStream;
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

class DeserializationTest extends ModelListHelper {
  public static final String XML_0 = "/presentation/presentation.0.xml"; // NON-NLS
  public static final String XML_1 = "/presentation/presentation.1.xml"; // NON-NLS
  public static final String XML_2 = "/presentation/presentation.2.xml"; // NON-NLS
  public static final String XML_3 = "/presentation/presentation.3.xml"; // NON-NLS
  public static final String XML_4 = "/presentation/presentation.4.xml"; // NON-NLS
  public static final String XML_5 = "/presentation/presentation.5.xml"; // NON-NLS

  @Test
  void test_empty_xml() throws Exception {
    InputStream xml_0 = checkXml(XML_0);
    assertThrows(Exception.class, () -> deserialize(xml_0, XmlGraphicModel.class));

    InputStream xml_1 = checkXml(XML_1);
    assertThrows(Exception.class, () -> deserialize(xml_1, XmlGraphicModel.class));
  }

  @Test
  void test_empty_presentation() throws Exception {
    InputStream xml = checkXml(XML_2);

    XmlGraphicModel result = deserialize(xml, XmlGraphicModel.class);

    assertNotNull(result);
    assertNotNull(result.getUuid());
    assertFalse(result.getUuid().isEmpty());
    assertTrue(result.getReferencedSeries().isEmpty());
    assertTrue(result.getModels().isEmpty());
    assertTrue(result.getLayers().isEmpty());
    assertEquals(0, result.getLayerCount());
    assertTrue(result.getAllGraphics().isEmpty());
  }

  @Test
  void test_basic_presentation() throws Exception {
    InputStream xml = checkXml(XML_3);

    XmlGraphicModel result = deserialize(xml, XmlGraphicModel.class);
    XmlGraphicModel expected = new XmlGraphicModel();

    assertNotNull(result);
    assertEquals(PRESENTATION_UUID_0, result.getUuid());

    assertEquals(expected.getReferencedSeries(), result.getReferencedSeries());
    assertTrue(result.getReferencedSeries().isEmpty());
    assertEquals(expected.getModels(), result.getModels());
    assertTrue(result.getModels().isEmpty());
    assertEquals(expected.getLayers(), result.getLayers());
    assertTrue(result.getLayers().isEmpty());
    assertEquals(expected.getLayerCount(), result.getLayerCount());
    assertEquals(0, result.getLayerCount());
    assertEquals(expected.getAllGraphics(), result.getAllGraphics());
    assertTrue(result.getAllGraphics().isEmpty());
  }

  @Test
  void test_basic_presentation_with_image_reference() throws Exception {
    ImageElement img = mockImage(PRESENTATION_UUID_1, PRESENTATION_UUID_2);
    InputStream xml = checkXml(XML_4);

    XmlGraphicModel result = deserialize(xml, XmlGraphicModel.class);
    XmlGraphicModel expected = new XmlGraphicModel(img);

    assertNotNull(result);
    assertEquals(PRESENTATION_UUID_0, result.getUuid());

    assertEquals(1, result.getReferencedSeries().size());
    assertEquals(1, expected.getReferencedSeries().size());

    ReferencedSeries resultRef = result.getReferencedSeries().getFirst();
    ReferencedSeries expectedRef = expected.getReferencedSeries().getFirst();

    assertEquals(expectedRef.getUuid(), resultRef.getUuid());
    assertEquals(PRESENTATION_UUID_2, resultRef.getUuid());
    assertEquals(1, resultRef.getImages().size());
    assertEquals(1, expectedRef.getImages().size());

    ReferencedImage resultImgRef = resultRef.getImages().getFirst();
    ReferencedImage expectedImgRef = expectedRef.getImages().getFirst();
    assertEquals(expectedImgRef.getUuid(), resultImgRef.getUuid());
    assertEquals(PRESENTATION_UUID_1, resultImgRef.getUuid());

    assertEquals(expected.getModels(), result.getModels());
    assertTrue(result.getModels().isEmpty());
    assertEquals(expected.getLayers(), result.getLayers());
    assertTrue(result.getLayers().isEmpty());
    assertEquals(expected.getLayerCount(), result.getLayerCount());
    assertEquals(0, result.getLayerCount());
    assertEquals(expected.getAllGraphics(), result.getAllGraphics());
    assertTrue(result.getAllGraphics().isEmpty());
  }

  @Test
  void test_presentation_with_one_graphic() throws Exception {
    InputStream xml = checkXml(XML_5);
    XmlGraphicModel result = deserialize(xml, XmlGraphicModel.class);

    assertNotNull(result);
    assertEquals(PRESENTATION_UUID_0, result.getUuid());

    assertEquals(1, result.getModels().size());
    assertEquals(1, result.getLayers().size());

    Layer layer = result.getLayers().getFirst();
    assertInstanceOf(DefaultLayer.class, layer);
    assertEquals(LAYER_UUID_0, layer.getUuid());
    assertEquals(40, layer.getLevel());
    assertNull(layer.getName());
    assertEquals(LayerType.DRAW, layer.getType());
    assertEquals(Boolean.TRUE, layer.getVisible());

    Graphic graphic = result.getModels().getFirst();
    assertInstanceOf(PointGraphic.class, graphic);
    assertEquals(GRAPHIC_UUID_0, graphic.getUuid());
  }

  private InputStream checkXml(String path) {
    InputStream xml = getClass().getResourceAsStream(path);
    assertNotNull(xml);
    return xml;
  }
}
