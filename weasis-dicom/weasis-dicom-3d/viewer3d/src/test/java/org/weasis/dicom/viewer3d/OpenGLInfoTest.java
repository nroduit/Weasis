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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class OpenGLInfoTest {

  private static OpenGLInfo info(String glVersion) {
    return new OpenGLInfo(glVersion, glVersion, "vendor", "renderer", 2048);
  }

  @ParameterizedTest
  @CsvSource({
    // The release number is optional, so GL_VERSION is not valid semver.
    "'4.1 Metal - 90.5', 4.1.0",
    "'4.6.0 NVIDIA 550.54.14', 4.6.0",
    "'3.3', 3.3.0",
    "'4.5 (Core Profile) Mesa 24.0.9', 4.5.0",
    "'OpenGL ES 3.2 v1.r26p0', 3.2.0",
    // Unparsable versions must not be mistaken for a compliant one.
    "'unknown', 0.0.0",
  })
  void getVersionReadsMajorAndMinorFromVendorSpecificString(String glVersion, String expected) {
    assertEquals(expected, info(glVersion).getVersion().toString());
  }

  @ParameterizedTest
  @ValueSource(strings = {"4.1 Metal - 90.5", "3.3", "4.6.0 NVIDIA 550.54.14"})
  void isVersionCompliantAcceptsAtLeast33(String glVersion) {
    assertTrue(info(glVersion).isVersionCompliant());
  }

  @ParameterizedTest
  @ValueSource(strings = {"3.2 Mesa 20.0", "2.1", "unknown"})
  void isVersionCompliantRejectsBelow33(String glVersion) {
    assertFalse(info(glVersion).isVersionCompliant());
  }

  @ParameterizedTest
  @CsvSource({"'4.3.0 NVIDIA', true", "'4.6 Mesa', true", "'4.1 Metal - 90.5', false"})
  void isComputeShaderCapableRequires43(String glVersion, boolean expected) {
    assertEquals(expected, info(glVersion).isComputeShaderCapable());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "llvmpipe (LLVM 15.0.7, 256 bits)",
        "SwiftShader Device (Subzero)",
        "ANGLE (Microsoft, Direct3D11 WARP vs_5_0 ps_5_0)",
      })
  void looksSoftwareIgnoresRendererCase(String renderer) {
    assertTrue(new OpenGLInfo("4.1", "4.1", "vendor", renderer, 2048).looksSoftware());
  }

  @ParameterizedTest
  @ValueSource(strings = {"Apple M1 Pro", "NVIDIA GeForce RTX 3080/PCIe/SSE2"})
  void looksSoftwareAcceptsRealGpu(String renderer) {
    assertFalse(new OpenGLInfo("4.1", "4.1", "vendor", renderer, 2048).looksSoftware());
  }
}
