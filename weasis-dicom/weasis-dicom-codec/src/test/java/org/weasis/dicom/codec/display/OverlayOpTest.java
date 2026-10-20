/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.display;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Rectangle;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.opencv.data.ImageCV;

/** The overlay mask of the stored frame takes the geometry of the displayed image. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class OverlayOpTest {

  @BeforeAll
  static void loadOpenCV() throws IOException {
    Path modules = Path.of("..", "..", "weasis-opencv").toAbsolutePath().normalize();
    boolean loaded = false;
    if (Files.isDirectory(modules)) {
      try (var files = Files.walk(modules, 4)) {
        for (Path library :
            files
                .filter(
                    p -> p.getFileName().toString().equals(System.mapLibraryName("opencv_java")))
                .toList()) {
          try {
            System.load(library.toString());
            loaded = true;
            break;
          } catch (UnsatisfiedLinkError ignored) {
            // Try the next platform's build output.
          }
        }
      }
    }
    assumeTrue(loaded, "OpenCV native library unavailable");
  }

  private static ImageElement image(double rescaleX, double rescaleY) {
    ImageElement image = mock(ImageElement.class);
    when(image.getRescaleX()).thenReturn(rescaleX);
    when(image.getRescaleY()).thenReturn(rescaleY);
    return image;
  }

  /** A mask with one overlay pixel. */
  private static ImageCV mask(int width, int height, int x, int y) {
    ImageCV mask = new ImageCV(height, width, CvType.CV_8UC1);
    mask.put(0, 0, new byte[width * height]);
    mask.put(y, x, new byte[] {(byte) 255});
    return mask;
  }

  private static int[] row(ImageCV mask, int y) {
    int[] values = new int[mask.width()];
    for (int x = 0; x < values.length; x++) {
      values[x] = (int) mask.get(y, x)[0] == 0 ? 0 : 1;
    }
    return values;
  }

  @Test
  void mask_is_cropped_to_the_displayed_area() {
    ImageCV result =
        OverlayOp.toImage(mask(6, 4, 3, 2), image(1, 1), new Rectangle(2, 1, 3, 2), 3, 2);

    assertEquals(3, result.width());
    assertEquals(2, result.height());
    assertArrayEquals(new int[] {0, 0, 0}, row(result, 0));
    assertArrayEquals(new int[] {0, 1, 0}, row(result, 1));
  }

  @Test
  void mask_is_stretched_with_non_square_pixels() {
    // Pixels twice as wide as high: column 1 of the stored frame covers columns 2 and 3
    ImageCV result = OverlayOp.toImage(mask(4, 2, 1, 0), image(2, 1), null, 8, 2);

    assertEquals(8, result.width());
    assertArrayEquals(new int[] {0, 0, 1, 1, 0, 0, 0, 0}, row(result, 0));
    assertArrayEquals(new int[] {0, 0, 0, 0, 0, 0, 0, 0}, row(result, 1));
  }

  @Test
  void mask_is_stretched_then_cropped() {
    ImageCV result =
        OverlayOp.toImage(mask(4, 2, 1, 0), image(2, 1), new Rectangle(2, 0, 4, 2), 4, 2);

    assertArrayEquals(new int[] {1, 1, 0, 0}, row(result, 0));
  }

  @Test
  void mask_is_not_stretched_for_an_image_left_with_its_stored_pixels() {
    // The export applies the overlays before stretching the image
    ImageCV result = OverlayOp.toImage(mask(4, 2, 1, 0), image(2, 1), null, 4, 2);

    assertArrayEquals(new int[] {0, 1, 0, 0}, row(result, 0));
  }
}
