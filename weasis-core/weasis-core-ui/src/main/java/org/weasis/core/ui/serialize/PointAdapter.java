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

import java.awt.geom.Point2D;
import java.util.Objects;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.adapters.XmlAdapter;

public class PointAdapter {

    private PointAdapter() {
    }

    static class Pt {
        @XmlAttribute(required = true)
        double x;
        @XmlAttribute(required = true)
        double y;
    }

    public static class Point2DAdapter extends XmlAdapter<Pt, Point2D> {

        @Override
        public Pt marshal(Point2D v) throws Exception {
            if (Objects.isNull(v)) {
                return null;
            }
            Pt p = new Pt();
            p.x = v.getX();
            p.y = v.getY();
            return p;
        }

        @Override
        public Point2D unmarshal(Pt v) throws Exception {
            return new Point2D.Double(v.x, v.y);
        }
    }
}
