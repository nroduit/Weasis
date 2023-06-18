/*
 * Copyright (c) 2022 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.utils;

import org.weasis.core.api.util.ResourceUtil.ResourcePath;

public enum DicomResource implements ResourcePath {
  ATTRIBUTES_VIEW("attributes-view.xml"), // NON-NLS
  CALLING_NODES("dicomCallingNodes.xml"),
  LUTS("luts"), // NON-NLS
  PRESETS("presets.xml"),
  SERIES_SPITTING_RULES("series-splitting-rules.xml"), // NON-NLS
  CGET_SOP_UID("store-tcs.properties"); // NON-NLS

  private final String path;

  DicomResource(String path) {
    this.path = path;
  }

  public String getPath() {
    return path;
  }
}
