/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.launcher;

import java.awt.GraphicsEnvironment;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JOptionPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The glibc floor of the bundled native libraries on Linux, checked at launch before any of them
 * loads: an older system is told that it is no longer supported instead of failing on the first
 * native load with an {@code UnsatisfiedLinkError}. The floor is the configuration value {@link
 * #P_GLIBC_MIN}, kept with the native library versions in {@code base.json} and documented in
 * {@code Native-Build-Overview.md}; nothing here knows a version number. The version of the running
 * libc comes from {@code gnu_get_libc_version()} through the FFM API, without loading anything, or
 * from {@code getconf GNU_LIBC_VERSION}; a Linux without glibc (musl) is unsupported too. Other
 * operating systems skip the check.
 */
public final class GlibcFloor {

  private static final Logger LOGGER = LoggerFactory.getLogger(GlibcFloor.class);

  /** The glibc the bundled native libraries need, {@code major.minor}; empty disables the check. */
  public static final String P_GLIBC_MIN = "weasis.native.glibc.min"; // NON-NLS

  private static final Pattern VERSION = Pattern.compile("(\\d+)\\.(\\d+)");

  /** The outcome of the check: the floor, what the system has (null without glibc). */
  public record Check(String floor, String found) {

    public boolean supported() {
      return found != null && compare(found, floor) >= 0;
    }

    /** The sentence shown to the user, {@code appName} being the application name. */
    public String message(String appName) {
      String key = found == null ? "GlibcFloor.no_glibc" : "GlibcFloor.unsupported"; // NON-NLS
      return String.format(Messages.getString(key), appName, floor, found);
    }
  }

  private GlibcFloor() {}

  static boolean isLinux() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux"); // NON-NLS
  }

  /** The check for this system, or empty when it does not apply (not Linux, no floor set). */
  public static Optional<Check> check(String floor) {
    return check(floor, isLinux(), GlibcFloor::glibcVersion);
  }

  static Optional<Check> check(
      String floor, boolean linux, java.util.function.Supplier<Optional<String>> version) {
    if (!linux || floor == null || floor.isBlank() || !VERSION.matcher(floor).find()) {
      return Optional.empty();
    }
    return Optional.of(new Check(floor.trim(), version.get().orElse(null)));
  }

  /**
   * Refuses to go on when the system is below the floor: the message on the log and the error
   * stream, a dialog when a display is there, then the exit code stage 1 does not retry.
   */
  public static void enforce(String floor, String appName) {
    Optional<Check> check = check(floor);
    if (check.isEmpty() || check.get().supported()) {
      check.ifPresent(c -> LOGGER.info("glibc {} (floor {})", c.found(), c.floor()));
      return;
    }
    String message = check.get().message(appName);
    LOGGER.error("Refusing to start: {}", message);
    System.err.println("Refusing to start: " + message); // NON-NLS
    if (!GraphicsEnvironment.isHeadless()) {
      try {
        JOptionPane.showMessageDialog(
            null,
            message + "\n\n" + Messages.getString("GlibcFloor.requirements"),
            Messages.getString("GlibcFloor.title"),
            JOptionPane.ERROR_MESSAGE);
      } catch (RuntimeException e) {
        LOGGER.warn("Cannot show the dialog", e);
      }
    }
    System.exit(BootstrapContract.EXIT_UNSUPPORTED);
  }

  /** The glibc version of this process, empty when the C library is not glibc. */
  public static Optional<String> glibcVersion() {
    Optional<String> ffm = glibcVersionFfm();
    return ffm.isPresent() ? ffm : glibcVersionGetconf();
  }

  static Optional<String> glibcVersionFfm() {
    try {
      Linker linker = Linker.nativeLinker();
      Optional<MemorySegment> symbol =
          linker.defaultLookup().find("gnu_get_libc_version"); // NON-NLS
      if (symbol.isEmpty()) {
        return Optional.empty();
      }
      MethodHandle handle =
          linker.downcallHandle(symbol.get(), FunctionDescriptor.of(ValueLayout.ADDRESS));
      MemorySegment text = (MemorySegment) handle.invokeExact();
      return parse(text.reinterpret(64).getString(0));
    } catch (Throwable e) {
      LOGGER.debug("gnu_get_libc_version not callable: {}", e.toString());
      return Optional.empty();
    }
  }

  static Optional<String> glibcVersionGetconf() {
    try {
      Process process =
          new ProcessBuilder("getconf", "GNU_LIBC_VERSION") // NON-NLS
              .redirectErrorStream(true)
              .start();
      String line;
      try (BufferedReader reader =
          new BufferedReader(
              new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        line = reader.readLine();
      }
      if (!process.waitFor(5, TimeUnit.SECONDS) || process.exitValue() != 0) {
        return Optional.empty();
      }
      return parse(line);
    } catch (Exception e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      return Optional.empty();
    }
  }

  /** {@code 2.39} out of {@code 2.39}, {@code glibc 2.39} or {@code 2.39.1}. */
  static Optional<String> parse(String text) {
    if (text == null) {
      return Optional.empty();
    }
    Matcher m = VERSION.matcher(text);
    return m.find() ? Optional.of(m.group(1) + "." + m.group(2)) : Optional.empty();
  }

  /** Numeric comparison of two {@code major.minor} versions. */
  static int compare(String a, String b) {
    Matcher ma = VERSION.matcher(a);
    Matcher mb = VERSION.matcher(b);
    if (!ma.find() || !mb.find()) {
      return a.compareTo(b);
    }
    int major = Integer.compare(Integer.parseInt(ma.group(1)), Integer.parseInt(mb.group(1)));
    return major != 0
        ? major
        : Integer.compare(Integer.parseInt(ma.group(2)), Integer.parseInt(mb.group(2)));
  }
}
