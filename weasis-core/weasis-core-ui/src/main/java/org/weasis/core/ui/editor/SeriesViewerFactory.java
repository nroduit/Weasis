/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
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
