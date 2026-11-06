/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.print;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayNameGeneration(ReplaceUnderscores.class)
class PdfWriterTest {

  @Test
  void writes_one_image_object_per_page_with_a_valid_cross_reference_table(@TempDir Path dir)
      throws IOException {
    Path file = dir.resolve("pages.pdf");
    BufferedImage rgb = new BufferedImage(4, 3, BufferedImage.TYPE_3BYTE_BGR);
    rgb.setRGB(0, 0, Color.RED.getRGB());
    BufferedImage gray = new BufferedImage(2, 2, BufferedImage.TYPE_BYTE_GRAY);
    BufferedImage neutral = new BufferedImage(2, 2, BufferedImage.TYPE_3BYTE_BGR);
    neutral.setRGB(1, 1, Color.GRAY.getRGB());
    try (PdfWriter writer = new PdfWriter(file)) {
      writer.addPage(rgb, 595.2756, 841.8898);
      writer.addPage(gray, 841.8898, 595.2756);
      writer.addPage(neutral, 595.2756, 841.8898);
      assertEquals(3, writer.pageCount());
    }
    byte[] bytes = Files.readAllBytes(file);
    String text = new String(bytes, StandardCharsets.ISO_8859_1);
    Matcher startxref = Pattern.compile("startxref\\n(\\d+)\\n%%EOF").matcher(text);
    assertTrue(startxref.find(), "startxref");
    int xref = Integer.parseInt(startxref.group(1));
    Matcher firstObject = Pattern.compile("(\\d{10}) 00000 n").matcher(text.substring(xref));
    assertTrue(firstObject.find(), "xref entries");
    long offset = Long.parseLong(firstObject.group(1));
    assertAll(
        () -> assertTrue(text.startsWith("%PDF-1.4")),
        () -> assertTrue(text.startsWith("xref", xref), "xref at the announced offset"),
        () -> assertTrue(text.startsWith("1 0 obj", (int) offset), "object 1 at its offset"),
        () -> assertEquals(3, count(text, "/Type /Page ")),
        () -> assertEquals(1, count(text, "/DeviceRGB")),
        () -> assertEquals(2, count(text, "/DeviceGray"), "a neutral color page is stored gray"),
        () -> assertEquals(1, count(text, "/Count 3")),
        () -> assertTrue(text.contains("/MediaBox [0 0 595.2756 841.8898]")));
  }

  @Test
  void samples_are_taken_in_rgb_order_whatever_the_raster_layout() {
    BufferedImage bgr = new BufferedImage(2, 1, BufferedImage.TYPE_3BYTE_BGR);
    bgr.setRGB(0, 0, new Color(10, 20, 30).getRGB());
    bgr.setRGB(1, 0, new Color(200, 100, 0).getRGB());
    BufferedImage gray = new BufferedImage(2, 1, BufferedImage.TYPE_BYTE_GRAY);
    gray.getRaster().setSample(1, 0, 0, 250);
    assertAll(
        () ->
            assertArrayEquals(
                new byte[] {10, 20, 30, (byte) 200, 100, 0}, PdfWriter.pixels(bgr, false)),
        () -> assertArrayEquals(new byte[] {0, (byte) 250}, PdfWriter.pixels(gray, true)));
  }

  private static int count(String text, String token) {
    int n = 0;
    for (int i = text.indexOf(token); i >= 0; i = text.indexOf(token, i + 1)) {
      n++;
    }
    return n;
  }
}
