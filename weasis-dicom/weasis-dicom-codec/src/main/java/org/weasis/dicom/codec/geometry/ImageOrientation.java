/* Copyright (c) 2001-2020, David A. Clunie DBA Pixelmed Publishing. All rights reserved. */

package org.weasis.dicom.codec.geometry;

import org.dcm4che3.data.Tag;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeries.MEDIA_POSITION;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;

/**
 * <p>
 * A class of static methods to provide descriptions of images, including image orientation relative to the patient from
 * the mathematical position and orientation attributes, and including other descriptive attributes such as from dicom
 * directory records and images using multi-frame functional groups.
 *
 * C.7.6.1.1.1 Patient Orientation. The Patient Orientation (0020,0020) relative to the image plane shall be specified
 * by two values that designate the anatomical direction of the positive row axis (left to right) and the positive
 * column axis (top to bottom). The first entry is the direction of the rows, given by the direction of the last pixel
 * in the first row from the first pixel in that row. The second entry is the direction of the columns, given by the
 * direction of the last pixel in the first column from the first pixel in that column. Anatomical direction shall be
 * designated by the capital letters: A (anterior), P (posterior), R (right), L (left), H (head), F (foot). Each value
 * of the orientation attribute shall contain at least one of these characters. If refinements in the orientation
 * descriptions are to be specified, then they shall be designated by one or two additional letters in each value.
 * Within each value, the letters shall be ordered with the principal orientation designated in the first character.
 *
 * C.7.6.2.1.1 Image Position And Image Orientation. The Image Position (0020,0032) specifies the x, y, and z
 * coordinates of the upper left hand corner of the image; it is the center of the first voxel transmitted. Image
 * Orientation (0020,0037) specifies the direction cosines of the first row and the first column with respect to the
 * patient. These Attributes shall be provide as a pair. Row value for the x, y, and z axes respectively followed by the
 * Column value for the x, y, and z axes respectively. The direction of the axes is defined fully by the patient's
 * orientation. The x-axis is increasing to the left hand side of the patient. The y-axis is increasing to the posterior
 * side of the patient. The z-axis is increasing toward the head of the patient. The patient based coordinate system is
 * a right handed system, i.e. the vector cross product of a unit vector along the positive x-axis and a unit vector
 * along the positive y-axis is equal to a unit vector along the positive z-axis.
 * </p>
 *
 * @author David A. Clunie
 * @author Nicolas Roduit
 */
public abstract class ImageOrientation {

    public enum Label {
        UNKNOWN, AXIAL, SAGITTAL, CORONAL, OBLIQUE
    }

    public static final String DIR_R = "R"; //$NON-NLS-1$
    public static final String DIR_L = "L"; //$NON-NLS-1$
    public static final String DIR_A = "A"; //$NON-NLS-1$
    public static final String DIR_P = "P"; //$NON-NLS-1$
    public static final String DIR_F = "F"; //$NON-NLS-1$
    public static final String DIR_H = "H"; //$NON-NLS-1$

    private static final double OBLIQUITY_THRESHOLD = 0.8;

    /**
     * <p>
     * Get a label describing the major axis from a unit vector (direction cosine) as found in ImageOrientationPatient.
     * </p>
     *
     * <p>
     * Some degree of deviation from one of the standard orthogonal axes is allowed before deciding no major axis
     * applies and returning null.
     * </p>
     *
     * @param x
     * @param y
     * @param z
     * @return the string describing the orientation of the vector, or null if oblique
     */
    public static final String getMajorAxisFromPatientRelativeDirectionCosine(double x, double y, double z) {
        String axis = null;

        String orientationX = x < 0 ? DIR_R : DIR_L;
        String orientationY = y < 0 ? DIR_A : DIR_P;
        String orientationZ = z < 0 ? DIR_F : DIR_H;

        double absX = Math.abs(x);
        double absY = Math.abs(y);
        double absZ = Math.abs(z);

        // The tests here really don't need to check the other dimensions,
        // just the threshold, since the sum of the squares should be == 1.0
        // but just in case ...

        if (absX > OBLIQUITY_THRESHOLD && absX > absY && absX > absZ) {
            axis = orientationX;
        } else if (absY > OBLIQUITY_THRESHOLD && absY > absX && absY > absZ) {
            axis = orientationY;
        } else if (absZ > OBLIQUITY_THRESHOLD && absZ > absX && absZ > absY) {
            axis = orientationZ;
        }

        return axis;
    }

