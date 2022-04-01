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

import java.io.File;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.service.startlevel.StartLevel;
import org.tukaani.xz.XZInputStream;

/**
 * @author Richard S. Hall
 * @author Nicolas Roduit
 */
public class AutoProcessor {

  private static final Logger LOGGER = System.getLogger(AutoProcessor.class.getName());

  /** The property name used for the bundle directory. */
  public static final String AUTO_DEPLOY_DIR_PROPERTY = "felix.auto.deploy.dir";
  /** The default name used for the bundle directory. */
  public static final String AUTO_DEPLOY_DIR_VALUE = "bundle"; // NON-NLS
  /** The property name used to specify auto-deploy actions. */
  public static final String AUTO_DEPLOY_ACTION_PROPERTY = "felix.auto.deploy.action";
  /** The property name used to specify auto-deploy start level. */
  public static final String AUTO_DEPLOY_STARTLEVEL_PROPERTY = "felix.auto.deploy.startlevel";
  /** The name used for the auto-deploy install action. */
  public static final String AUTO_DEPLOY_INSTALL_VALUE = "install"; // NON-NLS
  /** The name used for the auto-deploy start action. */
  public static final String AUTO_DEPLOY_START_VALUE = "start"; // NON-NLS
  /** The name used for the auto-deploy update action. */
  public static final String AUTO_DEPLOY_UPDATE_VALUE = "update"; // NON-NLS
  /** The name used for the auto-deploy uninstall action. */
  public static final String AUTO_DEPLOY_UNINSTALL_VALUE = "uninstall"; // NON-NLS
  /** The property name prefix for the launcher's auto-install property. */
  public static final String AUTO_INSTALL_PROP = "felix.auto.install";
  /** The property name prefix for the launcher's auto-start property. */
  public static final String AUTO_START_PROP = "felix.auto.start";

  public static final String XZ_COMPRESSION = ".xz";

  private AutoProcessor() {}

  /**
   * Used to instigate auto-deploy directory process and auto-install/auto-start configuration
   * property processing during.
   *
   * @param configMap Map of configuration properties.
   * @param context The system bundle context.
   * @param weasisLoader the WeasisLoader value
   */
  public static void process(
      Map<String, String> configMap,
      Properties modulesi18n,
      BundleContext context,
      WeasisLoader weasisLoader) {
    Map<String, String> map = (configMap == null) ? new HashMap<>() : configMap;
    processAutoDeploy(map, context, weasisLoader);
    processAutoProperties(map, modulesi18n, context, weasisLoader);
  }

