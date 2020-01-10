/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.model.graphic.imp.area;

import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.List;

import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.test.testers.GraphicTester;

public class ThreePointsCircleGraphicTest extends GraphicTester<ThreePointsCircleGraphic> {
    private static final String XML_0 = "/graphic/threePointsCircle/threePointsCircle.graphic.0.xml"; //$NON-NLS-1$
    private static final String XML_1 = "/graphic/threePointsCircle/threePointsCircle.graphic.1.xml"; //$NON-NLS-1$

    static final String BASIC_TPL = "<threePointsCircle fill=\"%s\" showLabel=\"%s\" thickness=\"%s\" uuid=\"%s\">" //$NON-NLS-1$
        + "<paint rgb=\"%s\"/>" //$NON-NLS-1$
        + "<pts/>" //$NON-NLS-1$
        + "</threePointsCircle>"; //$NON-NLS-1$

    public static final ThreePointsCircleGraphic COMPLETE_OBJECT = new ThreePointsCircleGraphic();
    static {
        COMPLETE_OBJECT.setUuid(GRAPHIC_UUID_1);
        COMPLETE_OBJECT.setFilled(Boolean.TRUE);

        List<Point2D.Double> pts = Arrays.asList(new Point2D.Double(1293.5, 1023.0), new Point2D.Double(1461.5, 1156.0),
            new Point2D.Double(1494.5, 1090.0));
        COMPLETE_OBJECT.setPts(pts);
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

    @Override
    public String getXmlFilePathCase0() {
        return XML_0;
    }

    @Override
    public String getXmlFilePathCase1() {
        return XML_1;
    }

    @Override
    public ThreePointsCircleGraphic getExpectedDeserializeCompleteGraphic() {
        return COMPLETE_OBJECT;
    }
}
