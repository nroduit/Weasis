package org.weasis.core.ui.serialize;

import java.awt.geom.Point2D;

import org.simpleframework.xml.convert.Converter;
import org.simpleframework.xml.stream.InputNode;
import org.simpleframework.xml.stream.OutputNode;

public class Point2DConverter implements Converter<Point2D> {

    @Override
    public Point2D read(InputNode node) {
        double x = 0;
        double y = 0;
        try {
            x = Double.parseDouble(node.getAttribute("x").getValue()); //$NON-NLS-1$
            y = Double.parseDouble(node.getAttribute("y").getValue()); //$NON-NLS-1$
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new Point2D.Double(x, y);
    }

    @Override
    public void write(OutputNode node, Point2D pt) throws Exception {
        if (pt != null) {
            node.setAttribute("x", Double.toString(pt.getX())); //$NON-NLS-1$
            node.setAttribute("y", Double.toString(pt.getY())); //$NON-NLS-1$
        }
    }
}