    /**
     * <p>
     * Get a label describing the axial, coronal or sagittal plane from row and column unit vectors (direction cosines)
     * as found in ImageOrientationPatient.
     * </p>
     *
     * <p>
     * Some degree of deviation from one of the standard orthogonal planes is allowed before deciding the plane is
     * OBLIQUE.
     * </p>
     *
     * @param rowX
     * @param rowY
     * @param rowZ
     * @param colX
     * @param colY
     * @param colZ
     * @return the string describing the plane of orientation, AXIAL, CORONAL, SAGITTAL or OBLIQUE
     */

    public static final Label makeImageOrientationLabelFromImageOrientationPatient(double rowX, double rowY,
        double rowZ, double colX, double colY, double colZ) {
        String rowAxis = getMajorAxisFromPatientRelativeDirectionCosine(rowX, rowY, rowZ);
        String colAxis = getMajorAxisFromPatientRelativeDirectionCosine(colX, colY, colZ);
        if (rowAxis != null && colAxis != null) {
            if ((rowAxis.equals(DIR_R) || rowAxis.equals(DIR_L)) && (colAxis.equals(DIR_A) || colAxis.equals(DIR_P))) {
                return Label.AXIAL;
            } else if ((colAxis.equals(DIR_R) || colAxis.equals(DIR_L))
                && (rowAxis.equals(DIR_A) || rowAxis.equals(DIR_P))) {
                return Label.AXIAL;
            } else if ((rowAxis.equals(DIR_R) || rowAxis.equals(DIR_L))
                && (colAxis.equals(DIR_H) || colAxis.equals(DIR_F))) {
                return Label.CORONAL;
            } else if ((colAxis.equals(DIR_R) || colAxis.equals(DIR_L))
                && (rowAxis.equals(DIR_H) || rowAxis.equals(DIR_F))) {
                return Label.CORONAL;
            } else if ((rowAxis.equals(DIR_A) || rowAxis.equals(DIR_P))
                && (colAxis.equals(DIR_H) || colAxis.equals(DIR_F))) {
                return Label.SAGITTAL;
            } else if ((colAxis.equals(DIR_A) || colAxis.equals(DIR_P))
                && (rowAxis.equals(DIR_H) || rowAxis.equals(DIR_F))) {
                return Label.SAGITTAL;
            }
        }
        return Label.OBLIQUE;
    }

    public static final Label makeImageOrientationLabelFromImageOrientationPatient(double[] v) {
        if (v == null || v.length < 6) {
            return null;
        }
        return ImageOrientation.makeImageOrientationLabelFromImageOrientationPatient(v[0], v[1], v[2], v[3], v[4],
            v[5]);
    }

    /**
     * <p>
     * Get a PatientOrientation style string from a unit vector (direction cosine) as found in ImageOrientationPatient.
     * </p>
     *
     * <p>
     * Returns letters representing R (right) or L (left), A (anterior) or P (posterior), F (feet) or H (head).
     * </p>
     *
     * <p>
     * If the orientation is not precisely orthogonal to one of the major axes, more than one letter is returned, from
     * major to minor axes, with up to three letters in the case of a "double oblique".
     * </p>
     *
     * @param x
     * @param y
     * @param z
     * @return the string describing the orientation of the vector
     */
    public static final String makePatientOrientationFromPatientRelativeDirectionCosine(double x, double y, double z) {
        StringBuilder buffer = new StringBuilder();

        String orientationX = x < 0 ? DIR_R : DIR_L;
        String orientationY = y < 0 ? DIR_A : DIR_P;
        String orientationZ = z < 0 ? DIR_F : DIR_H;

        double absX = Math.abs(x);
        double absY = Math.abs(y);
        double absZ = Math.abs(z);

        for (int i = 0; i < 3; ++i) {
            if (absX > .0001 && absX >= absY && absX >= absZ) {
                buffer.append(orientationX);
                absX = 0;
            } else if (absY > .0001 && absY >= absX && absY >= absZ) {
                buffer.append(orientationY);
                absY = 0;
            } else if (absZ > .0001 && absZ >= absX && absZ >= absY) {
                buffer.append(orientationZ);
                absZ = 0;
            } else {
                break;
            }
        }
        return buffer.toString();
    }

