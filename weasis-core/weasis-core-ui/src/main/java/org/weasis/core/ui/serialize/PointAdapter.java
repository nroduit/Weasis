package org.weasis.core.ui.serialize;

import java.awt.geom.Point2D;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.adapters.XmlAdapter;

public class PointAdapter {

    static class Pt {
        @XmlAttribute(required = true)
        double x;
        @XmlAttribute(required = true)
        double y;
    }

    public static class Point2DAdapter extends XmlAdapter<Pt, Point2D> {

        @Override
        public Pt marshal(Point2D v) throws Exception {
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
