/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

@DisplayNameGeneration(ReplaceUnderscores.class)
class WaveExportTest {

  @TempDir Path dir;

  /** A view whose page is 400 x 150 px with a black bar in the middle, at 4 px per millimetre. */
  private static WaveView view() {
    WaveView view = Mockito.mock(WaveView.class);
    Mockito.when(view.getPageWidth()).thenReturn(400);
    Mockito.when(view.getPageHeight()).thenReturn(150);
    Mockito.when(view.getPixelPerMm()).thenReturn(4.0);
    Mockito.doAnswer(
            invocation -> {
              Graphics2D g2 = invocation.getArgument(0);
              g2.setColor(Color.BLACK);
              g2.fillRect(100, 60, 200, 30);
              return null;
            })
        .when(view)
        .paintPage(Mockito.any(Graphics2D.class));
    return view;
  }

  @Test
  void png_has_the_page_size_and_the_painted_content() throws Exception {
    File file = dir.resolve("page.png").toFile();
    WaveExport.write(view(), file);
    BufferedImage image = ImageIO.read(file);
    assertEquals(400, image.getWidth());
    assertEquals(150, image.getHeight());
    assertEquals(0xFFFFFF, image.getRGB(10, 10) & 0xFFFFFF, "white background");
    assertEquals(0x000000, image.getRGB(200, 75) & 0xFFFFFF, "painted bar");
  }

  @Test
  void jpeg_is_written_from_the_extension() throws Exception {
    File file = dir.resolve("page.jpg").toFile();
    WaveExport.write(view(), file);
    BufferedImage image = ImageIO.read(file);
    assertEquals(400, image.getWidth());
  }

  @Test
  void pdf_page_is_at_the_paper_scale() throws Exception {
    File file = dir.resolve("page.pdf").toFile();
    WaveExport.write(view(), file);
    byte[] bytes = Files.readAllBytes(file.toPath());
    String head =
        new String(
            bytes, 0, Math.min(bytes.length, 8), java.nio.charset.StandardCharsets.ISO_8859_1);
    assertTrue(head.startsWith("%PDF-"), "PDF header");
    String text = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
    java.util.regex.Matcher box =
        java.util.regex.Pattern.compile("MediaBox\\s*\\[([^\\]]*)\\]").matcher(text);
    assertTrue(box.find(), "page size");
    String[] values = box.group(1).trim().split("\\s+");
    // 400 px at 4 px/mm = 100 mm = 283.46 pt wide, 150 px = 37.5 mm = 106.3 pt high
    assertEquals(283.46, Double.parseDouble(values[2]) - Double.parseDouble(values[0]), 0.5);
    assertEquals(106.3, Double.parseDouble(values[3]) - Double.parseDouble(values[1]), 0.5);
  }
}
