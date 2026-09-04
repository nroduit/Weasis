/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.layer.imp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.opencv.core.Scalar;
import org.weasis.core.api.image.FlipOp;
import org.weasis.core.api.image.SimpleOpManager;
import org.weasis.core.api.image.cv.OpenCvTestLoader;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.opencv.data.ImageCV;

@DisplayNameGeneration(ReplaceUnderscores.class)
class RenderedImageLayerTest {
  private static final int WIDTH = 7; // odd size, so a row stride mistake cannot hide
  private static final int HEIGHT = 5;

  @BeforeAll
  static void loadOpenCv() {
    assumeTrue(OpenCvTestLoader.tryLoad(), "OpenCV native library not available");
  }

  @Test
  void gray_image_is_drawn_with_its_levels() {
    var image = new ImageCV(HEIGHT, WIDTH, CvType.CV_8UC1, new Scalar(90));
    image.put(2, 3, new byte[] {(byte) 200});

    BufferedImage screen = draw(layerShowing(image));

    assertEquals(0x5A5A5A, screen.getRGB(0, 0) & 0xFFFFFF);
    assertEquals(0xC8C8C8, screen.getRGB(3, 2) & 0xFFFFFF);
  }

  @Test
  void color_image_keeps_its_channel_order() {
    var image = new ImageCV(HEIGHT, WIDTH, CvType.CV_8UC3, new Scalar(10, 20, 30)); // B, G, R
    image.put(4, 6, new byte[] {(byte) 255, 0, 0});

    BufferedImage screen = draw(layerShowing(image));

    assertEquals(0x1E140A, screen.getRGB(0, 0) & 0xFFFFFF);
    assertEquals(0x0000FF, screen.getRGB(6, 4) & 0xFFFFFF);
  }

  @Test
  void repaint_after_an_update_shows_the_new_content_of_the_same_image_object() {
    var image = new ImageCV(HEIGHT, WIDTH, CvType.CV_8UC1, new Scalar(50));
    RenderedImageLayer<ImageElement> layer = layerShowing(image);
    assertEquals(0x323232, draw(layer).getRGB(1, 1) & 0xFFFFFF);

    image.setTo(new Scalar(180));
    layer.updateDisplayOperations();

    assertEquals(0xB4B4B4, draw(layer).getRGB(1, 1) & 0xFFFFFF);
  }

  @Test
  void image_of_another_size_or_type_replaces_the_kept_one() {
    var layer = layerShowing(new ImageCV(HEIGHT, WIDTH, CvType.CV_8UC1, new Scalar(50)));
    draw(layer);

    show(layer, new ImageCV(HEIGHT + 2, WIDTH + 3, CvType.CV_8UC3, new Scalar(0, 255, 0)));

    assertEquals(0x00FF00, draw(layer).getRGB(WIDTH + 2, HEIGHT + 1) & 0xFFFFFF);
  }

  @Test
  void sixteen_bit_image_is_still_drawn() {
    var layer = layerShowing(new ImageCV(HEIGHT, WIDTH, CvType.CV_16UC1, new Scalar(65535)));

    assertEquals(0xFFFFFF, draw(layer).getRGB(0, 0) & 0xFFFFFF);
  }

  private static RenderedImageLayer<ImageElement> layerShowing(ImageCV image) {
    var manager = new SimpleOpManager();
    manager.addImageOperationAction(new FlipOp()); // no flip requested: passes the image through
    var layer = new RenderedImageLayer<ImageElement>(manager);
    show(layer, image);
    return layer;
  }

  private static void show(RenderedImageLayer<ImageElement> layer, ImageCV image) {
    layer.getDisplayOpManager().setFirstNode(image);
    layer.updateDisplayOperations();
  }

  private static BufferedImage draw(RenderedImageLayer<ImageElement> layer) {
    var screen = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
    Graphics2D g2d = screen.createGraphics();
    layer.drawImage(g2d);
    g2d.dispose();
    return screen;
  }
}
