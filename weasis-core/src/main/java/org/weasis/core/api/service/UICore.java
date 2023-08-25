/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.service;

import bibliothek.gui.dock.common.CContentArea;
import bibliothek.gui.dock.common.CControl;
import bibliothek.gui.dock.common.CWorkingArea;
import bibliothek.gui.dock.common.event.CVetoFocusListener;
import bibliothek.gui.dock.common.intern.CDockable;
import java.awt.Window;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.util.ClosableURLConnection;
import org.weasis.core.api.util.LocalUtil;
import org.weasis.core.api.util.NetworkUtil;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.URLParameters;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.util.ToolBarContainer;
import org.weasis.core.ui.util.Toolbar;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.StringUtil;

public final class UICore {
  public static final String P_FORMAT_CODE = "locale.format.code";

  public static final String CONFIRM_CLOSE = "weasis.confirm.closing";
  public static final String LINUX_WINDOWS_DECORATION = "weasis.linux.windows.decoration";
  private static final Logger LOGGER = LoggerFactory.getLogger(UICore.class);
  private static final UICore INSTANCE = new UICore();
  private final ToolBarContainer toolbarContainer;
  public final List<ViewerPlugin<?>> viewerPlugins;
  private final List<DataExplorerView> explorerPlugins;
  private final List<Toolbar> explorerPluginToolbars;
  private final List<SeriesViewerFactory> seriesViewerFactories;
  private final CVetoFocusListener dockingVetoFocus;
  private final CControl dockingControl;
  private final CContentArea baseArea;
  private final CWorkingArea mainArea;

  private final List<Codec> codecPlugins;
  private final WProperties systemPreferences;
  private final WProperties localPersistence;
  private final WProperties initialSystemPreferences;
  private final HashMap<String, WProperties> pluginPersistenceMap;
  private final File propsFile;

  /** Do not instantiate UICore, get OSGI singleton service from GuiUtils.getUICore() */
  private UICore() {
    this.dockingControl = new CControl();
    this.baseArea = dockingControl.getContentArea();
    this.mainArea = dockingControl.createWorkingArea("mainArea");
    this.toolbarContainer = new ToolBarContainer();
    this.initialSystemPreferences = new WProperties();
    this.systemPreferences = new WProperties();
    this.pluginPersistenceMap = new HashMap<>();
    this.codecPlugins = Collections.synchronizedList(new ArrayList<>());
    this.localPersistence = new WProperties();
    this.viewerPlugins = Collections.synchronizedList(new ArrayList<>());
    this.explorerPlugins = Collections.synchronizedList(new ArrayList<>());
    this.explorerPluginToolbars = Collections.synchronizedList(new ArrayList<>());
    this.seriesViewerFactories = Collections.synchronizedList(new ArrayList<>());
    this.dockingVetoFocus =
        new CVetoFocusListener() {

          @Override
          public boolean willLoseFocus(CDockable dockable) {
            return false;
          }

          @Override
          public boolean willGainFocus(CDockable dockable) {
            return false;
          }
        };

    BundleContext context = AppProperties.getBundleContext();
    readSystemPreferences(context);
    this.propsFile =
        new File(systemPreferences.getProperty("weasis.pref.dir"), "weasis.properties");
    if (!propsFile.canRead()) {
      try {
        if (!propsFile.createNewFile()) {
          LOGGER.warn("File already exist {}", propsFile.getPath());
        }
      } catch (IOException e) {
        LOGGER.error("Cannot write {}", propsFile.getPath(), e);
      }
    }
    String code = systemPreferences.getProperty(UICore.P_FORMAT_CODE);
    if (StringUtil.hasLength(code)) {
      Locale l = LocalUtil.textToLocale(code);
      if (!l.equals(Locale.getDefault())) {
        LocalUtil.setLocaleFormat(l);
      }
    }

    String path = systemPreferences.getProperty("weasis.resources.path");
    ResourceUtil.setResourcePath(path);

    File dataFolder = AppProperties.getBundleDataFolder(context);
    FileUtil.readProperties(new File(dataFolder, "persistence.properties"), localPersistence);
  }

  public static UICore getInstance() {
    return INSTANCE;
  }

  private void readSystemPreferences(BundleContext context) {
    systemPreferences.clear();
    if (context != null) {
      String pkeys = context.getProperty("wp.list");
      if (StringUtil.hasText(pkeys)) {
        for (String key : pkeys.split(",")) {
          systemPreferences.setProperty(key, context.getProperty(key));
          initialSystemPreferences.setProperty(
              key, context.getProperty("wp.init." + key)); // NON-NLS
        }
        // In case the remote file is empty or has fewer properties than the local file, set a pref
        // to force
        // rewriting both files
        String diffRemote = "wp.init.diff.remote.pref";
        initialSystemPreferences.setProperty(diffRemote, context.getProperty(diffRemote));
        saveSystemPreferences();
      }
    }
  }

