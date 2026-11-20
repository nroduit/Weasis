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

import java.util.Objects;
import org.weasis.core.api.util.ResourceUtil.ResourceIconPath;

/**
 * A mark a plugin puts on a series of the explorer: an icon on its thumbnail and a short text in
 * the list.
 *
 * @param icon the icon drawn on the thumbnail, from the resources of the application
 * @param label the short text of the list (a word), may be null to show nothing there
 */
public record SeriesBadge(ResourceIconPath icon, String label) {

  public SeriesBadge {
    Objects.requireNonNull(icon);
  }
}
