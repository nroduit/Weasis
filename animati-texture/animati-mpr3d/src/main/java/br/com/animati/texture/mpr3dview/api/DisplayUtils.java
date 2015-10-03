/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Informática Ltda.
 * (http://www.animati.com.br)
 */
package br.com.animati.texture.mpr3dview.api;

import java.awt.Component;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;

import javax.vecmath.Vector3d;

import org.weasis.core.api.gui.util.DecFormater;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.dicom.codec.geometry.ImageOrientation;

/**
 *
 * @author Gabriela Bauermann (gabriela@animati.com.br)
 * @version 2012 Jun, 05
 */
public class DisplayUtils {

    /** To prevent instantiation. */
    private DisplayUtils() {
    }

    public static final char getImageOrientationOposite(char c) {
        switch (c) {
            case 'L':
                return 'R';
            case 'R':
                return 'L';
            case 'P':
                return 'A';
            case 'A':
                return 'P';
            case 'H':
                return 'F';
            case 'F':
                return 'H';
        }
        return ' ';
    }

    public static void rotate(Vector3d vSrc, Vector3d axis, double angle, Vector3d vDst) {
        axis.normalize();
        vDst.x = axis.x * (axis.x * vSrc.x + axis.y * vSrc.y + axis.z * vSrc.z) * (1 - Math.cos(angle))
            + vSrc.x * Math.cos(angle) + (-axis.z * vSrc.y + axis.y * vSrc.z) * Math.sin(angle);
        vDst.y = axis.y * (axis.x * vSrc.x + axis.y * vSrc.y + axis.z * vSrc.z) * (1 - Math.cos(angle))
            + vSrc.y * Math.cos(angle) + (axis.z * vSrc.x + axis.x * vSrc.z) * Math.sin(angle);
        vDst.z = axis.z * (axis.x * vSrc.x + axis.y * vSrc.y + axis.z * vSrc.z) * (1 - Math.cos(angle))
            + vSrc.z * Math.cos(angle) + (-axis.y * vSrc.x + axis.x * vSrc.y) * Math.sin(angle);
    }

    public static String ajustLengthDisplay(double scaleLength, Unit[] unit) {
        double ajustScaleLength = scaleLength;

        Unit ajustUnit = unit[0];

        if (scaleLength < 1.0) {
            Unit down = ajustUnit;
            while ((down = down.getDownUnit()) != null) {
                double length = scaleLength * down.getConversionRatio(unit[0].getConvFactor());
                if (length > 1) {
                    ajustUnit = down;
                    ajustScaleLength = length;
                    break;
                }
            }
        } else if (scaleLength > 10.0) {
            Unit up = ajustUnit;
            while ((up = up.getUpUnit()) != null) {
                double length = scaleLength * up.getConversionRatio(unit[0].getConvFactor());
                if (length < 1) {
                    break;
                }
                ajustUnit = up;
                ajustScaleLength = length;
            }
        }
        // Trick to keep the value as a return parameter
        unit[0] = ajustUnit;
        if (ajustScaleLength < 1.0) {
            return ajustScaleLength < 0.001 ? DecFormater.scientificFormat(ajustScaleLength)
                : DecFormater.fourDecimal(ajustScaleLength);
        }
        return ajustScaleLength > 50000.0 ? DecFormater.scientificFormat(ajustScaleLength)
            : DecFormater.twoDecimal(ajustScaleLength);
    }

    public static double ajustShowScale(double ratio, int maxLength) {
        int digits = (int) ((Math.log(maxLength * ratio) / Math.log(10)) + 1);
        double scaleLength = Math.pow(10, digits);
        double scaleSize = scaleLength / ratio;

        int loop = 0;
        while ((int) scaleSize > maxLength) {
            scaleLength /= findGeometricSuite(scaleLength);
            scaleSize = scaleLength / ratio;
            loop++;
            if (loop > 50) {
                return 0.0;
            }
        }
        return scaleSize;
    }

    public static double findGeometricSuite(double length) {
        int shift = (int) ((Math.log(length) / Math.log(10)) + 0.1);
        int firstDigit = (int) (length / Math.pow(10, shift) + 0.5);
        if (firstDigit == 5) {
            return 2.5;
        }
        return 2.0;

    }

    public static Rectangle2D getOutLine(Line2D l) {
        Rectangle2D r = l.getBounds2D();
        r.setFrame(r.getX() - 1.0, r.getY() - 1.0, r.getWidth() + 2.0, r.getHeight() + 2.0);
        return r;
    }

    public static String[] getOrientationFromImgOrPat(double[] imgOrPatient, int rotationAngle, boolean flip) {

        // Set the opposite vector direction (otherwise label should be placed in mid-right and mid-bottom
        Vector3d vr = new Vector3d(-imgOrPatient[0], -imgOrPatient[1], -imgOrPatient[2]);
        Vector3d vc = new Vector3d(-imgOrPatient[3], -imgOrPatient[4], -imgOrPatient[5]);

        Vector3d vl = new Vector3d(imgOrPatient[0], -imgOrPatient[1], -imgOrPatient[2]);
        Vector3d vp = new Vector3d(-imgOrPatient[3], imgOrPatient[4], -imgOrPatient[5]);

        if (rotationAngle != 0) {
            double rad = Math.toRadians(rotationAngle);
            double[] normal = ImageOrientation.computeNormalVectorOfPlan(imgOrPatient);
            if (normal != null && normal.length == 3) {
                Vector3d result = new Vector3d(0.0, 0.0, 0.0);
                Vector3d axis = new Vector3d(normal);
                DisplayUtils.rotate(vr, axis, -rad, result);
                vr = result;

                result = new Vector3d(0.0, 0.0, 0.0);
                DisplayUtils.rotate(vc, axis, -rad, result);
                vc = result;

                result = new Vector3d(0.0, 0.0, 0.0);
                DisplayUtils.rotate(vl, axis, -rad, result);
                vl = result;

                result = new Vector3d(0.0, 0.0, 0.0);
                DisplayUtils.rotate(vp, axis, -rad, result);
                vp = result;
            }
        }

        if (flip) {
            vr.x = -vr.x;
            vr.y = -vr.y;
            vr.z = -vr.z;
        }

        String[] result = new String[4];
        result[0] = ImageOrientation.makePatientOrientationFromPatientRelativeDirectionCosine(vr.x, vr.y, vr.z);
        result[1] = ImageOrientation.makePatientOrientationFromPatientRelativeDirectionCosine(vc.x, vc.y, vc.z);
        result[2] = ImageOrientation.makePatientOrientationFromPatientRelativeDirectionCosine(vl.x, vl.y, vl.z);
        result[3] = ImageOrientation.makePatientOrientationFromPatientRelativeDirectionCosine(vp.x, vp.y, vp.z);

        return result;
    }

    public static String[] getOrientationFromPatOr(String[] po, boolean flip) {
        String[] result = new String[4];

        if (flip) {
            result[0] = po[0];
            StringBuffer buf = new StringBuffer();
            for (char c : po[0].toCharArray()) {
                buf.append(DisplayUtils.getImageOrientationOposite(c));
            }
            result[2] = buf.toString();
        } else {
            StringBuffer buf = new StringBuffer();
            for (char c : po[0].toCharArray()) {
                buf.append(DisplayUtils.getImageOrientationOposite(c));
            }
            result[0] = buf.toString();
            result[2] = po[0];
        }
        StringBuffer buf = new StringBuffer();
        for (char c : po[1].toCharArray()) {
            buf.append(DisplayUtils.getImageOrientationOposite(c));
        }
        result[1] = buf.toString();
        result[3] = po[1];

        return result;
    }

    /**
     * Finds the parent plugin (ViewContainer) of a view.
     * 
     * @param viewer
     *            Given viewer.
     * @return The parent plugin or null (if there is no parent plugin).
     */
    public static ViewerPlugin getPluginParent(Component viewer) {
        Component obj = viewer;
        while (obj.getParent() != null) {
            obj = obj.getParent();
            if (obj instanceof ViewerPlugin) {
                return (ViewerPlugin) obj;
            }
        }

        return null;
    }

    public static String getLossyTransferSyntaxUID(String tsuid) {
        if (tsuid != null) {
            if ("1.2.840.10008.1.2.4.50".equals(tsuid)) {
                return "JPEG Baseline";
            }
            if ("1.2.840.10008.1.2.4.51".equals(tsuid)) {
                return "JPEG Extended";
            }
            if ("1.2.840.10008.1.2.4.81".equals(tsuid)) {
                return "JPEG-LS (Near-Lossless)";
            }
            if ("1.2.840.10008.1.2.4.91".equals(tsuid)) {
                return "JPEG 2000";
            }
        }
        return null;
    }

    public static Object getTagValue(TagW tag, MediaSeriesGroup patient, MediaSeriesGroup study, Series series,
        ImageElement image) {
        if (image != null && image.containTagKey(tag)) {
            return image.getTagValue(tag);
        }
        if (series != null && series.containTagKey(tag)) {
            return series.getTagValue(tag);
        }
        if (study != null && study.containTagKey(tag)) {
            return study.getTagValue(tag);
        }
        if (patient != null && patient.containTagKey(tag)) {
            return patient.getTagValue(tag);
        }
        return null;
    }

    public static int convertStringToNumber(String number) {
        try {
            int num = Integer.parseInt(number);
            return num;
        } catch (NumberFormatException ex) {
        }
        return 0;
    }

}
