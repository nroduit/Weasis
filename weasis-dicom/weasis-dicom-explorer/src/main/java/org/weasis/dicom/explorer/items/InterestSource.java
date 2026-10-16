/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.items;

/** What makes an image one of interest: who points at it. */
public enum InterestSource {
  /** Referenced by a Key Object Selection document. */
  KEY_OBJECT("ko"), // NON-NLS
  /** Carries measurements or annotations drawn in Weasis. */
  GRAPHICS("graphics"), // NON-NLS
  /** Referenced by a presentation state that carries graphic or text annotations. */
  PRESENTATION_STATE("pr"), // NON-NLS
  /** Referenced by a segment of a SEG. */
  SEGMENTATION("seg"); // NON-NLS

  private final String id;

  InterestSource(String id) {
    this.id = id;
  }

  public String getId() {
    return id;
  }

  public static InterestSource fromId(String id) {
    if (id == null) {
      return null;
    }
    for (InterestSource source : values()) {
      if (source.id.equalsIgnoreCase(id.trim())) {
        return source;
      }
    }
    return null;
  }
}
