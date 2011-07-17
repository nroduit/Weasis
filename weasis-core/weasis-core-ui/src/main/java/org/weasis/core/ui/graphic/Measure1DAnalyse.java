package org.weasis.core.ui.graphic;

import java.awt.Color;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.util.ArrayList;

import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.gui.util.MathUtil;
import org.weasis.core.api.image.measure.MeasurementsAdapter;

public class Measure1DAnalyse {

    public final static String[] measurList = { "First point X", "First point Y", "Last point X", "Last point Y",
        "Line length", "Orientation", "Orientation significance", "Azimuth", "Azimuth significance", "Barycenter x",
        "Barycenter y", "Color (RGB)" };
    private MeasurementsAdapter adapter;
    private AbstractDragGraphic line;
    private double[] regression;
    private ArrayList<ChainPoint> pointList = null;
    private double length = -1.0;

    /**
     * Measure1DAnalyse
     */
    public Measure1DAnalyse(AbstractDragGraphic line, MeasurementsAdapter adapter) {
        this.line = line;
        this.adapter = adapter;
    }

    public ArrayList getMeasure1DAnalyse(boolean[] selected, int nbMeasures) {
        ArrayList measVal = new ArrayList(nbMeasures);
        if (selected[0]) {
            measVal.add(adapter.getXCalibratedValue(getStartPointX()));
        }
        if (selected[1]) {
            measVal.add(adapter.getYCalibratedValue(getStartPointY()));
        }
        if (selected[2]) {
            measVal.add(adapter.getXCalibratedValue(getEndPointX()));
        }
        if (selected[3]) {
            measVal.add(adapter.getYCalibratedValue(getEndPointY()));
        }
        if (selected[4]) {
            measVal.add(getSegmentLength());
        }
        if (selected[5]) {
            measVal.add(getSegmentOrientation());
        }
        if (selected[6]) {
            measVal.add(getCorrelationCoefficient());
        }
        if (selected[7]) {
            measVal.add(getSegmentAzimuth());
        }
        if (selected[8]) {
            measVal.add(getOrientationIndice());
        }
        if (selected[9]) {
            measVal.add(adapter.getXCalibratedValue(getBarycenterX()));
        }
        if (selected[10]) {
            measVal.add(adapter.getYCalibratedValue(getBarycenterY()));
        }
        if (selected[11]) {
            measVal.add(JMVUtils.getValueRGBasText2((Color) line.getColorPaint()));
        }

        return measVal;
    }

    public double getStartPointX() {
        if (line instanceof LineGraphic)
            return ((LineGraphic) line).getStartPoint().getX();
        // else if (line instanceof FreeHandLineGraphic)
        // return ((FreeHandLineGraphic) line).getFirstX();
        // else if (line instanceof PolygonGraphic)
        // return ((PolygonGraphic) line).getFirstX();
        return 0;
    }

    public double getStartPointY() {
        if (line instanceof LineGraphic)
            return ((LineGraphic) line).getStartPoint().getY();
        // else if (line instanceof FreeHandLineGraphic)
        // return ((FreeHandLineGraphic) line).getFirstY();
        // else if (line instanceof PolygonGraphic)
        // return ((PolygonGraphic) line).getFirstY();

        return 0;
    }

    public double getEndPointX() {
        if (line instanceof LineGraphic)
            return ((LineGraphic) line).getEndPoint().getX();
        // else if (line instanceof FreeHandLineGraphic)
        // return ((FreeHandLineGraphic) line).getLastX();
        // else if (line instanceof PolygonGraphic)
        // return ((PolygonGraphic) line).getLastX();

        return 0;
    }

    public double getEndPointY() {
        if (line instanceof LineGraphic)
            return ((LineGraphic) line).getEndPoint().getY();
        // else if (line instanceof FreeHandLineGraphic)
        // return ((FreeHandLineGraphic) line).getLastY();
        // else if (line instanceof PolygonGraphic)
        // return ((PolygonGraphic) line).getLastY();
        return 0;
    }

