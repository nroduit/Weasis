/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tukaani.xz.XZInputStream;
import org.weasis.plugin.WeasisPluginManager;
import org.weasis.pref.ConfigData;

/**
 * AutoProcessor for PF4J plugin management
 *
 * @author Nicolas Roduit
 */
public class AutoProcessor {
  private static final Logger LOGGER = LoggerFactory.getLogger(AutoProcessor.class);

  /** The property name prefix for the plugin location entries (e.g. {@code weasis.plugins.60}). */
  public static final String PLUGINS_PROP = "weasis.plugins.";

  /** The property limiting which numeric plugin entries are processed for the current profile. */
  public static final String PLUGINS_MAX_LEVEL_PROP = "weasis.plugins.max.level";

  public static final String XZ_COMPRESSION = ".xz";
  public static final String JAR_EXTENSION = ".jar";

  public static final String PLUGIN_STORAGE_CLEAN = "weasis.plugin.cache.clean";
  public static final String PLUGIN_STORAGE_CLEAN_ONFIRSTINIT = "onFirstInit";

  /**
   * Launcher-only system property ({@code -Dweasis.plugin.cache.warmup=true}, not a shipped
   * configuration key): keeps the populated cache across launches so a development ({@code
   * -SNAPSHOT}) build can be benchmarked like a stable release — the startup wipe is skipped and
   * {@code -SNAPSHOT} jars are treated as immutable (reused from the cache instead of re-copied).
   */
  public static final String PLUGIN_CACHE_WARMUP = "weasis.plugin.cache.warmup";

  private AutoProcessor() {}

  /**
   * Downloads and prepares plugins for PF4J from configuration properties.
   *
   * @param configMap Map of configuration properties.
   * @param modulesI18n Internationalization modules
   * @param pluginManager The PF4J plugin manager.
   * @param weasisLoader the WeasisLoader value
   */
  public static void process(
      Map<String, String> configMap,
      Properties modulesI18n,
      WeasisPluginManager pluginManager,
      WeasisLoader weasisLoader) {
    Map<String, String> map = (configMap == null) ? new HashMap<>() : configMap;
    processAutoProperties(map, modulesI18n, pluginManager, weasisLoader);
  }

