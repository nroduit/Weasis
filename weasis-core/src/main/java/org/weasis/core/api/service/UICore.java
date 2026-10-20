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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
import org.weasis.core.api.hp.HangingProtocolDocuments;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.net.ClosableURLConnection;
import org.weasis.core.api.net.NetworkUtil;
import org.weasis.core.api.net.URLParameters;
import org.weasis.core.api.setting.CoreSettings;
import org.weasis.core.api.setting.RemoteSettings;
import org.weasis.core.api.setting.Scope;
import org.weasis.core.api.setting.SettingCatalog;
import org.weasis.core.api.setting.SettingEnvironment;
import org.weasis.core.api.setting.Settings;
import org.weasis.core.api.setting.SettingsFileStore;
import org.weasis.core.api.util.DocumentStore;
import org.weasis.core.api.util.LocalUtil;
import org.weasis.core.api.util.ResourceMonitor;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.SiteDocuments;
import org.weasis.core.ui.docking.MiniStrip;
import org.weasis.core.ui.docking.Shell;
import org.weasis.core.ui.docking.SidePanel;
import org.weasis.core.ui.docking.ws.Workspace;
import org.weasis.core.ui.docking.ws.WorkspaceManager;
import org.weasis.core.ui.editor.ScreenRoles;
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

  /** Whether the user documents are mirrored to the remote preference service. */
  /** Mirrors the user settings and documents to the preference service. */
  public static final String P_SYNC = "weasis.pref.sync"; // NON-NLS

  /**
   * @deprecated the 4.8 name of {@link #P_SYNC}, still read
   */
  @Deprecated(since = "5.0")
  public static final String P_SYNC_DOCUMENTS = "weasis.pref.sync.documents"; // NON-NLS

  private static final Logger LOGGER = LoggerFactory.getLogger(UICore.class);
  private final ToolBarContainer toolbarContainer;
  private final List<Launcher> dicomLaunchers;
  private final List<Launcher> otherLaunchers;
  public final List<ViewerPlugin<?>> viewerPlugins;
  private final List<DataExplorerView> explorerPlugins;
  private final List<Toolbar> explorerPluginToolbars;
  private final WorkspaceManager workspaceManager;
  private final SidePanel explorerSidePanel;
  private final SidePanel toolsSidePanel;
  private final MiniStrip miniStrip;
  private volatile ViewerPlugin<?> selectedPlugin; // NOSONAR reference swapped whole

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
    Window mainWindow = retrieveMainWindow();
    if (mainWindow == null || GraphicsEnvironment.isHeadless()) {
      // No GUI available (e.g. unit tests or headless mode): docking stays uninitialized
      this.workspaceManager = null;
      this.explorerSidePanel = null;
      this.toolsSidePanel = null;
      this.miniStrip = null;
    } else {
      this.workspaceManager = new WorkspaceManager(mainWindow);
      this.explorerSidePanel = new SidePanel(SidePanel.Role.EXPLORER);
      this.toolsSidePanel = new SidePanel(SidePanel.Role.TOOLS);
      this.miniStrip = new MiniStrip();
      Shell.register(new Shell(explorerSidePanel, toolsSidePanel, miniStrip));
    }
    this.toolbarContainer = new ToolBarContainer();
    this.configData = retrieveconfigData();
    this.initialSystemPreferences = new WProperties(configData.getSystemProps());
    this.systemPreferences = new WProperties(configData.getSystemProps());
    this.pluginPersistenceMap = new HashMap<>();
    this.localPersistence = new WProperties();
    ScreenRoles.setStore(() -> localPersistence);
    this.viewerPlugins = Collections.synchronizedList(new ArrayList<>());
    this.explorerPlugins = Collections.synchronizedList(new ArrayList<>());
    this.explorerPluginToolbars = Collections.synchronizedList(new ArrayList<>());
    readSystemPreferences();
    String prefDir = systemPreferences.getProperty("weasis.pref.dir"); // NON-NLS
    if (StringUtil.hasText(prefDir)) {
      SettingsFileStore.configureUser(Path.of(prefDir));
    }
    DocumentStore.configure(
        StringUtil.hasText(prefDir) ? Path.of(prefDir) : null,
        Path.of(
            systemPreferences.getProperty(
                "weasis.config.dir", AppProperties.WEASIS_PATH.toString()))); // NON-NLS
    SettingsFileStore.configureMachine(
        Path.of(
            systemPreferences.getProperty(
                "weasis.config.dir", AppProperties.WEASIS_PATH.toString())), // NON-NLS
        AppProperties.WEASIS_PATH.resolve("data")); // NON-NLS
    startRemoteSettings(prefDir);
    HangingProtocolDocuments.configure();
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

    SettingsFileStore.machine().load(AppProperties.CORE_PLUGIN_ID, "", localPersistence);
    Settings.install(
        SettingEnvironment.of(
            systemPreferences,
            localPersistence,
            BundlePreferences::getDefaultPreferences,
            configData.getSystemProps()));
    SettingCatalog.declare(CoreSettings.class);

    this.dicomLaunchers = Launcher.loadLaunchers(Launcher.Type.DICOM);
    this.otherLaunchers = Launcher.loadLaunchers(Launcher.Type.OTHER);
    for (Launcher.Type type : Launcher.Type.values()) {
      SiteDocuments.onReload(type.getFilename(), () -> Launcher.reload(type));
    }

    loadPreferences();
  }

  private static Window retrieveMainWindow() {
    MBeanServer server = ManagementFactory.getPlatformMBeanServer();
    try {
      ObjectName objectName = ObjectName.getInstance("weasis:name=MainWindow"); // NON-NLS
      Object containerObj = server.getAttribute(objectName, "RootPaneContainer");
      if (containerObj instanceof Window window) {
        return window;
      }
    } catch (InstanceNotFoundException ignored) {
    } catch (JMException e) {
      LOGGER.debug("Error while receiving main window", e);
    }
    return null;
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
      flushSettingsWhenPluginsStop();
    } catch (Exception e) {
      LOGGER.error("Error starting Weasis core plugin", e);
    }
  }

  /**
   * Saves the user settings once the last plugin has stopped: plugins write their settings while
   * stopping, and the store's own shutdown hook may run before the launcher stops them.
   */
  private void flushSettingsWhenPluginsStop() {
    WeasisPluginManager pluginManager = getPluginManager();
    pluginManager.addPluginStateListener(
        event -> {
          if (event.getPluginState() == PluginState.STOPPED
              && pluginManager.getStartedPlugins().isEmpty()) {
            SettingsFileStore.user().flushAll();
            SettingsFileStore.machine().flushAll();
            RemoteSettings.getInstance().push();
          }
        });
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
      SettingsFileStore.user().flushAll();
      ResourceMonitor.getInstance().shutdown();

      SettingsFileStore.machine().store(AppProperties.CORE_PLUGIN_ID, "", getLocalPersistence());
      SettingsFileStore.machine().flushAll();
      RemoteSettings.getInstance().push();
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
   * Tells the mirror of the user layer that a document changed.
   *
   * @deprecated the documents are mirrored by {@link RemoteSettings}, which every user document
   *     write already notifies; the arguments are ignored
   */
  @Deprecated(since = "5.0")
  public void storeRemotePref(Path filePath, String contentType) {
    RemoteSettings.getInstance().changed();
  }

  /**
   * Turns on the mirror of the user settings and documents when {@value #P_SYNC} is on, a
   * preference service is set and the session may store remotely, then merges what the service
   * holds before any plugin reads its settings.
   */
  private void startRemoteSettings(String prefDir) {
    boolean on =
        systemPreferences.getBooleanProperty(
            P_SYNC, systemPreferences.getBooleanProperty(P_SYNC_DOCUMENTS, false));
    String url = getPrefServiceUrl();
    if (!on
        || !StringUtil.hasText(url)
        || !StringUtil.hasText(prefDir)
        || (isLocalSession() && !isStoreLocalSession())) {
      LOGGER.debug("The user settings are not mirrored to a preference service");
      return;
    }
    RemoteSettings remote = RemoteSettings.getInstance();
    remote.configure(
        new RemoteSettings.HttpTransport(
            url, AppProperties.WEASIS_USER, AppProperties.WEASIS_PROFILE),
        SettingsFileStore.user(),
        DocumentStore.folder(Scope.USER),
        Path.of(prefDir, RemoteSettings.STATE_FILE));
    SettingsFileStore.user().onSaved(remote::changed);
    DocumentStore.onUserDocumentWritten(remote::changed);
    remote.pull();
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

  public List<Launcher> getDicomLaunchers() {
    return dicomLaunchers;
  }

  public List<Launcher> getOtherLaunchers() {
    return otherLaunchers;
  }

  public WorkspaceManager getWorkspaceManager() {
    return workspaceManager;
  }

  public Workspace getCenterWorkspace() {
    return workspaceManager == null ? null : workspaceManager.mainWorkspace();
  }

  /** The side of that role: explorer (leading) or tools (trailing); null when headless. */
  public SidePanel getSidePanel(SidePanel.Role role) {
    return role == SidePanel.Role.EXPLORER ? explorerSidePanel : toolsSidePanel;
  }

  /** The side on that window side for the current locale; null when headless. */
  public SidePanel getSidePanel(SidePanel.Side side) {
    if (explorerSidePanel == null) {
      return null;
    }
    return explorerSidePanel.getSide() == side ? explorerSidePanel : toolsSidePanel;
  }

  public MiniStrip getMiniStrip() {
    return miniStrip;
  }

  public Window getApplicationWindow() {
    return workspaceManager == null ? null : workspaceManager.mainWindow();
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