  /** Processes bundles in the auto-deploy directory, performing the specified deploy actions. */
  private static void processAutoDeploy(
      Map<String, String> configMap, BundleContext context, WeasisLoader weasisLoader) {
    // Determine if auto deploy actions to perform.
    String action = configMap.get(AUTO_DEPLOY_ACTION_PROPERTY);
    action = (action == null) ? "" : action;
    List<String> actionList = new ArrayList<>();
    StringTokenizer st = new StringTokenizer(action, ",");
    while (st.hasMoreTokens()) {
      String s = st.nextToken().trim().toLowerCase();
      if (s.equals(AUTO_DEPLOY_INSTALL_VALUE)
          || s.equals(AUTO_DEPLOY_START_VALUE)
          || s.equals(AUTO_DEPLOY_UPDATE_VALUE)
          || s.equals(AUTO_DEPLOY_UNINSTALL_VALUE)) {
        actionList.add(s);
      }
    }

    // Perform auto-deploy actions.
    if (!actionList.isEmpty()) {
      // Retrieve the Start Level service, since it will be needed
      // to set the start level of the installed bundles.
      StartLevel sl =
          (StartLevel)
              context.getService(
                  context.getServiceReference(
                      org.osgi.service.startlevel.StartLevel.class.getName()));

      // Get start level for auto-deploy bundles.
      int startLevel = sl.getInitialBundleStartLevel();
      if (configMap.get(AUTO_DEPLOY_STARTLEVEL_PROPERTY) != null) {
        try {
          startLevel = Integer.parseInt(configMap.get(AUTO_DEPLOY_STARTLEVEL_PROPERTY));
        } catch (NumberFormatException ex) {
          // Ignore and keep default level.
        }
      }

      // Get list of already installed bundles as a map.
      Map<String, Bundle> installedBundleMap = new HashMap<>();
      Bundle[] bundles = context.getBundles();
      for (Bundle bundle : bundles) {
        installedBundleMap.put(bundle.getLocation(), bundle);
      }

      // Get the auto deploy directory.
      String autoDir = configMap.get(AUTO_DEPLOY_DIR_PROPERTY);
      autoDir = (autoDir == null) ? AUTO_DEPLOY_DIR_VALUE : autoDir;
      // Look in the specified bundle directory to create a list
      // of all JAR files to install.
      File[] files = new File(autoDir).listFiles();
      List<File> jarList = new ArrayList<>();
      if (files != null) {
        Arrays.sort(files);
        for (File file : files) {
          if (file.getName().endsWith(".jar")) {
            jarList.add(file);
          }
        }
      }
      weasisLoader.setMax(jarList.size());

      boolean cache =
          Boolean.TRUE
              .toString()
              .equals(System.getProperty("http.bundle.cache", Boolean.TRUE.toString()));
      // Install bundle JAR files and remember the bundle objects.
      final List<Bundle> startBundleList = new ArrayList<>();
      for (int i = 0; i < jarList.size(); i++) {
        // Look up the bundle by location, removing it from
        // the map of installed bundles so the remaining bundles
        // indicate which bundles may need to be uninstalled.
        File jar = jarList.get(i);
        Bundle b = installedBundleMap.remove((jar).toURI().toString());
        try {
          weasisLoader.writeLabel(WeasisLoader.LBL_DOWNLOADING + " " + jar.getName());

          // If the bundle is not already installed, then install it
          // if the 'install' action is present.
          if ((b == null) && actionList.contains(AUTO_DEPLOY_INSTALL_VALUE)) {
            b = installBundle(context, jarList.get(i).toURI().toString(), cache);
          }
          // If the bundle is already installed, then update it
          // if the 'update' action is present.
          else if (b != null && actionList.contains(AUTO_DEPLOY_UPDATE_VALUE)) {
            b.update();
          }

          // If we have found and/or successfully installed a bundle,
          // then add it to the list of bundles to potentially start
          // and also set its start level accordingly.
          if (b != null) {
            weasisLoader.setValue(i + 1);
            if (!isFragment(b)) {
              startBundleList.add(b);
              sl.setBundleStartLevel(b, startLevel);
            }
          }

        } catch (Exception ex) {
          LOGGER.log(
              Level.ERROR,
              () -> String.format("Auto-deploy install %s", jar.getName()), // NON-NLS
              ex);
          if (!Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT.equals(
              configMap.get(Constants.FRAMEWORK_STORAGE_CLEAN))) {
            // Reset all the old cache
            throw new IllegalStateException("A bundle cannot be started");
          }
        }
      }

      // Uninstall all bundles not in the auto-deploy directory if
      // the 'uninstall' action is present.
      if (actionList.contains(AUTO_DEPLOY_UNINSTALL_VALUE)) {
        for (Entry<String, Bundle> entry : installedBundleMap.entrySet()) {
          Bundle b = entry.getValue();
          if (b.getBundleId() != 0) {
            try {
              b.uninstall();
            } catch (BundleException ex) {
              LOGGER.log(
                  Level.ERROR,
                  () ->
                      String.format(
                          "Auto-deploy uninstall bundle %s", b.getSymbolicName()), // NON-NLS
                  ex);
            }
          }
        }
      }

      // Start all installed and/or updated bundles if the 'start'
      // action is present.
      if (actionList.contains(AUTO_DEPLOY_START_VALUE)) {
        for (Bundle b : startBundleList) {
          try {
            b.start();
          } catch (BundleException ex) {
            LOGGER.log(
                Level.ERROR,
                () ->
                    String.format("Auto-deploy install bundle %s", b.getSymbolicName()), // NON-NLS
                ex);
          }
        }
      }
    }
  }

