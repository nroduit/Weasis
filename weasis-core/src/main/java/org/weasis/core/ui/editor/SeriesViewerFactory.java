/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.swing.Action;
import org.weasis.core.api.gui.util.GUIEntry;
import org.weasis.core.api.media.data.MediaElement;

public interface SeriesViewerFactory extends GUIEntry {

  SeriesViewer<?> createSeriesViewer(Map<String, Object> properties);

  boolean canReadMimeType(String mimeType);

  boolean isViewerCreatedByThisFactory(SeriesViewer<? extends MediaElement> viewer);

  int getLevel();

  boolean canAddSeries();

  boolean canExternalizeSeries();

  default List<Action> getOpenActions() {
    return Collections.emptyList();
  }
}
