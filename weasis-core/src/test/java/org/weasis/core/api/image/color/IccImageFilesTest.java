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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.weasis.core.api.image.cv.ImageCVIO;
import org.weasis.core.api.image.cv.OpenCvTestLoader;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.internal.cv.NativeOpenCVCodec;
import org.weasis.opencv.data.ImageCV;

@DisplayNameGeneration(ReplaceUnderscores.class)
class IccImageFilesTest {

  @TempDir Path dir;

  @BeforeAll
  static void loadOpenCV() {
    assumeTrue(OpenCvTestLoader.tryLoad(), "OpenCV native library unavailable, skipping");
  }

  @Test
  void png_and_jpeg_carry_the_profile_read_back_by_the_image_reader() {
    IccProfile profile = IccProfile.of(ColorTransformsTest.wideGamut().getData()).orElseThrow();
    for (String name : new String[] {"capture.png", "capture.jpg"}) {
      Path file = dir.resolve(name);
      assertTrue(IccImageFiles.write(image(), file, null, profile), name);

      ImageCVIO reader = new ImageCVIO(file.toUri(), mime(name), new NativeOpenCVCodec());
      ImageElement element = reader.getMediaElement()[0];
      assertNotNull(element.getImage(), name);
      assertEquals(profile, element.getTagValue(TagW.IccProfile), name);
    }
  }

  @Test
  void a_profile_too_large_for_one_jpeg_segment_is_not_embedded() {
    byte[] big = Arrays.copyOf(IccProfile.SRGB.data(), IccImageFiles.JPEG_MAX_PROFILE + 4);
    // keep the header consistent with the new size
    ByteBuffer.wrap(big).putInt(0, big.length);
    IccProfile profile = IccProfile.of(big).orElseThrow();
    assertFalse(IccImageFiles.canEmbed(Path.of("a.jpg"), profile));
    assertTrue(IccImageFiles.canEmbed(Path.of("a.png"), profile));
    assertFalse(IccImageFiles.canEmbed(Path.of("a.tif"), IccProfile.SRGB));
  }

  @Test
  void an_untagged_file_has_no_profile() {
    Path file = dir.resolve("plain.png");
    assertTrue(IccImageFiles.write(image(), file, null, null));
    ImageElement element =
        new ImageCVIO(file.toUri(), "image/png", new NativeOpenCVCodec()).getMediaElement()[0];
    assertNotNull(element.getImage());
    assertNull(element.getTagValue(TagW.IccProfile));
  }

  private static String mime(String name) {
    return name.endsWith(".png") ? "image/png" : "image/jpeg"; // NON-NLS
  }

  private static ImageCV image() {
    ImageCV img = new ImageCV(24, 32, CvType.CV_8UC3);
    Core.randu(img, 0, 256);
    return img;
  }
}
