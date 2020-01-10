/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.model.graphic.imp;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.List;

import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.test.testers.GraphicTester;

public class AnnotationGraphicTest extends GraphicTester<AnnotationGraphic> {
    private static final String XML_0 = "/graphic/annotation/annotation.graphic.0.xml"; //$NON-NLS-1$
    private static final String XML_1 = "/graphic/annotation/annotation.graphic.1.xml"; //$NON-NLS-1$

    static final String BASIC_TPL = "<annotation fill=\"%s\" showLabel=\"%s\" thickness=\"%s\" uuid=\"%s\">" //$NON-NLS-1$
        + "<paint rgb=\"%s\"/>" //$NON-NLS-1$
        + "<pts/>" //$NON-NLS-1$
        + "</annotation>"; //$NON-NLS-1$

    public static final AnnotationGraphic COMPLETE_OBJECT = new AnnotationGraphic();
    static {
        COMPLETE_OBJECT.setUuid(GRAPHIC_UUID_1);
        COMPLETE_OBJECT.setLineThickness(8.0f);
        COMPLETE_OBJECT.setColorPaint(Color.GRAY);
        List<Point2D.Double> pts = Arrays.asList(new Point2D.Double(1281.5, 856.0), new Point2D.Double(1347.5, 1068.0));
        COMPLETE_OBJECT.setPts(pts);
        COMPLETE_OBJECT.setLabelHeight(13.125);
        COMPLETE_OBJECT.setLabelWidth(93.140625);
        COMPLETE_OBJECT.setLabelBounds(new Rectangle2D.Double(1294.9296875, 1042.3125, 105.140625, 51.375));
        COMPLETE_OBJECT.setLabels(new String[] { "Lorem ipsum", "Test 2", "weasis blablabla" }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    @Override
    public String getTemplate() {
        return BASIC_TPL;
    }

    @Override
    public Object[] getParameters() {
        return new Object[] { Graphic.DEFAULT_FILLED, Graphic.DEFAULT_LABEL_VISISIBLE, Graphic.DEFAULT_LINE_THICKNESS,
            getGraphicUuid(), WProperties.color2Hexadecimal(Graphic.DEFAULT_COLOR, true) };
    }

    public static void checkForDeserializeBasicGraphic(AnnotationGraphic result, AnnotationGraphic expected) {
        assertThat(result.getLabels()).isNullOrEmpty();
        assertThat(result.getLabelBounds()).isNull();
        assertThat(result.getLabelWidth()).isNull();
        assertThat(result.getLabelHeight()).isNull();
    }

    @Override
    public void additionalTestsForDeserializeBasicGraphic(AnnotationGraphic result, AnnotationGraphic expected) {
        checkForDeserializeBasicGraphic(result, expected);
    }

    @Override
    public String getXmlFilePathCase0() {
        return XML_0;
    }

    @Override
    public String getXmlFilePathCase1() {
        return XML_1;
    }

    @Override
    public AnnotationGraphic getExpectedDeserializeCompleteGraphic() {
        return COMPLETE_OBJECT;
    }

}
