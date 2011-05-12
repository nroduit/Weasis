package org.weasis.core.ui.graphic;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;

/**
 * <p>
 * Title: JMicroVision
 * </p>
 * 
 * <p>
 * Description: Image processing and analysis
 * </p>
 * 
 * <p>
 * Copyright: Copyright (c) 2002 -2005
 * </p>
 * 
 * <p>
 * Company:
 * </p>
 * 
 * @author Nicolas Roduit
 * @version 1.2.0
 */
public class RasterizeGraphicsToCoord {

    public static ArrayList<ChainPoint> rasterizeSegment(int x0, int y0, int x1, int y1) {
        ArrayList<ChainPoint> list = new ArrayList<ChainPoint>();
        int dx = x1 - x0;
        int dy = y1 - y0;
        int stepy = 1;
        int stepx = 1;
        int fraction;

        if (dx < 0) {
            dx = -dx;
            stepx = -1;
        }

        if (dy < 0) {
            dy = -dy;
            stepy = -1;
        }

        dx <<= 1; // dx is now 2*dx
        dy <<= 1; // dy is now 2*dy
        list.add(new ChainPoint(x0, y0));
        if (dx > dy) {
            fraction = dy - (dx >> 1); // same as 2*dy - dx
            while (x0 != x1) {
                if (fraction >= 0) {
                    y0 += stepy;
                    fraction -= dx;
                }
                x0 += stepx;
                fraction += dy;
                list.add(new ChainPoint(x0, y0));
            }
        } else {
            fraction = dx - (dy >> 1);
            while (y0 != y1) {
                if (fraction >= 0) {
                    x0 += stepx;
                    fraction -= dy;
                }
                y0 += stepy;
                fraction += dx;
                list.add(new ChainPoint(x0, y0));
            }
        }
        list.trimToSize();
        return list;
    }

    public static ArrayList<ChainPoint> rasterizeFreehandline(float[] coord) {
        ArrayList<ChainPoint> list = new ArrayList<ChainPoint>();
        if (coord != null && coord.length > 2) {
            ChainPoint last = new ChainPoint((int) coord[0], (int) coord[1]);
            for (int m = 2; m < coord.length; m = m + 2) {
                if (Math.abs(coord[m] - coord[m - 2]) > 1 || Math.abs(coord[m + 1] - coord[m - 1]) > 1) {
                    list.addAll(rasterizeSegment(last.x, last.y, (int) coord[m], (int) coord[m + 1]));
                    last = list.remove(list.size() - 1);
                } else {
                    list.add(last);
                    last = new ChainPoint((int) coord[m], (int) coord[m + 1]);
                }
            }
        }
        list.trimToSize();
        return list;
    }

    public static byte[] getFreemanCodeFromFreehandline(float[] coord) {
        ArrayList<ChainPoint> list = rasterizeFreehandline(coord);
        byte[] tab = new byte[list.size() - 1];
        for (int i = 0; i < tab.length; i++) {
            tab[i] = getFreemanDirection(list.get(i), list.get(i + 1));
        }
        return tab;
    }

    private static byte getFreemanDirection(ChainPoint last, ChainPoint next) {
        int x = next.x - last.x;
        int y = next.y - last.y;
        if (x == 1) {
            if (y == 1) {
                return (byte) 7;
            } else if (y == -1) {
                return (byte) 1;
            } else {
                return (byte) 0;
            }
        } else if (x == -1) {
            if (y == 1) {
                return (byte) 5;
            } else if (y == -1) {
                return (byte) 3;
            } else {
                return (byte) 4;
            }
        } else {
            if (y == 1) {
                return (byte) 6;
            } else {
                return (byte) 2;
            }
        }
    }

    public static ArrayList<ChainPoint> rasterizeRectangle(int x, int y, int width, int height) {
        ArrayList<ChainPoint> list = new ArrayList<ChainPoint>((width + height - 2) * 2);
        int limit = y + height;
        int staticVal = x;
        for (int i = y; i < limit; i++) {
            list.add(new ChainPoint(staticVal, i));
        }
        limit = x + width;
        staticVal = y + height - 1;
        for (int i = x + 1; i < limit; i++) {
            list.add(new ChainPoint(i, staticVal));
        }
        limit = y;
        staticVal = x + width - 1;
        for (int i = y + height - 2; i >= limit; i--) {
            list.add(new ChainPoint(staticVal, i));
        }
        limit = x + 1;
        staticVal = y;
        for (int i = x + width - 2; i >= limit; i--) {
            list.add(new ChainPoint(i, staticVal));
        }
        return list;
    }

