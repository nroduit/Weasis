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

/** Row height of the compact list, and the size of the micro-thumbnail it leaves room for. */
public enum ListDensity {
  SMALL("small", 20, 0, "DicomExplorer.density_small"), // NON-NLS
  MEDIUM("medium", 26, 22, "DicomExplorer.density_medium"), // NON-NLS
  LARGE("large", 40, 36, "DicomExplorer.density_large"); // NON-NLS

  private final String id;
  private final int rowHeight;
  private final int thumbnailSize;
  private final String titleKey;

  ListDensity(String id, int rowHeight, int thumbnailSize, String titleKey) {
    this.id = id;
    this.rowHeight = rowHeight;
    this.thumbnailSize = thumbnailSize;
    this.titleKey = titleKey;
  }

  public String getId() {
    return id;
  }

  /** Row height in logical pixels. */
  public int getRowHeight() {
    return rowHeight;
  }

  /** Side of the micro-thumbnail in logical pixels, 0 for none. */
  public int getThumbnailSize() {
    return thumbnailSize;
  }

  public String getTitle() {
    return Messages.getString(titleKey);
  }

  public static ListDensity fromId(String id) {
    if (id == null) {
      return null;
    }
    for (ListDensity density : values()) {
      if (density.id.equalsIgnoreCase(id.trim())) {
        return density;
      }
    }
    return null;
  }
}
