/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.joml.Vector3d;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.viewer2d.mpr.MprView.Plane;
import org.weasis.dicom.viewer2d.subtraction.SyntheticSeries;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ReformatServiceTest {

  @Test
  void the_slice_through_the_centre_of_a_slice_is_that_slice() {
    // Axial 256 x 256 at 1 mm, slices 2 mm apart from z = 10
    DicomSeries series = SyntheticSeries.mr("1.2.3.60", 8, 10); // NON-NLS
    List<DicomImageElement> slices = series.copyOfMedias(null, null);
    Vector3d centre = ReformatService.centerOf(slices.get(3));
    assertAll(
        () -> assertEquals(new Vector3d(28, 28, 16), centre, "TLHC -100 + 128 px of 1 mm, z 10+6"),
        () -> assertSame(slices.get(3), ReformatService.sliceThrough(series, centre)),
        () ->
            assertSame(
                slices.get(4), ReformatService.sliceThrough(series, new Vector3d(0, 0, 17.2))),
        () ->
            assertSame(
                slices.getLast(), ReformatService.sliceThrough(series, new Vector3d(0, 0, 99))),
        () -> assertEquals(Plane.AXIAL, ReformatService.planeOf(series)),
        () ->
            assertSame(
                series, ReformatService.sourceOf(series), "an acquired series is its own source"),
        () -> assertTrue(ReformatService.canReformat(series)),
        () ->
            assertFalse(
                ReformatService.canReformat(SyntheticSeries.mr("1.2.3.61", 3, 0)),
                "too few slices"),
        () -> assertNull(ReformatService.centerOf(null)));
  }
}
