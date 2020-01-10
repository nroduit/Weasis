/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.model.graphic.imp.line;

import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.List;

import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.test.testers.GraphicTester;

public class PerpendicularLineGraphicTest extends GraphicTester<PerpendicularLineGraphic> {
    private static final String XML_0 = "/graphic/perpendicularLine/perpendicularLine.graphic.0.xml"; //$NON-NLS-1$
    private static final String XML_1 = "/graphic/perpendicularLine/perpendicularLine.graphic.1.xml"; //$NON-NLS-1$

    static final String BASIC_TPL = "<perpendicularLine fill=\"%s\" showLabel=\"%s\" thickness=\"%s\" uuid=\"%s\">" //$NON-NLS-1$
        + "<paint rgb=\"%s\"/>" //$NON-NLS-1$
        + "<pts/>" //$NON-NLS-1$
        + "</perpendicularLine>"; //$NON-NLS-1$

    public static final PerpendicularLineGraphic COMPLETE_OBJECT = new PerpendicularLineGraphic();
    static {
        COMPLETE_OBJECT.setUuid(GRAPHIC_UUID_1);

        List<Point2D.Double> pts = Arrays.asList(new Point2D.Double(1131.5, 980.0), new Point2D.Double(1330.5, 1178.0),
            new Point2D.Double(1355.5, 1089.0), new Point2D.Double(1298.5635365776284, 1146.2240213184443));
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
    public PerpendicularLineGraphic getExpectedDeserializeCompleteGraphic() {
        return COMPLETE_OBJECT;
    }
}
