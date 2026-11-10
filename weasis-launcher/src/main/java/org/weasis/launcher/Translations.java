/*
 * Copyright (c) 2026 Weasis Team and other contributors.
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
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.ResourceBundle.Control;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.pref.ConfigData;

/**
 * The translation packages of the {@code weasis-i18n} distribution: one {@code
 * <module>-i18n.jar.xz} per module, listed with its version qualifier in {@code
 * buildNumber.properties}.
 *
 * <p>A package is cached in {@link ConfigData#F_TRANSLATION_FOLDER} of the plugin cache and
 * downloaded again when the qualifier of the distribution differs from the one recorded in {@value
 * #VERSIONS_FILE}. The plugin packages are published through the shared library class loader by
 * {@code WeasisPluginManager.loadCommonLibraries}; the package of the launcher ({@value
 * #LAUNCHER_MODULE}) is read before any plugin, see {@link #loadLauncher}.
 */
public final class Translations {
  private static final Logger LOGGER = LoggerFactory.getLogger(Translations.class);

  public static final String SUFFIX = "-i18n"; // NON-NLS
  public static final String LAUNCHER_MODULE = "weasis-launcher"; // NON-NLS
  static final String VERSIONS_FILE = "versions.properties"; // NON-NLS
  private static final String PACKAGE_EXTENSION = ".jar.xz"; // NON-NLS

  /** The folder of the cached translation packages, known once a package has been fetched. */
  private static volatile Path translationDir;

  private Translations() {}

  /**
   * Returns the bundle of a shared library for the default locale, looking up its translations in
   * the cached translation packages. A library that can be on the application class path (the
   * packaged application puts weasis-core there) uses this instead of {@link
   * ResourceBundle#getBundle(String)}, because the class loader of the application does not see the
   * translation packages. The bundles are read eagerly and the packages closed afterward.
   *
   * @param baseName the base name of the bundle, e.g. {@code org.weasis.core.messages}
   * @param owner a class of the library, whose class loader provides the English base
   */
  public static ResourceBundle getBundle(String baseName, Class<?> owner) {
    Locale locale = Locale.getDefault();
    ClassLoader parent = owner.getClassLoader();
    URL[] packages = translationPackages();
    if (PseudoLocale.isPseudo(locale)) {
      return PseudoLocale.wrap(ResourceBundle.getBundle(baseName, Locale.ROOT, parent));
    }
    if (packages.length == 0 || Locale.ENGLISH.getLanguage().equals(locale.getLanguage())) {
      return ResourceBundle.getBundle(baseName, locale, parent);
    }
    try (URLClassLoader loader = new URLClassLoader(packages, parent)) {
      return ResourceBundle.getBundle(baseName, locale, loader);
    } catch (IOException e) {
      LOGGER.debug("Cannot close the translation packages", e);
      return ResourceBundle.getBundle(baseName, locale, parent);
    }
  }

  private static URL[] translationPackages() {
    Path dir = translationDir;
    if (dir == null || !Files.isDirectory(dir)) {
      return new URL[0];
    }
    try (Stream<Path> stream = Files.list(dir)) {
      return stream
          .filter(p -> p.getFileName().toString().endsWith(AutoProcessor.JAR_EXTENSION))
          .sorted()
          .map(Translations::toURL)
          .filter(Objects::nonNull)
          .toArray(URL[]::new);
    } catch (IOException e) {
      LOGGER.error("Cannot list the translation packages of {}", dir, e);
      return new URL[0];
    }
  }

  private static URL toURL(Path path) {
    try {
      return path.toUri().toURL();
    } catch (MalformedURLException e) {
      return null;
    }
  }

  /** The value of {@code http.bundle.cache}, true by default. */
  static boolean useHttpCache() {
    return Boolean.parseBoolean(
        System.getProperty("http.bundle.cache", Boolean.TRUE.toString())); // NON-NLS
  }

