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

import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Which implementation of {@link Gl} drives the renderer. Both are shipped while the port is under
 * way, so a regression on one machine is a preference away from a working viewer.
 */
public enum GlBackend {
  /** JOGL, the binding of the 4.x line. Kept for one release as the fallback, then removed. */
  JOGL,
  /** The context Weasis owns, driven through the Foreign Function &amp; Memory API. */
  FFM;

  private static final Logger LOGGER = LoggerFactory.getLogger(GlBackend.class);

  public static final String P_BACKEND = "weasis.3d.backend"; // NON-NLS

  /** The back-end to try first, {@value #P_BACKEND} deciding it and {@link #FFM} by default. */
  public static GlBackend selected() {
    String value = System.getProperty(P_BACKEND);
    if (value == null || value.isBlank()) {
      return DEFAULT;
    }
    try {
      return valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      LOGGER.warn("Unknown value of {}: {}; using {}", P_BACKEND, value, DEFAULT);
      return DEFAULT;
    }
  }

  private static final GlBackend DEFAULT = FFM;

  /** The one tried when the selected back-end has no context to offer on this machine. */
  public GlBackend fallback() {
    return this == FFM ? JOGL : FFM;
  }
}
