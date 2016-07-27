/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse  License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.ui.editor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.swing.Action;

import org.weasis.core.api.gui.util.GUIEntry;
import org.weasis.core.api.media.data.MediaElement;

public interface SeriesViewerFactory extends GUIEntry {

    SeriesViewer<? extends MediaElement<?>> createSeriesViewer(Map<String, Object> properties);

    boolean canReadMimeType(String mimeType);

    boolean isViewerCreatedByThisFactory(SeriesViewer<? extends MediaElement<?>> viewer);

    int getLevel();

    boolean canAddSeries();

    boolean canExternalizeSeries();

    default List<Action> getOpenActions() {
        return Collections.emptyList();
    }
}