    public double getBarycenterX() {
        if (line instanceof LineGraphic) {
            LineGraphic graph = (LineGraphic) line;
            double x1 = graph.getStartPoint().getX();
            double x2 = graph.getEndPoint().getX();
            double offset = x1 > x2 ? x2 : x1;
            return offset + Math.abs((x2 - x1) / 2.0);
        }
        // else if (line instanceof FreeHandLineGraphic)
        // return getBaryCenter(getPointList(((FreeHandLineGraphic) line).getPoints())).getX();
        // else if (line instanceof PolygonGraphic)
        // return getBaryCenter(getPointList(((PolygonGraphic) line).getPoints())).getX();
        return 0;
    }

    public double getBarycenterY() {
        if (line instanceof LineGraphic) {
            LineGraphic graph = (LineGraphic) line;
            double y1 = graph.getStartPoint().getY();
            double y2 = graph.getEndPoint().getY();
            double offset = y1 > y2 ? y2 : y1;
            return offset + Math.abs((y2 - y1) / 2.0);
        }
        // else if (line instanceof FreeHandLineGraphic)
        // return getBaryCenter(getPointList(((FreeHandLineGraphic) line).getPoints())).getY();
        // else if (line instanceof PolygonGraphic)
        // return getBaryCenter(getPointList(((PolygonGraphic) line).getPoints())).getY();
        return 0;
    }

    public static Point2D getBaryCenter(ArrayList<ChainPoint> list) {
        double x = 0.0;
        double y = 0.0;
        for (int i = 0; i < list.size(); i++) {
            ChainPoint p = list.get(i);
            x += p.x;
            y += p.y;
        }
        x /= list.size();
        y /= list.size();
        return new Point2D.Double(x, y);
    }

    public double getSegmentLength() {
        if (line instanceof LineGraphic) {
            Point2D A = ((LineGraphic) line).getStartPoint();
            Point2D B = ((LineGraphic) line).getEndPoint();
            if (A != null && B != null)
                return A.distance(B) * adapter.getCalibRatio();
        }
        // else if (line instanceof FreeHandLineGraphic) {
        // if (length < 0) {
        // FreeHandLineGraphic graph = (FreeHandLineGraphic) line;
        // double dist =
        // MathUtil.computeDistanceFloat(graph.getFirstX(), graph.getFirstY(), graph.getLastX(),
        // graph.getLastY());
        // if (dist < 1)
        // return 0.0;
        // length = computePerimeter(RasterizeGraphicsToCoord.getFreemanCodeFromFreehandline(graph.getPoints()));
        // length = length < dist ? dist : length;
        // }
        // return length;
        // } else if (line instanceof PolygonGraphic)
        // return getPolygonPerimeter((PolygonGraphic) line);

        return 0;
    }

    public double getOrientationIndice() {
        if (line instanceof LineGraphic)
            return 1.0;
        // else if (line instanceof FreeHandLineGraphic) {
        // FreeHandLineGraphic graph = (FreeHandLineGraphic) line;
        // return MathUtil.computeDistanceFloat(graph.getFirstX(), graph.getFirstY(), graph.getLastX(),
        // graph.getLastY())
        // / getSegmentLength();
        // } else if (line instanceof PolygonGraphic) {
        // PolygonGraphic graph = (PolygonGraphic) line;
        // return MathUtil.computeDistanceFloat(graph.getFirstX(), graph.getFirstY(), graph.getLastX(),
        // graph.getLastY())
        // / getSegmentLength();
        // }
        return 0;
    }

    public ArrayList<ChainPoint> getPointList(float[] points) {
        if (pointList == null) {
            pointList = RasterizeGraphicsToCoord.rasterizeFreehandline(points);
        }
        return pointList;
    }

