/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.util;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MirroredSVGIconTest {

  /** A black square in the left half of a 16 x 16 icon. */
  private static final String LEFT_HALF =
      """
      <svg width="16" height="16" viewBox="0 0 16 16" xmlns="http://www.w3.org/2000/svg">
        <rect x="0" y="0" width="8" height="16" fill="#000000"/>
      </svg>
      """;

  @Test
  void theIconIsPaintedFlippedAtItsPlaceAndItsFormsStayFlipped(@TempDir Path dir)
      throws IOException {
    Path file = Files.writeString(dir.resolve("left.svg"), LEFT_HALF);
    FlatSVGIcon icon = new FlatSVGIcon(file.toUri());
    MirroredSVGIcon mirrored = new MirroredSVGIcon(icon);

    BufferedImage plain = paint(icon);
    BufferedImage flipped = paint(mirrored);
    assertAll(
        () -> assertTrue(opaque(plain, 10 + 2, 8) && !opaque(plain, 10 + 13, 8)),
        () -> assertTrue(!opaque(flipped, 10 + 2, 8) && opaque(flipped, 10 + 13, 8)),
        () -> assertTrue(!opaque(flipped, 5, 8) && !opaque(flipped, 30, 8)),
        () -> assertEquals(16, mirrored.getIconWidth()),
        () -> assertInstanceOf(MirroredSVGIcon.class, mirrored.derive(24, 24)),
        () -> assertInstanceOf(MirroredSVGIcon.class, mirrored.derive(2f)),
        () -> assertInstanceOf(MirroredSVGIcon.class, mirrored.getDisabledIcon()));
  }

  private static BufferedImage paint(FlatSVGIcon icon) {
    BufferedImage image = new BufferedImage(36, 16, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    icon.paintIcon(null, g, 10, 0);
    g.dispose();
    return image;
  }

  private static boolean opaque(BufferedImage image, int x, int y) {
    return (image.getRGB(x, y) >>> 24) > 128;
  }
}
