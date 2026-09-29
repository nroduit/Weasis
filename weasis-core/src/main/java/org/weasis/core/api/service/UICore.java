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

import bibliothek.gui.DockStation;
import bibliothek.gui.Dockable;
import bibliothek.gui.dock.DockElement;
import bibliothek.gui.dock.StackDockStation;
import bibliothek.gui.dock.common.CContentArea;
import bibliothek.gui.dock.common.CControl;
import bibliothek.gui.dock.common.CWorkingArea;
import bibliothek.gui.dock.common.DefaultSingleCDockable;
import bibliothek.gui.dock.common.event.CVetoFocusListener;
import bibliothek.gui.dock.common.intern.CDockable;
import bibliothek.gui.dock.common.intern.CommonDockable;
import bibliothek.gui.dock.event.KeyboardListener;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Locale.Category;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.prefs.Preferences;
import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.ShortcutManager;
import org.weasis.core.api.gui.util.ShortcutTable;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.net.ClosableURLConnection;
import org.weasis.core.api.net.NetworkUtil;
import org.weasis.core.api.net.URLParameters;
import org.weasis.core.api.util.LocalUtil;
import org.weasis.core.api.util.ResourceMonitor;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.launcher.Launcher;
import org.weasis.core.ui.model.graphic.GraphicRegistry;
import org.weasis.core.ui.model.graphic.GraphicToolProvider;
import org.weasis.core.ui.model.graphic.profile.MeasurementProfiles;
import org.weasis.core.ui.model.layer.AbstractInfoLayer;
import org.weasis.core.ui.util.ToolBarContainer;
import org.weasis.core.ui.util.Toolbar;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.PropertiesUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.plugin.WeasisPluginManager;
import org.weasis.pref.ConfigData;

public final class UICore {
  public static final String P_FORMAT_CODE = "locale.format.code";

  public static final String CONFIRM_CLOSE = "weasis.confirm.closing";
  public static final String CONFIRM_DELETE_MEASUREMENT = "weasis.confirm.delete.measurement";
  public static final String LINUX_WINDOWS_DECORATION = "weasis.linux.windows.decoration";
  public static final String USE_SYSTEM_FILE_CHOOSER = "weasis.use.system.file.chooser";
  private static final Logger LOGGER = LoggerFactory.getLogger(UICore.class);
  private final ToolBarContainer toolbarContainer;
  private final List<Launcher> dicomLaunchers;
  private final List<Launcher> otherLaunchers;
  public final List<ViewerPlugin<?>> viewerPlugins;
  private final List<DataExplorerView> explorerPlugins;
  private final List<Toolbar> explorerPluginToolbars;
  private final CVetoFocusListener dockingVetoFocus;
  private final CControl dockingControl;
  private final CContentArea baseArea;
  private final CWorkingArea mainArea;
  private volatile ViewerPlugin<?> selectedPlugin;

  /** The graphic tool providers registered per plugin, to unregister them when it stops. */
  private final Map<String, List<GraphicToolProvider>> graphicToolProviders =
      new ConcurrentHashMap<>();

  private final WProperties systemPreferences;
  private final WProperties localPersistence;
  private final WProperties initialSystemPreferences;
  private final ConfigData configData;
  private final HashMap<String, WProperties> pluginPersistenceMap;
  private final File propsFile;

  private static final class Holder {
    private static final UICore INSTANCE = new UICore();
  }

