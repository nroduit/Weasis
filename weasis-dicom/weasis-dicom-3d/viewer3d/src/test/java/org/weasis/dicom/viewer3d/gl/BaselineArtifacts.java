/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.gl;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import javax.imageio.ImageIO;

/**
 * Where a baseline run puts what it produced. The frames themselves are not kept in the repository:
 * a ray-cast image is only reproducible on the driver that rendered it, so the equivalence gate of
 * the later steps compares two back-ends within one run rather than against a stored picture. What
 * is stored is for the eye and for the record.
 */
final class BaselineArtifacts {

  /**
   * Directory the run writes to, from {@code -Dweasis.3d.baseline.dir=…}; nothing is written
   * otherwise.
   */
  static final String P_DIR = "weasis.3d.baseline.dir"; // NON-NLS

  private BaselineArtifacts() {}

  static Optional<Path> directory() {
    String dir = System.getProperty(P_DIR);
    if (dir == null || dir.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(Files.createDirectories(Path.of(dir)));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Identifies a frame's pixels, so two back-ends can be compared without keeping the image. */
  static String digest(int[] pixels) {
    ByteBuffer buffer = ByteBuffer.allocate(pixels.length * Integer.BYTES);
    buffer.asIntBuffer().put(pixels);
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(buffer.array()));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Writes a frame; the quad pass already sampled upside down, so the rows are in image order. */
  static void writePng(Path directory, String name, int[] pixels, int width, int height) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    image.setRGB(0, 0, width, height, pixels, 0, width);
    try {
      ImageIO.write(image, "png", directory.resolve(name + ".png").toFile()); // NON-NLS
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static void writeText(Path directory, String name, String content) {
    try {
      Files.writeString(directory.resolve(name), content);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * {@code true} when the frame is not a flat fill — the cheapest proof that something was drawn.
   */
  static boolean hasContent(int[] pixels) {
    int first = pixels[0];
    for (int pixel : pixels) {
      if (pixel != first) {
        return true;
      }
    }
    return false;
  }
}