    public double getCorrelationCoefficient() {
        if (line instanceof LineGraphic)
            return 1.0;
        // else if (line instanceof FreeHandLineGraphic)
        // return getCorrelationCoefficient(((FreeHandLineGraphic) line).getPoints());
        // else if (line instanceof PolygonGraphic)
        // return getCorrelationCoefficient(((PolygonGraphic) line).getPoints());
        return 0;
    }

    private double getCorrelationCoefficient(float[] points) {
        if (regression == null) {
            regression = regression(getPointList(points));
        }
        double angle = getSegmentOrientation(points);
        if (angle < 5 || angle > 175 || (angle > 85 && angle < 95)) {
            AffineTransform trans = AffineTransform.getRotateInstance(Math.PI / 4.0, points[0], points[1]);
            Shape r = trans.createTransformedShape(line.getShape());
            double[] reg = regression(RasterizeGraphicsToCoord.rasterizeFreehandline(getCoordinates(r, points.length)));
            regression[2] = reg[2];
        }
        return Math.abs(regression[2]);
    }

    private float[] getCoordinates(Shape shape, int nbPoints) {
        PathIterator iterator = shape.getPathIterator(null);
        float[] points = new float[nbPoints];
        float[] floats = new float[6];
        int nbSeg = 0;
        while (!iterator.isDone()) {
            int segType = iterator.currentSegment(floats);
            switch (segType) {
                case PathIterator.SEG_MOVETO:
                case PathIterator.SEG_LINETO:
                    points[nbSeg++] = floats[0];
                    points[nbSeg++] = floats[1];
            }
            iterator.next();
        }
        return points;
    }

    public double getSegmentOrientation() {
        if (line instanceof LineGraphic) {
            Point2D p1 = ((LineGraphic) line).getStartPoint();
            Point2D p2 = ((LineGraphic) line).getEndPoint();
            return MathUtil.getOrientation(p1.getX(), p1.getY(), p2.getX(), p2.getY());
        }
        // } else if (line instanceof FreeHandLineGraphic) {
        // return getSegmentOrientation(((FreeHandLineGraphic) line).getPoints());
        // } else if (line instanceof PolygonGraphic) {
        // return getSegmentOrientation(((PolygonGraphic) line).getPoints());
        // }
        return 0;
    }

    public double getSegmentOrientation(float[] points) {
        double x1 = points[0];
        double x2 = points[points.length - 2];
        if (regression == null) {
            regression = regression(getPointList(points));
        }
        if (x1 == x2) {
            x2 = points[points.length - 4] < x2 ? x2 + 5 : x2 - 5;
        }
        if (regression[0] == 0.0)
            return MathUtil.getOrientation(x1, points[1], x2, points[points.length - 1]);
        return MathUtil.getOrientation(x1, regression[0] * x1 + regression[1], x2, regression[0] * x2 + regression[1]);
    }

    public double getSegmentAzimuth() {
        if (line instanceof LineGraphic) {
            Point2D p1 = ((LineGraphic) line).getStartPoint();
            Point2D p2 = ((LineGraphic) line).getEndPoint();
            return MathUtil.getAzimuth(p1.getX(), p1.getY(), p2.getX(), p2.getY());
        }
        // else if (line instanceof FreeHandLineGraphic)
        // return getSegmentAzimuth(((FreeHandLineGraphic) line).getPoints());
        // else if (line instanceof PolygonGraphic)
        // return getSegmentAzimuth(((PolygonGraphic) line).getPoints());
        return 0;
    }

    public double getSegmentAzimuth(float[] points) {
        double x1 = points[0];
        double x2 = points[points.length - 2];
        if (regression == null) {
            regression = regression(getPointList(points));
        }
        if (x1 == x2) {
            x2 = points[points.length - 4] < x2 ? x2 + 5 : x2 - 5;
        }
        if (regression[0] == 0.0)
            return MathUtil.getAzimuth(x1, points[1], x2, points[points.length - 1]);
        return MathUtil.getAzimuth(x1, regression[0] * x1 + regression[1], x2, regression[0] * x2 + regression[1]);
    }

