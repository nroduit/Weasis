/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.gl;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/**
 * What the platform back-ends declare by hand, checked where the build runs rather than only where
 * the code executes. The Foreign Function &amp; Memory API has no header to compare against, so a
 * wrong offset is a corrupt call and not a compilation error; the expected values are those of
 * {@code wingdi.h} and {@code winuser.h} on x64.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class PlatformContextTest {

  @Test
  @DisplayName("PIXELFORMATDESCRIPTOR has the size and the offsets of wingdi.h")
  void pixel_format_descriptor_matches_the_header() {
    Map<String, Long> expected = new LinkedHashMap<>();
    expected.put("nSize", 0L);
    expected.put("nVersion", 2L);
    expected.put("dwFlags", 4L);
    expected.put("iPixelType", 8L);
    expected.put("cColorBits", 9L);
    expected.put("cRedBits", 10L);
    expected.put("cRedShift", 11L);
    expected.put("cGreenBits", 12L);
    expected.put("cGreenShift", 13L);
    expected.put("cBlueBits", 14L);
    expected.put("cBlueShift", 15L);
    expected.put("cAlphaBits", 16L);
    expected.put("cAlphaShift", 17L);
    expected.put("cAccumBits", 18L);
    expected.put("cAccumRedBits", 19L);
    expected.put("cAccumGreenBits", 20L);
    expected.put("cAccumBlueBits", 21L);
    expected.put("cAccumAlphaBits", 22L);
    expected.put("cDepthBits", 23L);
    expected.put("cStencilBits", 24L);
    expected.put("cAuxBuffers", 25L);
    expected.put("iLayerType", 26L);
    expected.put("bReserved", 27L);
    expected.put("dwLayerMask", 28L);
    expected.put("dwVisibleMask", 32L);
    expected.put("dwDamageMask", 36L);

    assertAll(
        () -> assertEquals(40L, WglContext.PIXEL_FORMAT_DESCRIPTOR.byteSize(), "byte size"),
        () ->
            assertAll(
                expected.entrySet().stream()
                    .map(
                        field ->
                            (org.junit.jupiter.api.function.Executable)
                                () ->
                                    assertEquals(
                                        field.getValue(),
                                        WglContext.pfd(field.getKey()),
                                        field.getKey()))));
  }

  @Test
  @DisplayName("Compute shaders are claimed for 4.3 and above, and for nothing below it")
  void compute_capability_follows_the_version() {
    assertAll(
        () -> assertEquals(false, GlContext.isComputeCapable(3, 3), "3.3"), // NON-NLS
        () -> assertEquals(false, GlContext.isComputeCapable(4, 1), "4.1"), // NON-NLS
        () -> assertEquals(false, GlContext.isComputeCapable(4, 2), "4.2"), // NON-NLS
        () -> assertEquals(true, GlContext.isComputeCapable(4, 3), "4.3"), // NON-NLS
        () -> assertEquals(true, GlContext.isComputeCapable(4, 6), "4.6"), // NON-NLS
        () -> assertEquals(true, GlContext.isComputeCapable(5, 0), "5.0")); // NON-NLS
  }

  @Test
  @DisplayName("The version a driver reports is read whatever the vendor appends to it")
  void the_reported_version_is_parsed() {
    assertAll(
        () -> assertArrayEquals(new int[] {4, 6}, GlContext.parseVersion("4.6.0 NVIDIA 595.91.07")),
        () -> assertArrayEquals(new int[] {4, 1}, GlContext.parseVersion("4.1 Metal - 89.3")),
        () ->
            assertArrayEquals(
                new int[] {4, 5},
                GlContext.parseVersion("4.5 (Core Profile) Mesa 26.0.8-1ubuntu0.3")),
        () -> assertArrayEquals(new int[] {3, 3}, GlContext.parseVersion("3.3.0")),
        () -> assertArrayEquals(new int[] {0, 0}, GlContext.parseVersion("OpenGL ES 3.2")),
        () -> assertArrayEquals(new int[] {0, 0}, GlContext.parseVersion(null)),
        () -> assertEquals(false, GlContext.isSupported(new int[] {3, 2}), "3.2"), // NON-NLS
        () -> assertEquals(true, GlContext.isSupported(new int[] {3, 3}), "3.3"), // NON-NLS
        () -> assertEquals(true, GlContext.isSupported(new int[] {4, 1}), "4.1")); // NON-NLS
  }

  @Test
  @DisplayName("WNDCLASSEXW has the size and the offsets of winuser.h on x64")
  void window_class_matches_the_header() {
    Map<String, Long> expected = new LinkedHashMap<>();
    expected.put("cbSize", 0L);
    expected.put("style", 4L);
    expected.put("lpfnWndProc", 8L);
    expected.put("cbClsExtra", 16L);
    expected.put("cbWndExtra", 20L);
    expected.put("hInstance", 24L);
    expected.put("hIcon", 32L);
    expected.put("hCursor", 40L);
    expected.put("hbrBackground", 48L);
    expected.put("lpszMenuName", 56L);
    expected.put("lpszClassName", 64L);
    expected.put("hIconSm", 72L);

    assertAll(
        () -> assertEquals(80L, WglContext.WNDCLASSEXW.byteSize(), "byte size"),
        () ->
            assertAll(
                expected.entrySet().stream()
                    .map(
                        field ->
                            (org.junit.jupiter.api.function.Executable)
                                () ->
                                    assertEquals(
                                        field.getValue(),
                                        WglContext.wndClass(field.getKey()),
                                        field.getKey()))));
  }
}