  /** Do not instantiate UICore, get the singleton service from GuiUtils.getUICore() */
  private UICore() {
    this.dockingControl = new CControl();
    if (GraphicsEnvironment.isHeadless()) {
      this.baseArea = null; // For test where no GUI is available
    } else {
      this.baseArea = dockingControl.getContentArea();
    }
    this.mainArea = dockingControl.createWorkingArea("mainArea");
    this.toolbarContainer = new ToolBarContainer();

    this.configData = retrieveconfigData();
    this.initialSystemPreferences = new WProperties(configData.getSystemProps());
    this.systemPreferences = new WProperties(configData.getSystemProps());
    this.pluginPersistenceMap = new HashMap<>();
    this.localPersistence = new WProperties();
    this.viewerPlugins = Collections.synchronizedList(new ArrayList<>());
    this.explorerPlugins = Collections.synchronizedList(new ArrayList<>());
    this.explorerPluginToolbars = Collections.synchronizedList(new ArrayList<>());
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
    dockingControl.getController().getKeyboardController().addListener(new StackTabSwitcher());

    readSystemPreferences();
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
    Locale.setDefault(Category.FORMAT, LocalUtil.textToLocale(code));

    String path = systemPreferences.getProperty("weasis.resources.path");
    ResourceUtil.setResourcePath(path);

    Path dataFolder = BundlePreferences.getDataFolder(AppProperties.CORE_PLUGIN_ID);
    if (dataFolder != null) {
      PropertiesUtil.loadProperties(dataFolder.resolve("persistence.properties"), localPersistence);
    }

    this.dicomLaunchers = Launcher.loadLaunchers(Launcher.Type.DICOM);
    this.otherLaunchers = Launcher.loadLaunchers(Launcher.Type.OTHER);

    loadPreferences();
  }

  private static ConfigData retrieveconfigData() {
    MBeanServer server = ManagementFactory.getPlatformMBeanServer();
    try {
      ObjectName objectName = ObjectName.getInstance("weasis:name=MainWindow"); // NON-NLS
      Object preferences = server.getAttribute(objectName, "ConfigData");
      if (preferences instanceof ConfigData configData) {
        return configData;
      }
    } catch (InstanceNotFoundException ignored) {
    } catch (JMException e) {
      LOGGER.debug("Error while receiving main window", e);
    }
    throw new IllegalStateException("Cannot retrieve ConfigData");
  }

  private void loadPreferences() {
    try {

      initLoggerAndAudit(systemPreferences);

      Preferences prefs = BundlePreferences.getDefaultPreferences(AppProperties.CORE_PLUGIN_ID);
      AbstractInfoLayer.applyPreferences(prefs);
      MeasureTool.viewSetting.initMonitors(localPersistence);
      MeasureTool.viewSetting.applyPreferences(prefs);

      // Initialize keyboard shortcut manager
      ShortcutManager shortcutManager = ShortcutManager.getInstance();
      shortcutManager.registerDefaults();
      GraphicRegistry.getInstance().registerShortcuts();
      shortcutManager.loadPreferences(prefs);
      shortcutManager.applyToFeatures();

      GraphicRegistry.getInstance()
          .addListener(() -> MeasureTool.viewSetting.applyLabelsOfNewTools(prefs));
      listenGraphicToolProviders();
    } catch (Exception e) {
      LOGGER.error("Error starting Weasis core plugin", e);
    }
  }

  /**
   * Feeds the {@link GraphicRegistry} with the {@link GraphicToolProvider} extensions of the
   * started plugins, and follows the plugins started or stopped afterwards.
   */
  private void listenGraphicToolProviders() {
    WeasisPluginManager pluginManager = getPluginManager();
    for (PluginWrapper plugin : pluginManager.getStartedPlugins()) {
      registerGraphicToolProviders(plugin);
    }
    pluginManager.addPluginStateListener(
        event -> {
          if (event.getPluginState() == PluginState.STARTED) {
            registerGraphicToolProviders(event.getPlugin());
          } else if (event.getPluginState() == PluginState.STOPPED) {
            unregisterGraphicToolProviders(event.getPlugin());
          }
        });
  }

  private void registerGraphicToolProviders(PluginWrapper plugin) {
    List<GraphicToolProvider> providers =
        plugin.getPluginManager().getExtensions(GraphicToolProvider.class, plugin.getPluginId());
    if (!providers.isEmpty()
        && graphicToolProviders.putIfAbsent(plugin.getPluginId(), providers) == null) {
      GraphicRegistry registry = GraphicRegistry.getInstance();
      providers.forEach(registry::register);
    }
  }

  private void unregisterGraphicToolProviders(PluginWrapper plugin) {
    List<GraphicToolProvider> providers = graphicToolProviders.remove(plugin.getPluginId());
    if (providers != null) {
      GraphicRegistry registry = GraphicRegistry.getInstance();
      providers.forEach(registry::unregister);
    }
  }

