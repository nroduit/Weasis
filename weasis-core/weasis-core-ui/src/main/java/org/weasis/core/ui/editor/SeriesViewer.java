/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
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
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.util.WtoolBar;

public interface SeriesViewer<E extends MediaElement> {

    public String getPluginName();

    public void close();

    public List<MediaSeries<E>> getOpenSeries();

    public void addSeries(MediaSeries<E> sequence);

    public void removeSeries(MediaSeries<E> sequence);

    public JMenu fillSelectedPluginMenu(JMenu menu);

    public WtoolBar[] getToolBar();

    public WtoolBar getStatusBar();

    public PluginTool[] getToolPanel();

    public void setSelected(boolean selected);

    public MediaSeriesGroup getGroupID();

}
