/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.geometry;

import java.util.Objects;
import org.dcm4che3.data.Tag;
import org.joml.Vector3d;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeries.MEDIA_POSITION;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;

/**
 * <a
 * href="https://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.7.6.2.html#sect_C.7.6.2.1.1">Image
 * Position and Image Orientation (Patient)</a>
 *
 * @author Nicolas Roduit
 * @author David A. Clunie
 */
public abstract class ImageOrientation {

  public enum Plan {
    UNKNOWN,
    AXIAL,
    SAGITTAL,
    CORONAL,
    OBLIQUE
  }

  private static final double OBLIQUITY_THRESHOLD = 0.8;

  public static Vector3d getRowImagePosition(TagReadable taggable) {
    double[] imagePosition =
        TagD.getTagValue(
            Objects.requireNonNull(taggable), Tag.ImageOrientationPatient, double[].class);
    if (imagePosition != null && imagePosition.length == 6) {
      return new Vector3d(imagePosition);
    }
    return null;
  }

  public static Vector3d getColumnImagePosition(TagReadable taggable) {
    double[] imagePosition =
        TagD.getTagValue(
            Objects.requireNonNull(taggable), Tag.ImageOrientationPatient, double[].class);
    if (imagePosition != null && imagePosition.length == 6) {
      return new Vector3d(imagePosition[3], imagePosition[4], imagePosition[5]);
    }
    return null;
  }

  /**
   * Get the orientation describing the major axis from a unit vector (direction cosine) as found in
   * ImageOrientationPatient.
   *
   * <p>Some degree of deviation from one of the standard orthogonal axes is allowed before deciding
   * no major axis applies and returning null.
   *
   * @param v the vector (direction cosine)
   * @return the string describing the orientation of the vector, or null if oblique
   */
  private static Orientation getPatientOrientation(Vector3d v, boolean quadruped) {
    double absX = Math.abs(v.x);
    double absY = Math.abs(v.y);
    double absZ = Math.abs(v.z);

    if (absX > OBLIQUITY_THRESHOLD && absX > absY && absX > absZ) {
      return quadruped
          ? PatientOrientation.getQuadrupedXOrientation(v)
          : PatientOrientation.getBipedXOrientation(v);
    } else if (absY > OBLIQUITY_THRESHOLD && absY > absX && absY > absZ) {
      return quadruped
          ? PatientOrientation.getQuadrupedYOrientation(v)
          : PatientOrientation.getBipedYOrientation(v);
    } else if (absZ > OBLIQUITY_THRESHOLD && absZ > absX && absZ > absY) {
      return quadruped
          ? PatientOrientation.getQuadrupedZOrientation(v)
          : PatientOrientation.getBipedZOrientation(v);
    }
    return null;
  }

  /**
   * Get a plan describing the axial, coronal or sagittal plane from row and column unit vectors
   * (direction cosines) as found in ImageOrientationPatient.
   *
   * <p>Some degree of deviation from one of the standard orthogonal planes is allowed before
   * deciding the plane is OBLIQUE.
   *
   * @param vr the row vector
   * @param vc the column vector
   * @return the string describing the plane of orientation, AXIAL, CORONAL, SAGITTAL or OBLIQUE
   */
  public static Plan getPlan(Vector3d vr, Vector3d vc) {
    Orientation rowAxis = getPatientOrientation(vr, false);
    Orientation colAxis = getPatientOrientation(vc, false);
    if (rowAxis != null && colAxis != null) {
      if (rowAxis.getColor().equals(PatientOrientation.blue)
          && colAxis.getColor().equals(PatientOrientation.red)) {
        return Plan.AXIAL;
      } else if (colAxis.getColor().equals(PatientOrientation.blue)
          && rowAxis.getColor().equals(PatientOrientation.red)) {
        return Plan.AXIAL;
      } else if (rowAxis.getColor().equals(PatientOrientation.blue)
          && colAxis.getColor().equals(PatientOrientation.green)) {
        return Plan.CORONAL;
      } else if (colAxis.getColor().equals(PatientOrientation.blue)
          && rowAxis.getColor().equals(PatientOrientation.green)) {
        return Plan.CORONAL;
      } else if (rowAxis.getColor().equals(PatientOrientation.red)
          && colAxis.getColor().equals(PatientOrientation.green)) {
        return Plan.SAGITTAL;
      } else if (colAxis.getColor().equals(PatientOrientation.red)
          && rowAxis.getColor().equals(PatientOrientation.green)) {
        return Plan.SAGITTAL;
      }
    }
    return Plan.OBLIQUE;
  }