    /**
     * <p>
     * Get a PatientOrientation style string from row and column unit vectors (direction cosines) as found in
     * ImageOrientationPatient.
     * </p>
     *
     * <p>
     * Returns letters representing R (right) or L (left), A (anterior) or P (posterior), F (feet) or H (head).
     * </p>
     *
     * <p>
     * If the orientation is not precisely orthogonal to one of the major axes, more than one letter is returned, from
     * major to minor axes, with up to three letters in the case of a "double oblique".
     * </p>
     *
     * <p>
     * The row and column letters returned are separated by the usual DICOM string delimiter, a backslash.
     * </p>
     *
     * @param rowX
     * @param rowY
     * @param rowZ
     * @param colX
     * @param colY
     * @param colZ
     * @return the string describing the row and then the column
     */
    public static final String makePatientOrientationFromImageOrientationPatient(double rowX, double rowY, double rowZ,
        double colX, double colY, double colZ) {
        return makePatientOrientationFromPatientRelativeDirectionCosine(rowX, rowY, rowZ) + "\\" //$NON-NLS-1$
            + makePatientOrientationFromPatientRelativeDirectionCosine(colX, colY, colZ);
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

    public static double[] computeNormalVectorOfPlan(double[] vector) {
        if (vector != null && vector.length == 6) {
            double[] norm = new double[3];
            norm[0] = vector[1] * vector[5] - vector[2] * vector[4];
            norm[1] = vector[2] * vector[3] - vector[0] * vector[5];
            norm[2] = vector[0] * vector[4] - vector[1] * vector[3];
            return norm;
        }
        return null;
    }

    public static boolean hasSameOrientation(MediaSeries<DicomImageElement> series1,
        MediaSeries<DicomImageElement> series2) {
        // Test if the two series have the same orientation
        if (series1 != null && series2 != null) {
            DicomImageElement image1 = series1.getMedia(MEDIA_POSITION.MIDDLE, null, null);
            DicomImageElement image2 = series2.getMedia(MEDIA_POSITION.MIDDLE, null, null);
            return hasSameOrientation(image1, image2);
        }
        return false;
    }

    public static boolean hasSameOrientation(DicomImageElement image1, DicomImageElement image2) {
        // Test if the two images have the same orientation
        if (image1 != null && image2 != null) {
            double[] v1 = TagD.getTagValue(image1, Tag.ImageOrientationPatient, double[].class);
            double[] v2 = TagD.getTagValue(image2, Tag.ImageOrientationPatient, double[].class);
            if (v1 != null && v1.length == 6 && v2 != null && v2.length == 6) {
                Label label1 = ImageOrientation.makeImageOrientationLabelFromImageOrientationPatient(v1[0], v1[1],
                    v1[2], v1[3], v1[4], v1[5]);
                Label label2 = ImageOrientation.makeImageOrientationLabelFromImageOrientationPatient(v2[0], v2[1],
                    v2[2], v2[3], v2[4], v2[5]);

                if (label1 != null && !label1.equals(Label.OBLIQUE)) {
                    return label1.equals(label2);
                }
                // If oblique search if the plan has approximately the same orientation
                double[] postion1 = computeNormalVectorOfPlan(v1);
                double[] postion2 = computeNormalVectorOfPlan(v2);
                if (postion1 != null && postion2 != null) {
                    double prod = postion1[0] * postion2[0] + postion1[1] * postion2[1] + postion1[2] * postion2[2];
                    // A little tolerance
                    if (prod > 0.95) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
