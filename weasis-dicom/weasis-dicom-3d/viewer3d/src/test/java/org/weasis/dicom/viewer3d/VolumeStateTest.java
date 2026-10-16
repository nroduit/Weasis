/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.viewer3d.VolumeState.CameraState;
import org.weasis.dicom.viewer3d.VolumeState.Rendering;

class VolumeStateTest {

  @Test
  void theVolumePartSurvivesJson() {
    VolumeState state =
        new VolumeState(
            "1.2.3", // NON-NLS
            "1x1", // NON-NLS
            "ct-bone", // NON-NLS
            new CameraState(new double[] {0, 0, -500}, new double[] {0, 0.5, 0, 0.866}, 1.5, true),
            new Rendering("mip_max", true, false, 3, 0.8, 1500, 300, true)); // NON-NLS
    JsonObject json =
        state.writeTo(Json.createObjectBuilder().add("name", "CT")).build(); // NON-NLS
    VolumeState read = VolumeState.fromJson(json);
    assertAll(
        () -> assertEquals("CT", json.getString("name")), // NON-NLS
        () -> assertEquals("1.2.3", json.getJsonArray("series").getString(0)), // NON-NLS
        () -> assertEquals("ct-bone", read.preset()), // NON-NLS
        () -> assertArrayEquals(state.camera().position(), read.camera().position()),
        () -> assertArrayEquals(state.camera().rotation(), read.camera().rotation()),
        () -> assertEquals(1.5, read.camera().zoom()),
        () -> assertTrue(read.camera().orthographic()),
        () -> assertEquals(state.rendering(), read.rendering()));
  }

  @Test
  void aStateWithoutTheVolumePartReadsAsUnknown() {
    VolumeState read = VolumeState.fromJson(Json.createObjectBuilder().build());
    assertAll(
        () -> assertNull(read.series()),
        () -> assertNull(read.preset()),
        () -> assertNull(read.camera()),
        () -> assertNull(read.rendering()));
  }
}
