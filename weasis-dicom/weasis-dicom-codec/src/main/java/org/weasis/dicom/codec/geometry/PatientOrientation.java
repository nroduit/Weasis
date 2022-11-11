/*
 * Copyright (c) 2022 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.geometry;

import java.awt.Color;
import java.util.Objects;
import org.dcm4che3.data.Tag;
import org.joml.Vector3d;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.dicom.codec.TagD;

/**
 * <a
 * href="https://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.7.6.2.html#sect_C.7.6.2.1.1">Image
 * Position and Image Orientation (Patient)</a>
 */
public class PatientOrientation {
  public static final Color blue = new Color(44783);
  public static final Color red = new Color(15539236);
  public static final Color green = new Color(897355);

  public enum Biped implements Orientation {
    R("Right", blue), // NON-NLS
    L("Left", blue), // NON-NLS
    A("Anterior", red), // NON-NLS
    P("Posterior", red), // NON-NLS
    F("Foot", green), // NON-NLS
    H("Head", green); // NON-NLS

    private final String fullName;
    private final Color color;

    Biped(String fullName, Color color) {
      this.fullName = fullName;
      this.color = color;
    }

    @Override
    public String getFullName() {
      return fullName;
    }

    @Override
    public String toString() {
      return fullName;
    }

    @Override
    public Color getColor() {
      return color;
    }
  }

  public enum Quadruped implements Orientation {
    RT("Right", blue), // NON-NLS
    LE("Left", blue), // NON-NLS
    V("Ventral", red), // NON-NLS
    D("Dorsal", red), // NON-NLS
    CD("Caudal", green), // NON-NLS
    CR("Cranial", green); // NON-NLS

    private final String fullName;
    private final Color color;

    Quadruped(String fullName, Color color) {
      this.fullName = fullName;
      this.color = color;
    }

    public String getFullName() {
      return fullName;
    }

    @Override
    public String toString() {
      return fullName;
    }

    public Color getColor() {
      return color;
    }
  }

  public static Biped getBipedXOrientation(Vector3d v) {
    return v.x < 0 ? Biped.R : Biped.L;
  }

  public static Biped getBipedYOrientation(Vector3d v) {
    return v.y < 0 ? Biped.A : Biped.P;
  }

  public static Biped getBipedZOrientation(Vector3d v) {
    return v.z < 0 ? Biped.F : Biped.H;
  }

  public static Quadruped getQuadrupedXOrientation(Vector3d v) {
    return v.x < 0 ? Quadruped.RT : Quadruped.LE;
  }

  public static Quadruped getQuadrupedYOrientation(Vector3d v) {
    return v.y < 0 ? Quadruped.V : Quadruped.D;
  }

  public static Quadruped getQuadrupedZOrientation(Vector3d v) {
    return v.z < 0 ? Quadruped.CD : Quadruped.CR;
  }

  public static Biped getOppositeOrientation(Biped val) {
    return switch (val) {
      case R -> Biped.L;
      case L -> Biped.R;
      case A -> Biped.P;
      case P -> Biped.A;
      case F -> Biped.H;
      case H -> Biped.F;
    };
  }

  public static Quadruped getOppositeOrientation(Quadruped val) {
    return switch (val) {
      case RT -> Quadruped.LE;
      case LE -> Quadruped.RT;
      case V -> Quadruped.D;
      case D -> Quadruped.V;
      case CD -> Quadruped.CR;
      case CR -> Quadruped.CD;
    };
  }

  public static Vector3d getPatientPosition(TagReadable taggable) {
    double[] patientPosition =
        TagD.getTagValue(
            Objects.requireNonNull(taggable), Tag.ImagePositionPatient, double[].class);
    if (patientPosition != null && patientPosition.length == 3) {
      return new Vector3d(patientPosition);
    }
    return null;
  }
}
