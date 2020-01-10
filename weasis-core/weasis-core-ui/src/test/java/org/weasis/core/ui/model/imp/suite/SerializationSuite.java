/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.model.imp.suite;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
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

public class SerializationSuite extends ModelListHelper {
    @Test
    public void test_empty_model() throws Exception {
        GraphicModel model = new XmlGraphicModel();
        model.setUuid(UUID_1);

        String actual = serialize(model);
        String expected = String.format(
            TPL_XML_PREFIX + "<presentation uuid=\"%s\"><references/><layers/><graphics/></presentation>", UUID_1); //$NON-NLS-1$
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void test_add_one_point_graphic() throws Exception {
        GraphicModel model = new XmlGraphicModel();
        model.setUuid(UUID_1);

        Graphic graphic = PointGraphicTest.COMPLETE_OBJECT;
        model.addGraphic(graphic);

        assertThat(model.getLayers()).hasSize(1);
        Layer layer = model.getLayers().get(0);

        String actual = serialize(model);
        String expected = String.format(TPL_XML_PREFIX + "<presentation uuid=\"%1$s\">" //$NON-NLS-1$
            + "<references/>" //$NON-NLS-1$
            + "<layers>" //$NON-NLS-1$
            + "<layer level=\"40\" locked=\"false\" selectable=\"true\" type=\"DRAW\" visible=\"true\" uuid=\"%2$s\"/>" //$NON-NLS-1$
            + "</layers>" //$NON-NLS-1$
            + "<graphics>" //$NON-NLS-1$
            + "<point pointSize=\"1\" fill=\"false\" showLabel=\"true\" thickness=\"3.0\" " //$NON-NLS-1$
            + "uuid=\"%3$s\">" //$NON-NLS-1$
            + "<paint rgb=\"ffff0000\"/>" //$NON-NLS-1$
            + "<layer>%2$s</layer>" //$NON-NLS-1$
            + "<pts>" //$NON-NLS-1$
            + "<pt x=\"1665.5\" y=\"987.0\"/>" //$NON-NLS-1$
            + "</pts>" //$NON-NLS-1$
            + "</point>" //$NON-NLS-1$
            + "</graphics>" //$NON-NLS-1$
            + "</presentation>", //$NON-NLS-1$
            UUID_1, layer.getUuid(), graphic.getUuid());

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void test_with_image_element() throws Exception {
        ImageElement img = mockImage(UUID_2, UUID_3);
        GraphicModel model = new XmlGraphicModel(img);
        model.setUuid(UUID_1);

        String actual = serialize(model);
        String expected = String.format(TPL_XML_PREFIX + "<presentation uuid=\"%s\">" //$NON-NLS-1$
            + "<references>" //$NON-NLS-1$
            + "<series uuid=\"%s\">" //$NON-NLS-1$
            + "<image frames=\"\" uuid=\"%s\"/>" //$NON-NLS-1$
            + "</series>" //$NON-NLS-1$
            + "</references>" //$NON-NLS-1$
            + "<layers/>" //$NON-NLS-1$
            + "<graphics/>" //$NON-NLS-1$
            + "</presentation>", //$NON-NLS-1$
            UUID_1, UUID_3, UUID_2);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void test_with_mulitiple_graphics_and_one_layer() throws Exception {
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

        assertThat(model.getLayers()).hasSize(1);
        Layer layer = model.getLayers().get(0);

        assertThat(pointGraphic.getLayer()).isEqualTo(layer);
        assertThat(pixelInfoGraphic.getLayer()).isEqualTo(layer);
        assertThat(annotationGraphic.getLayer()).isEqualTo(layer);
        assertThat(lineGraphic.getLayer()).isEqualTo(layer);
        assertThat(polylineGraphic.getLayer()).isEqualTo(layer);
        assertThat(polygonGraphic.getLayer()).isEqualTo(layer);
        assertThat(rectangleGraphic.getLayer()).isEqualTo(layer);
        assertThat(angleGraphic.getLayer()).isEqualTo(layer);
        assertThat(cobbAngleGraphic.getLayer()).isEqualTo(layer);

        String actual = serialize(model);
        String expected = String.format(TPL_XML_PREFIX + "<presentation uuid=\"%s\">" //$NON-NLS-1$
            + "<references>" //$NON-NLS-1$
            + "<series uuid=\"%s\">" //$NON-NLS-1$
            + "<image frames=\"\" uuid=\"%s\"/>" //$NON-NLS-1$
            + "</series>" //$NON-NLS-1$
            + "</references>" //$NON-NLS-1$
            + "<layers>" //$NON-NLS-1$
            + serializeWithoutHeader(layer) + "</layers>" //$NON-NLS-1$
            + "<graphics>" //$NON-NLS-1$
            + serializeWithoutHeader(pointGraphic) + serializeWithoutHeader(pixelInfoGraphic)
            + serializeWithoutHeader(annotationGraphic) + serializeWithoutHeader(lineGraphic)
            + serializeWithoutHeader(parallelLine) + serializeWithoutHeader(polylineGraphic)
            + serializeWithoutHeader(polygonGraphic) + serializeWithoutHeader(rectangleGraphic)
            + serializeWithoutHeader(angleGraphic) + serializeWithoutHeader(cobbAngleGraphic) + "</graphics>" //$NON-NLS-1$
            + "</presentation>", UUID_1, UUID_3, UUID_2); //$NON-NLS-1$

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void test_with_mulitiple_graphics_and_multiple_layers() throws Exception {
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

        assertThat(model.getLayers()).hasSize(3);
        Layer layer1 = model.getLayers().get(0);
        Layer layer2 = model.getLayers().get(1);
        Layer layer3 = model.getLayers().get(2);

        assertThat(layer1).isNotNull();
        assertThat(layer2).isNotNull();
        assertThat(layer3).isNotNull();

        assertThat(pointGraphic.getLayer()).isEqualTo(layer1);
        assertThat(pixelInfoGraphic.getLayer()).isEqualTo(layer2);
        assertThat(annotationGraphic.getLayer()).isEqualTo(layer1);
        assertThat(lineGraphic.getLayer()).isEqualTo(layer3);
        assertThat(polylineGraphic.getLayer()).isEqualTo(layer1);
        assertThat(polygonGraphic.getLayer()).isEqualTo(layer1);
        assertThat(rectangleGraphic.getLayer()).isEqualTo(layer3);
        assertThat(angleGraphic.getLayer()).isEqualTo(layer1);
        assertThat(cobbAngleGraphic.getLayer()).isEqualTo(layer1);

        String actual = serialize(model);
        String expected = String.format(TPL_XML_PREFIX + "<presentation uuid=\"%s\">" //$NON-NLS-1$
            + "<references>" //$NON-NLS-1$
            + "<series uuid=\"%s\">" //$NON-NLS-1$
            + "<image frames=\"\" uuid=\"%s\"/>" //$NON-NLS-1$
            + "</series>" //$NON-NLS-1$
            + "</references>" //$NON-NLS-1$
            + "<layers>" //$NON-NLS-1$
            + serializeWithoutHeader(layer1) + serializeWithoutHeader(layer2) + serializeWithoutHeader(layer3)
            + "</layers>" //$NON-NLS-1$
            + "<graphics>" //$NON-NLS-1$
            + serializeWithoutHeader(pointGraphic) + serializeWithoutHeader(pixelInfoGraphic)
            + serializeWithoutHeader(annotationGraphic) + serializeWithoutHeader(lineGraphic)
            + serializeWithoutHeader(parallelLine) + serializeWithoutHeader(polylineGraphic)
            + serializeWithoutHeader(polygonGraphic) + serializeWithoutHeader(rectangleGraphic)
            + serializeWithoutHeader(angleGraphic) + serializeWithoutHeader(cobbAngleGraphic) + "</graphics>" //$NON-NLS-1$
            + "</presentation>", UUID_1, UUID_3, UUID_2); //$NON-NLS-1$

        assertThat(actual).isEqualTo(expected);
        consoleDisplay(model);
    }
}