  private void storeLauncherPref(Properties props, String remotePrefURL) throws IOException {
    if (!isLocalSession() || isStoreLocalSession()) {
      String sURL =
          String.format(
              "%s?user=%s&profile=%s", // NON-NLS
              remotePrefURL,
              getUrlEncoding(AppProperties.WEASIS_USER),
              getUrlEncoding(AppProperties.WEASIS_PROFILE));
      URLParameters urlParameters = getURLParameters(true);
      ClosableURLConnection http = NetworkUtil.getUrlConnection(sURL, urlParameters);
      try (OutputStream out = http.getOutputStream()) {
        props.store(new DataOutputStream(out), null);
      }
      if (http.getUrlConnection() instanceof HttpURLConnection httpURLConnection) {
        NetworkUtil.readResponse(httpURLConnection, urlParameters.getUnmodifiableHeaders());
      }
    }
  }

  public static String getUrlEncoding(String val) {
    return URLEncoder.encode(val, StandardCharsets.UTF_8);
  }

  private static URLParameters getURLParameters(boolean post) {
    Map<String, String> map = new HashMap<>();
    map.put(post ? "Content-Type" : "Accept", "text/x-java-properties"); // NON-NLS
    return new URLParameters(map, post);
  }

  public String getPrefServiceUrl() {
    return systemPreferences.getProperty("weasis.pref.url");
  }

  public String getConfigServiceUrl() {
    return GuiUtils.getUICore().getSystemPreferences().getProperty("weasis.config.url");
  }

  public String getStatisticServiceUrl() {
    return GuiUtils.getUICore().getSystemPreferences().getProperty("weasis.stat.url");
  }

  public boolean isLocalSession() {
    return GuiUtils.getUICore()
        .getSystemPreferences()
        .getBooleanProperty("weasis.pref.local.session", false);
  }

  public boolean isStoreLocalSession() {
    return GuiUtils.getUICore()
        .getSystemPreferences()
        .getBooleanProperty("weasis.pref.store.local.session", false);
  }

  public synchronized void saveSystemPreferences() {
    // Set in a popup message of the launcher
    String key = "weasis.accept.disclaimer";
    systemPreferences.setProperty(key, System.getProperty(key));
    key = "weasis.version.release";
    systemPreferences.setProperty(key, System.getProperty(key));

    if (!systemPreferences.equals(initialSystemPreferences)) {
      FileUtil.storeProperties(GuiUtils.getUICore().getPropsFile(), systemPreferences, null);
      String remotePrefURL = getPrefServiceUrl();
      if (remotePrefURL != null) {
        try {
          storeLauncherPref(systemPreferences, remotePrefURL);
        } catch (Exception e) {
          LOGGER.error(
              "Cannot store Launcher preference for user: {}", AppProperties.WEASIS_USER, e);
        }
      }
      initialSystemPreferences.clear();
      initialSystemPreferences.putAll(systemPreferences);
    }
  }

  public CControl getDockingControl() {
    return dockingControl;
  }

  public CWorkingArea getMainArea() {
    return mainArea;
  }

  public CContentArea getBaseArea() {
    return baseArea;
  }

  public Window getApplicationWindow() {
    return WinUtil.getParentWindow(baseArea);
  }

  public List<DataExplorerView> getExplorerPlugins() {
    return explorerPlugins;
  }

  public ToolBarContainer getToolbarContainer() {
    return toolbarContainer;
  }

  public List<ViewerPlugin<?>> getViewerPlugins() {
    return viewerPlugins;
  }

  public List<Toolbar> getExplorerPluginToolbars() {
    return explorerPluginToolbars;
  }

  public List<SeriesViewerFactory> getSeriesViewerFactories() {
    return seriesViewerFactories;
  }

  public CVetoFocusListener getDockingVetoFocus() {
    return dockingVetoFocus;
  }

  public List<Codec> getCodecPlugins() {
    return codecPlugins;
  }

  /**
   * This the persistence used at launch which can be stored remotely. These are the preferences
   * necessary for launching unlike the preferences associated with the plugins.
   *
   * @return the properties
   */
  public WProperties getSystemPreferences() {
    return systemPreferences;
  }

  /**
   * This the common local persistence for UI. It should be used only for preferences for which
   * remote storage makes no sense.
   *
   * @return the properties
   */
  public WProperties getLocalPersistence() {
    return localPersistence;
  }

  public WProperties getPluginPersistence(String key) {
    return pluginPersistenceMap.computeIfAbsent(key, k -> new WProperties());
  }

  public File getPropsFile() {
    return propsFile;
  }

  public DataExplorerView getExplorerPlugin(String name) {
    if (name != null) {
      synchronized (explorerPlugins) {
        for (DataExplorerView view : explorerPlugins) {
          if (name.equals(view.getUIName())) {
            return view;
          }
        }
      }
    }
    return null;
  }

