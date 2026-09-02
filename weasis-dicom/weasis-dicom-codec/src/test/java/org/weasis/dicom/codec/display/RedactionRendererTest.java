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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.weasis.dicom.codec.Redaction.Mask;

@DisplayName("RedactionRenderer")
class RedactionRendererTest {

  /** Loaded without touching {@code Core}, whose static initializer needs the native library. */
  private static final boolean OPENCV = loadOpenCv();

  /**
   * Loads {@code opencv_java} from the build output of a {@code weasis-opencv-core-*} module found
   * above the working directory; only the one matching this platform loads.
   */
  private static boolean loadOpenCv() {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    String libFile =
        os.contains("win")
            ? "opencv_java.dll" // NON-NLS
            : os.contains("mac") ? "libopencv_java.dylib" : "libopencv_java.so"; // NON-NLS
    for (Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        dir != null;
        dir = dir.getParent()) {
      Path modules = dir.resolve("weasis-opencv"); // NON-NLS
      if (Files.isDirectory(modules)) {
        return loadFirst(modules, libFile);
      }
    }
    return false;
  }

  private static boolean loadFirst(Path modules, String libFile) {
    try (Stream<Path> dirs = Files.list(modules)) {
      for (Path lib :
          dirs.map(d -> d.resolve("target").resolve("classes").resolve(libFile))
              .filter(Files::isRegularFile)
              .toList()) {
        try {
          System.load(lib.toString());
          return true;
        } catch (UnsatisfiedLinkError e) {
          // Another architecture: try the next module
        }
      }
    } catch (IOException e) {
      return false;
    }
    return false;
  }

  private static void assumeOpenCv() {
    assumeTrue(OPENCV, "OpenCV native library unavailable, skipping");
  }

  private static Mask mask(Shape shape) {
    return new Mask(List.of(shape));
  }

  /** A uniform background with bright "text" over {@code text}. */
  private static Mat withText(int type, Scalar background, Scalar ink, Rect text) {
    Mat image = new Mat(60, 60, type, background);
    image.submat(text).setTo(ink);
    return image;
  }

  @Test
  @DisplayName("the median of an odd or even sample, and of nothing")
  void median() {
    assertAll(
        () -> assertEquals(3, RedactionRenderer.median(new double[] {9, 1, 3})),
        () -> assertEquals(2.5, RedactionRenderer.median(new double[] {4, 1, 2, 3})),
        () -> assertEquals(0, RedactionRenderer.median(new double[0])));
  }

  @Test
  @DisplayName("a rectangle is filled with the median of the background around it")
  void fillMatchesBackground() {
    assumeOpenCv();
    Mat image = withText(CvType.CV_8UC1, new Scalar(100), new Scalar(255), new Rect(15, 15, 20, 8));

    Mat result = RedactionRenderer.burn(image, mask(new Rectangle(12, 12, 26, 14)));

    assertAll(
        () -> assertEquals(100, result.get(18, 20)[0], "the text is gone"),
        () -> assertEquals(100, result.get(0, 0)[0], "the background is untouched"),
        () -> assertEquals(255, image.get(18, 20)[0], "the source is not modified"));
  }

  @Test
  @DisplayName("a polygon is filled as drawn, not by its bounding box")
  void polygonKeepsOutsidePixels() {
    assumeOpenCv();
    Mat image = withText(CvType.CV_8UC1, new Scalar(50), new Scalar(200), new Rect(10, 10, 30, 30));
    Path2D triangle = new Path2D.Double();
    triangle.moveTo(10, 10);
    triangle.lineTo(40, 10);
    triangle.lineTo(10, 40);
    triangle.closePath();

    Mat result = RedactionRenderer.burn(image, mask(triangle));

    assertAll(
        () -> assertEquals(200, image.get(12, 12)[0], "sanity: text under the corner"),
        () -> assertTrue(result.get(12, 12)[0] < 200, "inside the triangle is filled"),
        () ->
            assertEquals(200, result.get(36, 36)[0], "inside the bounds but outside the triangle"));
  }

  @Test
  @DisplayName("color and 16-bit images get a median per channel at their own depth")
  void fillOnColorAnd16Bit() {
    assumeOpenCv();
    Rect text = new Rect(20, 20, 10, 6);
    Rectangle region = new Rectangle(18, 18, 14, 10);
    Mat color = withText(CvType.CV_8UC3, new Scalar(10, 20, 30), new Scalar(250, 250, 250), text);
    Mat deep = withText(CvType.CV_16UC1, new Scalar(3000), new Scalar(60000), text);

    Mat colorResult = RedactionRenderer.burn(color, mask(region));
    Mat deepResult = RedactionRenderer.burn(deep, mask(region));

    assertAll(
        () -> assertArrayEquals(new double[] {10, 20, 30}, colorResult.get(22, 22)),
        () -> assertEquals(CvType.CV_16UC1, deepResult.type()),
        () -> assertEquals(3000, deepResult.get(22, 22)[0]));
  }
}
