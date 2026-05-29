/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.utils;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.Test;

class UltrasoundTest {

  // Physical unit codes per DICOM PS3.3 C.8.5.5 (Ultrasound Region Calibration).
  private static final int UNIT_CM = 3;
  private static final int UNIT_SECONDS = 4;

  // -- valueOfUnits ---------------------------------------------------------

  @Test
  void valueOfUnits_mapsAllStandardCodes() {
    assertAll(
        () -> assertEquals("Percent", Ultrasound.valueOfUnits(1)),
        () -> assertEquals("dB", Ultrasound.valueOfUnits(2)),
        () -> assertEquals("cm", Ultrasound.valueOfUnits(3)),
        () -> assertEquals("seconds", Ultrasound.valueOfUnits(4)),
        () -> assertEquals("hertz(seconds-1)", Ultrasound.valueOfUnits(5)),
        () -> assertEquals("dB/seconds", Ultrasound.valueOfUnits(6)),
        () -> assertEquals("cm/sec", Ultrasound.valueOfUnits(7)),
        () -> assertEquals("cm2", Ultrasound.valueOfUnits(8)),
        () -> assertEquals("cm2/sec", Ultrasound.valueOfUnits(9)),
        () -> assertEquals("cm3", Ultrasound.valueOfUnits(10)),
        () -> assertEquals("cm3/sec", Ultrasound.valueOfUnits(11)),
        () -> assertEquals("degrees", Ultrasound.valueOfUnits(12)));
  }

  @Test
  void valueOfUnits_unknownCodeReturnsNone() {
    // Critical: an unknown unit code (e.g. future DICOM addition) must fall back to "None"
    // rather than throw, so calibration UI degrades gracefully.
    assertAll(
        () -> assertEquals("None", Ultrasound.valueOfUnits(0)),
        () -> assertEquals("None", Ultrasound.valueOfUnits(-1)),
        () -> assertEquals("None", Ultrasound.valueOfUnits(99)));
  }

  // -- getRegions -----------------------------------------------------------

  @Test
  void getRegions_missingSequenceReturnsEmptyList() {
    assertTrue(Ultrasound.getRegions(new Attributes()).isEmpty());
  }

  @Test
  void getRegions_emptySequenceReturnsEmptyList() {
    Attributes a = new Attributes();
    a.newSequence(Tag.SequenceOfUltrasoundRegions, 0);

    assertTrue(Ultrasound.getRegions(a).isEmpty());
  }

  @Test
  void getRegions_singleRegionReturnsSingletonList() {
    Attributes a = new Attributes();
    Sequence seq = a.newSequence(Tag.SequenceOfUltrasoundRegions, 1);
    seq.add(makeRegion(UNIT_CM, 0.01, 0.01));

    List<Attributes> regions = Ultrasound.getRegions(a);

    assertEquals(1, regions.size());
  }

  @Test
  void getRegions_multipleRegionsPreservesOrder() {
    Attributes a = new Attributes();
    Sequence seq = a.newSequence(Tag.SequenceOfUltrasoundRegions, 3);
    seq.add(makeRegion(UNIT_CM, 0.01, 0.01));
    seq.add(makeRegion(UNIT_SECONDS, 1e-4, 0));
    seq.add(makeRegion(UNIT_CM, 0.02, 0.02));

    List<Attributes> regions = Ultrasound.getRegions(a);

    assertEquals(3, regions.size());
  }

  // -- getUnitsForXY --------------------------------------------------------

  @Test
  void getUnitsForXY_matchingXAndYReturnsThatUnit() {
    Attributes region = new Attributes();
    region.setInt(Tag.PhysicalUnitsXDirection, VR.US, UNIT_CM);
    region.setInt(Tag.PhysicalUnitsYDirection, VR.US, UNIT_CM);

    assertEquals(UNIT_CM, Ultrasound.getUnitsForXY(region));
  }

  @Test
  void getUnitsForXY_mismatchedXAndYReturnsNull() {
    // Anisotropic units (e.g. cm × seconds for M-mode) cannot produce a single spatial calibration.
    Attributes region = new Attributes();
    region.setInt(Tag.PhysicalUnitsXDirection, VR.US, UNIT_CM);
    region.setInt(Tag.PhysicalUnitsYDirection, VR.US, UNIT_SECONDS);

    assertNull(Ultrasound.getUnitsForXY(region));
  }

