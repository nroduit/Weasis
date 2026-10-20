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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.color.ICC_Profile;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.image.color.ColorManagement;
import org.weasis.core.api.image.color.IccProfile;

@DisplayNameGeneration(ReplaceUnderscores.class)
class IccProfileModuleTest {

  @Test
  void srgb_or_no_profile_writes_the_input_srgb_profile() {
    for (IccProfile profile : new IccProfile[] {null, IccProfile.SRGB}) {
      Attributes attrs = new Attributes();
      IccProfileModule.write(attrs, profile);
      assertArrayEquals(SrgbInputProfile.data(), attrs.getSafeBytes(Tag.ICCProfile));
      assertEquals("SRGB", attrs.getString(Tag.ColorSpace));
    }
  }

  @Test
  void a_display_class_profile_is_relabeled_as_input_device() throws Exception {
    IccProfile wide = wideGamut();
    assertEquals("mntr", wide.deviceClass());
    Attributes attrs = new Attributes();
    attrs.setString(Tag.ColorSpace, VR.CS, "SRGB");

    IccProfileModule.write(attrs, wide);

    IccProfile written = IccProfile.of(attrs.getSafeBytes(Tag.ICCProfile)).orElseThrow();
    assertEquals("scnr", written.deviceClass(), "PS3.3 C.11.15.1.1");
    assertFalse(attrs.contains(Tag.ColorSpace), "not sRGB colors");
    assertTrue(ColorManagement.to(written, wide).isIdentity(), "same colors");

    byte[] data = written.data();
    byte[] hashed = data.clone();
    Arrays.fill(hashed, 44, 48, (byte) 0);
    Arrays.fill(hashed, 64, 68, (byte) 0);
    Arrays.fill(hashed, 84, 100, (byte) 0);
    assertArrayEquals(
        MessageDigest.getInstance("MD5").digest(hashed), Arrays.copyOfRange(data, 84, 100));
  }

  private static IccProfile wideGamut() {
    ICC_Profile profile = ICC_Profile.getInstance(IccProfile.SRGB.data());
    profile.setData(ICC_Profile.icSigRedColorantTag, xyz(0.6097, 0.3111, 0.0195));
    profile.setData(ICC_Profile.icSigGreenColorantTag, xyz(0.2053, 0.6257, 0.0609));
    profile.setData(ICC_Profile.icSigBlueColorantTag, xyz(0.1492, 0.0632, 0.7446));
    return IccProfile.of(profile.getData()).orElseThrow();
  }

  private static byte[] xyz(double x, double y, double z) {
    ByteBuffer buf = ByteBuffer.allocate(20);
    buf.put(new byte[] {'X', 'Y', 'Z', ' '}).putInt(0);
    buf.putInt((int) Math.round(x * 65536)).putInt((int) Math.round(y * 65536));
    buf.putInt((int) Math.round(z * 65536));
    return buf.array();
  }
}
