/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.dcm4che3.data.Tag;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.image.util.Unit;

/** Dropping a manual calibration brings back the pixel spacing of the DICOM file. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class DicomCalibrationRestoreTest {

  private static DicomImageElement image(double[] pixelSpacing, String description) {
    DcmMediaReader reader = mock(DcmMediaReader.class);
    when(reader.getTagValue(TagD.get(Tag.Modality))).thenReturn("CT"); // NON-NLS
    when(reader.getTagValue(TagD.get(Tag.PixelSpacing))).thenReturn(pixelSpacing);
    when(reader.getTagValue(TagD.get(Tag.PixelSpacingCalibrationDescription)))
        .thenReturn(description);
    return new DicomImageElement(reader, 0);
  }

  private static void calibrateByHand(DicomImageElement image) {
    image.setPixelSize(0.123);
    image.setPixelSpacingUnit(Unit.CENTIMETER);
    image.setPixelSizeCalibrationDescription("Modified by user"); // NON-NLS
    image.setPixelSizeModifiedByUser(true);
  }

  @Test
  void the_pixel_spacing_of_the_file_is_restored() {
    DicomImageElement image = image(new double[] {0.7, 0.7}, "Fiducial"); // NON-NLS
    calibrateByHand(image);
    image.initPixelConfiguration();
    assertAll(
        () -> assertEquals(0.7, image.getPixelSize(), 1e-9),
        () -> assertEquals(Unit.MILLIMETER, image.getPixelSpacingUnit()),
        () -> assertEquals("Fiducial", image.getPixelSizeCalibrationDescription()), // NON-NLS
        () -> assertFalse(image.isPixelSizeModifiedByUser()));
  }

  @Test
  void a_file_without_spacing_goes_back_to_pixels() {
    DicomImageElement image = image(null, null);
    calibrateByHand(image);
    image.initPixelConfiguration();
    assertAll(
        () -> assertEquals(1.0, image.getPixelSize(), 1e-9),
        () -> assertEquals(Unit.PIXEL, image.getPixelSpacingUnit()),
        () -> assertNull(image.getPixelSizeCalibrationDescription()),
        () -> assertFalse(image.isPixelSizeModifiedByUser()));
  }
}
