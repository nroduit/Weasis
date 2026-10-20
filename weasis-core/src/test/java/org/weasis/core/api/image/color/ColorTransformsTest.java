/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image.color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.ColorConvertOp;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.nio.ByteBuffer;
import java.util.Random;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.image.color.ColorTransform.Kind;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ColorTransformsTest {

  /** sRGB with the D50-adapted Adobe RGB (1998) primaries: a wide-gamut matrix/TRC profile. */
  static ICC_Profile wideGamut() {
    ICC_Profile profile = ICC_Profile.getInstance(srgb().getData());
    profile.setData(ICC_Profile.icSigRedColorantTag, xyz(0.6097, 0.3111, 0.0195));
    profile.setData(ICC_Profile.icSigGreenColorantTag, xyz(0.2053, 0.6257, 0.0609));
    profile.setData(ICC_Profile.icSigBlueColorantTag, xyz(0.1492, 0.0632, 0.7446));
    return ICC_Profile.getInstance(profile.getData());
  }

  static ICC_Profile srgb() {
    return ICC_Profile.getInstance(ColorSpace.CS_sRGB);
  }

  @Test
  void same_colors_are_the_identity() {
    assertSame(ColorTransform.IDENTITY, ColorTransforms.build(srgb(), srgb()));
    assertSame(
        ColorTransform.IDENTITY,
        ColorTransforms.build(ICC_Profile.getInstance(srgb().getData()), srgb()));
  }

  @Test
  void two_matrix_profiles_use_the_tables() {
    ColorTransform transform = ColorTransforms.build(wideGamut(), srgb());
    assertEquals(Kind.MATRIX, transform.kind());
    assertWithin(1, transform, wideGamut(), srgb());
  }

  @Test
  void linear_destination_uses_the_tables() {
    ICC_Profile linear = ICC_Profile.getInstance(ColorSpace.CS_LINEAR_RGB);
    ColorTransform transform = ColorTransforms.build(srgb(), linear);
    assertEquals(Kind.MATRIX, transform.kind());
    assertWithin(1, transform, srgb(), linear);
  }

  /** Different primaries: interpolating clipped colors would be tens of levels off at the edge. */
  @Test
  void cube_matches_the_module_next_to_the_gamut_boundary() {
    CubeTransform cube = ColorTransforms.cube(wideGamut(), srgb());
    assertWithin(2, cube, wideGamut(), srgb());
  }

  @Test
  void pairs_are_cached() {
    IccProfile wide = IccProfile.of(wideGamut().getData()).orElseThrow();
    assertSame(
        ColorTransforms.of(wide, IccProfile.SRGB), ColorTransforms.of(wide, IccProfile.SRGB));
    assertSame(ColorTransform.IDENTITY, ColorTransforms.of(wide, wide));
  }

  private static void assertWithin(
      int tolerance, ColorTransform transform, ICC_Profile src, ICC_Profile dst) {
    int count = 512 * 64;
    byte[] rgb = new byte[count * 3];
    new Random(7).nextBytes(rgb);
    WritableRaster in = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE, count, 1, 3, null);
    System.arraycopy(rgb, 0, ((DataBufferByte) in.getDataBuffer()).getData(), 0, rgb.length);
    WritableRaster out = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE, count, 1, 3, null);
    new ColorConvertOp(new ICC_Profile[] {src, dst}, null).filter(in, out);
    byte[] expected = ((DataBufferByte) out.getDataBuffer()).getData();

    byte[] bgr = new byte[rgb.length];
    for (int i = 0; i < rgb.length; i += 3) {
      bgr[i] = rgb[i + 2];
      bgr[i + 1] = rgb[i + 1];
      bgr[i + 2] = rgb[i];
    }
    byte[] result = new byte[bgr.length];
    transform.convertRow(bgr, result, bgr.length);
    int max = 0;
    for (int i = 0; i < rgb.length; i += 3) {
      for (int c = 0; c < 3; c++) {
        max = Math.max(max, Math.abs((expected[i + c] & 0xFF) - (result[i + 2 - c] & 0xFF)));
      }
    }
    assertTrue(max <= tolerance, "max difference with the module: " + max);
  }

  private static byte[] xyz(double x, double y, double z) {
    ByteBuffer buf = ByteBuffer.allocate(20);
    buf.put("XYZ ".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(0);
    buf.putInt((int) Math.round(x * 65536)).putInt((int) Math.round(y * 65536));
    buf.putInt((int) Math.round(z * 65536));
    return buf.array();
  }
}
