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

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.SwingUtilities;

import org.noos.xing.mydoggy.ToolWindowAnchor;
import org.noos.xing.mydoggy.ToolWindowManagerDescriptor;
import org.noos.xing.mydoggy.ToolWindowType;
import org.noos.xing.mydoggy.ToolWindowTypeDescriptor;
import org.noos.xing.mydoggy.plaf.MyDoggyToolWindowBar;
import org.noos.xing.mydoggy.plaf.MyDoggyToolWindowManager;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.image.ViewerPlugin;

public class UIManager {

    static class WinManager implements Runnable {

        MyDoggyToolWindowManager winManager;

        @Override
        public void run() {
            winManager =
                new MyDoggyToolWindowManager(Locale.getDefault(), MyDoggyToolWindowManager.class.getClassLoader());
            winManager.getResourceManager().setUserBundle(Locale.getDefault(), "/toolWindow", //$NON-NLS-1$
                UIManager.class.getClassLoader());
            // set the size of the splitPane separator
            for (ToolWindowAnchor anchor : ToolWindowAnchor.values()) {
                MyDoggyToolWindowBar bar = winManager.getBar(anchor);
                bar.setDividerSize(7);
            }

            ToolWindowManagerDescriptor desc = winManager.getToolWindowManagerDescriptor();
            desc.setNumberingEnabled(false);

            for (ToolWindowType toolWinType : ToolWindowType.values()) {
                if (toolWinType.equals(ToolWindowType.EXTERN)) {
                    continue;
                }
                ToolWindowTypeDescriptor type = winManager.getTypeDescriptorTemplate(toolWinType);
                type.setIdVisibleOnTitleBar(false);
                type.setAnimating(false);
            }
        }
    }

    private final static WinManager manager = new WinManager();
    static {
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                manager.run();
            } else {
                SwingUtilities.invokeAndWait(manager);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }
    public final static MyDoggyToolWindowManager toolWindowManager = manager.winManager;

    public final static AtomicInteger dockableUIGenerator = new AtomicInteger(1);

    public final static List<ViewerPlugin> VIEWER_PLUGINS = Collections.synchronizedList(new ArrayList<ViewerPlugin>());
    public final static List<DataExplorerView> EXPLORER_PLUGINS =
        Collections.synchronizedList(new ArrayList<DataExplorerView>());
    public static final List<SeriesViewerFactory> SERIES_VIEWER_FACTORIES =
        Collections.synchronizedList(new ArrayList<SeriesViewerFactory>());

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
        synchronized (UIManager.SERIES_VIEWER_FACTORIES) {
            List<SeriesViewerFactory> plugins = UIManager.SERIES_VIEWER_FACTORIES;
            for (final SeriesViewerFactory factory : plugins) {
                if (factory != null && factory.isViewerCreatedByThisFactory(seriesViewer)) {
                    return factory;
                }
            }
        }
        return null;
    }

    public static SeriesViewerFactory getViewerFactory(String mimeType) {
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

    public static SeriesViewerFactory getViewerFactory(String[] mimeTypeList) {
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

    public static List<SeriesViewerFactory> getViewerFactoryList(String[] mimeTypeList) {
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

            public int compare(SeriesViewerFactory s1, SeriesViewerFactory s2) {
                return (s1.getLevel() < s2.getLevel() ? -1 : (s1.getLevel() == s2.getLevel() ? 0 : 1));
            }
        });

        return plugins;
    }
}
