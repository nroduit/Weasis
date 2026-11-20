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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.api.setting.CoreSettings;
import org.weasis.core.api.setting.Settings;
import org.weasis.core.util.StringUtil;

/**
 * Color management of the display: whether it is on, the profile of the display, and the conversion
 * of a source profile to it. A color image without a profile is sRGB (PS3.4 N.2), and so is a
 * display without one, so nothing is converted until an image declares other colors or a display
 * profile is set.
 *
 * <p>The display profile is the one of the workstation (setting {@code
 * weasis.color.display.profile}), unless a monitor has a profile of its own: local setting {@code
 * weasis.screen.<id>.profile}, kept with the screen roles and never in the remote preferences, as a
 * monitor id means nothing on another workstation.
 */
public final class ColorManagement {
  private static final Logger LOGGER = LoggerFactory.getLogger(ColorManagement.class);

  private record State(boolean enabled, String file, IccProfile display) {}

  /** Local setting prefix and suffix: {@code weasis.screen.<id>.profile}. */
  public static final String SCREEN_PREFIX = "weasis.screen."; // NON-NLS

  public static final String SCREEN_SUFFIX = ".profile"; // NON-NLS

  private static final AtomicLong GENERATION = new AtomicLong();
  private static volatile State state;
  private static volatile Supplier<WProperties> screenStore = () -> null;

  /** The profiles read for the monitors, by file; an unreadable file maps to sRGB. */
  private static final Map<String, IccProfile> SCREEN_PROFILES = new ConcurrentHashMap<>();

  private ColorManagement() {}

  public static boolean isEnabled() {
    return current().enabled();
  }

  /** The profile of the display: the configured file, or sRGB. */
  public static IccProfile displayProfile() {
    return current().display();
  }

  /**
   * The profile of a monitor: its own when it has one, else the one of the workstation.
   *
   * @param screenId the device id of the monitor, or null for the workstation profile
   */
  public static IccProfile displayProfile(String screenId) {
    String file = screenFile(screenId);
    return file == null
        ? displayProfile()
        : SCREEN_PROFILES.computeIfAbsent(file, ColorManagement::load);
  }

  /** The profile file a monitor has of its own, if any. */
  public static Optional<Path> screenProfileFile(String screenId) {
    String file = screenFile(screenId);
    return file == null ? Optional.empty() : Optional.of(Path.of(file));
  }

  /** Whether the monitor shows colors in a profile other than sRGB. */
  public static boolean hasDisplayProfile(String screenId) {
    return !IccProfile.SRGB.equals(displayProfile(screenId));
  }

  /**
   * Gives a monitor a profile of its own, or the one of the workstation again with a null file.
   *
   * @throws IOException if the file holds no RGB ICC profile
   */
  public static void setScreenProfile(String screenId, Path file) throws IOException {
    WProperties properties = screenStore.get();
    if (properties == null || !StringUtil.hasText(screenId)) {
      return;
    }
    String key = SCREEN_PREFIX + screenId + SCREEN_SUFFIX;
    if (file == null) {
      properties.remove(key);
    } else {
      Path absolute = file.toAbsolutePath();
      IccProfile profile = readProfile(absolute);
      SCREEN_PROFILES.put(absolute.toString(), profile);
      properties.setProperty(key, absolute.toString());
    }
    GENERATION.incrementAndGet();
  }

  /** The local settings the profiles of the monitors live in; set once by the UI core. */
  public static void setScreenStore(Supplier<WProperties> localPersistence) {
    screenStore = Objects.requireNonNull(localPersistence);
    SCREEN_PROFILES.clear();
    GENERATION.incrementAndGet();
  }

  private static String screenFile(String screenId) {
    WProperties properties = screenStore.get();
    if (properties == null || !StringUtil.hasText(screenId)) {
      return null;
    }
    String file = properties.getProperty(SCREEN_PREFIX + screenId + SCREEN_SUFFIX);
    return StringUtil.hasText(file) ? file : null;
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
    return toDisplay(source, null);
  }

  /**
   * The conversion of colors described by {@code source} (null: sRGB) to the monitor {@code
   * screenId} (null: the workstation profile), the identity when color management is off.
   */
  public static ColorTransform toDisplay(IccProfile source, String screenId) {
    return current().enabled() ? to(source, displayProfile(screenId)) : ColorTransform.IDENTITY;
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
