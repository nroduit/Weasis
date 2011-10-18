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

import java.util.List;

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

    JMenu fillSelectedPluginMenu(JMenu menu);

    List<Toolbar> getToolBar();

    WtoolBar getStatusBar();

    List<DockableTool> getToolPanel();

    void setSelected(boolean selected);

    MediaSeriesGroup getGroupID();

}