  /**
   * Processes the auto-installation and auto-start properties from the specified configuration
   * properties.
   */
  private static void processAutoProperties(
      Map<String, String> configMap,
      Properties modulesi18n,
      BundleContext context,
      WeasisLoader weasisLoader) {
    // Retrieve the Start Level service, since it will be needed
    // to set the start level of the installed bundles.
    StartLevel sl =
        (StartLevel)
            context.getService(
                context.getServiceReference(
                    org.osgi.service.startlevel.StartLevel.class.getName()));

    // Retrieve all auto-install and auto-start properties and install
    // their associated bundles. The auto-installation property specifies a
    // space-delimited list of bundle URLs to be automatically installed
    // into each new profile, while the auto-start property specifies
    // bundles to be installed and started. The start level to which the
    // bundles are assigned is specified by appending a ".n" to the
    // property name, where "n" is the desired start level for the list
    // of bundles. If no start level is specified, the default start
    // level is assumed.
    Map<String, BundleElement> bundleList = new HashMap<>();

    Set<String> set = configMap.keySet();
    for (String o : set) {
      String key = o.toLowerCase();

      // Ignore all keys that are not an auto property.
      if (!key.startsWith(AUTO_INSTALL_PROP) && !key.startsWith(AUTO_START_PROP)) {
        continue;
      }
      // If the auto property does not have a start level,
      // then assume it is the default bundle start level, otherwise
      // parse the specified start level.
      int startLevel = sl.getInitialBundleStartLevel();
      try {
        startLevel = Integer.parseInt(key.substring(key.lastIndexOf('.') + 1));
      } catch (NumberFormatException ex) {
        LOGGER.log(Level.ERROR, () -> String.format("Invalid start level %s", key), ex); // NON-NLS
      }
      boolean canBeStarted = key.startsWith(AUTO_START_PROP);
      StringTokenizer st = new StringTokenizer(configMap.get(key), "\" ", true);
      for (String location = nextLocation(st); location != null; location = nextLocation(st)) {
        String bundleName = getBundleNameFromLocation(location);
        if (!"System Bundle".equals(bundleName)) { // NON-NLS
          BundleElement b = new BundleElement(startLevel, location, canBeStarted);
          bundleList.put(bundleName, b);
        }
      }
    }
    weasisLoader.setMax(bundleList.size());

    final Map<String, Bundle> installedBundleMap = new HashMap<>();
    Bundle[] bundles = context.getBundles();
    for (Bundle value : bundles) {
      String bundleName = getBundleNameFromLocation(value.getLocation());
      if (bundleName == null) {
        // Should never happen
        continue;
      }
      try {
        BundleElement b = bundleList.get(bundleName);
        // Remove the bundles in cache when they are not in the config.properties list
        if (b == null) {
          if (!"System Bundle".equals(bundleName)) { // NON-NLS
            value.uninstall();
            LOGGER.log(Level.INFO, "Uninstall unused bundle: {0}", bundleName);
          }
          continue;
        }
        // Remove snapshot version to install it every time
        if (value.getVersion().getQualifier().endsWith("SNAPSHOT")) {
          value.uninstall();
          LOGGER.log(Level.INFO, "Uninstall SNAPSHOT bundle: {0}", bundleName);
          continue;
        }
        installedBundleMap.put(bundleName, value);

      } catch (Exception e) {
        LOGGER.log(
            Level.ERROR,
            () ->
                String.format("Cannot remove from OSGI cache the bundle %s", bundleName), // NON-NLS
            e);
      }
    }

    boolean cache =
        Boolean.TRUE
            .toString()
            .equals(System.getProperty("http.bundle.cache", Boolean.TRUE.toString()));
    int bundleIter = 0;

    // Parse and install the bundles associated with the key.
    for (Entry<String, BundleElement> element : bundleList.entrySet()) {
      String bundleName = element.getKey();
      BundleElement bundle = element.getValue();
      if (bundle == null) {
        // Should never happen
        continue;
      }
      try {
        weasisLoader.writeLabel(WeasisLoader.LBL_DOWNLOADING + " " + bundleName);
        // Do not download again the same bundle version but with different location or already in
        // installed
        // in cache from a previous version of Weasis
        Bundle b = installedBundleMap.get(bundleName);
        if (b == null) {
          b = installBundle(context, bundle.getLocation(), cache);
          installedBundleMap.put(bundleName, b);
        }
        sl.setBundleStartLevel(b, bundle.getStartLevel());
        loadTranslationBundle(context, b, installedBundleMap, modulesi18n, cache);
      } catch (Exception ex) {
        if (bundleName.contains(System.getProperty("native.library.spec"))) {
          LOGGER.log(
              Level.ERROR,
              () -> String.format("Cannot install a native bundle %s", bundleName), // NON-NLS
              ex);
        } else {
          LOGGER.log(
              Level.ERROR,
              () -> String.format("Cannot install bundle %s", bundleName), // NON-NLS
              ex);
          if (!Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT.equals(
              configMap.get(Constants.FRAMEWORK_STORAGE_CLEAN))) {
            // Reset all the old cache
            throw new IllegalStateException("A bundle cannot be started");
          }
        }
      } finally {
        bundleIter++;
        weasisLoader.setValue(bundleIter);
      }
    }

    weasisLoader.writeLabel(Messages.getString("AutoProcessor.start"));
    // Now loop through the auto-start bundles and start them.
    for (Entry<String, BundleElement> element : bundleList.entrySet()) {
      String bundleName = element.getKey();
      BundleElement bundle = element.getValue();
      if (bundle == null) {
        // Should never happen
        continue;
      }
      if (bundle.isCanBeStarted()) {
        try {
          Bundle b = installedBundleMap.get(bundleName);
          if (b == null) {
            // Try to reinstall
            b = installBundle(context, bundle.getLocation(), cache);
          }
          if (b != null) {
            b.start();
          }
        } catch (Exception ex) {
          LOGGER.log(
              Level.ERROR,
              () -> String.format("Cannot start bundle %s", bundleName), // NON-NLS
              ex);
        }
      }
    }
  }

