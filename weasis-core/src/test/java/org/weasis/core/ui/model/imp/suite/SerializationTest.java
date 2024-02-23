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
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.imp.AnnotationGraphicTest;
import org.weasis.core.ui.model.graphic.imp.PixelInfoGraphicTest;
import org.weasis.core.ui.model.graphic.imp.PointGraphicTest;
import org.weasis.core.ui.model.graphic.imp.angle.AngleToolGraphicTest;
import org.weasis.core.ui.model.graphic.imp.angle.CobbAngleToolGraphicTest;
import org.weasis.core.ui.model.graphic.imp.area.PolygonGraphicTest;
import org.weasis.core.ui.model.graphic.imp.area.RectangleGraphicTest;
import org.weasis.core.ui.model.graphic.imp.line.LineGraphicTest;
import org.weasis.core.ui.model.graphic.imp.line.ParallelLineGraphicTest;
import org.weasis.core.ui.model.graphic.imp.line.PolylineGraphicTest;
import org.weasis.core.ui.model.imp.XmlGraphicModel;
import org.weasis.core.ui.model.layer.Layer;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.test.utils.ModelListHelper;

class SerializationTest extends ModelListHelper {
  @Test
  void test_empty_model() throws Exception {
    GraphicModel model = new XmlGraphicModel();
    model.setUuid(UUID_1);

    String actual = serialize(model);
    String expected =
        String.format(
            TPL_XML_PREFIX
                + "<presentation uuid=\"%s\"><references/><layers/><graphics/></presentation>", // NON-NLS
            UUID_1);
    assertEquals(expected, actual);
  }

  @Test
  void test_add_one_point_graphic() throws Exception {
    GraphicModel model = new XmlGraphicModel();
    model.setUuid(UUID_1);

    Graphic graphic = PointGraphicTest.COMPLETE_OBJECT;
    model.addGraphic(graphic);

    assertEquals(1, model.getLayers().size());
    Layer layer = model.getLayers().getFirst();

    String actual = serialize(model);
    String expected =
        String.format(
            TPL_XML_PREFIX
                + "<presentation uuid=\"%1$s\">" // NON-NLS
                + "<references/>" // NON-NLS
                + "<layers>" // NON-NLS
                + "<layer level=\"40\" locked=\"false\" selectable=\"true\" type=\"DRAW\" visible=\"true\" uuid=\"%2$s\"/>" // NON-NLS
                + "</layers>" // NON-NLS
                + "<graphics>" // NON-NLS
                + "<point pointSize=\"1\" fillOpacity=\"1.0\" fill=\"false\" showLabel=\"true\" thickness=\"3.0\" " // NON-NLS
                + "uuid=\"%3$s\">" // NON-NLS
                + "<paint rgb=\"ffff0000\"/>" // NON-NLS
                + "<layer>%2$s</layer>" // NON-NLS
                + "<pts>" // NON-NLS
                + "<pt x=\"1665.5\" y=\"987.0\"/>" // NON-NLS
                + "</pts>" // NON-NLS
                + "</point>" // NON-NLS
                + "</graphics>" // NON-NLS
                + "</presentation>", // NON-NLS
            UUID_1,
            layer.getUuid(),
            graphic.getUuid());

    assertEquals(expected, actual);
  }

  @Test
  void test_with_image_element() throws Exception {
    ImageElement img = mockImage(UUID_2, UUID_3);
    GraphicModel model = new XmlGraphicModel(img);
    model.setUuid(UUID_1);

    String actual = serialize(model);
    String expected =
        String.format(
            TPL_XML_PREFIX
                + "<presentation uuid=\"%s\">" // NON-NLS
                + "<references>" // NON-NLS
                + "<series uuid=\"%s\">" // NON-NLS
                + "<image frames=\"\" uuid=\"%s\"/>" // NON-NLS
                + "</series>" // NON-NLS
                + "</references>" // NON-NLS
                + "<layers/>" // NON-NLS
                + "<graphics/>" // NON-NLS
                + "</presentation>", // NON-NLS
            UUID_1,
            UUID_3,
            UUID_2);

    assertEquals(expected, actual);
  }

