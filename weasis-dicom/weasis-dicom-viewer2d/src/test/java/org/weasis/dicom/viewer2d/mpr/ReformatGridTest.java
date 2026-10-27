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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.joml.Vector3d;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.viewer2d.mpr.MprView.Plane;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ReformatGridTest {

  private static final Vector3d[] BOX = {
    new Vector3d(-100, -50, -30),
    new Vector3d(100, -50, -30),
    new Vector3d(-100, 50, -30),
    new Vector3d(-100, -50, 30),
    new Vector3d(100, 50, 30)
  };

  @Test
  void a_coronal_grid_runs_from_anterior_to_posterior_with_superior_rows_first() {
    ReformatGrid grid = ReformatGrid.of(BOX, 1.0, Plane.CORONAL);
    assertAll(
        () -> assertEquals(201, grid.width()),
        () -> assertEquals(61, grid.height()),
        () -> assertEquals(101, grid.slices()),
        () -> assertEquals(new Vector3d(-100, -50, 30), grid.tlhc(0)),
        () -> assertEquals(new Vector3d(-100, -45, 30), grid.tlhc(5)),
        () -> assertArrayEquals(new double[] {1, 0, 0, 0, 0, -1}, grid.orientation()),
        () -> assertEquals(1.0, grid.geometry(0).getSliceThickness()),
        () -> assertEquals(1.0, grid.geometry(0).getNormal().y, 1e-9, "normal along A-P"));
  }

  @Test
  void a_sagittal_grid_runs_from_right_to_left_and_an_axial_one_from_inferior_to_superior() {
    ReformatGrid sagittal = ReformatGrid.of(BOX, 0.5, Plane.SAGITTAL);
    ReformatGrid axial = ReformatGrid.of(BOX, 2.0, Plane.AXIAL);
    assertAll(
        () -> assertEquals(201, sagittal.width(), "the anterior-posterior extent at 0.5 mm"),
        () -> assertEquals(121, sagittal.height()),
        () -> assertEquals(401, sagittal.slices()),
        () -> assertEquals(new Vector3d(-99.5, -50, 30), sagittal.tlhc(1)),
        () -> assertArrayEquals(new double[] {0, 1, 0, 0, 0, -1}, sagittal.orientation()),
        () -> assertEquals(101, axial.width()),
        () -> assertEquals(51, axial.height()),
        () -> assertEquals(31, axial.slices()),
        () -> assertEquals(new Vector3d(-100, -50, -28), axial.tlhc(1)),
        () -> assertArrayEquals(new double[] {1, 0, 0, 0, 1, 0}, axial.orientation()),
        () ->
            assertThrows(
                IllegalArgumentException.class, () -> ReformatGrid.of(BOX, 0, Plane.AXIAL)));
  }
}