  /** Processes the {@code weasis.plugins.N} entries and downloads plugins to the cache. */
  private static void processAutoProperties(
      Map<String, String> configMap,
      Properties modulesi18n,
      WeasisPluginManager pluginManager,
      WeasisLoader weasisLoader) {

    LOGGER.info("Processing plugin configuration for PF4J");

    Path pluginsDir = pluginManager.getPluginCachePath();
    Path cacheDir = pluginsDir.getParent();

    boolean warmup = Boolean.getBoolean(PLUGIN_CACHE_WARMUP);

    // Warm-up mode keeps the cache to benchmark a warm start, so it overrides the startup wipe.
    boolean cleanCache =
        !warmup && PLUGIN_STORAGE_CLEAN_ONFIRSTINIT.equals(configMap.get(PLUGIN_STORAGE_CLEAN));

    if (cleanCache) {
      LOGGER.info("Cleaning plugin cache");
      FileUtil.delete(cacheDir);
    }
    if (warmup) {
      LOGGER.info("*PERF* Plugin cache warm-up mode enabled (SNAPSHOT jars treated as cacheable)");
    }

    int maxLevel = Integer.parseInt(configMap.getOrDefault(PLUGINS_MAX_LEVEL_PROP, "300"));

    // Collect all plugin locations from configuration
    Map<String, String> pluginList = new HashMap<>();
    Set<String> configKeys = configMap.keySet();

    for (String key : configKeys) {
      int level = getPluginLevel(key);
      if (level < 0 || level > maxLevel) {
        continue;
      }

      String value = configMap.get(key);
      StringTokenizer st = new StringTokenizer(value, "\" ", true);
      for (String location = nextLocation(st); location != null; location = nextLocation(st)) {
        pluginList.put(getPluginNameFromLocation(location), location);
      }
    }

    weasisLoader.setMax(pluginList.size());

    // Get currently loaded plugins
    Map<String, PluginWrapper> loadedPlugins = new HashMap<>();
    for (PluginWrapper plugin : pluginManager.getPlugins()) {
      loadedPlugins.put(plugin.getPluginId(), plugin);
    }

    boolean httpCache =
        Boolean.TRUE
            .toString()
            .equals(System.getProperty("http.bundle.cache", Boolean.TRUE.toString()));

    int pluginIter = 0;
    Set<String> classpathJars = WeasisPluginManager.classpathJarNames();

    // Download and prepare plugins
    for (Entry<String, String> entry : pluginList.entrySet()) {
      String pluginName = entry.getKey();
      String location = entry.getValue();

      boolean cacheable = isCacheable(pluginName, warmup);

      // Jars shipped on the application classpath (packaged app: common-lib/) are already in the
      // system classloader and must not be duplicated in the per-user cache.
      boolean onClasspath = cacheable && classpathJars.contains(pluginName + JAR_EXTENSION);

      try {
        Path pluginPath = null;
        if (onClasspath) {
          LOGGER.debug("On the application classpath, skipping download: {}", pluginName);
        } else {
          weasisLoader.writeLabel(WeasisLoader.LBL_DOWNLOADING + " " + pluginName);
          pluginPath = downloadPlugin(cacheDir, location, pluginName, httpCache, cacheable);
        }

        if (onClasspath || pluginPath != null) {
          if (pluginPath != null) {
            LOGGER.info("Downloaded plugin: {} to {}", pluginName, pluginPath);
          }
          // Download translation bundles if available
          downloadTranslationPlugin(cacheDir, pluginName, modulesi18n, httpCache, loadedPlugins);
        }

      } catch (Exception ex) {
        if (pluginName.contains(System.getProperty("native.library.spec"))) {
          LOGGER.error("Cannot download native plugin: {}", pluginName, ex);
        } else {
          LOGGER.error("Cannot download plugin: {}", pluginName, ex);
          if (cleanCache) {
            throw new IllegalStateException("A plugin cannot be downloaded", ex);
          }
        }
      } finally {
        pluginIter++;
        weasisLoader.setValue(pluginIter);
      }
    }

    weasisLoader.writeLabel(Messages.getString("AutoProcessor.start"));
  }

  /**
   * Validates if a JAR file is a valid PF4J plugin by checking for plugin descriptor.
   *
   * @param pluginPath Path to the plugin JAR file
   * @return true if valid PF4J plugin, false otherwise
   */
  private static boolean isValidPF4JPlugin(Path pluginPath) {
    try (JarFile jarFile = new JarFile(pluginPath.toFile())) {
      // Check for plugin.properties in META-INF
      var entry = jarFile.getEntry("META-INF/plugin.properties");
      if (entry != null) {
        try (InputStream is = jarFile.getInputStream(entry)) {
          Properties props = new Properties();
          props.load(is);
          String pluginId = props.getProperty("plugin.id");
          if (pluginId != null && !pluginId.trim().isEmpty()) {
            LOGGER.debug("Valid PF4J plugin found with ID: {}", pluginId);
            return true;
          }
        }
      }

      // Check for @Extension annotations in manifest
      Manifest manifest = jarFile.getManifest();
      if (manifest != null) {
        String pluginId = manifest.getMainAttributes().getValue("Plugin-ID");
        if (pluginId != null && !pluginId.trim().isEmpty()) {
          LOGGER.debug("Valid PF4J plugin found with Plugin-ID: {}", pluginId);
          return true;
        }
      }

      LOGGER.warn("No valid PF4J plugin descriptor found in: {}", pluginPath);
      return false;
    } catch (Exception e) {
      LOGGER.error("Error validating plugin: {}", pluginPath, e);
      return false;
    }
  }

