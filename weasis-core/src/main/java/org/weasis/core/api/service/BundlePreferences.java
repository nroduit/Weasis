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

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.BackingStoreException;
import java.util.prefs.InvalidPreferencesFormatException;
import java.util.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.net.ClosableURLConnection;
import org.weasis.core.api.net.NetworkUtil;
import org.weasis.core.api.net.URLParameters;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.MathUtil;
import org.weasis.core.util.StringUtil;

public class BundlePreferences {
  private static final Logger LOGGER = LoggerFactory.getLogger(BundlePreferences.class);

  private static final String PREFERENCES_NODE = "org/weasis";

  private BundlePreferences() {}

  /**
   * Gets a path to the data folder of the given plugin.
   *
   * @param pluginId the plugin ID
   * @return Path to the data folder for the plugin, or the default data folder if context is null
   */
  public static Path getDataFolder(String pluginId) {
    Path dataFolder;
    if (StringUtil.hasText(pluginId)) {
      dataFolder = AppProperties.WEASIS_PATH.resolve("data").resolve(pluginId);
    } else {
      dataFolder = AppProperties.WEASIS_PATH.resolve("data").resolve(AppProperties.CORE_PLUGIN_ID);
    }
    try {
      Files.createDirectories(dataFolder);
    } catch (IOException e) {
      // Log error but continue, return the path anyway
      LOGGER.warn("Failed to create data folder: {}", dataFolder, e);
    }
    return dataFolder;
  }

  /**
   * Gets a file path within the data folder for the given plugin.
   *
   * @param pluginId the plugin ID
   * @param filename the filename to append to the data folder path
   * @return Path to the file within the data folder
   */
  public static Path getFileInDataFolder(String pluginId, String filename) {
    Path dataFolder = getDataFolder(pluginId);
    return dataFolder.resolve(filename);
  }

  public static Preferences getDefaultPreferences(String pluginId) {
    return getUserPreferences(pluginId, AppProperties.WEASIS_USER);
  }

  public static Preferences getUserPreferences(String pluginId, String name) {
    if (pluginId != null) {
      String user = name == null ? AppProperties.WEASIS_USER : name;
      Preferences root = Preferences.userRoot();
      return root.node(PREFERENCES_NODE + "/" + pluginId);
    }
    return null;
  }

  /**
   * Loads preferences from both local file and remote service, merging them if both exist. Remote
   * preferences take precedence over local ones.
   *
   * @param pluginId the plugin ID
   * @param localFile the local file path
   * @return the merged Preferences, or null if neither source is available
   */
  public static Preferences load(String pluginId, Path localFile) {
    Preferences localPrefs = null;
    Preferences remotePrefs = null;

    try {
      localPrefs = loadFromFile(pluginId, localFile);
      remotePrefs = loadFromService(pluginId);
    } catch (Exception e) {
      LOGGER.error("Error loading preferences for plugin: {}", pluginId, e);
    }

    // Merge preferences if remote exists
    if (remotePrefs != null) {
      if (localPrefs == null) {
        localPrefs = getUserPreferences(pluginId, AppProperties.WEASIS_USER);
      }
      mergePreferences(localPrefs, remotePrefs);
    }

    return localPrefs;
  }

  /**
   * Loads preferences from a local file.
   *
   * @param pluginId the plugin ID
   * @param file the file path
   * @return loaded Preferences, or null if file doesn't exist
   */
  private static Preferences loadFromFile(String pluginId, Path file) {
    if (file != null && Files.exists(file)) {
      try (InputStream is = new FileInputStream(file.toFile())) {
        Preferences.importPreferences(is);
        LOGGER.info("Loaded preferences from file: {}", file);
        return getUserPreferences(pluginId, AppProperties.WEASIS_USER);
      } catch (IOException | InvalidPreferencesFormatException e) {
        LOGGER.error("Unable to load preferences from file: {}", file, e);
      }
    }
    return null;
  }

  /**
   * Loads preferences from a remote service.
   *
   * @param pluginId the plugin ID
   * @return loaded Preferences from remote service, or null if not available
   */
  private static Preferences loadFromService(String pluginId) {
    String prefUrl = GuiUtils.getUICore().getPrefServiceUrl();

    if (!StringUtil.hasText(prefUrl)) {
      return null;
    }

    // Check if we should use remote preferences
    if (GuiUtils.getUICore().isLocalSession() && !GuiUtils.getUICore().isStoreLocalSession()) {
      return null;
    }

    try {
      String serviceURL = buildServiceUrl(pluginId, prefUrl);
      if (serviceURL == null) {
        return null;
      }

      try (ClosableURLConnection conn =
          NetworkUtil.getUrlConnection(serviceURL, getURLParameters(false))) {
        return loadFromRemoteConnection(pluginId, conn);
      }
    } catch (IOException e) {
      LOGGER.warn("Unable to load preferences from remote service: {}", prefUrl, e);
    }
    return null;
  }

