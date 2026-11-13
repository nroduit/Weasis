/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image.cv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Animation;
import org.opencv.imgcodecs.Imgcodecs;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.internal.cv.NativeOpenCVCodec;
import org.weasis.opencv.data.PlanarImage;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ImageCVIOAnimationTest {

  private static final int FRAMES = 4;
  private static final int[] DURATIONS = {100, 200, 100, 200};

  @TempDir Path dir;

  @BeforeAll
  static void loadOpenCV() {
    assumeTrue(OpenCvTestLoader.tryLoad(), "OpenCV native library unavailable, skipping");
  }

  @Test
  void an_animated_png_is_a_multiframe_series_with_a_cine_rate() {
    assertAnimation(dir.resolve("anim.png"), "image/png");
  }

  @Test
  void an_animated_gif_is_a_multiframe_series_with_a_cine_rate() {
    assertAnimation(dir.resolve("anim.gif"), "image/gif");
  }

  @Test
  void a_still_png_stays_a_single_image() {
    Path file = dir.resolve("still.png");
    assertTrue(Imgcodecs.imwrite(file.toString(), frame(0)));
    ImageCVIO reader = new ImageCVIO(file.toUri(), "image/png", new NativeOpenCVCodec());
    assertEquals(1, reader.getMediaElementNumber());
    MediaSeries<ImageElement> series = reader.getMediaSeries();
    assertEquals(1, series.size(null));
    assertNull(series.getTagValue(TagW.FrameRate));
    assertNotNull(reader.getMediaElement()[0].getImage());
  }

  private static void assertAnimation(Path file, String mime) {
    write(file);
    ImageCVIO reader = new ImageCVIO(file.toUri(), mime, new NativeOpenCVCodec());
    assertEquals(FRAMES, reader.getMediaElementNumber());

    MediaSeries<ImageElement> series = reader.getMediaSeries();
    assertEquals(FRAMES, series.size(null));
    // 4 frames in 600 ms
    assertEquals(1000.0 * FRAMES / 600, (Double) series.getTagValue(TagW.FrameRate), 0.01);

    ImageElement[] elements = reader.getMediaElement();
    for (int i = FRAMES - 1; i >= 0; i--) {
      assertEquals(i, elements[i].getKey());
      assertEquals((double) DURATIONS[i], elements[i].getTagValue(TagW.FrameDuration));
      PlanarImage img = elements[i].getImage();
      assertNotNull(img, "frame " + i);
      assertEquals(3, img.channels());
      // The marker of frame i is the only bright column block
      double[] px = img.toMat().get(8, 4 + i * 8);
      assertTrue(px[1] > 200, "frame " + i + " marker");
      if (i > 0) {
        assertTrue(img.toMat().get(8, 4)[1] < 50, "frame " + i + " previous marker cleared");
      }
    }
  }

  private static void write(Path file) {
    Animation animation = new Animation(0, new Scalar(0, 0, 0, 0));
    List<Mat> frames = new ArrayList<>();
    for (int i = 0; i < FRAMES; i++) {
      frames.add(frame(i));
    }
    animation.set_frames(frames);
    animation.set_durations(new MatOfInt(DURATIONS));
    assertTrue(Imgcodecs.imwriteanimation(file.toString(), animation), file.toString());
  }

  /** A dark frame with a green block whose position gives the frame index. */
  private static Mat frame(int index) {
    Mat mat = new Mat(16, 40, CvType.CV_8UC3, new Scalar(10, 10, 10));
    mat.submat(4, 12, index * 8, index * 8 + 8).setTo(new Scalar(0, 255, 0));
    return mat;
  }
}