    public static ArrayList<String> getObjectMeasureList(boolean[] select) {
        ArrayList<String> list = new ArrayList<String>(select.length);
        for (int i = 0; i < select.length; i++) {
            if (select[i]) {
                list.add(measurList[i]);
            }
        }
        list.trimToSize();
        return list;
    }

    public static String[] createHeaders(boolean[] selected) {
        java.util.List<String> measures = getObjectMeasureList(selected);
        return measures.toArray(new String[measures.size()]);
    }

    public static double[] regression(ArrayList<ChainPoint> list) {
        double mean_x = 0.0;
        double mean_y = 0.0;
        for (int i = 0; i < list.size(); i++) {
            ChainPoint p = list.get(i);
            mean_x += p.x;
            mean_y += p.y;
        }
        mean_x /= list.size();
        mean_y /= list.size();
        /*
         * We have to solve two equations with two unknows:
         * 
         * 1) mean(y) = b + m*mean(x) 2) mean(xy) = b*mean(x) + m*mean(x²)
         * 
         * Those formulas lead to a quadratic equation. However, the formulas become very simples if we set 'mean(x)=0'.
         * We can achieve this result by computing instead of (2):
         * 
         * 2b) mean(dx y) = m*mean(dx²)
         * 
         * where dx=x-mean(x). In this case mean(dx)==0.
         */
        double mean_x2 = 0;
        double mean_y2 = 0;
        double mean_xy = 0;
        for (int i = 0; i < list.size(); i++) {
            ChainPoint p = list.get(i);
            double xi = p.x;
            double yi = p.y;
            xi -= mean_x;
            mean_x2 += xi * xi;
            mean_y2 += yi * yi;
            mean_xy += xi * yi;
        }
        mean_x2 /= list.size();
        mean_y2 /= list.size();
        mean_xy /= list.size();
        /*
         * Assuming that 'mean(x)==0', then the correlation coefficient can be approximate by:
         * 
         * R = mean(xy) / sqrt( mean(x²) * (mean(y²) - mean(y)²) )
         */
        double[] val = new double[3];
        val[0] = mean_xy / mean_x2; // slope
        if (Double.isNaN(val[0])) {
            val[0] = 0.0;
        }
        val[1] = mean_y - mean_x * val[0]; // y0 or b
        val[2] = mean_xy / Math.sqrt(mean_x2 * (mean_y2 - mean_y * mean_y)); // R
        if (Double.isInfinite(val[2]) || Double.isNaN(val[2])) {
            val[2] = 1;
        }
        return val;
    }

    public static double computePerimeter(byte[] chain) {
        double perimeter = 0;
        int corner = 0;
        int evenCode = 0;
        int oddCode = 0;
        // si le blob mesure 1 pixel, il y a que le point de départ et la chaine est nulle
        if (chain.length == 0)
            return perimeter = 2d;
        // process le 1er élément de la chaine
        if ((chain[0] % 2) != 0) {
            oddCode++;
        } else {
            evenCode++;
        }

        for (int i = 1; i < chain.length; i++) {
            int code = chain[i];
            // si il y a un reste à la division par 2, alors incrémente le compteur impair, sinon pair
            if (code % 2 != 0) {
                oddCode++;
            } else {
                evenCode++;
                // le compteur corner comptabilise le nombre de changement de direction de la chaîne
            }
            if (code != chain[i - 1]) {
                corner++;
            }
        }

        // Vossepoel & Smeulders (1982) :
        // Ne = Number of even chain codes
        // No = Number of odd chain codes
        // Nc = Number of "corners" (where the chain code changes)
        // Perimeter = (0.980) Ne + (1.406) No - (0.091) Nc
        perimeter = (0.98d * evenCode) + (1.406d * oddCode) - (0.091d * corner);
        return perimeter;
    }
}
