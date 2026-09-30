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

import com.formdev.flatlaf.util.SystemInfo;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.util.HardwareInfo;

/**
 * The one OpenGL context Weasis owns, offscreen and without any window system surface. One
 * implementation per platform binding; all of them are created, used and destroyed on the single
 * thread that owns them ({@link GlThread}).
 */
public sealed interface GlContext permits CglContext, EglContext, JoglContext, WglContext {

  /** Versions tried in turn; the first that a driver grants wins. */
  int[][] VERSIONS = {{4, 6}, {4, 5}, {4, 3}, {4, 1}, {3, 3}};

  /** Lowest version the shaders compile against. */
  int[] MINIMUM = {3, 3};

  /** Compute shaders are core since OpenGL 4.3; below that the renderer takes the FBO path. */
  static boolean isComputeCapable(int major, int minor) {
    return major > 4 || (major == 4 && minor >= 3);
  }

  /**
   * The version a driver reports, as {@code {major, minor}}. {@code GL_VERSION} starts with the
   * number and continues with whatever the vendor wants to add — {@code "4.1 Metal - 89.3"} on
   * macOS, {@code "4.6.0 NVIDIA 595.91.07"} elsewhere. {@code {0, 0}} when it cannot be read.
   */
  static int[] parseVersion(String version) {
    if (version == null) {
      return new int[] {0, 0};
    }
    java.util.regex.Matcher matcher =
        java.util.regex.Pattern.compile("(\\d+)\\.(\\d+)").matcher(version);
    return matcher.lookingAt()
        ? new int[] {Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))}
        : new int[] {0, 0};
  }

  /** {@code true} when the driver is at least the version the shaders need. */
  static boolean isSupported(int[] version) {
    return version[0] > MINIMUM[0] || (version[0] == MINIMUM[0] && version[1] >= MINIMUM[1]);
  }

  /**
   * Creates the context of the selected back-end, and of the other one when that fails. Falling
   * back rather than refusing is what keeps a machine the new binding cannot serve — a driver, a
   * platform or a library it has never met — rendering as it did before. Which one actually
   * answered is what the preference page and {@code view3d:info} report.
   *
   * @throws GlException when neither back-end has a context to offer
   */
  static GlContext create() {
    GlBackend selected = GlBackend.selected();
    try {
      return create(selected);
    } catch (RuntimeException | UnsatisfiedLinkError first) {
      GlBackend fallback = selected.fallback();
      LoggerFactory.getLogger(GlContext.class)
          .warn(
              "No OpenGL context through {} ({}); falling back to {}",
              selected,
              first.getMessage(),
              fallback);
      try {
        return create(fallback);
      } catch (RuntimeException | UnsatisfiedLinkError second) {
        first.addSuppressed(second);
        throw first instanceof GlException gl
            ? gl
            : new GlException("No OpenGL context on this machine", first);
      }
    }
  }

  static GlContext create(GlBackend backend) {
    if (backend == GlBackend.JOGL) {
      return JoglContext.create();
    }
    if (SystemInfo.isLinux) {
      return EglContext.create();
    }
    if (SystemInfo.isWindows) {
      return WglContext.create();
    }
    if (SystemInfo.isMacOS) {
      return CglContext.create();
    }
    throw new GlException(
        "The FFM OpenGL back-end has no context implementation for "
            + HardwareInfo.operatingSystem());
  }

  /** Binds the context to the calling thread. */
  void makeCurrent();

  /** Unbinds it, so another thread could take it. */
  void release();

  /** Destroys the context and everything opened for it. Idempotent. */
  void destroy();

  /** Vendor, renderer and version, for the logs and the preference page. */
  String description();

  /** How the context was obtained — {@code EGL}, {@code WGL}, {@code CGL} or {@code JOGL}. */
  String name();

  /** The binding driving this context, valid only while it is current. */
  Gl gl();
}
