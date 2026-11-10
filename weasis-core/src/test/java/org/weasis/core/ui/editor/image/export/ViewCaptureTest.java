/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image.export;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The pure parts of the shared capture: scale, fit, format. */
class ViewCaptureTest {

  @Test
  void theScaleBringsTheCaptureUnderTheLimit() {
    BufferedImage big = new BufferedImage(400, 300, BufferedImage.TYPE_3BYTE_BGR);
    double scale = ViewCapture.scaleFor(400, 300, 30_000);
    BufferedImage small = ViewCapture.scale(big, scale);
    assertAll(
        () -> assertEquals(1.0, ViewCapture.scaleFor(100, 100, 10_000)),
        () -> assertEquals(0.5, scale, 1e-9),
        () -> assertEquals(200, small.getWidth()),
        () -> assertEquals(150, small.getHeight()),
        () -> assertSame(big, ViewCapture.scale(big, 1.0)));
  }

  @Test
  void aFitLetterboxesAndKeepsTheRatio() {
    BufferedImage source = new BufferedImage(400, 200, BufferedImage.TYPE_3BYTE_BGR);
    BufferedImage target = new BufferedImage(200, 200, BufferedImage.TYPE_3BYTE_BGR);
    ViewCapture.Placement placement = ViewCapture.Placement.of(source, target);
    assertAll(
        () -> assertSame(target, ViewCapture.fit(source, target)),
        () -> assertEquals(200, placement.w()),
        () -> assertEquals(100, placement.h()),
        () -> assertEquals(50, placement.y()),
        () -> assertEquals(0.5, placement.ratio(), 1e-9));
  }

  @Test
  void theFormatFollowsTheExtension() {
    assertAll(
        () -> assertEquals("png", ViewCapture.formatOf(Path.of("a/shot.PNG"))),
        () -> assertEquals("jpg", ViewCapture.formatOf(Path.of("shot.jpeg"))),
        () -> assertThrows(IllegalArgumentException.class, () -> ViewCapture.formatOf(Path.of("shot.gif"))),
        () -> assertEquals(CaptureScope.CONTAINER, ViewCapture.Scope.LAYOUT.toCaptureScope()),
        () -> assertEquals("window", ViewCapture.Scope.WINDOW.toString()));
  }
}
