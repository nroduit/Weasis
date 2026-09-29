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

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.opencv.core.CvException;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.imgcodecs.Animation;
import org.opencv.imgcodecs.Imgcodecs;
import org.weasis.core.Messages;
import org.weasis.core.api.media.data.MaskingProfile;
import org.weasis.core.util.FileUtil;
import org.weasis.opencv.op.ImageConversion;

/**
 * Writes an APNG or a GIF through OpenCV. The encoder takes the whole animation at once, so every
 * frame stays in memory until {@link #close()}: the resident total is what {@link
 * #MAX_RESIDENT_BYTES} bounds, and a source that would exceed it is stopped rather than failing at
 * the encode.
 *
 * <p>The PNG compression level is left at its default on purpose. Level 9 measured 43 times slower
 * for 1.8 times smaller, which at full resolution is minutes of frozen export.
 *
 * <p>GIF dithering, by contrast, must be turned off explicitly — see {@link #encoderParams()}.
 */
public class AnimatedImageSink implements FrameSink {

  /** Raw frame bytes held at once. Roughly 170 frames of a full HD capture. */
  public static final long MAX_RESIDENT_BYTES = 1024L * 1024L * 1024L;

  public static final int MAX_FRAMES = 3000;

  /** GIF stores delays in centiseconds, so anything faster is not representable. */
  private static final int MIN_DURATION_MS = 10;

  private final Path file;
  private final AnimationFormat format;
  private final List<Mat> frames = new ArrayList<>();
  private final List<Integer> durations = new ArrayList<>();
  private long residentBytes;

  public AnimatedImageSink(Path file, AnimationFormat format) {
    this.file = Objects.requireNonNull(file);
    this.format = Objects.requireNonNull(format);
  }

  public static FrameSinkFactory factory(AnimationFormat format) {
    return new FrameSinkFactory() {
      @Override
      public String title() {
        return format.getTitle();
      }

      @Override
      public String extension() {
        return format.getExtension();
      }

      @Override
      public String advice() {
        return format.getAdvice();
      }

      @Override
      public boolean buffersRawFrames() {
        return true;
      }

      @Override
      public FrameSink create(Path file, MaskingProfile profile) {
        return new AnimatedImageSink(file, format);
      }
    };
  }

  @Override
  public synchronized void open(int width, int height) {
    release();
  }

  @Override
  public synchronized boolean isFull() {
    return frames.size() >= MAX_FRAMES || residentBytes >= MAX_RESIDENT_BYTES;
  }

  @Override
  public synchronized void write(BufferedImage frame, int durationMs) throws IOException {
    if (isFull()) {
      throw new IOException(Messages.getString("animation.memory.limit"));
    }
    Mat mat = ImageConversion.toMat(frame);
    frames.add(mat);
    durations.add(Math.max(MIN_DURATION_MS, durationMs));
    residentBytes += (long) mat.width() * mat.height() * mat.channels();
  }

  @Override
  public synchronized void close() throws IOException {
    try {
      if (frames.isEmpty()) {
        throw new IOException(Messages.getString("animation.no.frame"));
      }
      Animation animation = new Animation();
      animation.set_loop_count(0);
      animation.set_frames(frames);
      animation.set_durations(
          new MatOfInt(durations.stream().mapToInt(Integer::intValue).toArray()));
      boolean written;
      try {
        written =
            Imgcodecs.imwriteanimation(
                file.toString(), animation, new MatOfInt(encoderParams(format)));
      } catch (CvException e) {
        throw new IOException(unsupported(), e);
      }
      if (!written) {
        throw new IOException(unsupported());
      }
    } finally {
      release();
    }
  }

  @Override
  public synchronized void abort() {
    release();
    FileUtil.delete(file);
  }

  /**
   * Turns GIF dithering off. The default diffuses quantization error across all three channels, so
   * a grayscale frame comes back with colour in it: measured on an 8-bit MR frame, only 55.7% of
   * the gray pixels stayed gray and channels separated by up to 98 levels — visible as coloured
   * speckle over the whole image. Without it the frame is exactly gray again, mean error drops from
   * 7.45 to 5.22, peak from 84 to 36, and the file is a quarter smaller because the dither noise
   * was defeating the LZW runs.
   *
   * <p>It is off for colour content too, where it also measured better on both mean (4.33 vs 6.41)
   * and peak (35 vs 84) error. The cost is encode time — the default uses a fast fixed palette,
   * this forces an adaptive one — which measured 26 ms per 512² frame.
   *
   * <p>APNG needs nothing: it is lossless and has no palette.
   */
  static int[] encoderParams(AnimationFormat format) {
    return format == AnimationFormat.GIF
        ? new int[] {Imgcodecs.IMWRITE_GIF_DITHER, Imgcodecs.IMWRITE_GIF_FAST_NO_DITHER}
        : new int[0];
  }

  private String unsupported() {
    return String.format(Messages.getString("animation.encode.failed"), format.getTitle());
  }

  private void release() {
    frames.forEach(ImageConversion::releaseMat);
    frames.clear();
    durations.clear();
    residentBytes = 0;
  }
}
