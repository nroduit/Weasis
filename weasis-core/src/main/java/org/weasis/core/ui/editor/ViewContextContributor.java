/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor;

import java.util.Map;
import org.pf4j.ExtensionPoint;
import org.weasis.core.api.media.data.MediaSeries;

/**
 * A plugin that knows something about what is displayed adds it to the {@link ViewContext} under
 * dotted fact names ({@code hp.protocol}, {@code hp.step}, {@code hp.features}), so the tools,
 * toolbars and menus of every plugin can decide their relevance on the same facts.
 */
public interface ViewContextContributor extends ExtensionPoint {

  /**
   * Adds facts about the selected viewer and series.
   *
   * @param viewer the selected viewer, never null
   * @param series the selected series of that viewer, null when it shows none
   * @param facts the facts gathered so far, to add to
   */
  void contribute(SeriesViewer<?> viewer, MediaSeries<?> series, Map<String, Object> facts);
}
