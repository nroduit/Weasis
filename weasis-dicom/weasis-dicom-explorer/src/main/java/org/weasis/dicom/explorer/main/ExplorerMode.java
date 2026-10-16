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

import org.weasis.dicom.explorer.Messages;

/** How the DICOM explorer draws the items of the selected patient. */
public enum ExplorerMode {
  /** Patient, study and series thumbnails in a column. */
  THUMBNAILS("thumbnails", "DicomExplorer.mode_thumbnails"), // NON-NLS
  /** One row per series and per non-image object, studies as group rows. */
  LIST("list", "DicomExplorer.mode_list"); // NON-NLS

  private final String id;
  private final String titleKey;

  ExplorerMode(String id, String titleKey) {
    this.id = id;
    this.titleKey = titleKey;
  }

  /** The stable identifier of the commands and the settings. */
  public String getId() {
    return id;
  }

  public String getTitle() {
    return Messages.getString(titleKey);
  }

  /** The mode with this identifier (case-insensitive), or null. */
  public static ExplorerMode fromId(String id) {
    if (id == null) {
      return null;
    }
    for (ExplorerMode mode : values()) {
      if (mode.id.equalsIgnoreCase(id.trim())) {
        return mode;
      }
    }
    return null;
  }
}