  public void savePreferences() {
    try {
      saveSystemPreferences();

      // Save preferences
      Preferences prefs = BundlePreferences.getDefaultPreferences(AppProperties.CORE_PLUGIN_ID);
      AbstractInfoLayer.savePreferences(prefs);
      MeasurementProfiles.restoreUserSettings();
      MeasureTool.viewSetting.savePreferences(prefs);
      ShortcutManager.getInstance().savePreferences(prefs);
      prefs.sync();
      ResourceMonitor.getInstance().shutdown();

      Path dataFolder = BundlePreferences.getDataFolder(AppProperties.CORE_PLUGIN_ID);
      Path filePath = dataFolder.resolve("persistence.properties");
      FileUtil.prepareToWriteFile(filePath);
      PropertiesUtil.storeProperties(filePath, getLocalPersistence(), null);
    } catch (Exception e) {
      LOGGER.error("Error stopping Weasis core plugin", e);
    }
  }

  private void initLoggerAndAudit(WProperties properties) {

    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    loggerContext.reset();

    AuditLog.applyConfig(systemPreferences, loggerContext);

    ch.qos.logback.classic.Logger logger =
        (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

    // Audit log for giving statistics about usage of Weasis
    String loggerKey = "audit.log";
    if (properties.getBooleanProperty(loggerKey, false)) {
      String pattern =
          systemPreferences.getProperty(
              AuditLog.LOG_PATTERN,
              "%d{dd.MM.yyyy HH:mm:ss.SSS} *%-5level* %msg" + "%ex{0}%nopex%n");

      PatternLayoutEncoder encoder = AuditLog.getPatternLayoutEncoder(loggerContext, pattern);

      RollingFileAppender<ILoggingEvent> rollingFileAppender =
          AuditLog.getRollingFilesAppender(logger, AuditLog.NAME_AUDIT);
      WProperties p = AuditLog.getAuditProperties();

      AuditLog.updateRollingFilesAppender(rollingFileAppender, loggerContext, p, encoder);
      AuditLog.LOGGER.info("Start audit log session");
    } else {
      logger.detachAppender(AuditLog.NAME_AUDIT);
      if (AuditLog.LOGGER instanceof ch.qos.logback.classic.Logger auditLogger) {
        auditLogger.detachAndStopAllAppenders();
        auditLogger.setLevel(Level.OFF);
      }
    }
  }

  /**
   * Thread safe singleton instance.
   *
   * @return a thread safe singleton instance with a lazy initialization.
   */
  public static UICore getInstance() {
    return Holder.INSTANCE;
  }

  public WeasisPluginManager getPluginManager() {
    return configData.getPluginManager();
  }

  private void readSystemPreferences() {
    systemPreferences.clear();
    if (configData != null) {
      Map<String, String> props = configData.getSystemProps();
      String pkeys = props.get("wp.list");
      if (StringUtil.hasText(pkeys)) {
        for (String key : pkeys.split(",")) {
          systemPreferences.setProperty(key, props.get(key));
          initialSystemPreferences.setProperty(key, props.get("wp.init." + key)); // NON-NLS
        }
        // In case the remote file is empty or has fewer properties than the local file, set a pref
        // to force
        // rewriting both files
        String diffRemote = "wp.init.diff.remote.pref";
        initialSystemPreferences.setProperty(diffRemote, props.get(diffRemote));
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
      if (http.urlConnection() instanceof HttpURLConnection httpURLConnection) {
        NetworkUtil.readResponse(httpURLConnection, urlParameters.headers());
      }
    }
  }

  /**
   * Stores a file remotely to the preference service URL when it is configured. Does nothing if no
   * remote pref URL is set or the session conditions prevent remote storage.
   *
   * @param filePath the local file to upload
   * @param contentType the MIME type of the content (e.g. {@code "application/json"})
   */
  public void storeRemotePref(Path filePath, String contentType) {
    String remotePrefURL = getPrefServiceUrl();
    if (remotePrefURL != null) {
      try {
        if (!isLocalSession() || isStoreLocalSession()) {
          String sURL =
              String.format(
                  "%s?user=%s&profile=%s", // NON-NLS
                  remotePrefURL,
                  getUrlEncoding(AppProperties.WEASIS_USER),
                  getUrlEncoding(AppProperties.WEASIS_PROFILE));
          Map<String, String> map = new HashMap<>();
          map.put("Content-Type", contentType); // NON-NLS
          URLParameters urlParameters = new URLParameters(map, true);
          ClosableURLConnection http = NetworkUtil.getUrlConnection(sURL, urlParameters);
          try (OutputStream out = http.getOutputStream()) {
            Files.copy(filePath, out);
          }
          if (http.urlConnection() instanceof HttpURLConnection httpURLConnection) {
            NetworkUtil.readResponse(httpURLConnection, urlParameters.headers());
          }
        }
      } catch (Exception e) {
        LOGGER.error("Cannot store remote preference for user: {}", AppProperties.WEASIS_USER, e);
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

  /**
   * Handles keyboard-driven tab navigation within {@link StackDockStation} containers only.
   *
   * <ul>
   *   <li>{@code Ctrl+Tab} / {@code Ctrl+Shift+Tab} — cycles tabs within a {@link StackDockStation}
   *       (tabbed views)
   * </ul>
   *
   * <p>Navigation is intentionally restricted to {@link StackDockStation}; other station types such
   * as {@code SplitDockStation} display all dockables simultaneously and do not use tab semantics.
   */
  public static final class StackTabSwitcher implements KeyboardListener {
    @Override
    public DockElement getTreeLocation() {
      return null;
    }

    private final ShortcutTable<Integer> tabShifts =
        new ShortcutTable<Integer>()
            .on(ShortcutManager.ID_DOCKING_NEXT_TAB, 1)
            .on(ShortcutManager.ID_DOCKING_PREV_TAB, -1);

    @Override
    public boolean keyPressed(DockElement element, KeyEvent event) {
      return tabShifts.find(event).map(delta -> shift(element, delta)).orElse(false);
    }

    @Override
    public boolean keyReleased(DockElement element, KeyEvent event) {
      return false;
    }

    @Override
    public boolean keyTyped(DockElement element, KeyEvent event) {
      return false;
    }

    private boolean shift(DockElement element, int delta) {
      StackDockStation stack = resolveStackDockStation(element);
      if (stack == null) {
        return false;
      }
      Dockable focused = stack.getFrontDockable();
      int index = -1;
      for (int i = 0, n = stack.getDockableCount(); i < n; i++) {
        if (stack.getDockable(i) == focused) {
          index = i;
          break;
        }
      }
      if (index != -1) {
        index = Math.floorMod(index + delta, stack.getDockableCount());
        Dockable next = stack.getDockable(index);
        stack.getController().setFocusedDockable(next, true);
        if (next instanceof CommonDockable commonDockable
            && commonDockable.getDockable() instanceof DefaultSingleCDockable single) {
          single.toFront();
        }
        return true;
      }
      return false;
    }

    private static StackDockStation resolveStackDockStation(DockElement element) {
      DockStation asStation = element.asDockStation();
      if (asStation instanceof StackDockStation stack) {
        return stack;
      }
      DockStation container;
      if (asStation != null) {
        container = asStation;
      } else {
        if (element instanceof CommonDockable commonDockable
            && commonDockable.getDockable() instanceof DefaultSingleCDockable single) {
          if (single.intern().getDockParent() instanceof StackDockStation stack) {
            return stack;
          }
        }
        return null;
      }
      Dockable front = container.getFrontDockable();
      if (front != null && front.asDockStation() instanceof StackDockStation stack) {
        return stack;
      }
      return null;
    }
  }

  public String getPrefServiceUrl() {
    return systemPreferences.getProperty("weasis.pref.url");
  }

  public String getConfigServiceUrl() {
    return systemPreferences.getProperty("weasis.config.url");
  }

  public String getStatisticServiceUrl() {
    return systemPreferences.getProperty("weasis.stat.url");
  }

  public boolean isLocalSession() {
    return systemPreferences.getBooleanProperty("weasis.pref.local.session", false);
  }

  public boolean isStoreLocalSession() {
    return systemPreferences.getBooleanProperty("weasis.pref.store.local.session", false);
  }

  public synchronized void saveSystemPreferences() {
    // Set in a popup message of the launcher
    String key = "weasis.accept.disclaimer";
    systemPreferences.setProperty(key, System.getProperty(key));
    key = "weasis.version.release";
    systemPreferences.setProperty(key, System.getProperty(key));

    if (!systemPreferences.equals(initialSystemPreferences)) {
      if (propsFile != null) {
        PropertiesUtil.storeProperties(propsFile.toPath(), systemPreferences, null);
        String remotePrefURL = getPrefServiceUrl();
        if (remotePrefURL != null) {
          try {
            storeLauncherPref(systemPreferences, remotePrefURL);
          } catch (Exception e) {
            LOGGER.error(
                "Cannot store Launcher preference for user: {}", AppProperties.WEASIS_USER, e);
          }
        }
      }
      initialSystemPreferences.clear();
      initialSystemPreferences.putAll(systemPreferences);
    }
  }

  public CControl getDockingControl() {
    return dockingControl;
  }

  public List<Launcher> getDicomLaunchers() {
    return dicomLaunchers;
  }

  public List<Launcher> getOtherLaunchers() {
    return otherLaunchers;
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

  /** The viewer whose tab is selected in the main window. */
  public Optional<ViewerPlugin<?>> getSelectedPlugin() {
    return Optional.ofNullable(selectedPlugin);
  }

  /** Called by the main window only, which owns the selection. */
  public void setSelectedPlugin(ViewerPlugin<?> plugin) {
    this.selectedPlugin = plugin;
  }

  public List<Toolbar> getExplorerPluginToolbars() {
    return explorerPluginToolbars;
  }

  public List<SeriesViewerFactory> getSeriesViewerFactories() {
    return getPluginManager().getExtensions(SeriesViewerFactory.class);
  }

  public CVetoFocusListener getDockingVetoFocus() {
    return dockingVetoFocus;
  }

  public List<? extends Codec<? extends MediaElement>> getCodecPlugins() {
    return getPluginManager().getExtensions(Codec.class).stream()
        .map(codec -> (Codec<? extends MediaElement>) codec)
        .toList();
  }

  public ConfigData getConfigData() {
    return configData;
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
      for (final SeriesViewerFactory factory : getSeriesViewerFactories()) {
        if (clazz.isInstance(factory)) {
          return factory;
        }
      }
    }
    return null;
  }

  public SeriesViewerFactory getViewerFactory(SeriesViewer seriesViewer) {
    if (seriesViewer != null) {
      for (final SeriesViewerFactory factory : getSeriesViewerFactories()) {
        if (factory != null && factory.isViewerCreatedByThisFactory(seriesViewer)) {
          return factory;
        }
      }
    }
    return null;
  }

  public SeriesViewerFactory getViewerFactory(String mimeType) {
    if (mimeType != null) {
      int level = Integer.MAX_VALUE;
      SeriesViewerFactory best = null;
      for (final SeriesViewerFactory f : getSeriesViewerFactories()) {
        if (f != null && f.canReadMimeType(mimeType)) {
          if (f.getLevel() < level) {
            level = f.getLevel();
            best = f;
          }
        }
      }
      return best;
    }
    return null;
  }

  public SeriesViewerFactory getViewerFactory(String[] mimeTypeList) {
    if (mimeTypeList != null && mimeTypeList.length > 0) {
      int level = Integer.MAX_VALUE;
      SeriesViewerFactory best = null;
      for (final SeriesViewerFactory f : getSeriesViewerFactories()) {
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
    return null;
  }

  public List<SeriesViewerFactory> getViewerFactoryList(String[] mimeTypeList) {
    if (mimeTypeList != null && mimeTypeList.length > 0) {
      List<SeriesViewerFactory> plugins = new ArrayList<>();
      for (final SeriesViewerFactory viewerFactory : getSeriesViewerFactories()) {
        if (viewerFactory != null) {
          for (String mime : mimeTypeList) {
            if (viewerFactory.canReadMimeType(mime)) {
              plugins.add(viewerFactory);
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
      GuiExecutor.execute(
          () -> {
            for (final ViewerPlugin<?> viewerPlugin : pluginsToRemove) {
              viewerPlugin.close();
              viewerPlugin.handleFocusAfterClosing();
            }
          });
    }
  }
}