  /**
   * Downloads a plugin from the given location to the plugins directory.
   *
   * @param pluginsDir The plugins directory
   * @param location The URL/URI location of the plugin
   * @param pluginName The name of the plugin
   * @param httpCache Whether to use HTTP cache
   * @return The path to the downloaded plugin, or null if failed
   */
  private static Path downloadPlugin(
      Path pluginsDir, String location, String pluginName, boolean httpCache, boolean cacheable)
      throws IOException {

    // Check if it's a compressed plugin
    boolean isCompressed = location.endsWith(XZ_COMPRESSION);
    String actualLocation = isCompressed ? location.substring(0, location.length() - 3) : location;

    // Check if this is a translation file
    boolean isTranslation = pluginName.contains("-i18n");

    // Create target path
    String fileName = pluginName + JAR_EXTENSION;
    Path targetPath = pluginsDir.resolve(ConfigData.F_PLUGIN_FOLDER).resolve(fileName);

    if (cacheable) {
      if (Files.exists(targetPath)) {
        LOGGER.debug("Plugin already exists in cache: {}", targetPath);
        return targetPath;
      } else {
        Path otherPath =
            pluginsDir
                .resolve(isTranslation ? ConfigData.F_TRANSLATION_FOLDER : ConfigData.F_LIB_FOLDER)
                .resolve(fileName);
        if (Files.exists(otherPath)) {
          targetPath = otherPath;
          LOGGER.debug("File already exists in cache: {}", targetPath);
          return targetPath;
        }
      }
    }

    // Create tmp directory for downloading
    Path tmpDir = pluginsDir.resolve("tmp");
    Files.createDirectories(tmpDir);
    Path tmpPath = tmpDir.resolve(fileName);

    // Download the plugin
    try {
      if (isCompressed) {
        // Download compressed version
        URL url = new URL(location);
        try (InputStream input =
            new XZInputStream(FileUtil.getAdaptedConnection(url, httpCache).getInputStream()); ) {
          Files.copy(input, tmpPath, StandardCopyOption.REPLACE_EXISTING);
          LOGGER.info("Downloaded compressed plugin to tmp: {}", fileName);
        }
      } else {
        // Download regular JAR
        URL url = new URI(actualLocation).toURL();
        try (InputStream input = FileUtil.getAdaptedConnection(url, httpCache).getInputStream()) {
          Files.copy(input, tmpPath, StandardCopyOption.REPLACE_EXISTING);
          LOGGER.info("Downloaded plugin to tmp: {}", fileName);
        }
      }

      // Validate the downloaded plugin
      if (!isValidPF4JPlugin(tmpPath)) {
        // Determine target directory based on file type
        Path targetDir =
            pluginsDir.resolve(
                isTranslation ? ConfigData.F_TRANSLATION_FOLDER : ConfigData.F_LIB_FOLDER);
        try {
          Files.createDirectories(targetDir);
          Path finalPath = targetDir.resolve(fileName);
          Files.move(tmpPath, finalPath, StandardCopyOption.REPLACE_EXISTING);
          LOGGER.info(
              "Stored non-plugin to {} directory: {}",
              isTranslation ? "translation" : "lib",
              finalPath);
          return finalPath;
        } catch (IOException e) {
          LOGGER.error(
              "Failed to move file from tmp to {} directory: {}",
              isTranslation ? "translation" : "lib",
              tmpPath,
              e);
          FileUtil.delete(tmpPath);
        }
        return null;
      }

      // Move from tmp to final destination
      Files.createDirectories(targetPath.getParent());
      Files.move(tmpPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
      LOGGER.info("Stored plugin to: {}", targetPath);

      return targetPath;
    } catch (Exception e) {
      LOGGER.error("Failed to download plugin from: {}", location, e);
      FileUtil.delete(tmpPath);
      return null;
    }
  }

  /**
   * Downloads translation plugin if available.
   *
   * @param pluginsDir The plugins directory
   * @param pluginName The base plugin name
   * @param modulesi18n Translation modules properties
   * @param httpCache Whether to use HTTP cache
   * @param loadedPlugins Currently loaded plugins
   */
  private static void downloadTranslationPlugin(
      Path pluginsDir,
      String pluginName,
      Properties modulesi18n,
      boolean httpCache,
      Map<String, PluginWrapper> loadedPlugins) {

    if (modulesi18n.isEmpty()) {
      return;
    }

    String i18nFileName = pluginName + "-i18n.jar.xz"; // NON-NLS
    String versionQualifier = modulesi18n.getProperty(i18nFileName);

    if (versionQualifier != null) {
      String baseURL = System.getProperty(ConfigData.P_WEASIS_I18N);
      if (baseURL != null) {
        String uri = baseURL + (baseURL.endsWith("/") ? i18nFileName : "/" + i18nFileName);
        String i18nPluginName = pluginName + "-i18n";

        try {
          // Check if translation plugin needs update
          PluginWrapper existingPlugin = loadedPlugins.get(i18nPluginName);
          boolean needsDownload = true;

          if (existingPlugin != null) {
            String existingVersion = existingPlugin.getDescriptor().getVersion();
            needsDownload = !versionQualifier.equals(existingVersion);
          }

          if (needsDownload) {
            downloadPlugin(pluginsDir, uri, i18nPluginName, httpCache, true);
            LOGGER.info("Downloaded translation plugin: {}", i18nPluginName);
          }

        } catch (Exception e) {
          LOGGER.error("Cannot download translation plugin: {}", uri, e);
        }
      }
    }
  }

  /**
   * Extracts plugin name from location URL/path.
   *
   * @param location The plugin location
   * @return The plugin name without extension
   */
  /**
   * A jar is treated as immutable — its cached copy is reused instead of re-downloaded — when it is
   * a fixed release version, or when warm-up mode forces even {@code -SNAPSHOT} jars to be cached.
   */
  static boolean isCacheable(String pluginName, boolean warmup) {
    return warmup || !pluginName.endsWith("-SNAPSHOT");
  }

  static String getPluginNameFromLocation(String location) {
    if (location != null) {
      int index = location.lastIndexOf('/');
      String name = index >= 0 ? location.substring(index + 1) : location;

      // Remove .jar extension
      index = name.lastIndexOf(JAR_EXTENSION);
      if (index >= 0) {
        name = name.substring(0, index);
      }

      // Remove .xz extension if present
      index = name.lastIndexOf(XZ_COMPRESSION);
      if (index >= 0) {
        name = name.substring(0, index);
      }

      return name;
    }
    return null;
  }

  /**
   * Parses the next location from a string tokenizer.
   *
   * @param st The string tokenizer
   * @return The next location string, or null if none
   */
  private static String nextLocation(StringTokenizer st) {
    String retVal = null;

    if (st.countTokens() > 0) {
      String tokenList = "\" ";
      StringBuilder tokBuf = new StringBuilder(10);
      boolean inQuote = false;
      boolean tokStarted = false;
      boolean exit = false;

      while (st.hasMoreTokens() && !exit) {
        String tok = st.nextToken(tokenList);
        if (tok.equals("\"")) {
          inQuote = !inQuote;
          tokenList = inQuote ? "\"" : "\" ";
        } else if (tok.equals(" ")) {
          if (tokStarted) {
            retVal = tokBuf.toString();
            tokStarted = false;
            tokBuf = new StringBuilder(10);
            exit = true;
          }
        } else {
          tokStarted = true;
          tokBuf.append(tok.trim());
        }
      }

      // Handle case where end of token stream and still got data
      if (!exit && tokStarted) {
        retVal = tokBuf.toString();
      }

      if (Utils.hasText(retVal)) {
        retVal = Utils.adaptPathToUri(retVal);
      }
    }

    return retVal;
  }

  /**
   * Returns the numeric level of a {@code weasis.plugins.N} key, or -1 when the key is not a
   * numeric plugin entry (e.g. {@link #PLUGINS_MAX_LEVEL_PROP}).
   */
  static int getPluginLevel(String key) {
    if (key == null || !key.toLowerCase().startsWith(PLUGINS_PROP)) {
      return -1;
    }
    String suffix = key.substring(PLUGINS_PROP.length());
    if (suffix.isEmpty() || !suffix.chars().allMatch(Character::isDigit)) {
      return -1;
    }
    try {
      return Integer.parseInt(suffix);
    } catch (NumberFormatException e) {
      return -1;
    }
  }
}