  @Test
  void test_with_multiple_graphics_and_one_layer() throws Exception {
    ImageElement img = mockImage(UUID_2, UUID_3);
    GraphicModel model = new XmlGraphicModel(img);
    model.setUuid(UUID_1);

    Graphic pointGraphic = PointGraphicTest.COMPLETE_OBJECT.copy();
    Graphic pixelInfoGraphic = PixelInfoGraphicTest.COMPLETE_OBJECT.copy();
    Graphic annotationGraphic = AnnotationGraphicTest.COMPLETE_OBJECT.copy();
    Graphic lineGraphic = LineGraphicTest.COMPLETE_OBJECT.copy();
    Graphic parallelLine = ParallelLineGraphicTest.COMPLETE_OBJECT.copy();
    Graphic polylineGraphic = PolylineGraphicTest.COMPLETE_OBJECT.copy();
    Graphic polygonGraphic = PolygonGraphicTest.COMPLETE_OBJECT.copy();
    Graphic rectangleGraphic = RectangleGraphicTest.COMPLETE_OBJECT.copy();
    Graphic angleGraphic = AngleToolGraphicTest.COMPLETE_OBJECT.copy();
    Graphic cobbAngleGraphic = CobbAngleToolGraphicTest.COMPLETE_OBJECT.copy();

    model.addGraphic(pointGraphic);
    model.addGraphic(pixelInfoGraphic);
    model.addGraphic(annotationGraphic);
    model.addGraphic(lineGraphic);
    model.addGraphic(parallelLine);
    model.addGraphic(polylineGraphic);
    model.addGraphic(polygonGraphic);
    model.addGraphic(rectangleGraphic);
    model.addGraphic(angleGraphic);
    model.addGraphic(cobbAngleGraphic);

    assertEquals(1, model.getLayers().size());
    Layer layer = model.getLayers().getFirst();

    assertEquals(layer, pointGraphic.getLayer());
    assertEquals(layer, pixelInfoGraphic.getLayer());
    assertEquals(layer, annotationGraphic.getLayer());
    assertEquals(layer, lineGraphic.getLayer());
    assertEquals(layer, polylineGraphic.getLayer());
    assertEquals(layer, polygonGraphic.getLayer());
    assertEquals(layer, rectangleGraphic.getLayer());
    assertEquals(layer, angleGraphic.getLayer());
    assertEquals(layer, cobbAngleGraphic.getLayer());

    String actual = serialize(model);
    String expected =
        String.format(
            TPL_XML_PREFIX
                + "<presentation uuid=\"%s\">" // NON-NLS
                + "<references>" // NON-NLS
                + "<series uuid=\"%s\">" // NON-NLS
                + "<image frames=\"\" uuid=\"%s\"/>" // NON-NLS
                + "</series>" // NON-NLS
                + "</references>" // NON-NLS
                + "<layers>" // NON-NLS
                + serializeWithoutHeader(layer)
                + "</layers>" // NON-NLS
                + "<graphics>" // NON-NLS
                + serializeWithoutHeader(pointGraphic)
                + serializeWithoutHeader(pixelInfoGraphic)
                + serializeWithoutHeader(annotationGraphic)
                + serializeWithoutHeader(lineGraphic)
                + serializeWithoutHeader(parallelLine)
                + serializeWithoutHeader(polylineGraphic)
                + serializeWithoutHeader(polygonGraphic)
                + serializeWithoutHeader(rectangleGraphic)
                + serializeWithoutHeader(angleGraphic)
                + serializeWithoutHeader(cobbAngleGraphic)
                + "</graphics>" // NON-NLS
                + "</presentation>", // NON-NLS
            UUID_1,
            UUID_3,
            UUID_2);

    assertEquals(expected, actual);
  }

