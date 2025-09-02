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

import org.osgi.framework.Version;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StringUtil;

public record OpenGLInfo(
    String shortVersion, String version, String vendor, String renderer, int max3dTextureSize) {

  public Version getVersion() {
    try {
      return new Version(shortVersion().split(StringUtil.SPACE)[0]);
    } catch (Exception e) {
      LoggerFactory.getLogger(OpenGLInfo.class)
          .error("Cannot read opengl version {}", shortVersion().split(StringUtil.SPACE)[0]);
      return new Version(0, 0, 0);
    }
  }

  public boolean isVersionCompliant() {
    // Compliant with Compute Shaders
    Version minimalVersion = new Version(4, 3, 0);
    return getVersion().compareTo(minimalVersion) >= 0;
  }

  /** Check if the renderer is a software renderer without real GPU. */
  public boolean looksSoftware() {
    return renderer.contains("llvmpipe")
        || renderer.contains("softpipe")
        || renderer.contains("swiftshader")
        || (renderer.contains("angle") && renderer.contains("warp"));
  }
}
