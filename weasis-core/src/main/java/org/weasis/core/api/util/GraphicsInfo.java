/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.util;

import java.util.Optional;

/**
 * Holds the OpenGL renderer detected by the 3D viewer so that other modules — which must not depend
 * on the 3D module — can display it (for instance the resource dashboard).
 *
 * <p>It stays empty until the 3D subsystem initializes OpenGL. {@link ResourceMonitor} persists the
 * last known value across sessions and restores it at startup, so the GPU is shown even on a run
 * where no 3D view was opened.
 */
public final class GraphicsInfo {

  /** The OpenGL renderer backing the 3D viewer. */
  public record Gpu(String vendor, String renderer, String glVersion, boolean softwareRendered) {}

  private static volatile Gpu gpu;

  private GraphicsInfo() {}

  /** Publishes the detected OpenGL renderer; called once the 3D viewer has initialized OpenGL. */
  public static void set(
      String vendor, String renderer, String glVersion, boolean softwareRendered) {
    gpu =
        new Gpu(
            vendor == null ? "" : vendor,
            renderer == null ? "" : renderer,
            glVersion == null ? "" : glVersion,
            softwareRendered);
  }

  /**
   * @return the detected GPU, or empty until the 3D viewer has initialized OpenGL.
   */
  public static Optional<Gpu> get() {
    return Optional.ofNullable(gpu);
  }
}