  @Test
  void test_with_multiple_graphics_and_multiple_layers() throws Exception {
    ImageElement img = mockImage(UUID_2, UUID_3);
    GraphicModel model = new XmlGraphicModel(img);
    model.setUuid(UUID_1);

    Graphic pointGraphic = PointGraphicTest.COMPLETE_OBJECT.copy();
    Graphic pixelInfoGraphic = PixelInfoGraphicTest.COMPLETE_OBJECT.copy();
    Graphic annotationGraphic = AnnotationGraphicTest.COMPLETE_OBJECT.copy();
    Graphic lineGraphic = LineGraphicTest.COMPLETE_OBJECT.copy();
    Graphic parallelLine = ParallelLineGraphicTest.COMPLETE_OBJECT.copy();
    Graphic polylineGraphic = PolylineGraphicTest.COMPLETE_OBJECT.copy();
    Graphic polygonGraphic = PolygonGraphicTest.COMPLETE_OBJECT.copy();
    Graphic rectangleGraphic = RectangleGraphicTest.COMPLETE_OBJECT.copy();
    Graphic angleGraphic = AngleToolGraphicTest.COMPLETE_OBJECT.copy();
    Graphic cobbAngleGraphic = CobbAngleToolGraphicTest.COMPLETE_OBJECT.copy();

    pixelInfoGraphic.setLayerType(LayerType.ANNOTATION);
    lineGraphic.setLayerType(LayerType.MEASURE);
    rectangleGraphic.setLayerType(LayerType.MEASURE);

    model.addGraphic(pointGraphic);
    model.addGraphic(pixelInfoGraphic);
    model.addGraphic(annotationGraphic);
    model.addGraphic(lineGraphic);
    model.addGraphic(parallelLine);
    model.addGraphic(polylineGraphic);
    model.addGraphic(polygonGraphic);
    model.addGraphic(rectangleGraphic);
    model.addGraphic(angleGraphic);
    model.addGraphic(cobbAngleGraphic);

    assertEquals(3, model.getLayers().size());
    Layer layer1 = model.getLayers().get(0);
    Layer layer2 = model.getLayers().get(1);
    Layer layer3 = model.getLayers().get(2);

    assertNotNull(layer1);
    assertNotNull(layer2);
    assertNotNull(layer3);

    assertEquals(layer1, pointGraphic.getLayer());
    assertEquals(layer2, pixelInfoGraphic.getLayer());
    assertEquals(layer1, annotationGraphic.getLayer());
    assertEquals(layer3, lineGraphic.getLayer());
    assertEquals(layer1, polylineGraphic.getLayer());
    assertEquals(layer1, polygonGraphic.getLayer());
    assertEquals(layer3, rectangleGraphic.getLayer());
    assertEquals(layer1, angleGraphic.getLayer());
    assertEquals(layer1, cobbAngleGraphic.getLayer());

    String actual = serialize(model);
    String expected =
        String.format(
            TPL_XML_PREFIX
                + "<presentation uuid=\"%s\">" // NON-NLS
                + "<references>" // NON-NLS
                + "<series uuid=\"%s\">" // NON-NLS
                + "<image frames=\"\" uuid=\"%s\"/>" // NON-NLS
                + "</series>" // NON-NLS
                + "</references>" // NON-NLS
                + "<layers>" // NON-NLS
                + serializeWithoutHeader(layer1)
                + serializeWithoutHeader(layer2)
                + serializeWithoutHeader(layer3)
                + "</layers>" // NON-NLS
                + "<graphics>" // NON-NLS
                + serializeWithoutHeader(pointGraphic)
                + serializeWithoutHeader(pixelInfoGraphic)
                + serializeWithoutHeader(annotationGraphic)
                + serializeWithoutHeader(lineGraphic)
                + serializeWithoutHeader(parallelLine)
                + serializeWithoutHeader(polylineGraphic)
                + serializeWithoutHeader(polygonGraphic)
                + serializeWithoutHeader(rectangleGraphic)
                + serializeWithoutHeader(angleGraphic)
                + serializeWithoutHeader(cobbAngleGraphic)
                + "</graphics>" // NON-NLS
                + "</presentation>", // NON-NLS
            UUID_1,
            UUID_3,
            UUID_2);

    assertEquals(expected, actual);
    consoleDisplay(model);
  }
}
