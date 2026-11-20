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
import org.weasis.dicom.codec.geometry.GeometryOfSlice;
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
        () ->
            assertEquals(
                new Vector3d(27.5, 27.5, 16), centre, "TLHC -100 + 127.5 px of 1 mm, z 10+6"),
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
        () -> assertNull(ReformatService.centerOf((DicomImageElement) null)));
  }

  @Test
  void the_center_of_a_wide_slice_takes_the_columns_along_the_rows() {
    // 512 rows x 583 columns, pixels 0.5 mm across and 0.25 mm down, from the origin
    GeometryOfSlice geometry =
        new GeometryOfSlice(
            new Vector3d(1, 0, 0),
            new Vector3d(0, 1, 0),
            new Vector3d(0, 0, 0),
            new Vector3d(0.5, 0.25, 1),
            1,
            new Vector3d(512, 583, 1));
    assertEquals(
        new Vector3d(582 / 2.0 * 0.5, 511 / 2.0 * 0.25, 0), ReformatService.centerOf(geometry));
  }
}
