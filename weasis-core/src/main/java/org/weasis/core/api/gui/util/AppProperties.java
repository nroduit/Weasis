/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.util;

import com.formdev.flatlaf.util.SystemInfo;
import java.io.File;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.FileUtil;

/** The Class AppProperties. */
public class AppProperties {
  private static final String UNKNOWN = "unknown"; // NON-NLS

  private static final Logger LOGGER = LoggerFactory.getLogger(AppProperties.class);

  /** The version of the application (for display) */
  public static final String WEASIS_VERSION =
      System.getProperty("weasis.version", "0.0.0"); // NON-NLS

  /** The name of the application (for display) */
  public static final String WEASIS_NAME = System.getProperty("weasis.name", "Weasis"); // NON-NLS

  /**
   * The current user of the application (defined either in the launch property "weasis.user" or by
   * the user of the operating system session if the property is null)
   */
  public static final String WEASIS_USER =
      SystemInfo.isWindows
          ? System.getProperty("weasis.user", UNKNOWN).trim().toUpperCase()
          : System.getProperty("weasis.user", UNKNOWN).trim(); // NON-NLS

  /**
   * The name of the configuration profile (defined in config-ext.properties). The value is
   * “default” if null. This property allows to have separated preferences (in a new directory).
   */
  public static final String WEASIS_PROFILE =
      System.getProperty("weasis.profile", "default"); // NON-NLS

  /** The User-Agent header to be used with HttpURLConnection */
  public static final String WEASIS_USER_AGENT = System.getProperty("http.agent"); // NON-NLS

  /** The directory for writing temporary files */
  public static final File APP_TEMP_DIR;

  static {
    String tempDir = System.getProperty("java.io.tmpdir");
    File tdir;
    if (tempDir == null || tempDir.length() == 1) {
      String dir = System.getProperty("user.home", ""); // NON-NLS
      tdir = new File(dir);
    } else {
      tdir = new File(tempDir);
    }
    /*
     * Set the username and the id (weasis source instance on web) to avoid mixing files by several users (Linux)
     * or by running multiple instances of Weasis from different sources.
     */
    APP_TEMP_DIR =
        new File(
            tdir,
            "weasis-"
                + System.getProperty("user.name", "tmp") // NON-NLS
                + "."
                + System.getProperty("weasis.source.id", UNKNOWN));
    System.setProperty("weasis.tmp.dir", APP_TEMP_DIR.getAbsolutePath());
    try {
      // Clean temp folder, necessary when the application has crashed.
      FileUtil.deleteDirectoryContents(APP_TEMP_DIR, 3, 0);
    } catch (Exception e) {
      LOGGER.error("Error cleaning temporary files", e);
    }
  }

  /** The path of the directory “.weasis” (containing the installation and the preferences) */
  public static final String WEASIS_PATH =
      System.getProperty(
          "weasis.path", System.getProperty("user.home") + File.separator + ".weasis"); // NON-NLS

  public static final File FILE_CACHE_DIR = buildAccessibleTempDirectory("cache"); // NON-NLS

  public static final GhostGlassPane glassPane = new GhostGlassPane();

  private AppProperties() {}

  public static BundleContext getBundleContext() {
    Bundle bundle = FrameworkUtil.getBundle(AppProperties.class);
    return bundle == null ? null : bundle.getBundleContext();
  }

  public static BundleContext getBundleContext(ServiceReference<?> sRef) {
    if (sRef != null) {
      Bundle bundle = sRef.getBundle();
      return bundle == null ? getBundleContext() : bundle.getBundleContext();
    }
    return null;
  }

  public static File getBundleDataFolder(BundleContext context) {
    if (context == null) {
      return null;
    }
    return new File(
        AppProperties.WEASIS_PATH + File.separator + "data", context.getBundle().getSymbolicName());
  }

  public static File buildAccessibleTempDirectory(String... subFolderName) {
    if (subFolderName != null) {
      StringBuilder buf = new StringBuilder();
      for (String s : subFolderName) {
        buf.append(s);
        buf.append(File.separator);
      }
      File file = new File(AppProperties.APP_TEMP_DIR, buf.toString());
      try {
        file.mkdirs();
        return file;
      } catch (Exception e) {
        LOGGER.error("Cannot build directory", e);
      }
    }
    return AppProperties.APP_TEMP_DIR;
  }

  public static Version getVersion(String version) {
    String v = "";
    if (version != null) {
      int start = version.startsWith("v") ? 1 : 0;
      int end = version.indexOf('-');
      if (end < 0) {
        end = version.length();
      }
      v = end > 0 ? version.substring(start, end) : version;
    }
    return new Version(v);
  }
}
