/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.wado;

import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StringUtil;

/**
 * HTTP service used to retrieve the thumbnail of a series. Set per archive with the {@code
 * thumbnailMode} manifest attribute, as archives implement different subsets of these services.
 */
public enum ThumbnailMode {
  /** Probes the services below in order and remembers what the archive supports. */
  AUTO,
  /**
   * DICOMweb thumbnail service: {@code /studies/{uid}/series/{uid}[/instances/{uid}]/thumbnail}.
   */
  RS_THUMBNAIL,
  /** DICOMweb rendered service on a single instance: {@code .../instances/{uid}/rendered}. */
  RENDERED,
  /** WADO-URI rendering: {@code ?requestType=WADO&...&contentType=image/jpeg}. */
  WADO_URI,
  /** No thumbnail is requested from the archive. */
  NONE;

  private static final Logger LOGGER = LoggerFactory.getLogger(ThumbnailMode.class);

  /** Returns the mode named by {@code value}, or {@link #AUTO} when it is absent or unknown. */
  public static ThumbnailMode of(String value) {
    if (!StringUtil.hasText(value)) {
      return AUTO;
    }
    try {
      return valueOf(value.trim().toUpperCase(Locale.ROOT)); // NOSONAR hasText checks null
    } catch (IllegalArgumentException e) {
      LOGGER.warn("Unknown thumbnail mode '{}', using {}", value, AUTO);
      return AUTO;
    }
  }
}