  @Test
  void getUnitsForXY_missingXReturnsNull() {
    Attributes region = new Attributes();
    region.setInt(Tag.PhysicalUnitsYDirection, VR.US, UNIT_CM);

    assertNull(Ultrasound.getUnitsForXY(region));
  }

  @Test
  void getUnitsForXY_missingYReturnsNull() {
    Attributes region = new Attributes();
    region.setInt(Tag.PhysicalUnitsXDirection, VR.US, UNIT_CM);

    assertNull(Ultrasound.getUnitsForXY(region));
  }

  // -- getUniqueSpatialRegion ----------------------------------------------

  @Test
  void getUniqueSpatialRegion_noRegionsReturnsNull() {
    assertNull(Ultrasound.getUniqueSpatialRegion(new Attributes()));
  }

  @Test
  void getUniqueSpatialRegion_singleCmRegionReturnsIt() {
    Attributes a = new Attributes();
    Sequence seq = a.newSequence(Tag.SequenceOfUltrasoundRegions, 1);
    seq.add(makeRegion(UNIT_CM, 0.01, 0.01));

    Attributes region = Ultrasound.getUniqueSpatialRegion(a);

    assertNotNull(region);
    assertEquals(0.01, region.getDouble(Tag.PhysicalDeltaX, 0.0), 1.0e-12);
  }

  @Test
  void getUniqueSpatialRegion_nonSpatialRegionIgnored() {
    // A region in seconds (M-mode) is not spatial; must be filtered out.
    Attributes a = new Attributes();
    Sequence seq = a.newSequence(Tag.SequenceOfUltrasoundRegions, 1);
    seq.add(makeRegion(UNIT_SECONDS, 1e-4, 0));

    assertNull(Ultrasound.getUniqueSpatialRegion(a));
  }

  @Test
  void getUniqueSpatialRegion_multipleRegionsSameCalibrationReturnsFirst() {
    Attributes a = new Attributes();
    Sequence seq = a.newSequence(Tag.SequenceOfUltrasoundRegions, 2);
    seq.add(makeRegion(UNIT_CM, 0.01, 0.01));
    seq.add(makeRegion(UNIT_CM, 0.01, 0.01)); // identical calibration

    Attributes region = Ultrasound.getUniqueSpatialRegion(a);

    assertNotNull(region);
    assertEquals(0.01, region.getDouble(Tag.PhysicalDeltaX, 0.0), 1.0e-12);
  }

  @Test
  void getUniqueSpatialRegion_multipleRegionsDifferentCalibrationReturnsNull() {
    // Two spatial regions with different mm/px ratios → ambiguous calibration → must refuse
    // rather than pick one silently.
    Attributes a = new Attributes();
    Sequence seq = a.newSequence(Tag.SequenceOfUltrasoundRegions, 2);
    seq.add(makeRegion(UNIT_CM, 0.01, 0.01));
    seq.add(makeRegion(UNIT_CM, 0.02, 0.02)); // different ratio

    assertNull(Ultrasound.getUniqueSpatialRegion(a));
  }

  @Test
  void getUniqueSpatialRegion_spatialAndNonSpatialMixed_returnsSpatial() {
    Attributes a = new Attributes();
    Sequence seq = a.newSequence(Tag.SequenceOfUltrasoundRegions, 2);
    seq.add(makeRegion(UNIT_SECONDS, 1e-4, 0));
    seq.add(makeRegion(UNIT_CM, 0.01, 0.01));

    Attributes region = Ultrasound.getUniqueSpatialRegion(a);

    assertNotNull(region);
    assertEquals(UNIT_CM, region.getInt(Tag.PhysicalUnitsXDirection, -1));
  }

  // -- helpers --------------------------------------------------------------

  private static Attributes makeRegion(int unitCode, double deltaX, double deltaY) {
    Attributes region = new Attributes();
    region.setInt(Tag.PhysicalUnitsXDirection, VR.US, unitCode);
    region.setInt(Tag.PhysicalUnitsYDirection, VR.US, unitCode);
    region.setDouble(Tag.PhysicalDeltaX, VR.FD, deltaX);
    region.setDouble(Tag.PhysicalDeltaY, VR.FD, deltaY);
    return region;
  }
}