    public static ArrayList<ChainPoint> rasterizeEllipse(int mx, int my, int a, int b) {
        ArrayList<ChainPoint>[] list1 = drawEllipsePart(mx, my, a, b, false);
        ArrayList<ChainPoint>[] list2 = drawEllipsePart(mx, my, b, a, true);

        for (int i = 0; i < list1.length; i++) {
            ArrayList<ChainPoint> chain1;
            ArrayList<ChainPoint> chain2;
            if (i % 2 == 0) {
                chain1 = list1[i];
                chain2 = list2[i];
            } else {
                chain1 = list2[i];
                chain2 = list1[i];
            }
            if (!chain1.get(chain1.size() - 1).equals(chain2.get(chain2.size() - 1))) {
                chain1.add(chain2.get(chain2.size() - 1));
            }
            for (int j = chain2.size() - 2; j > 0; j--) {
                chain1.add(chain2.get(j));
            }
        }
        list1[0].addAll(list2[1]);
        list1[0].addAll(list1[2]);
        list1[0].addAll(list2[3]);

        // methode pour obtenir que la chaine commence bien par le pixel le plus en haut � gauche
        int miny = list1[0].get(0).y;
        int k = 1;
        while (list1[0].get(k).y <= miny) {
            k++;
            if (k >= list1[0].size()) {
                break;
            }
        }
        if (k > 1) {
            for (int i = 0; i < k - 1; i++) {
                list1[0].add(list1[0].get(i));
            }
            for (int i = k - 2; i >= 0; i--) {
                list1[0].remove(i);
            }

        }
        list1[0].trimToSize();
        return list1[0];
    }

    private static ArrayList<ChainPoint>[] drawEllipsePart(int mx, int my, int a, int b, boolean mirror) {
        ArrayList<ChainPoint>[] list = new ArrayList[4];
        for (int i = 0; i < list.length; i++) {
            list[i] = new ArrayList<ChainPoint>();
        }
        int x = 0, y = b, a2 = a * a, b2 = b * b, d = b2 - a2 * b + a2 / 4;
        while (b2 * x <= a2 * y) {
            drawEllipsePoint(list, mx, my, x, y, mirror);
            d += 2 * (b2 * x++ - (d >= 0 ? a2 * --y : 0)) + 3 * b2;
        }
        return list;
    }

    private static void drawEllipsePoint(ArrayList<ChainPoint> list[], int mx, int my, int x, int y, boolean mirror) {
        if (mirror) {
            list[0].add(new ChainPoint(mx - y, my - x));
            list[1].add(new ChainPoint(mx - y, my + x));
            list[2].add(new ChainPoint(mx + y, my + x));
            list[3].add(new ChainPoint(mx + y, my - x));
        } else {
            list[0].add(new ChainPoint(mx - x, my - y));
            list[1].add(new ChainPoint(mx - x, my + y));
            list[2].add(new ChainPoint(mx + x, my + y));
            list[3].add(new ChainPoint(mx + x, my - y));
        }
    }

    /**
     * Tranform line coordinates that are in 8 connectivity to 4 connectivity, the line is drawn counterclockwise and
     * the added pixels are inside the shape. Ensure a 8 connectivity object will hit the background line.
     * 
     * @param points
     *            ArrayList
     * @return ArrayList
     */
    public static ArrayList<ChainPoint> convertToBackgroundLine(ArrayList<ChainPoint> points) {
        int size = points.size();
        ArrayList<ChainPoint> bckPts = new ArrayList<ChainPoint>(size);
        if (size > 0) {
            bckPts.add(points.get(0));
            for (int i = 1; i < size; i++) {
                ChainPoint p1 = points.get(i - 1);
                ChainPoint p2 = points.get(i);
                bckPts.add(p2);
                int val = (p2.x - p1.x) * (p2.y - p1.y);
                if (val != 0) {
                    if (val == 1) {
                        bckPts.add(new ChainPoint(p1.y, p2.x));
                    } else {
                        bckPts.add(new ChainPoint(p1.x, p2.y));
                    }
                }
            }
        }
        bckPts.trimToSize();
        return bckPts;
    }

    public static float[] transformToCounterCockWiseCoord(float[] coord) {
        if (coord == null || coord.length < 6) {
            return coord;
        }
        int index = 0;
        float miny = Float.MAX_VALUE;
        float minx = Float.MIN_VALUE;
        for (int m = 1; m < coord.length; m = m + 2) {
            if (coord[m] < miny || (coord[m] == miny && coord[m - 1] < minx)) {
                miny = coord[m];
                minx = coord[m - 1];
                index = m - 1;
            }
        }
        float[] newCoord = new float[coord.length];
        for (int i = index; i < coord.length; i++) {
            newCoord[i - index] = coord[i];
        }
        for (int i = 0; i < index; i++) {
            newCoord[i + coord.length - index] = coord[i];
        }
        if (newCoord[coord.length - 2] < newCoord[2]) {
            float[] newCoord2 = new float[coord.length];
            newCoord2[0] = newCoord[0];
            newCoord2[1] = newCoord[1];
            for (int m = 3, k = coord.length - 1; m < coord.length; m = m + 2, k--) {
                newCoord2[m] = newCoord[k];
                newCoord2[m - 1] = newCoord[--k];
            }
            newCoord = newCoord2;
        }
        return newCoord;
    }