  public SeriesViewerFactory getViewerFactory(Class<? extends SeriesViewerFactory> clazz) {
    if (clazz != null) {
      synchronized (seriesViewerFactories) {
        for (final SeriesViewerFactory factory : seriesViewerFactories) {
          if (clazz.isInstance(factory)) {
            return factory;
          }
        }
      }
    }
    return null;
  }

  public SeriesViewerFactory getViewerFactory(SeriesViewer seriesViewer) {
    if (seriesViewer != null) {
      synchronized (seriesViewerFactories) {
        for (final SeriesViewerFactory factory : seriesViewerFactories) {
          if (factory != null && factory.isViewerCreatedByThisFactory(seriesViewer)) {
            return factory;
          }
        }
      }
    }
    return null;
  }

  public SeriesViewerFactory getViewerFactory(String mimeType) {
    if (mimeType != null) {
      synchronized (seriesViewerFactories) {
        int level = Integer.MAX_VALUE;
        SeriesViewerFactory best = null;
        for (final SeriesViewerFactory f : seriesViewerFactories) {
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

  public SeriesViewerFactory getViewerFactory(String[] mimeTypeList) {
    if (mimeTypeList != null && mimeTypeList.length > 0) {
      synchronized (seriesViewerFactories) {
        int level = Integer.MAX_VALUE;
        SeriesViewerFactory best = null;
        for (final SeriesViewerFactory f : seriesViewerFactories) {
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

  public List<SeriesViewerFactory> getViewerFactoryList(String[] mimeTypeList) {
    if (mimeTypeList != null && mimeTypeList.length > 0) {
      List<SeriesViewerFactory> plugins = new ArrayList<>();
      synchronized (seriesViewerFactories) {
        for (final SeriesViewerFactory viewerFactory : seriesViewerFactories) {
          if (viewerFactory != null) {
            for (String mime : mimeTypeList) {
              if (viewerFactory.canReadMimeType(mime)) {
                plugins.add(viewerFactory);
              }
            }
          }
        }
      }

      plugins.sort(Comparator.comparingInt(SeriesViewerFactory::getLevel));
      return plugins;
    }
    return null;
  }

  public boolean isSeriesOpenInViewer(MediaSeries<?> mediaSeries) {
    if (mediaSeries == null) {
      return false;
    }
    synchronized (viewerPlugins) {
      for (final ViewerPlugin<?> plugin : viewerPlugins) {
        List<? extends MediaSeries<?>> openSeries = plugin.getOpenSeries();
        if (openSeries != null) {
          for (MediaSeries<?> s : openSeries) {
            if (mediaSeries == s) {
              // The sequence is still open in another view or plugin
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  public void closeSeries(MediaSeries<?> mediaSeries) {
    if (mediaSeries == null) {
      return;
    }
    mediaSeries.setOpen(isSeriesOpenInViewer(mediaSeries));
    // TODO setSelected and setFocused must be global to all view as open
    mediaSeries.setSelected(false, null);
    mediaSeries.setFocused(false);
  }

  public void closeSeriesViewerType(Class<? extends SeriesViewer<?>> clazz) {
    final List<ViewerPlugin<?>> pluginsToRemove = new ArrayList<>();
    synchronized (viewerPlugins) {
      for (final ViewerPlugin<?> plugin : viewerPlugins) {
        if (clazz.isInstance(plugin)) {
          // Do not close Series directly, it can produce deadlock.
          pluginsToRemove.add(plugin);
        }
      }
    }
    closeSeriesViewer(pluginsToRemove);
  }

  public void closeSeriesViewer(final List<? extends ViewerPlugin<?>> pluginsToRemove) {
    if (pluginsToRemove != null) {
      GuiExecutor.instance()
          .execute(
              () -> {
                for (final ViewerPlugin<?> viewerPlugin : pluginsToRemove) {
                  viewerPlugin.close();
                  viewerPlugin.handleFocusAfterClosing();
                }
              });
    }
  }

  public void updateTools(SeriesViewer<?> oldPlugin, SeriesViewer<?> plugin, boolean force) {
    List<DockableTool> oldTool = oldPlugin == null ? null : oldPlugin.getToolPanel();
    List<DockableTool> tool = plugin == null ? null : plugin.getToolPanel();
    if (force || !Objects.equals(tool, oldTool)) {
      if (oldTool != null) {
        for (DockableTool p : oldTool) {
          p.closeDockable();
        }
      }
      if (tool != null) {
        for (DockableTool p : tool) {
          if (p.isComponentEnabled()) {
            p.showDockable();
          }
        }
      }
    }
  }

  public void updateToolbars(SeriesViewer<?> oldPlugin, SeriesViewer<?> plugin, boolean force) {
    List<Toolbar> oldToolBars = oldPlugin == null ? null : oldPlugin.getToolBars();
    List<Toolbar> toolBars = plugin == null ? null : plugin.getToolBars();
    if (force || toolBars != oldToolBars) {
      toolbarContainer.registerToolBar(toolBars);
    }
  }
}
