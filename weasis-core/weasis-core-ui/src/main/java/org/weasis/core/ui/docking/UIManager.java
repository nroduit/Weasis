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
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;

import org.noos.xing.mydoggy.ToolWindowAnchor;
import org.noos.xing.mydoggy.ToolWindowManagerDescriptor;
import org.noos.xing.mydoggy.ToolWindowType;
import org.noos.xing.mydoggy.ToolWindowTypeDescriptor;
import org.noos.xing.mydoggy.plaf.MyDoggyToolWindowBar;
import org.noos.xing.mydoggy.plaf.MyDoggyToolWindowManager;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.image.ViewerPlugin;

public class UIManager {

    public static final MyDoggyToolWindowManager toolWindowManager = builWinManager();

    public static final AtomicInteger dockableUIGenerator = new AtomicInteger(1);

    public static final List<ViewerPlugin> VIEWER_PLUGINS = Collections.synchronizedList(new ArrayList<ViewerPlugin>());
    public static final List<DataExplorerView> EXPLORER_PLUGINS =
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

    private static MyDoggyToolWindowManager builWinManager() {
        MyDoggyToolWindowManager result = null;

        FutureTask<MyDoggyToolWindowManager> future =
            new FutureTask<MyDoggyToolWindowManager>(new Callable<MyDoggyToolWindowManager>() {

                @Override
                public MyDoggyToolWindowManager call() throws Exception {
                    MyDoggyToolWindowManager winManager =
                        new MyDoggyToolWindowManager(Locale.getDefault(), MyDoggyToolWindowManager.class
                            .getClassLoader());
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
                    return winManager;
                }
            });
        GuiExecutor.instance().invokeAndWait(future);
        try {
            result = future.get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        return result;
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
