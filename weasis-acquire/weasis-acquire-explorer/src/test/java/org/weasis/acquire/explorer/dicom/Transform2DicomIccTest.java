/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer.dicom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.color.ICC_Profile;
import java.nio.ByteBuffer;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.image.color.IccProfile;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.opencv.data.PlanarImage;

/** The ICC Profile module of a dicomized photo describes the pixels written. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class Transform2DicomIccTest {

  @Test
  void the_profile_of_the_photo_is_written_as_an_input_device_profile() {
    IccProfile wide = wideGamut();
    Attributes attrs = new Attributes();

    Transform2Dicom.writeIccProfile(attrs, photo(3, wide));

    IccProfile written = IccProfile.of(attrs.getSafeBytes(Tag.ICCProfile)).orElseThrow();
    assertEquals("scnr", written.deviceClass());
    assertFalse(attrs.contains(Tag.ColorSpace));
  }

  @Test
  void an_untagged_photo_states_srgb() {
    Attributes attrs = new Attributes();
    Transform2Dicom.writeIccProfile(attrs, photo(3, null));
    assertNotNull(attrs.getSafeBytes(Tag.ICCProfile));
    assertEquals("SRGB", attrs.getString(Tag.ColorSpace));
  }

  @Test
  void a_grayscale_photo_has_no_icc_profile_module() {
    Attributes attrs = new Attributes();
    Transform2Dicom.writeIccProfile(attrs, photo(1, null));
    assertFalse(attrs.contains(Tag.ICCProfile));
  }

  private static ImageElement photo(int channels, IccProfile profile) {
    PlanarImage image = mock(PlanarImage.class);
    when(image.channels()).thenReturn(channels);
    ImageElement element = mock(ImageElement.class);
    when(element.getImage()).thenReturn(image);
    when(element.getTagValue(TagW.IccProfile)).thenReturn(profile);
    return element;
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
