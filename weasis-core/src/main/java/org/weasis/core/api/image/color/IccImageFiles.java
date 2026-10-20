/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image.color;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfInt;
import org.opencv.imgcodecs.Imgcodecs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.FileUtil;
import org.weasis.opencv.op.ImageIOHandler;

/**
 * Image files carrying the profile of their colors. A capture of what is displayed holds the values
 * sent to the display, so it embeds the display profile when one other than sRGB is in force;
 * exports rendered off-screen are converted to sRGB and need none.
 */
public final class IccImageFiles {
  private static final Logger LOGGER = LoggerFactory.getLogger(IccImageFiles.class);

  /**
   * Largest profile a JPEG file gets: the OpenCV writer puts the whole profile in one APP2 segment,
   * which carries at most 65 519 bytes of it (ICC.1 B.4 splits larger ones, the writer does not).
   */
  static final int JPEG_MAX_PROFILE = 65519;

  private IccImageFiles() {}

  /** The profile of a capture of the displayed views, when it is not sRGB. */
  public static Optional<IccProfile> displayedProfile() {
    return ColorManagement.isEnabled() && ColorManagement.hasDisplayProfile()
        ? Optional.of(ColorManagement.displayProfile())
        : Optional.empty();
  }

  /**
   * Whether the format of {@code file} can embed {@code profile}: PNG, or JPEG within a segment.
   */
  public static boolean canEmbed(Path file, IccProfile profile) {
    String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
    if (name.endsWith(".png")) { // NON-NLS
      return true;
    }
    boolean jpeg = name.endsWith(".jpg") || name.endsWith(".jpeg"); // NON-NLS
    return jpeg && profile.size() <= JPEG_MAX_PROFILE;
  }

  /**
   * Writes a BGR image, embedding {@code profile} when the format can hold it; without a profile, a
   * gray image or another format, the file is written as {@link ImageIOHandler#writeImage(Mat,
   * Path, MatOfInt)} does.
   *
   * @param params encoder parameters, or null
   * @param profile the profile of the colors, or null
   */
  public static boolean write(Mat image, Path file, MatOfInt params, IccProfile profile) {
    if (profile == null || image.channels() != 3 || !canEmbed(file, profile)) {
      return params == null
          ? ImageIOHandler.writeImage(image, file)
          : ImageIOHandler.writeImage(image, file, params);
    }
    MatOfInt types = new MatOfInt(Imgcodecs.IMAGE_METADATA_ICCP);
    Mat blob = new MatOfByte(profile.data());
    MatOfInt options = params == null ? new MatOfInt() : params;
    try {
      FileUtil.prepareToWriteFile(file);
      return Imgcodecs.imwriteWithMetadata(
          file.toAbsolutePath().toString(), image, types, List.of(blob), options);
    } catch (Exception e) {
      LOGGER.error("Cannot write {}", file, e);
      return false;
    } finally {
      types.release();
      blob.release();
      if (options != params) {
        options.release();
      }
    }
  }
}
