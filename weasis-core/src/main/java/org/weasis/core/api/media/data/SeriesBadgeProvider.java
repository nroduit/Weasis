/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.media.data;

import java.util.Set;
import org.weasis.core.api.util.ResourceUtil.ResourceIconPath;

/**
 * Icons a plugin draws on a series thumbnail, after the ones of the special elements (PR, KO, SEG)
 * that reference the series. Registered as a service and tracked in {@code
 * UICore.getSeriesBadgeProviders()}, it is consulted on every paint of the thumbnail, so an
 * implementation answers from its own state and never touches the network. A plugin whose state
 * changed repaints the thumbnail kept in {@link TagW#Thumbnail}.
 */
@FunctionalInterface
public interface SeriesBadgeProvider {

  /**
   * @param seriesUID the Series Instance UID of the thumbnail being painted
   * @return the icons to draw for that series, empty when there is nothing to show; never null
   */
  Set<ResourceIconPath> getBadges(String seriesUID);
}
