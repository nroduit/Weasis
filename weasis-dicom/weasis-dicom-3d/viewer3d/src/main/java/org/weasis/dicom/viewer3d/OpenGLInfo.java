/*
 * Copyright (c) 2022 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d;

import com.github.zafarkhaja.semver.Version;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.LoggerFactory;

public record OpenGLInfo(
    String shortVersion, String version, String vendor, String renderer, int max3dTextureSize) {

  /**
   * {@code GL_VERSION} is {@code <major>.<minor>[.<release>]} followed by vendor-specific text, and
   * OpenGL ES prefixes it with {@code "OpenGL ES "}. The release number is optional, so the raw
   * string is not valid semver: macOS reports {@code "4.1 Metal - 90.5"}.
   */
  private static final Pattern GL_VERSION = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

  public Version getVersion() {
    Matcher matcher = GL_VERSION.matcher(shortVersion());
    if (matcher.find()) {
      String release = matcher.group(3);
      return Version.of(
          Integer.parseInt(matcher.group(1)),
          Integer.parseInt(matcher.group(2)),
          release == null ? 0 : Integer.parseInt(release));
    }
    LoggerFactory.getLogger(OpenGLInfo.class)
        .error("Cannot read opengl version {}", shortVersion());
    return Version.of(0);
  }

  /**
   * Returns true if the OpenGL version is at least 3.3, which is the minimum required for the
   * FBO-based fragment shader fallback. GLSL 3.30 (OpenGL 3.3) is the first version where {@code
   * layout(location=N)} on vertex inputs and fragment outputs is core, and where FBOs, {@code
   * sampler3D}, {@code uint} uniforms and {@code textureSize()} are all guaranteed.
   */
  public boolean isVersionCompliant() {
    Version minimalVersion = Version.of(3, 3, 0);
    return getVersion().compareTo(minimalVersion) >= 0;
  }

  /** Returns true if the OpenGL version is at least 4.3, which is required for compute shaders. */
  public boolean isComputeShaderCapable() {
    Version minimalVersion = Version.of(4, 3, 0);
    return getVersion().compareTo(minimalVersion) >= 0;
  }

  /**
   * Check if the renderer is a software renderer without real GPU. Drivers report mixed case
   * ({@code "SwiftShader Device"}, {@code "ANGLE (… WARP …)"}), so the match is case-insensitive.
   */
  public boolean looksSoftware() {
    if (renderer == null) {
      return false;
    }
    String name = renderer.toLowerCase(Locale.ROOT);
    return name.contains("llvmpipe")
        || name.contains("softpipe")
        || name.contains("swiftshader")
        || (name.contains("angle") && name.contains("warp"));
  }
}
