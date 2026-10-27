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

import javax.swing.JPopupMenu;
import org.pf4j.ExtensionPoint;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;

/**
 * Adds items to the context menu of a series in an explorer, so a plugin offers what it can do with
 * a series without the explorer knowing it.
 */
public interface SeriesMenuContributor extends ExtensionPoint {

  /**
   * Adds the items that apply to the series, nothing when none applies.
   *
   * @param series the series the menu was opened on, never null
   * @param menu the menu being built
   */
  void contribute(MediaSeries<? extends MediaElement> series, JPopupMenu menu);
}