    public static ArrayList<ChainPoint> rasterizePolygon(float[] coord) {
        ArrayList<ChainPoint> list = new ArrayList<ChainPoint>();
        if (coord != null && coord.length > 5) {
            coord = transformToCounterCockWiseCoord(coord);
            ChainPoint last = new ChainPoint((int) coord[0], (int) coord[1]);
            for (int m = 2; m < coord.length; m = m + 2) {
                list.addAll(rasterizeSegment(last.x, last.y, (int) coord[m], (int) coord[m + 1]));
                last = list.remove(list.size() - 1);
            }
            if (!last.equals(list.get(0))) {
                ChainPoint first = list.get(0);
                list.addAll(rasterizeSegment(last.x, last.y, first.x, first.y));
            }
        }
        return list;
    }

    public static ArrayList<ChainPoint> transformShapeToContour(Shape shape) {
        if (shape == null) {
            return null;
        }
        if (shape instanceof Line2D) {
            Line2D pt = (Line2D) shape;
            return rasterizeSegment(floorInt(pt.getX1()), floorInt(pt.getY1()), floorInt(pt.getX2()),
                floorInt(pt.getY2()));
        }
        if (shape instanceof Rectangle) {
            Rectangle rect = (Rectangle) shape;
            return rasterizeRectangle(rect.x, rect.y, rect.width, rect.height);
        }
        if (shape instanceof Rectangle2D) {
            Rectangle2D rect = (Rectangle2D) shape;
            return rasterizeRectangle(floorInt(rect.getX()), floorInt(rect.getY()), floorInt(rect.getWidth()),
                floorInt(rect.getHeight()));
        }
        if (shape instanceof Ellipse2D) {
            Ellipse2D rect = (Ellipse2D) shape;
            return rasterizeEllipse(floorInt(rect.getCenterX()), floorInt(rect.getCenterY()),
                floorInt(rect.getWidth() / 2.0), floorInt(rect.getHeight() / 2.0));

        }
        ArrayList<ChainPoint> list = new ArrayList<ChainPoint>();
        Rectangle bounds = shape.getBounds();

        int matrixWidth = bounds.width + 1;
        int matrixHeight = bounds.height + 1;
        boolean[] matrix = new boolean[matrixWidth * matrixHeight];

        PathIterator iterator = shape.getPathIterator(null);
        float[] floats = new float[6];
        float x = 0;
        float y = 0;
        int nbSeg = 0;
        while (!iterator.isDone()) {
            int segType = iterator.currentSegment(floats);
            switch (segType) {
                case PathIterator.SEG_MOVETO:
                    x = floats[0];
                    y = floats[1];
                    break;
                case PathIterator.SEG_LINETO:
                case PathIterator.SEG_CLOSE:

                    // drawline((int)x, (int)y, (int)floats[0], (int)floats[1], matrix, matrixWidth, matrixHeight); ;
                    x = floats[0];
                    y = floats[1];
                    nbSeg++;
                    break;
                case PathIterator.SEG_CUBICTO:
                    break;
                case PathIterator.SEG_QUADTO:
                    break;

            }
            iterator.next();
        }
        return list;
    }

    /*
     * public Point2D[] rasterize(final Point2D[] vertices, final int[] vertexIndexes) {
     * 
     * if (vertices == null || vertices.length <= 1) { return vertices; }
     * 
     * if (vertexIndexes != null && vertexIndexes.length < vertices.length) { throw new
     * IllegalArgumentException("size of 'vertexIndexes' less than 'vertices'"); }
     * 
     * final List list = new LinkedList(); final Point lastPoint = new Point();
     * 
     * final LinePixelVisitor visitor = new LinePixelVisitor() {
     * 
     * public void visit(int x, int y) { if (list.size() == 0 || lastPoint.x != x || lastPoint.y != y) { lastPoint.x =
     * x; lastPoint.y = y; list.add(new Point(lastPoint)); } } };
     * 
     * int x0 = MathUtils.floorInt(vertices[0].getX()); int y0 = MathUtils.floorInt(vertices[0].getY()); if
     * (vertexIndexes != null) { vertexIndexes[0] = 0; } for (int i = 1; i < vertices.length; i++) { int x1 =
     * MathUtils.floorInt(vertices[i].getX()); int y1 = MathUtils.floorInt(vertices[i].getY());
     * _lineRasterizer.rasterize(x0, y0, x1, y1, visitor); if (vertexIndexes != null) { vertexIndexes[i] = (list.size()
     * > 0) ? list.size() - 1 : 0; } x0 = x1; y0 = y1; }
     * 
     * return (Point[])list.toArray(new Point[list.size()]); }
     */
    public static int floorInt(final double x) {
        return (int) Math.floor(x);
    }

}
