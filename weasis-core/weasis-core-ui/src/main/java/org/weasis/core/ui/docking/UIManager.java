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
package org.weasis.core.ui.docking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.image.ViewerPlugin;

import bibliothek.gui.dock.common.CContentArea;
import bibliothek.gui.dock.common.CControl;
import bibliothek.gui.dock.common.CWorkingArea;
import bibliothek.gui.dock.common.event.CVetoFocusListener;
import bibliothek.gui.dock.common.intern.CDockable;

public class UIManager {

    public static final AtomicInteger dockableUIGenerator = new AtomicInteger(1);

    public static final List<ViewerPlugin<?>> VIEWER_PLUGINS = Collections
        .synchronizedList(new ArrayList<ViewerPlugin<?>>());
    public static final List<DataExplorerView> EXPLORER_PLUGINS = Collections
        .synchronizedList(new ArrayList<DataExplorerView>());
    public static final List<SeriesViewerFactory> SERIES_VIEWER_FACTORIES = Collections
        .synchronizedList(new ArrayList<SeriesViewerFactory>());

    public static final CVetoFocusListener DOCKING_VETO_FOCUS = new CVetoFocusListener() {

        @Override
        public boolean willLoseFocus(CDockable dockable) {
            return false;
        }

        @Override
        public boolean willGainFocus(CDockable dockable) {
            return false;
        }
    };

    public static final CControl DOCKING_CONTROL = new CControl();
    public static final CContentArea BASE_AREA = DOCKING_CONTROL.getContentArea();
    public static final CWorkingArea MAIN_AREA = DOCKING_CONTROL.createWorkingArea("mainArea"); //$NON-NLS-1$

    // public static final CContentArea WEST_AREA = DOCKING_CONTROL.createContentArea("westArea");

    public static DataExplorerView getExplorerplugin(String name) {
        if (name != null) {
            synchronized (EXPLORER_PLUGINS) {
                for (DataExplorerView view : EXPLORER_PLUGINS) {
                    if (name.equals(view.getUIName())) {
                        return view;
                    }
                }
            }
        }
        return null;
    }

    public static SeriesViewerFactory getViewerFactory(SeriesViewer seriesViewer) {
        if (seriesViewer != null) {
            synchronized (UIManager.SERIES_VIEWER_FACTORIES) {
                List<SeriesViewerFactory> plugins = UIManager.SERIES_VIEWER_FACTORIES;
                for (final SeriesViewerFactory factory : plugins) {
                    if (factory != null && factory.isViewerCreatedByThisFactory(seriesViewer)) {
                        return factory;
                    }
                }
            }
        }
        return null;
    }

    public static SeriesViewerFactory getViewerFactory(String mimeType) {
        if (mimeType != null) {
            synchronized (UIManager.SERIES_VIEWER_FACTORIES) {
                int level = Integer.MAX_VALUE;
                SeriesViewerFactory best = null;
                for (final SeriesViewerFactory f : SERIES_VIEWER_FACTORIES) {
                    if (f != null && f.canReadMimeType(mimeType)) {
                        if (f.getLevel() < level) {
                            level = f.getLevel();
                            best = f;
                        }
                    }
                }
                return best;
            }
        }
        return null;
    }

    public static SeriesViewerFactory getViewerFactory(String[] mimeTypeList) {
        if (mimeTypeList != null && mimeTypeList.length > 0) {
            synchronized (UIManager.SERIES_VIEWER_FACTORIES) {
                List<SeriesViewerFactory> plugins = UIManager.SERIES_VIEWER_FACTORIES;
                int level = Integer.MAX_VALUE;
                SeriesViewerFactory best = null;
                for (final SeriesViewerFactory f : plugins) {
                    if (f != null) {
                        for (String mime : mimeTypeList) {
                            if (f.canReadMimeType(mime) && f.getLevel() < level) {
                                best = f;
                            }
                        }
                    }
                }
                return best;
            }
        }
        return null;
    }

    public static List<SeriesViewerFactory> getViewerFactoryList(String[] mimeTypeList) {
        if (mimeTypeList != null && mimeTypeList.length > 0) {
            List<SeriesViewerFactory> plugins = new ArrayList<SeriesViewerFactory>();
            synchronized (UIManager.SERIES_VIEWER_FACTORIES) {
                for (final SeriesViewerFactory viewerFactory : UIManager.SERIES_VIEWER_FACTORIES) {
                    if (viewerFactory != null) {
                        for (String mime : mimeTypeList) {
                            if (viewerFactory.canReadMimeType(mime)) {
                                plugins.add(viewerFactory);
                            }
                        }
                    }
                }
            }

            Collections.sort(plugins, new Comparator<SeriesViewerFactory>() {

                @Override
                public int compare(SeriesViewerFactory s1, SeriesViewerFactory s2) {
                    return (s1.getLevel() < s2.getLevel() ? -1 : (s1.getLevel() == s2.getLevel() ? 0 : 1));
                }
            });

            return plugins;
        }
        return null;
    }

    public static void closeSeriesViewerType(Class<? extends SeriesViewer<?>> clazz) {
        final List<SeriesViewer<?>> pluginsToRemove = new ArrayList<SeriesViewer<?>>();
        synchronized (UIManager.VIEWER_PLUGINS) {
            for (final SeriesViewer<?> plugin : UIManager.VIEWER_PLUGINS) {
                if (clazz.isInstance(plugin)) {
                    // Do not close Series directly, it can produce deadlock.
                    pluginsToRemove.add(plugin);
                }
            }
        }
        closeSeriesViewer(pluginsToRemove);
    }

    public static void closeSeriesViewer(final List<? extends SeriesViewer<?>> pluginsToRemove) {
        if (pluginsToRemove != null) {
            for (final SeriesViewer<?> viewerPlugin : pluginsToRemove) {
                viewerPlugin.close();
            }
        }
    }
}
