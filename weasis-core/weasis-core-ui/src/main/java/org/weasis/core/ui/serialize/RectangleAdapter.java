/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.serialize;

import java.awt.geom.Rectangle2D;
import java.util.Objects;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.adapters.XmlAdapter;

public class RectangleAdapter {

    private RectangleAdapter() {
    }

    static class RectanglePt {
        @XmlAttribute(required = true)
        double x;
        @XmlAttribute(required = true)
        double y;
        @XmlAttribute(required = true)
        double width;
        @XmlAttribute
        public double height;
    }

    public static class Rectangle2DAdapter extends XmlAdapter<RectanglePt, Rectangle2D> {

        @Override
        public RectanglePt marshal(Rectangle2D v) throws Exception {
            if (Objects.isNull(v)) {
                return null;
            }
            RectanglePt p = new RectanglePt();
            p.x = v.getX();
            p.y = v.getY();
            p.width = v.getWidth();
            p.height = v.getHeight();
            return p;
        }

        @Override
        public Rectangle2D unmarshal(RectanglePt v) throws Exception {
            return new Rectangle2D.Double(v.x, v.y, v.width, v.height);
        }
    }
}
