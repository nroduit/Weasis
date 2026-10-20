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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.ImageElement;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ShutterOpTest {

  private static ImageElement image(double rescaleX, double rescaleY) {
    ImageElement image = mock(ImageElement.class);
    when(image.getRescaleX()).thenReturn(rescaleX);
    when(image.getRescaleY()).thenReturn(rescaleY);
    return image;
  }

  @Test
  void square_pixels_keep_the_shutter() {
    Area shape = new Area(new Rectangle2D.Double(0, 0, 9, 9));
    assertSame(shape, ShutterOp.toImage(shape, image(1, 1)));
  }

  @Test
  void shutter_is_stretched_with_the_image() {
    // Columns 0 to 9 (pixel centres) of an image stretched twice in width: centres 0.5 to 19.5
    Area shape = new Area(new Rectangle2D.Double(0, 0, 9, 9));

    Rectangle2D b = ShutterOp.toImage(shape, image(2, 1)).getBounds2D();

    assertEquals(0.5, b.getMinX(), 1e-9);
    assertEquals(18.5, b.getMaxX(), 1e-9);
    assertEquals(0, b.getMinY(), 1e-9);
    assertEquals(9, b.getMaxY(), 1e-9);
  }
}
