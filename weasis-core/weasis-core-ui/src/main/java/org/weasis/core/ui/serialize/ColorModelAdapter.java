/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.ui.serialize;

import java.awt.Color;
import java.awt.Paint;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.adapters.XmlAdapter;

import org.weasis.core.api.service.WProperties;

public class ColorModelAdapter {

    private ColorModelAdapter() {
    }

    static class ColorModel {
        @XmlAttribute(required = true)
        String rgb;
    }

    public static class ColorAdapter extends XmlAdapter<ColorModel, Color> {

        @Override
        public ColorModel marshal(Color color) throws Exception {
            ColorModel m = new ColorModel();
            m.rgb = WProperties.color2Hexadecimal(color, true);
            return m;
        }

        @Override
        public Color unmarshal(ColorModel c) throws Exception {
            return WProperties.hexadecimal2Color(c.rgb);
        }
    }

    public static class PaintAdapter extends XmlAdapter<ColorModel, Paint> {

        @Override
        public ColorModel marshal(Paint color) throws Exception {
            ColorModel m = new ColorModel();
            if (color instanceof Color) {
                m.rgb = WProperties.color2Hexadecimal((Color) color, true);
            }
            return m;
        }

        @Override
        public Color unmarshal(ColorModel c) throws Exception {
            return WProperties.hexadecimal2Color(c.rgb);
        }
    }
}