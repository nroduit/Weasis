/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.editor;

import java.util.Collections;
import java.util.List;

import javax.swing.Action;
import javax.swing.JMenu;

import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.util.Toolbar;
import org.weasis.core.ui.util.WtoolBar;

public interface SeriesViewer<E extends MediaElement> {

    String getPluginName();

    void close();

    List<MediaSeries<E>> getOpenSeries();

    void addSeries(MediaSeries<E> series);

    void removeSeries(MediaSeries<E> series);

    JMenu fillSelectedPluginMenu(JMenu menuRoot);

    List<Toolbar> getToolBar();

    default WtoolBar getStatusBar() {
        return null;
    }

    List<DockableTool> getToolPanel();

    void setSelected(boolean selected);

    MediaSeriesGroup getGroupID();

    String getDockableUID();

    default List<Action> getExportActions() {
        return Collections.emptyList();
    }

    default List<Action> getPrintActions() {
        return Collections.emptyList();
    }

}