  static String getBundleNameFromLocation(String location) {
    if (location != null) {
      int index = location.lastIndexOf('/');
      String name = index >= 0 ? location.substring(index + 1) : location;
      index = name.lastIndexOf(".jar");
      return index >= 0 ? name.substring(0, index) : name;
    }
    return null;
  }

  private static void loadTranslationBundle(
      BundleContext context,
      Bundle b,
      final Map<String, Bundle> installedBundleMap,
      Properties modulesi18n,
      boolean cache) {
    if (!modulesi18n.isEmpty()) {
      if (b != null) {
        StringBuilder p = new StringBuilder(b.getSymbolicName());
        p.append("-i18n.jar.xz"); // NON-NLS
        String filename = p.toString();
        String value = modulesi18n.getProperty(filename);
        if (value != null) {
          String baseURL = System.getProperty(WeasisLauncher.P_WEASIS_I18N);
          if (baseURL != null) {
            String uri = baseURL + (baseURL.endsWith("/") ? filename : "/" + filename);
            String bundleName = getBundleNameFromLocation(filename);
            try {
              Bundle b2 = installedBundleMap.get(bundleName);
              if (b2 == null) {
                b2 = installBundle(context, uri, cache);
                installedBundleMap.put(bundleName, b);
              }
              if (b2 != null && !value.equals(b2.getVersion().getQualifier())) {
                if (b2.getLocation().startsWith(baseURL)) {
                  b2.update();
                } else {
                  // Handle same bundle version with different location
                  try {
                    b2.uninstall();
                    context.installBundle(
                        uri,
                        FileUtil.getAdaptedConnection(new URI(uri).toURL(), false)
                            .getInputStream());
                    installedBundleMap.put(bundleName, b);
                  } catch (Exception exc) {
                    LOGGER.log(
                        Level.ERROR,
                        () ->
                            String.format("Cannot install a translation bundle %s", uri), // NON-NLS
                        exc);
                  }
                }
              }
            } catch (Exception e) {
              LOGGER.log(
                  Level.ERROR,
                  () -> String.format("Cannot install a translation bundle %s", uri), // NON-NLS
                  e);
            }
          }
        }
      }
    }
  }

  private static String nextLocation(StringTokenizer st) {
    String retVal = null;

    if (st.countTokens() > 0) {
      String tokenList = "\" ";
      StringBuilder tokBuf = new StringBuilder(10);
      String tok = null;
      boolean inQuote = false;
      boolean tokStarted = false;
      boolean exit = false;
      while ((st.hasMoreTokens()) && (!exit)) {
        tok = st.nextToken(tokenList);
        if (tok.equals("\"")) {
          inQuote = !inQuote;
          if (inQuote) {
            tokenList = "\"";
          } else {
            tokenList = "\" ";
          }

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

      // Handle case where end of token stream and
      // still got data
      if ((!exit) && (tokStarted)) {
        retVal = tokBuf.toString();
      }

      if (Utils.hasText(retVal)) {
        retVal = Utils.adaptPathToUri(retVal);
      }
    }

    return retVal;
  }

  private static boolean isFragment(Bundle bundle) {
    return bundle.getHeaders().get(Constants.FRAGMENT_HOST) != null;
  }

  private static Bundle installBundle(BundleContext context, String location, boolean httpCache)
      throws Exception {
    boolean pack = location.endsWith(XZ_COMPRESSION);
    if (pack) {
      // Remove the pack classifier from the location path
      location = location.substring(0, location.length() - 3);
      pack = context.getBundle(location) == null;
    }

    if (pack) {
      final URL url = new URL(location + XZ_COMPRESSION);
      try (XZInputStream xzStream =
          new XZInputStream(FileUtil.getAdaptedConnection(url, httpCache).getInputStream())) {
        return context.installBundle(location, xzStream);
      } catch (Exception e) {
        LOGGER.log(
            Level.ERROR,
            () -> String.format("Cannot install xz compressed bundle %s", url), // NON-NLS
            e);
      }
    }
    return context.installBundle(
        location,
        FileUtil.getAdaptedConnection(new URI(location).toURL(), httpCache).getInputStream());
  }

  static class BundleElement {
    private final int startLevel;
    private final String location;
    private final boolean canBeStarted;

    public BundleElement(int startLevel, String location, boolean canBeStarted) {

      this.startLevel = startLevel;
      this.location = location;
      this.canBeStarted = canBeStarted;
    }

    public int getStartLevel() {
      return startLevel;
    }

    public String getLocation() {
      return location;
    }

    public boolean isCanBeStarted() {
      return canBeStarted;
    }
  }
}