  /** Loads preferences from a remote connection. */
  private static Preferences loadFromRemoteConnection(String pluginId, ClosableURLConnection conn)
      throws IOException {
    try (InputStream is = conn.getInputStream()) {
      // Check for HTTP 204 No Content
      if (conn.urlConnection() instanceof HttpURLConnection httpConn) {
        if (httpConn.getResponseCode() == HttpURLConnection.HTTP_NO_CONTENT) {
          LOGGER.debug("No remote preferences available (HTTP 204)");
          return null;
        }
      }

      Preferences.importPreferences(is);
      LOGGER.info("Loaded preferences from remote service");
      return getUserPreferences(pluginId, AppProperties.WEASIS_USER);
    } catch (InvalidPreferencesFormatException e) {
      LOGGER.error("Invalid preference format from remote service", e);
      return null;
    }
  }

  /** Builds the service URL for remote preferences. */
  private static String buildServiceUrl(String pluginId, String prefUrl) {
    if (!StringUtil.hasText(prefUrl) || !StringUtil.hasText(pluginId)) {
      return null;
    }

    return String.format(
        "%s?user=%s&profile=%s&module=%s",
        prefUrl,
        UICore.getUrlEncoding(AppProperties.WEASIS_USER),
        UICore.getUrlEncoding(AppProperties.WEASIS_PROFILE),
        UICore.getUrlEncoding(pluginId));
  }

  /**
   * Merges remote preferences into local preferences. Remote values override local values if
   * different.
   *
   * @param local the local preferences to update
   * @param remote the remote preferences to merge from
   */
  private static void mergePreferences(Preferences local, Preferences remote) {
    if (local == null || remote == null) {
      return;
    }

    try {
      // Merge keys from remote into local
      for (String key : remote.keys()) {
        String remoteValue = remote.get(key, null);
        String localValue = local.get(key, null);

        if (remoteValue != null && !remoteValue.equals(localValue)) {
          local.put(key, remoteValue);
        }
      }

      // Recursively merge child nodes
      for (String childName : remote.childrenNames()) {
        Preferences remoteChild = remote.node(childName);
        Preferences localChild = local.node(childName);
        mergePreferences(localChild, remoteChild);
      }
    } catch (BackingStoreException e) {
      LOGGER.error("Error merging preferences", e);
    }
  }

  /**
   * Saves preferences to both local file and optionally to remote service.
   *
   * @param prefs the preferences to save
   * @param file the local file path
   * @throws IOException if an I/O error occurs
   * @throws BackingStoreException if preferences cannot be saved
   */
  public static void save(Preferences prefs, Path file) throws IOException, BackingStoreException {

    // Save to local file
    FileUtil.prepareToWriteFile(file);
    try (OutputStream os = new FileOutputStream(file.toFile())) {
      prefs.exportSubtree(os);
      prefs.flush();
      LOGGER.info("Saved preferences to file: {}", file);
    }

    // Save to remote service if configured
    saveToRemoteService(prefs, file);
  }

