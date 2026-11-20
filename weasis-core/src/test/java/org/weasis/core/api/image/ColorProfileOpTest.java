/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.color.ICC_Profile;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Rect;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.ImageOpEvent.OpEvent;
import org.weasis.core.api.image.ImageOpNode.Param;
import org.weasis.core.api.image.color.ColorManagement;
import org.weasis.core.api.image.color.ColorTransform;
import org.weasis.core.api.image.color.IccProfile;
import org.weasis.core.api.image.cv.OpenCvTestLoader;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Taggable;
import org.weasis.core.api.service.WProperties;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.lut.ByteLut;
import org.weasis.opencv.op.lut.ColorLut;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ColorProfileOpTest {

  private static IccProfile wide;

  @BeforeAll
  static void loadOpenCV() {
    assumeTrue(OpenCvTestLoader.tryLoad(), "OpenCV native library unavailable, skipping");
    ICC_Profile profile = ICC_Profile.getInstance(IccProfile.SRGB.data());
    profile.setData(ICC_Profile.icSigRedColorantTag, xyz(0.6097, 0.3111, 0.0195));
    profile.setData(ICC_Profile.icSigGreenColorantTag, xyz(0.2053, 0.6257, 0.0609));
    profile.setData(ICC_Profile.icSigBlueColorantTag, xyz(0.1492, 0.0632, 0.7446));
    wide = IccProfile.of(profile.getData()).orElseThrow();
  }

  @Test
  void untagged_color_on_an_srgb_display_is_passed_through() throws Exception {
    ImageCV src = color(64, 48);
    assertSame(src, run(new ColorProfileOp(), src));
  }

  @Test
  void grayscale_is_never_converted() throws Exception {
    ImageCV gray = new ImageCV(8, 8, CvType.CV_8UC1);
    ColorProfileOp op = new ColorProfileOp();
    op.setParam(ColorProfileOp.P_SOURCE_PROFILE, wide);
    assertSame(gray, run(op, gray));
  }

  @Test
  void tagged_color_is_converted_into_a_new_image() throws Exception {
    ImageCV src = color(37, 23);
    byte[] before = pixels(src);
    ColorProfileOp op = new ColorProfileOp();
    op.setParam(ColorProfileOp.P_SOURCE_PROFILE, wide);

    PlanarImage out = run(op, src);

    assertNotSame(src, out);
    assertArrayEquals(before, pixels(src), "the input is not modified");
    ColorTransform transform = ColorManagement.toDisplay(wide);
    byte[] expected = new byte[before.length];
    transform.convertRow(before, expected, before.length);
    assertArrayEquals(expected, pixels(out.toImageCV()));
  }

  @Test
  void a_region_of_a_larger_image_is_converted_row_by_row() throws Exception {
    ImageCV parent = color(200, 120);
    ImageCV roi = new ImageCV(parent, new Rect(13, 7, 101, 77));
    ColorProfileOp op = new ColorProfileOp();
    op.setParam(ColorProfileOp.P_SOURCE_PROFILE, wide);

    PlanarImage out = run(op, roi);

    byte[] in = pixels(roi);
    byte[] expected = new byte[in.length];
    ColorManagement.toDisplay(wide).convertRow(in, expected, in.length);
    assertArrayEquals(expected, pixels(out.toImageCV()));
  }

  @Test
  void a_monitor_with_its_own_profile_gets_its_own_conversion(@TempDir Path dir) throws Exception {
    Path file = Files.write(dir.resolve("wide.icc"), wide.data());
    WProperties local = new WProperties();
    ColorManagement.setScreenStore(() -> local);
    try {
      long before = ColorManagement.generation();
      ColorManagement.setScreenProfile("screen-B", file);
      assertTrue(ColorManagement.generation() > before, "the views are rendered again");
      assertEquals(
          file.toAbsolutePath().toString(), local.getProperty("weasis.screen.screen-B.profile"));
      assertEquals(wide, ColorManagement.displayProfile("screen-B"));
      assertEquals(ColorManagement.displayProfile(), ColorManagement.displayProfile("screen-A"));

      ImageCV src = color(24, 16);
      ColorProfileOp onA = new ColorProfileOp();
      onA.setParam(ColorProfileOp.P_DISPLAY_ID, "screen-A");
      assertSame(src, run(onA, src), "sRGB image on a monitor without a profile of its own");

      ColorProfileOp onB = new ColorProfileOp();
      onB.setParam(ColorProfileOp.P_DISPLAY_ID, "screen-B");
      PlanarImage out = run(onB, src);
      assertNotSame(src, out);
      byte[] in = pixels(src);
      byte[] expected = new byte[in.length];
      ColorManagement.to(null, wide).convertRow(in, expected, in.length);
      assertArrayEquals(expected, pixels(out.toImageCV()));

      ColorManagement.setScreenProfile("screen-B", null);
      assertNull(local.getProperty("weasis.screen.screen-B.profile"));
      assertSame(src, run(onB, src), "back to the display profile of the workstation");
    } finally {
      ColorManagement.setScreenStore(() -> null);
    }
  }

  @Test
  void an_export_targets_srgb() throws Exception {
    ImageCV src = color(16, 16);
    ColorProfileOp op = ColorProfileOp.toSrgb(taggable(wide));
    assertEquals(wide, op.getParam(ColorProfileOp.P_SOURCE_PROFILE));
    assertNotSame(src, run(op, src));
  }

  @Test
  void the_profile_follows_the_image_and_the_presentation_state() {
    ImageElement image = mock(ImageElement.class);
    when(image.getTagValue(TagW.IccProfile)).thenReturn(wide);
    Taggable pr = taggable(IccProfile.SRGB);
    ColorProfileOp op = new ColorProfileOp();

    op.handleImageOpEvent(new ImageOpEvent(OpEvent.IMAGE_CHANGE, null, image, null));
    assertEquals(wide, op.getParam(ColorProfileOp.P_SOURCE_PROFILE));

    op.handleImageOpEvent(
        new ImageOpEvent(OpEvent.APPLY_PR, null, image, Map.of(ActionW.PR_STATE.cmd(), pr)));
    assertEquals(IccProfile.SRGB, op.getParam(ColorProfileOp.P_SOURCE_PROFILE));

    op.handleImageOpEvent(
        new ImageOpEvent(
            OpEvent.APPLY_PR, null, image, Map.of(ActionW.PR_STATE.cmd(), taggable(null))));
    assertEquals(wide, op.getParam(ColorProfileOp.P_SOURCE_PROFILE), "PS without the module");

    op.handleImageOpEvent(ImageOpEvent.of(OpEvent.RESET_DISPLAY));
    assertNull(op.getParam(ColorProfileOp.P_SOURCE_PROFILE));
  }

  @Test
  void colors_of_a_map_are_srgb_whatever_the_image_profile() throws Exception {
    ImageCV src = color(16, 8);
    ColorProfileOp op = new ColorProfileOp();
    op.setParam(ColorProfileOp.P_SOURCE_PROFILE, wide);
    op.setParam(ColorProfileOp.P_MAPPED_COLORS, true);

    PlanarImage out = run(op, src);

    byte[] in = pixels(src);
    byte[] expected = new byte[in.length];
    ColorManagement.toDisplay(null).convertRow(in, expected, in.length);
    assertArrayEquals(expected, pixels(out.toImageCV()), "converted as sRGB");
  }

  @Test
  void a_palette_of_a_state_is_a_map_unless_the_state_has_a_profile() {
    ImageElement image = mock(ImageElement.class);
    ByteLut palette = ColorLut.RED.getByteLut();
    ColorProfileOp op = new ColorProfileOp();

    op.handleImageOpEvent(
        new ImageOpEvent(OpEvent.APPLY_PR, null, image, Map.of(PseudoColorOp.P_LUT, palette)));
    assertEquals(true, op.getParam(ColorProfileOp.P_MAPPED_COLORS));

    op.handleImageOpEvent(
        new ImageOpEvent(
            OpEvent.APPLY_PR,
            null,
            image,
            Map.of(PseudoColorOp.P_LUT, palette, ActionW.PR_STATE.cmd(), taggable(wide))));
    assertEquals(false, op.getParam(ColorProfileOp.P_MAPPED_COLORS), "the state's own colors");
  }

  @Test
  void setting_the_same_profile_again_does_not_rerun() {
    ColorProfileOp op = new ColorProfileOp();
    op.setParam(ColorProfileOp.P_SOURCE_PROFILE, wide);
    long revision = op.getRevision();
    op.setParam(ColorProfileOp.P_SOURCE_PROFILE, IccProfile.of(wide.data()).orElseThrow());
    assertEquals(revision, op.getRevision());
  }

  private static Taggable taggable(IccProfile profile) {
    Taggable t = mock(Taggable.class);
    when(t.getTagValue(TagW.IccProfile)).thenReturn(profile);
    return t;
  }

  private static PlanarImage run(ColorProfileOp op, PlanarImage src) throws Exception {
    op.setParam(Param.INPUT_IMG, src);
    op.process();
    return (PlanarImage) op.getParam(Param.OUTPUT_IMG);
  }

  private static ImageCV color(int width, int height) {
    ImageCV img = new ImageCV(height, width, CvType.CV_8UC3);
    Core.randu(img, 0, 256);
    return img;
  }

  private static byte[] pixels(ImageCV img) {
    ImageCV continuous = img;
    if (!img.isContinuous()) {
      continuous = new ImageCV();
      img.copyTo(continuous);
    }
    byte[] data = new byte[(int) (continuous.total() * continuous.channels())];
    continuous.get(0, 0, data);
    return data;
  }

  private static byte[] xyz(double x, double y, double z) {
    ByteBuffer buf = ByteBuffer.allocate(20);
    buf.put(new byte[] {'X', 'Y', 'Z', ' '}).putInt(0);
    buf.putInt((int) Math.round(x * 65536)).putInt((int) Math.round(y * 65536));
    buf.putInt((int) Math.round(z * 65536));
    return buf.array();
  }
}