  public static Plan getPlan(TagReadable taggable) {
    Vector3d vr = ImageOrientation.getRowImagePosition(taggable);
    Vector3d vc = ImageOrientation.getColumnImagePosition(taggable);
    if (vr != null && vc != null) {
      return ImageOrientation.getPlan(vr, vc);
    }
    return null;
  }

  /**
   * Get the letter representation of the orientation of a vector.
   *
   * <p>For bipeds, R (right) or L (left), A (anterior) or P (posterior), F (feet) * or H (head).
   *
   * <p>For quadrupeds, Le or Rt, V or D, Cr or Cd (with lower case; use toUpperCase() to produce
   * valid CodeString for PatientOrientation).
   *
   * @param v the orientation vector
   * @param quadruped true if subject is a quadruped rather than a biped
   * @return a string rendering of the orientation, more than one letter if oblique to the
   *     orthogonal axes, or empty string (not null) if fails
   */
  public static String getOrientation(Vector3d v, boolean quadruped) {
    Orientation orientationX =
        quadruped
            ? PatientOrientation.getQuadrupedXOrientation(v)
            : PatientOrientation.getBipedXOrientation(v);
    Orientation orientationY =
        quadruped
            ? PatientOrientation.getQuadrupedYOrientation(v)
            : PatientOrientation.getBipedYOrientation(v);
    Orientation orientationZ =
        quadruped
            ? PatientOrientation.getQuadrupedZOrientation(v)
            : PatientOrientation.getBipedZOrientation(v);

    double absX = Math.abs(v.x);
    double absY = Math.abs(v.y);
    double absZ = Math.abs(v.z);

    StringBuilder buffer = new StringBuilder();
    for (int i = 0; i < 3; ++i) {
      if (absX > .0001 && absX >= absY && absX >= absZ) {
        buffer.append(orientationX.name());
        absX = 0;
      } else if (absY > .0001 && absY >= absX && absY >= absZ) {
        buffer.append(orientationY.name());
        absY = 0;
      } else if (absZ > .0001 && absZ >= absX && absZ >= absY) {
        buffer.append(orientationZ.name());
        absZ = 0;
      } else {
        break;
      }
    }
    return buffer.toString();
  }

  public static String getImageOrientationOpposite(String val, boolean quadruped) {
    if (quadruped) {
      return PatientOrientation.getOppositeOrientation(PatientOrientation.Quadruped.valueOf(val))
          .name();
    }
    return PatientOrientation.getOppositeOrientation(PatientOrientation.Biped.valueOf(val)).name();
  }

  public static boolean hasSameOrientation(
      MediaSeries<DicomImageElement> series1, MediaSeries<DicomImageElement> series2) {
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
      Vector3d vr1 = ImageOrientation.getRowImagePosition(image1);
      Vector3d vc1 = ImageOrientation.getColumnImagePosition(image1);
      Vector3d vr2 = ImageOrientation.getRowImagePosition(image2);
      Vector3d vc2 = ImageOrientation.getColumnImagePosition(image2);
      if (vr1 != null && vc1 != null && vr2 != null && vc2 != null) {
        return hasSameOrientation(vr1, vc1, vr2, vc2);
      }
    }
    return false;
  }

  public static boolean hasSameOrientation(Vector3d vr1, Vector3d vc1, Vector3d vr2, Vector3d vc2) {
    // Test if the two images have the same orientation
    if (vr1 != null && vc1 != null && vr2 != null && vc2 != null) {
      Plan plan1 = ImageOrientation.getPlan(vr1, vc1);
      Plan plan2 = ImageOrientation.getPlan(vr2, vc2);

      if (plan1 != null && !plan1.equals(Plan.OBLIQUE)) {
        return plan1.equals(plan2);
      }
      // If oblique search and if the plan has approximately the same orientation
      Vector3d normal1 = VectorUtils.computeNormalOfSurface(vr1, vc1);
      Vector3d normal2 = VectorUtils.computeNormalOfSurface(vr2, vc2);
      if (normal1 != null && normal2 != null) {
        normal1.mul(normal2);
        // A little tolerance
        return normal1.x + normal1.y + normal1.z > 0.95;
      }
    }
    return false;
  }
}
