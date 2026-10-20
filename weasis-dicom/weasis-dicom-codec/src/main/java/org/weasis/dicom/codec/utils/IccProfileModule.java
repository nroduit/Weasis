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

import java.util.Arrays;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.weasis.core.api.image.color.ColorManagement;
import org.weasis.core.api.image.color.IccProfile;

/**
 * Writes the ICC Profile module (PS3.3 C.11.15) of an object whose color pixels are described by a
 * given profile. The module requires an Input Device profile (class {@code scnr}, C.11.15.1.1),
 * while camera and display profiles are usually {@code mntr}: the class is relabeled and the
 * profile id recomputed, which leaves the transform unchanged.
 */
public final class IccProfileModule {
  private static final int CLASS_OFFSET = 12;
  private static final int FLAGS_OFFSET = 44;
  private static final int INTENT_OFFSET = 64;
  private static final int PROFILE_ID_OFFSET = 84;
  private static final String INPUT_CLASS = "scnr"; // NON-NLS

  private IccProfileModule() {}

  /**
   * Sets ICC Profile (0028,2000), and Color Space (0028,2002) = {@code SRGB} when the colors are
   * sRGB. A missing profile means sRGB: {@link SrgbInputProfile} is written, so every object states
   * its colors the same way.
   *
   * @param profile the profile of the pixels written, or null for sRGB
   */
  public static void write(Attributes attrs, IccProfile profile) {
    boolean srgb = profile == null || ColorManagement.to(profile, IccProfile.SRGB).isIdentity();
    attrs.setBytes(Tag.ICCProfile, VR.OB, srgb ? SrgbInputProfile.data() : asInputDevice(profile));
    if (srgb) {
      attrs.setString(Tag.ColorSpace, VR.CS, "SRGB"); // NON-NLS
    } else {
      attrs.remove(Tag.ColorSpace);
    }
  }

  /** The profile bytes with the Input Device class and the matching profile id. */
  static byte[] asInputDevice(IccProfile profile) {
    byte[] data = profile.data();
    if (INPUT_CLASS.equals(profile.deviceClass())) {
      return data;
    }
    SrgbInputProfile.signature(data, CLASS_OFFSET, INPUT_CLASS);
    // ICC.1 7.2.18: the id is the MD5 of the profile with the flags, intent and id fields zeroed
    byte[] hashed = data.clone();
    Arrays.fill(hashed, FLAGS_OFFSET, FLAGS_OFFSET + 4, (byte) 0);
    Arrays.fill(hashed, INTENT_OFFSET, INTENT_OFFSET + 4, (byte) 0);
    Arrays.fill(hashed, PROFILE_ID_OFFSET, PROFILE_ID_OFFSET + 16, (byte) 0);
    System.arraycopy(SrgbInputProfile.md5(hashed), 0, data, PROFILE_ID_OFFSET, 16);
    return data;
  }
}
