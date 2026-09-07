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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import javax.swing.JPanel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.editor.image.ViewCanvas;

/**
 * The frame size drives the memory projection and has to be even for the DICOM sink, so the
 * rounding is worth pinning down.
 */
@DisplayName("Frame grabber sizing")
class FrameGrabberTest {

  private static FrameGrabber grabber(int width, int height, double scale) {
    JPanel component = new JPanel();
    component.setSize(new Dimension(width, height));
    ViewCanvas<?> view = mock(ViewCanvas.class);
    when(view.getJComponent()).thenReturn(component);
    return new FrameGrabber(CaptureScope.VIEW, view, null, null, scale);
  }

  @Test
  @DisplayName("keeps the on-screen size at full scale")
  void fullScale() {
    assertEquals(new Dimension(1024, 768), grabber(1024, 768, 1.0).getFrameSize());
  }

  @Test
  @DisplayName("rounds every dimension down to an even number of pixels")
  void evenDimensions() {
    assertAll(
        () -> assertEquals(new Dimension(1024, 768), grabber(1025, 769, 1.0).getFrameSize()),
        () -> assertEquals(new Dimension(510, 382), grabber(1021, 765, 0.5).getFrameSize()));
  }

  @Test
  @DisplayName("applies the downscale factor")
  void downscale() {
    assertAll(
        () -> assertEquals(new Dimension(512, 384), grabber(1024, 768, 0.5).getFrameSize()),
        () -> assertEquals(new Dimension(256, 192), grabber(1024, 768, 0.25).getFrameSize()));
  }

  @Test
  @DisplayName("bounds the image area to what the canvas shows")
  void imageBounds() {
    Rectangle canvas = new Rectangle(0, 0, 800, 600);
    Point[] inside = {
      new Point(100, 50), new Point(700, 50), new Point(700, 550), new Point(100, 550)
    };
    Point[] rotated = {
      new Point(400, -100), new Point(900, 300), new Point(400, 700), new Point(-100, 300)
    };
    Point[] outside = {
      new Point(900, 700), new Point(950, 700), new Point(950, 750), new Point(900, 750)
    };
    assertAll(
        () ->
            assertEquals(
                new Rectangle(100, 50, 600, 500), FrameGrabber.imageBounds(inside, canvas)),
        () -> assertEquals(canvas, FrameGrabber.imageBounds(rotated, canvas)),
        () -> assertNull(FrameGrabber.imageBounds(outside, canvas)));
  }

  @Test
  @DisplayName("never produces an empty frame")
  void degenerateComponent() {
    assertEquals(new Dimension(2, 2), grabber(0, 0, 1.0).getFrameSize());
  }
}
