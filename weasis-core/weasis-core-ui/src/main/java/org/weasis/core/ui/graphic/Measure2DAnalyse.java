package org.weasis.core.ui.graphic;

import java.awt.Color;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;

import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.Statistics;

/**
 * @author Nicolas Roduit
 */

public class Measure2DAnalyse {
    public final static String[] measurList = { "Box X min", "Box Y min", "Box X width", "Box Y height", "Area",
        "Perimeter", "Barycenter x", "Barycenter y", "Orientation", "Length", "width", "Eccentricity", "Color (RGB)" };
    public final static int[] positionParameters = { 0, 1, 2, 3, 6, 7 };
    public final static int[] basicParameters = { 4, 5, 8, 9, 10, 11 };

    private Statistics stat;
    private Shape shape;
    private MeasurementsAdapter adapter;
    private BlobAnalyse2D blob = null;
    private Color color = null;

    public Measure2DAnalyse(Shape shape, MeasurementsAdapter adapter, Color color) {
        // TODO transform the shape according the x y calibration
        this.shape = shape;
        this.adapter = adapter;
        this.color = color;
    }

    public java.util.List getAnalyse(boolean[] selected, int nbMeasures) {
        ArrayList measVal = new ArrayList(nbMeasures);
        // Mesure 3, 4, 5 et 6 : boundingBox
        if (selected[0]) {
            measVal.add(adapter.getXCalibratedValue(shape.getBounds().getX()));
        }
        if (selected[1]) {
            measVal.add(adapter.getYCalibratedValue(shape.getBounds().getY()));
        }
        if (selected[2]) {
            measVal.add(adapter.getXCalibratedValue(shape.getBounds().getWidth()));
        }
        if (selected[3]) {
            measVal.add(adapter.getYCalibratedValue(shape.getBounds().getHeight()));
        }
        if (selected[4]) {
            // Mesure 7 : Area, retourne l'aire du blob calibré ou non calibré
            measVal.add(adapter.getCalibRatio() * adapter.getCalibRatio() * getArea());
        }
        if (selected[5]) {
            // TODO handle non square pixel
            // Mesure 8 : Perimeter, retourne le périmètre du blob calibré ou non calibré
            measVal.add(adapter.getCalibRatio() * getPerimeter());
        }
        if (selected[6] || selected[7] || selected[8] || selected[9] || selected[10] || selected[11]) {
            // cas ou une polyline forme un trait et une aire de 0
            if (getArea() == 0.0) {
                Double[] corruptGraph = new Double[nbMeasures];
                for (int i = 0; i < nbMeasures; i++) {
                    corruptGraph[i] = 0.0;
                }
                return Arrays.asList(corruptGraph);
            }

            // Mesure 9 et 10 : barycentre, somme des coordonnées de x divisées par nombre de pixel
            if (selected[6]) {
                measVal.add(adapter.getXCalibratedValue(shape.getBounds().x + getBaryCenterX()));
            }
            if (selected[7]) {
                measVal.add(adapter.getYCalibratedValue(shape.getBounds().y + getBaryCenterY()));
            }
            if (selected[8]) {
                // Mesure 11 : Orientation du blob
                measVal.add(convertOrientationToTrigoDegree(getOrientation()));
            }

            if (selected[9] || selected[10]) {
                // TODO handle non square pixel
                // Mesure 12 : Feret width
                // Mesure 13 : 90 degree height
                double[] minorAndMajorAxis = getLengthAndWidth(shape, getOrientation());
                if (selected[9]) {
                    measVal.add(adapter.getCalibRatio() * minorAndMajorAxis[0]);
                }
                if (selected[10]) {
                    measVal.add(adapter.getCalibRatio() * minorAndMajorAxis[1]);
                }
            }
            if (selected[11]) {
                // Mesure 14 : Eccentricity
                measVal.add(getEccentricity());
            }
            if (selected[12]) {
                measVal.add(JMVUtils.getValueRGBasText2(color));
            }

        }
        return measVal;
    }

    public double getArea() {
        if (shape instanceof Rectangle2D) {
            Rectangle2D rectangle = shape.getBounds2D();
            return rectangle.getWidth() * rectangle.getHeight();
        }
        if (shape instanceof Ellipse2D) {
            Rectangle2D rectangle = shape.getBounds2D();
            return (Math.PI * rectangle.getWidth() * rectangle.getHeight()) / 4.0;
        }
        return getBlob().getArea();
    }

    public BlobAnalyse2D getBlob() {
        return blob == null ? blob = new BlobAnalyse2D(shape) : blob;
    }

    private double getPerimeter() {
        if (shape instanceof Rectangle2D) {
            Rectangle2D rectangle = shape.getBounds2D();
            return (rectangle.getWidth() + rectangle.getHeight()) * 2.0;
        }
        if (shape instanceof Ellipse2D) {
            Rectangle2D rectangle = shape.getBounds2D();
            double a = rectangle.getWidth() / 2.0;
            double b = rectangle.getHeight() / 2.0;
            return 2.0 * Math.PI * Math.sqrt((a * a + b * b) / 2.0);
        }
        return getBlob().getPerimeter();
    }

    private Statistics getStatistics() {
        return stat == null ? stat = new Statistics(getBlob().getBlobSumCoorXY()) : stat;
    }

    private double getBaryCenterX() {
        if (shape instanceof Rectangle2D || shape instanceof Ellipse2D)
            return shape.getBounds2D().getWidth() / 2.0;
        return getStatistics().getBarycenterX();
    }

    private double getBaryCenterY() {
        if (shape instanceof Rectangle2D || shape instanceof Ellipse2D)
            return shape.getBounds2D().getHeight() / 2.0;
        return getStatistics().getBarycentery();
    }

    private double getOrientation() {
        if (shape instanceof Rectangle2D || shape instanceof Ellipse2D) {
            Rectangle2D rectangle = shape.getBounds2D();
            return rectangle.getWidth() < rectangle.getHeight() ? Math.PI / 2.0 : 0;
        }
        return getStatistics().orientationInRadian();
    }

    private double getEccentricity() {
        if (shape instanceof Rectangle2D || shape instanceof Ellipse2D) {
            Rectangle2D rectangle = shape.getBounds2D();
            double ratio =
                rectangle.getWidth() < rectangle.getHeight() ? (rectangle.getHeight() + 1.0)
                    / (rectangle.getWidth() + 1.0) : (rectangle.getWidth() + 1.0) / (rectangle.getHeight() + 1.0);
            return ratio * ratio;
        }
        return getStatistics().eccentricity();
    }

    public static double[] getLengthAndWidth(Shape shape, double teta) {
        double sum[] = new double[2];
        if (shape == null)
            return sum;
        if (shape.getBounds().width == 1 && shape.getBounds().width == 1) {
            sum[0] = 1.0;
            sum[1] = 1.0;
        } else {
            AffineTransform trans = AffineTransform.getRotateInstance(-teta);
            Shape shapeRotate = trans.createTransformedShape(shape);
            sum[0] = shapeRotate.getBounds2D().getHeight();
            sum[1] = shapeRotate.getBounds2D().getWidth();
            if (sum[1] > sum[0]) {
                double temp = sum[1];
                sum[1] = sum[0];
                sum[0] = temp;
            }
        }
        return sum;
    }

    public static double convertOrientationToTrigoDegree(double tetaAngle) {
        double angle;
        angle = Math.toDegrees(tetaAngle); // convert from radians to degrees
        // conversion angle trigonométrique de 0 à 180 degrés
        if (angle < 0) {
            angle = (-1) * angle;
        } else {
            angle = 180 - angle;
        }
        return angle;
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
}
