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

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.util.ClosableURLConnection;
import org.weasis.core.api.util.LocalUtil;
import org.weasis.core.api.util.NetworkUtil;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.URLParameters;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.StringUtil;

public class BundleTools {
  private static final Logger LOGGER = LoggerFactory.getLogger(BundleTools.class);

  public static final String P_FORMAT_CODE = "locale.format.code";

  public static final Map<String, String> SESSION_TAGS_MANIFEST = new HashMap<>(3);
  public static final Map<String, String> SESSION_TAGS_FILE = new HashMap<>(3);

  static {
    Properties properties = System.getProperties();
    for (String key : properties.stringPropertyNames()) {
      if (key.startsWith("TGM-")) { // NON-NLS
        SESSION_TAGS_MANIFEST.put(key.substring(4), properties.getProperty(key));
      } else if (key.startsWith("TGF-")) { // NON-NLS
        SESSION_TAGS_FILE.put(key.substring(4), properties.getProperty(key));
      }
    }
  }

  public static final String CONFIRM_CLOSE = "weasis.confirm.closing";
  public static final String LINUX_WINDOWS_DECORATION = "weasis.linux.windows.decoration";

  public static final List<Codec> CODEC_PLUGINS = Collections.synchronizedList(new ArrayList<>());
  /**
   * This the persistence used at launch which can be stored remotely. These are the preferences
   * necessary for launching unlike the preferences associated with the plugins.
   */
  public static final WProperties SYSTEM_PREFERENCES = new WProperties();
  /**
   * This the common local persistence for UI. It should be used only for preferences for which
   * remote storage makes no sense.
   */
  public static final WProperties LOCAL_UI_PERSISTENCE = new WProperties();

  private static final WProperties INIT_SYSTEM_PREFERENCES = new WProperties();
  private static final File propsFile;

  static {
    String prefPath = BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.pref.dir");
    propsFile = new File(prefPath, "weasis.properties");
    readSystemPreferences();

    if (!propsFile.canRead()) {
      try {
        if (!propsFile.createNewFile()) {
          LOGGER.warn("File already exist {}", propsFile.getPath());
        }
      } catch (IOException e) {
        LOGGER.error("Cannot write {}", propsFile.getPath(), e);
      }
    }
    String code = BundleTools.SYSTEM_PREFERENCES.getProperty(BundleTools.P_FORMAT_CODE);
    if (StringUtil.hasLength(code)) {
      Locale l = LocalUtil.textToLocale(code);
      if (!l.equals(Locale.getDefault())) {
        LocalUtil.setLocaleFormat(l);
      }
    }

    String path = BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.resources.path");
    ResourceUtil.setResourcePath(path);
  }

  private BundleTools() {}

  public static Codec getCodec(String mimeType, String preferredCodec) {
    Codec codec = null;
    synchronized (BundleTools.CODEC_PLUGINS) {
      for (Codec c : BundleTools.CODEC_PLUGINS) {
        if (c.isMimeTypeSupported(mimeType)) {
          if (c.getCodecName().equals(preferredCodec)) {
            codec = c;
            break;
          }
          // If the preferred codec cannot be found, the first-found codec is retained
          if (codec == null) {
            codec = c;
          }
        }
      }
      return codec;
    }
  }

  private static void readSystemPreferences() {
    SYSTEM_PREFERENCES.clear();
    BundleContext context = AppProperties.getBundleContext();
    if (context != null) {
      String pkeys = context.getProperty("wp.list");
      if (StringUtil.hasText(pkeys)) {
        for (String key : pkeys.split(",")) {
          SYSTEM_PREFERENCES.setProperty(key, context.getProperty(key));
          INIT_SYSTEM_PREFERENCES.setProperty(
              key, context.getProperty("wp.init." + key)); // NON-NLS
        }
        // In case the remote file is empty or has fewer properties than the local file, set a pref
        // to force
        // rewriting both files
        String diffRemote = "wp.init.diff.remote.pref";
        INIT_SYSTEM_PREFERENCES.setProperty(diffRemote, context.getProperty(diffRemote));
        saveSystemPreferences();
      }
    }
  }

  public static synchronized void saveSystemPreferences() {
    // Set in a popup message of the launcher
    String key = "weasis.accept.disclaimer";
    SYSTEM_PREFERENCES.setProperty(key, System.getProperty(key));
    key = "weasis.version.release";
    SYSTEM_PREFERENCES.setProperty(key, System.getProperty(key));

    if (!SYSTEM_PREFERENCES.equals(INIT_SYSTEM_PREFERENCES)) {
      FileUtil.storeProperties(propsFile, SYSTEM_PREFERENCES, null);
      String remotePrefURL = getPrefServiceUrl();
      if (remotePrefURL != null) {
        try {
          storeLauncherPref(SYSTEM_PREFERENCES, remotePrefURL);
        } catch (Exception e) {
          LOGGER.error(
              "Cannot store Launcher preference for user: {}", AppProperties.WEASIS_USER, e);
        }
      }
      INIT_SYSTEM_PREFERENCES.clear();
      INIT_SYSTEM_PREFERENCES.putAll(SYSTEM_PREFERENCES);
    }
  }

  public static String getPrefServiceUrl() {
    return BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.pref.url");
  }

  public static String getConfigServiceUrl() {
    return BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.config.url");
  }

  public static boolean isLocalSession() {
    return BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.pref.local.session", false);
  }

  public static boolean isStoreLocalSession() {
    return BundleTools.SYSTEM_PREFERENCES.getBooleanProperty(
        "weasis.pref.store.local.session", false);
  }

  public static String getEncodedValue(String val) {
    return URLEncoder.encode(val, StandardCharsets.UTF_8);
  }

  private static void storeLauncherPref(Properties props, String remotePrefURL) throws IOException {
    if (!isLocalSession() || isStoreLocalSession()) {
      String sURL =
          String.format(
              "%s?user=%s&profile=%s", // NON-NLS
              remotePrefURL,
              getEncodedValue(AppProperties.WEASIS_USER),
              getEncodedValue(AppProperties.WEASIS_PROFILE));
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

  private static URLParameters getURLParameters(boolean post) {
    Map<String, String> map = new HashMap<>(BundleTools.SESSION_TAGS_FILE);
    map.put(post ? "Content-Type" : "Accept", "text/x-java-properties"); // NON-NLS
    return new URLParameters(map, post);
  }
}
