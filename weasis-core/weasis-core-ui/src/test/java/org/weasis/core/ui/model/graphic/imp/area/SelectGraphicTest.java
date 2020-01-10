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

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.List;

import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.test.testers.GraphicTester;

public class SelectGraphicTest extends GraphicTester<SelectGraphic> {
    private static final String XML_0 = "/graphic/select/select.graphic.0.xml"; //$NON-NLS-1$
    private static final String XML_1 = "/graphic/select/select.graphic.1.xml"; //$NON-NLS-1$

    public static final String BASIC_TPL = "<selectGraphic fill=\"%s\" showLabel=\"%s\" thickness=\"%s\" uuid=\"%s\">" //$NON-NLS-1$
        + "<paint rgb=\"%s\"/>" //$NON-NLS-1$
        + "<pts/>" //$NON-NLS-1$
        + "</selectGraphic>"; //$NON-NLS-1$

    public static final SelectGraphic COMPLETE_OBJECT = new SelectGraphic();
    static {
        COMPLETE_OBJECT.setUuid(GRAPHIC_UUID_1);

        List<Point2D.Double> pts = Arrays.asList(new Point2D.Double(1440.5, 1161.0), new Point2D.Double(1769.5, 1328.0),
            new Point2D.Double(1769.5, 1161.0), new Point2D.Double(1440.5, 1328.0), new Point2D.Double(1605.0, 1161.0),
            new Point2D.Double(1605.0, 1328.0), new Point2D.Double(1769.5, 1244.5), new Point2D.Double(1440.5, 1244.5));
        COMPLETE_OBJECT.setPts(pts);
    }

    @Override
    public String getTemplate() {
        return BASIC_TPL;
    }

    @Override
    public Object[] getParameters() {
        return new Object[] { Graphic.DEFAULT_FILLED, Graphic.DEFAULT_LABEL_VISISIBLE, Graphic.DEFAULT_LINE_THICKNESS,
            getGraphicUuid(), WProperties.color2Hexadecimal(Color.WHITE, true) };
    }

    @Override
    public void additionalTestsForDeserializeBasicGraphic(SelectGraphic result, SelectGraphic expected) {
        assertThat(result.isGraphicComplete()).isFalse();
    }

    @Override
    protected void checkDefaultValues(Graphic result) {
        assertThat(result.getSelected()).isTrue();
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
    public SelectGraphic getExpectedDeserializeCompleteGraphic() {
        return COMPLETE_OBJECT;
    }
}