  /**
   * Returns the local jar of the translation package of a module, downloading it when it is missing
   * or outdated, or null when the distribution has no package for this module. When the download
   * fails, a cached copy (even outdated) is returned rather than nothing.
   *
   * @param cacheDir the plugin cache directory (parent of the {@code plugins} folder)
   * @param module the module name without the {@value #SUFFIX} suffix, e.g. {@code weasis-core}
   * @param modulesi18n the content of {@code buildNumber.properties}, empty for English
   */
  static synchronized Path fetch(
      Path cacheDir, String module, Properties modulesi18n, boolean httpCache) {
    if (cacheDir == null || module == null || modulesi18n == null || modulesi18n.isEmpty()) {
      return null;
    }
    String packageName = module + SUFFIX;
    String qualifier = modulesi18n.getProperty(packageName + PACKAGE_EXTENSION);
    String baseURL = System.getProperty(ConfigData.P_WEASIS_I18N);
    if (qualifier == null || !Utils.hasText(baseURL)) {
      return null;
    }

    Path dir = cacheDir.resolve(ConfigData.F_TRANSLATION_FOLDER);
    translationDir = dir;
    Path cached = dir.resolve(packageName + AutoProcessor.JAR_EXTENSION);
    Properties versions = readVersions(dir);
    boolean upToDate = qualifier.equals(versions.getProperty(packageName));
    String uri =
        baseURL + (baseURL.endsWith("/") ? "" : "/") + packageName + PACKAGE_EXTENSION; // NON-NLS
    try {
      Path jar = AutoProcessor.downloadPlugin(cacheDir, uri, packageName, httpCache, upToDate);
      if (jar != null) {
        if (!upToDate) {
          versions.setProperty(packageName, qualifier);
          writeVersions(dir, versions);
          LOGGER.info("Translation package {} updated to {}", packageName, qualifier);
        }
        return jar;
      }
    } catch (IOException e) {
      LOGGER.error("Cannot download the translation package: {}", uri, e);
    }
    return Files.isReadable(cached) ? cached : null;
  }

  /**
   * Returns the module of a plugin location name, e.g. {@code weasis-core} for {@code
   * weasis-core-5.0.0}, when the distribution has a translation package for it; null otherwise. The
   * longest listed module that is the name itself or the name followed by a version wins.
   */
  static String moduleOf(String pluginName, Properties modulesi18n) {
    if (pluginName == null || modulesi18n == null) {
      return null;
    }
    String suffix = SUFFIX + PACKAGE_EXTENSION;
    String best = null;
    for (String key : modulesi18n.stringPropertyNames()) {
      if (!key.endsWith(suffix)) {
        continue;
      }
      String module = key.substring(0, key.length() - suffix.length());
      boolean match =
          pluginName.equals(module)
              || (pluginName.length() > module.length() + 1
                  && pluginName.startsWith(module + "-")
                  && Character.isDigit(pluginName.charAt(module.length() + 1)));
      if (match && (best == null || module.length() > best.length())) {
        best = module;
      }
    }
    return best;
  }

  /**
   * Loads the translations of the launcher (its {@code messages} and the Swing dialogs) for the
   * default locale. The package is read eagerly and its jar closed, because a clean of the plugin
   * cache on the first start can delete it afterwards. Without a package, the launcher stays in
   * English and the Swing dialogs keep the strings of the JRE.
   */
  static void loadLauncher(Path cacheDir, Properties modulesi18n) {
    Locale locale = Locale.getDefault();
    if (Locale.ENGLISH.getLanguage().equals(locale.getLanguage())) {
      return;
    }
    Path jar = fetch(cacheDir, LAUNCHER_MODULE, modulesi18n, useHttpCache());
    if (jar == null) {
      return;
    }
    ClassLoader parent = Translations.class.getClassLoader();
    try (URLClassLoader loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, parent)) {
      Messages.setBundle(ResourceBundle.getBundle(Messages.BUNDLE_NAME, locale, loader));
      // Get the default i18n suffix for properties files
      String suffix = Control.getControl(Control.FORMAT_PROPERTIES).toBundleName("", locale);
      SwingResources.loadResources(loader, "swing/basic" + suffix + ".properties"); // NON-NLS
      SwingResources.loadResources(loader, "swing/synth" + suffix + ".properties"); // NON-NLS
      LOGGER.info("Launcher translations loaded from {}", jar.getFileName());
    } catch (MalformedURLException e) {
      LOGGER.error("Invalid translation package: {}", jar, e);
    } catch (Exception e) {
      LOGGER.error("Cannot read the launcher translations from {}", jar, e);
    }
  }

  private static Properties readVersions(Path dir) {
    Properties versions = new Properties();
    Path file = dir.resolve(VERSIONS_FILE);
    if (Files.isReadable(file)) {
      try (InputStream in = Files.newInputStream(file)) {
        versions.load(in);
      } catch (IOException e) {
        LOGGER.warn("Cannot read {}, the translation packages are downloaded again", file, e);
      }
    }
    return versions;
  }

  private static void writeVersions(Path dir, Properties versions) {
    Path file = dir.resolve(VERSIONS_FILE);
    try {
      Files.createDirectories(dir);
      try (OutputStream out = Files.newOutputStream(file)) {
        versions.store(out, "Version qualifiers of the cached translation packages"); // NON-NLS
      }
    } catch (IOException e) {
      LOGGER.error("Cannot write {}", file, e);
    }
  }
}
