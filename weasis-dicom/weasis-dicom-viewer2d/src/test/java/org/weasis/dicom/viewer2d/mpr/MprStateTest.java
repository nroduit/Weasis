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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.editor.image.ViewState.ViewPresentation;
import org.weasis.dicom.viewer2d.mpr.MprState.PlaneState;
import org.weasis.dicom.viewer2d.mpr.MprView.Plane;

class MprStateTest {

  @Test
  void theMprPartSurvivesJsonAndReplacesTheSeriesOfTheBaseState() {
    MprState state =
        new MprState(
            "1.2.3", // NON-NLS
            "mpr_1x3", // NON-NLS
            new double[] {10, 20.5, 30},
            new double[] {0, 0, 0.7071, 0.7071},
            "max", // NON-NLS
            Map.of(
                Plane.AXIAL, new PlaneState(3, ViewPresentation.DEFAULT),
                Plane.CORONAL, new PlaneState(0, ViewPresentation.DEFAULT)));
    JsonObject base =
        Json.createObjectBuilder()
            .add("name", "CT") // NON-NLS
            .add("series", Json.createArrayBuilder().add("generated-plane")) // NON-NLS
            .build();
    JsonObject json = state.writeTo(Json.createObjectBuilder(base)).build();
    MprState read = MprState.fromJson(json);
    assertAll(
        () -> assertEquals("CT", json.getString("name")), // NON-NLS
        () -> assertEquals("1.2.3", json.getJsonArray("series").getString(0)), // NON-NLS
        () -> assertEquals("1.2.3", read.series()), // NON-NLS
        () -> assertEquals("mpr_1x3", read.grid()), // NON-NLS
        () -> assertArrayEquals(state.crosshair(), read.crosshair()),
        () -> assertArrayEquals(state.rotation(), read.rotation()),
        () -> assertEquals("max", read.mip()), // NON-NLS
        () -> assertEquals(3, read.planes().get(Plane.AXIAL).thickness()),
        () -> assertEquals(0, read.planes().get(Plane.CORONAL).thickness()),
        () -> assertTrue(!read.planes().containsKey(Plane.SAGITTAL)),
        () -> assertEquals(ViewPresentation.DEFAULT, read.planes().get(Plane.AXIAL).view()));
  }

  @Test
  void aStateWithoutTheMprPartReadsAsUnknown() {
    MprState read = MprState.fromJson(Json.createObjectBuilder().build());
    assertAll(
        () -> assertNull(read.series()),
        () -> assertNull(read.crosshair()),
        () -> assertNull(read.rotation()),
        () -> assertNull(read.mip()),
        () -> assertTrue(read.planes().isEmpty()));
  }
}