  /** Saves preferences to remote service if configured and conditions are met. */
  private static void saveToRemoteService(Preferences prefs, Path file) {
    String prefUrl = GuiUtils.getUICore().getPrefServiceUrl();

    if (!StringUtil.hasText(prefUrl)) {
      return;
    }

    // Check if we should save to remote
    if (GuiUtils.getUICore().isLocalSession() && !GuiUtils.getUICore().isStoreLocalSession()) {
      return;
    }

    try {
      String pluginId = extractPluginIdFromPreferences(prefs);
      String serviceURL = buildServiceUrl(pluginId, prefUrl);

      if (serviceURL == null || !Files.exists(file)) {
        return;
      }

      URLParameters urlParams = getURLParameters(true);
      try (ClosableURLConnection conn = NetworkUtil.getUrlConnection(serviceURL, urlParams);
          OutputStream out = conn.getOutputStream();
          InputStream in = new FileInputStream(file.toFile())) {

        // Upload the file content
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
          out.write(buffer, 0, bytesRead);
        }
        out.flush();

        // Check response
        if (conn.urlConnection() instanceof HttpURLConnection httpConn) {
          NetworkUtil.readResponse(httpConn, urlParams.headers());
        }

        LOGGER.info("Saved preferences to remote service");
      }
    } catch (IOException e) {
      LOGGER.error("Failed to save preferences to remote service", e);
    }
  }

  /** Extracts plugin ID from preferences node path. */
  private static String extractPluginIdFromPreferences(Preferences prefs) {
    String path = prefs.absolutePath();
    // Extract from path like /org/weasis/pluginId
    String[] parts = path.split("/");
    if (parts.length >= 4 && PREFERENCES_NODE.equals(parts[1] + "/" + parts[2])) {
      return parts[3];
    }
    return AppProperties.CORE_PLUGIN_ID;
  }

  private static URLParameters getURLParameters(boolean post) {
    Map<String, String> map = new HashMap<>();
    map.put(post ? "Content-Type" : "Accept", "text/plain"); // NON-NLS
    return new URLParameters(map, post);
  }

  public static void putStringPreferences(Preferences pref, String key, String value) {
    // Does not support null key or value
    if (pref != null && key != null && value != null) {
      String val2 = pref.get(key, null);
      // Update only if the value is different to avoid setting the changeSet to true
      if (!value.equals(val2)) {
        pref.put(key, value);
      }
    }
  }

  public static void putBooleanPreferences(Preferences pref, String key, boolean value) {
    // Does not support null key
    if (pref != null && key != null) {
      Boolean result = null;
      final String s = pref.get(key, null);
      if (s != null) {
        result = Boolean.valueOf(s);
      }
      // Update only if the value is different to avoid setting the changeSet to true
      if (result == null || result != value) {
        pref.putBoolean(key, value);
      }
    }
  }

  public static void putByteArrayPreferences(Preferences pref, String key, byte[] value) {
    // Does not support null key or value
    if (pref != null && key != null && value != null) {
      byte[] val2 = pref.getByteArray(key, null);
      // Update only if the value is different to avoid setting the changeSet to true
      if (val2 == null || !Arrays.equals(value, val2)) {
        pref.putByteArray(key, value);
      }
    }
  }

  public static void putDoublePreferences(Preferences pref, String key, double value) {
    // Does not support null key
    if (pref != null && key != null) {
      Double result = null;
      final String s = pref.get(key, null);
      if (s != null) {
        try {
          result = Double.parseDouble(s);
        } catch (NumberFormatException ignore) {
          LOGGER.error("Cannot parse {} into double", s);
        }
      }
      // Update only if the value is different to avoid setting the changeSet to true
      if (result == null || !MathUtil.isEqual(result, value)) {
        pref.putDouble(key, value);
      }
    }
  }

  public static void putFloatPreferences(Preferences pref, String key, float value) {
    // Does not support null key
    if (pref != null && key != null) {
      Float result = null;
      final String s = pref.get(key, null);
      if (s != null) {
        try {
          result = Float.parseFloat(s);
        } catch (NumberFormatException ignore) {
          LOGGER.error("Cannot parse {} into float", s);
        }
      }
      // Update only if the value is different to avoid setting the changeSet to true
      if (result == null || !MathUtil.isEqual(result, value)) {
        pref.putFloat(key, value);
      }
    }
  }

  public static void putIntPreferences(Preferences pref, String key, int value) {
    // Does not support null key
    if (pref != null && key != null) {
      Integer result = null;
      final String s = pref.get(key, null);
      if (s != null) {
        try {
          result = Integer.parseInt(s);
        } catch (NumberFormatException ignore) {
          LOGGER.error("Cannot parse {} into int", s);
        }
      }
      // Update only if the value is different to avoid setting the changeSet to true
      if (result == null || result != value) {
        pref.putInt(key, value);
      }
    }
  }

  public static void putLongPreferences(Preferences pref, String key, long value) {
    // Does not support null key
    if (pref != null && key != null) {
      Long result = null;
      final String s = pref.get(key, null);
      if (s != null) {
        try {
          result = Long.parseLong(s);
        } catch (NumberFormatException ignore) {
          LOGGER.error("Cannot parse {} into long", s);
        }
      }
      // Update only if the value is different to avoid setting the changeSet to true
      if (result == null || result != value) {
        pref.putLong(key, value);
      }
    }
  }
}
