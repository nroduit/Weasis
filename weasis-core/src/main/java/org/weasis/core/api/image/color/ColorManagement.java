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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.setting.CoreSettings;
import org.weasis.core.api.setting.Settings;
import org.weasis.core.util.StringUtil;

/**
 * Color management of the display: whether it is on, the profile of the display, and the conversion
 * of a source profile to it. A color image without a profile is sRGB (PS3.4 N.2), and so is a
 * display without one, so nothing is converted until an image declares other colors or a display
 * profile is set.
 */
public final class ColorManagement {
  private static final Logger LOGGER = LoggerFactory.getLogger(ColorManagement.class);

  private record State(boolean enabled, String file, IccProfile display) {}

  private static final AtomicLong GENERATION = new AtomicLong();
  private static volatile State state;

  private ColorManagement() {}

  public static boolean isEnabled() {
    return current().enabled();
  }

  /** The profile of the display: the configured file, or sRGB. */
  public static IccProfile displayProfile() {
    return current().display();
  }

  /** The configured display profile file, if any. */
  public static Optional<Path> displayProfileFile() {
    String file = current().file();
    return StringUtil.hasText(file) ? Optional.of(Path.of(file)) : Optional.empty();
  }

  /** Whether a display profile other than sRGB is in force. */
  public static boolean hasDisplayProfile() {
    return !IccProfile.SRGB.equals(displayProfile());
  }

  /** Moves whenever the switch or the display profile changes, for the operations that use them. */
  public static long generation() {
    current();
    return GENERATION.get();
  }

  /**
   * The conversion of colors described by {@code source} (null: sRGB) to the display, the identity
   * when color management is off.
   */
  public static ColorTransform toDisplay(IccProfile source) {
    State s = current();
    return s.enabled() ? to(source, s.display()) : ColorTransform.IDENTITY;
  }

  /** The conversion of colors described by {@code source} (null: sRGB) to {@code destination}. */
  public static ColorTransform to(IccProfile source, IccProfile destination) {
    return ColorTransforms.of(source == null ? IccProfile.SRGB : source, destination);
  }

  /**
   * Reads an RGB profile file.
   *
   * @throws IOException if the file cannot be read or holds no RGB ICC profile
   */
  public static IccProfile readProfile(Path file) throws IOException {
    IccProfile profile =
        IccProfile.of(Files.readAllBytes(file))
            .orElseThrow(() -> new IOException("Not an ICC profile: " + file));
    if (!profile.isRgb()) {
      throw new IOException("Not an RGB profile (" + profile.colorSpace().strip() + "): " + file);
    }
    return profile;
  }

  private static State current() {
    boolean enabled = Settings.get(CoreSettings.COLOR_MANAGEMENT);
    String file = Objects.requireNonNullElse(Settings.get(CoreSettings.DISPLAY_PROFILE), "");
    State s = state;
    if (s == null || s.enabled() != enabled || !s.file().equals(file)) {
      synchronized (ColorManagement.class) {
        s = state;
        if (s == null || s.enabled() != enabled || !s.file().equals(file)) {
          s = new State(enabled, file, load(file));
          state = s;
          GENERATION.incrementAndGet();
        }
      }
    }
    return s;
  }

  private static IccProfile load(String file) {
    if (!StringUtil.hasText(file)) {
      return IccProfile.SRGB;
    }
    try {
      IccProfile profile = readProfile(Path.of(file));
      LOGGER.info("Display profile: {} from {}", profile, file);
      return profile;
    } catch (IOException | RuntimeException e) {
      LOGGER.warn("Cannot read the display profile {}, sRGB is used", file, e);
      return IccProfile.SRGB;
    }
  }
}
