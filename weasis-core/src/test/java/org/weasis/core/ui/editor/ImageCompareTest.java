/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.JsonObject;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.command.CommandModel;
import org.weasis.core.api.command.CommandResult;

/** The verb a scripted test uses to say that a capture still looks like its reference. */
class ImageCompareTest {

  private static Path image(Path dir, String name, int width, int height, Color color, int dots)
      throws IOException {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
    var g = image.createGraphics();
    g.setColor(color);
    g.fillRect(0, 0, width, height);
    g.setColor(Color.WHITE);
    for (int i = 0; i < dots; i++) {
      g.fillRect(i, 0, 1, 1);
    }
    g.dispose();
    Path file = dir.resolve(name);
    ImageIO.write(image, "png", file.toFile());
    return file;
  }

  private static JsonObject compare(Path a, Path b, String... options) {
    String[] argv = new String[4 + options.length];
    argv[0] = "--a";
    argv[1] = a.toString();
    argv[2] = "--b";
    argv[3] = b.toString();
    System.arraycopy(options, 0, argv, 4, options.length);
    CommandResult result = CommandModel.execute(new FileModel(), "compare", argv);
    assertEquals(CommandResult.OK, result.status(), result.error());
    try (var reader = jakarta.json.Json.createReader(new StringReader(result.body()))) {
      return reader.readObject();
    }
  }

  @Test
  void twoIdenticalImagesAreEqual(@TempDir Path dir) throws IOException {
    Path a = image(dir, "a.png", 20, 10, Color.BLACK, 0);
    Path b = image(dir, "b.png", 20, 10, Color.BLACK, 0);
    JsonObject body = compare(a, b);
    assertAll(
        () -> assertTrue(body.getBoolean("equal")),
        () -> assertEquals(0, body.getInt("differentPixels")),
        () -> assertEquals(20, body.getInt("width")));
  }

  @Test
  void theDifferenceIsCountedAndMeasured(@TempDir Path dir) throws IOException {
    Path a = image(dir, "a.png", 20, 10, Color.BLACK, 0);
    Path b = image(dir, "b.png", 20, 10, Color.BLACK, 10);
    JsonObject body = compare(a, b);
    assertAll(
        () -> assertFalse(body.getBoolean("equal")),
        () -> assertEquals(10, body.getInt("differentPixels")),
        () -> assertEquals(5.0, body.getJsonNumber("differentPixelsPercent").doubleValue()),
        () -> assertEquals(255, body.getInt("maxChannelDifference")));
  }

  @Test
  void aToleranceLetsARenderingDifferPass(@TempDir Path dir) throws IOException {
    Path a = image(dir, "a.png", 8, 8, new Color(10, 10, 10), 0);
    Path b = image(dir, "b.png", 8, 8, new Color(12, 12, 12), 0);
    assertAll(
        () -> assertFalse(compare(a, b).getBoolean("equal")),
        () -> assertTrue(compare(a, b, "--tolerance", "3").getBoolean("equal")));
  }

  @Test
  void twoSizesNeverMatch(@TempDir Path dir) throws IOException {
    Path a = image(dir, "a.png", 8, 8, Color.BLACK, 0);
    Path b = image(dir, "b.png", 9, 8, Color.BLACK, 0);
    JsonObject body = compare(a, b);
    assertAll(
        () -> assertFalse(body.getBoolean("equal")),
        () -> assertFalse(body.getBoolean("sameSize")),
        () -> assertEquals(9, body.getInt("otherWidth")));
  }
}
