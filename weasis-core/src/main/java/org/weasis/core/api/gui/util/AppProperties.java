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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.regex.Pattern;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.StringUtil;

/** The Class AppProperties provides application-wide configuration and utility methods. */
public final class AppProperties {
  private static final Logger LOGGER = LoggerFactory.getLogger(AppProperties.class);

  private static final String UNKNOWN = "unknown"; // NON-NLS

  /**
   * Matches the path separators of every platform and the characters Windows rejects in a file
   * name. Letters of any script are kept, so a user name is not mangled.
   */
  private static final Pattern UNSAFE_PATH_CHARS = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");

  /** The version of the application (for display) */
  public static final String WEASIS_VERSION =
      System.getProperty("weasis.version", "0.0.0"); // NON-NLS

  /** The name of the application (for display) */
  public static final String WEASIS_NAME = System.getProperty("weasis.name", "Weasis"); // NON-NLS

  /**
   * The current user of the application (defined either in the launch property "weasis.user" or by
   * the user of the operating system session if the property is null)
   */
  public static final String WEASIS_USER = getUserName();

  /**
   * The name of the configuration profile (defined in config-ext.properties). The value is
   * "default" if null. This property allows to have separated preferences (in a new directory).
   */
  public static final String WEASIS_PROFILE =
      System.getProperty("weasis.profile", "default"); // NON-NLS

  /** The User-Agent header to be used with HttpURLConnection */
  public static final String WEASIS_USER_AGENT = System.getProperty("http.agent"); // NON-NLS

  /** The directory for writing temporary files */
  public static final Path APP_TEMP_DIR = initializeTempDirectory();

  /** The path of the directory ".weasis" (containing the installation and the preferences) */
  public static final Path WEASIS_PATH = initializeWeasisPath();

  public static final String CACHE_NAME = "cache";

  /** The cache directory for files */
  public static final Path FILE_CACHE_DIR = buildAccessibleTempDirectory(CACHE_NAME); // NON-NLS

  /** The global glass pane instance */
  public static final GhostGlassPane glassPane = new GhostGlassPane();

  // Private constructor to prevent instantiation
  private AppProperties() {}

  /** Gets the username, applying Windows-specific uppercase transformation if needed. */
  private static String getUserName() {
    String user = System.getProperty("weasis.user", UNKNOWN).trim();
    return SystemInfo.isWindows ? user.toUpperCase() : user;
  }

  private static Path initializeTempDirectory() {
    Path tempDir = getTempDirectoryPath();
    /*
     * Set the username and the id (weasis source instance on web) to avoid mixing files by several users (Linux)
     * or by running multiple instances of Weasis from different sources.
     */
    Path appTempDir =
        tempDir.resolve(
            "weasis-"
                + toSafePathElement(System.getProperty("user.name"), "tmp") // NON-NLS
                + "."
                + toSafePathElement(System.getProperty("weasis.source.id"), UNKNOWN));

    System.setProperty("weasis.tmp.dir", appTempDir.toAbsolutePath().toString());
    try {
      // Clean temp folder, necessary when the application has crashed.
      FileUtil.deleteDirectoryContents(appTempDir, 3, 0);
    } catch (Exception e) {
      LOGGER.error("Error cleaning temporary files", e);
    }
    return appTempDir;
  }

  /**
   * Reduces a launch property to a single path element: such a value can be supplied by a {@code
   * weasis://} link and the resulting directory is deleted recursively at startup.
   */
  private static String toSafePathElement(String value, String defaultValue) {
    if (!StringUtil.hasText(value)) {
      return defaultValue;
    }
    return UNSAFE_PATH_CHARS.matcher(value.strip()).replaceAll("_");
  }

  private static Path getTempDirectoryPath() {
    String tempDir = System.getProperty("java.io.tmpdir");
    if (tempDir == null || tempDir.length() == 1) {
      String userHome = System.getProperty("user.home", ""); // NON-NLS
      return Paths.get(userHome);
    }
    return Paths.get(tempDir);
  }

  private static Path initializeWeasisPath() {
    String weasisPath =
        System.getProperty(
            "weasis.path", System.getProperty("user.home") + File.separator + ".weasis"); // NON-NLS
    return Paths.get(weasisPath);
  }

  /** Gets the bundle context for the AppProperties class. */
  public static BundleContext getBundleContext() {
    return getBundleContext(AppProperties.class);
  }

  /** Gets the bundle context for the specified class. */
  public static BundleContext getBundleContext(Class<?> cl) {
    return Optional.ofNullable(FrameworkUtil.getBundle(cl))
        .map(Bundle::getBundleContext)
        .orElse(null);
  }

  /** Gets the bundle context from a service reference. */
  public static BundleContext getBundleContext(ServiceReference<?> sRef) {
    if (sRef == null) {
      return null;
    }

    Bundle bundle = sRef.getBundle();
    return bundle != null ? bundle.getBundleContext() : getBundleContext();
  }

  /** Gets the bundle data folder path for the given bundle context. */
  public static Path getBundleDataFolder(BundleContext context) {
    if (context == null) {
      return null;
    }
    return WEASIS_PATH.resolve("data").resolve(context.getBundle().getSymbolicName());
  }

  /**
   * Builds a directory path within the application's temporary directory, creating it if it does
   * not exist. If no subfolder names are provided, returns the main temporary directory.
   *
   * @param subFolderNames the names of subfolders to create within the temporary directory
   * @return the path to the created or existing directory
   */
  public static Path buildAccessibleTempDirectory(String... subFolderNames) {
    if (subFolderNames == null || subFolderNames.length == 0) {
      return APP_TEMP_DIR;
    }

    Path result = APP_TEMP_DIR;
    for (String folder : subFolderNames) {
      result = result.resolve(folder);
    }
    try {
      Files.createDirectories(result);
      return result;
    } catch (Exception e) {
      LOGGER.error("Cannot build directory: {}", result, e);
      return APP_TEMP_DIR;
    }
  }

  /**
   * Creates a temporary file in a specific subdirectory of the application's temporary directory.
   *
   * @param subDirectory the subdirectory name
   * @param prefix the prefix string to be used in generating the file's name
   * @param suffix the suffix string to be used in generating the file's name
   * @return the created temporary file path
   * @throws IOException if the file cannot be created
   */
  public static Path createTempFile(String subDirectory, String prefix, String suffix)
      throws IOException {
    Path tempDir = buildAccessibleTempDirectory(subDirectory);
    return Files.createTempFile(tempDir, prefix, suffix);
  }

  /**
   * Parses a version string and returns a Version object. Handles version strings that may start
   * with 'v' and contain build information after '-'.
   */
  public static Version getVersion(String version) {
    if (!StringUtil.hasText(version)) {
      return new Version("0.0.0");
    }

    String cleanVersion = extractVersionNumber(version);
    return new Version(cleanVersion);
  }

  private static String extractVersionNumber(String version) {
    int start = version.startsWith("v") ? 1 : 0; // NON-NLS
    int end = version.indexOf('-');
    if (end < 0) {
      end = version.length();
    }
    return end > start ? version.substring(start, end) : "0.0.0";
  }
}
