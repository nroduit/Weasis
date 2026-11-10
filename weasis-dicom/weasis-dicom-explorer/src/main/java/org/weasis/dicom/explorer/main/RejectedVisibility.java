/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.main;

import java.util.Locale;

/**
 * What the explorer shows of the instances rejected by a rejection note (IOCM): nothing, the count
 * on their series, or the rejection notes listed as objects too. Nothing rejected is ever
 * displayed. A site setting, {@code weasis.dicom.explorer.rejected}.
 */
public enum RejectedVisibility {
  /** No trace of the rejected instances. */
  HIDDEN,
  /** The series shows how many of its images are rejected (the default). */
  COUNT,
  /** The count, and the rejection notes listed as objects of the study. */
  LIST;

  public String getId() {
    return name().toLowerCase(Locale.ROOT);
  }

  /** The value of the setting, {@link #COUNT} for an unknown or empty one. */
  public static RejectedVisibility fromId(String id) {
    if (id != null) {
      for (RejectedVisibility v : values()) {
        if (v.getId().equalsIgnoreCase(id.trim())) {
          return v;
        }
      }
    }
    return COUNT;
  }
}
