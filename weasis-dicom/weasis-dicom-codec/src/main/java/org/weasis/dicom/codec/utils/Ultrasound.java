/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;

public class Ultrasound {

  private Ultrasound() {}

  public static List<Attributes> getRegions(Attributes attributes) {
    Sequence seq = attributes.getSequence(Tag.SequenceOfUltrasoundRegions);
    if (seq == null || seq.isEmpty()) {
      return Collections.emptyList();
    }

    ArrayList<Attributes> list = new ArrayList<>(seq.size());
    for (Attributes attr : seq) {
      list.add(new Attributes(attr));
    }
    return list;
  }

  public static Attributes getUniqueSpatialRegion(Attributes attributes) {
    List<Attributes> regions = Ultrasound.getRegions(attributes);
    Attributes spatialCalib = null;
    for (Attributes r : regions) {
      Integer unit = getUnitsForXY(r);
      if (unit != null && unit.equals(3)) {
        if (spatialCalib == null) {
          spatialCalib = r;
        } else {
          Double calib1X =
              DicomMediaUtils.getDoubleFromDicomElement(spatialCalib, Tag.PhysicalDeltaX, null);
          Double calib1Y =
              DicomMediaUtils.getDoubleFromDicomElement(spatialCalib, Tag.PhysicalDeltaY, null);
          Double calib2X = DicomMediaUtils.getDoubleFromDicomElement(r, Tag.PhysicalDeltaX, null);
          Double calib2Y = DicomMediaUtils.getDoubleFromDicomElement(r, Tag.PhysicalDeltaY, null);
          if (!Objects.equals(calib1X, calib2X) || !Objects.equals(calib1Y, calib2Y)) {
            return null; // currently, cannot handle multiple spatial calibration
          }
        }
      }
    }
    return spatialCalib;
  }

  public static Integer getUnitsForXY(Attributes region) {
    Integer unitX =
        DicomMediaUtils.getIntegerFromDicomElement(region, Tag.PhysicalUnitsXDirection, null);
    Integer unitY =
        DicomMediaUtils.getIntegerFromDicomElement(region, Tag.PhysicalUnitsYDirection, null);
    if (unitX != null && unitX.equals(unitY)) {
      return unitX;
    }
    return null;
  }

  public static String valueOfUnits(int id) {
    return switch (id) {
      case 1 -> "Percent"; // NON-NLS
      case 2 -> "dB"; // NON-NLS
      case 3 -> "cm"; // NON-NLS
      case 4 -> "seconds"; // NON-NLS
      case 5 -> "hertz(seconds-1)"; // NON-NLS
      case 6 -> "dB/seconds"; // NON-NLS
      case 7 -> "cm/sec"; // NON-NLS
      case 8 -> "cm2"; // NON-NLS
      case 9 -> "cm2/sec"; // NON-NLS
      case 10 -> "cm3"; // NON-NLS
      case 11 -> "cm3/sec"; // NON-NLS
      case 12 -> "degrees"; // NON-NLS
      default -> "None"; // NON-NLS
    };
  }
}
