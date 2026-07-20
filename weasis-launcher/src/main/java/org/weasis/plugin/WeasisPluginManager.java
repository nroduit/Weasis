/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.plugin;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import org.pf4j.CompoundPluginDescriptorFinder;
import org.pf4j.DefaultPluginManager;
import org.pf4j.ExtensionFactory;
import org.pf4j.PluginDescriptorFinder;
import org.pf4j.PluginFactory;
import org.pf4j.PluginLoader;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.pref.ConfigData;

public class WeasisPluginManager extends DefaultPluginManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(WeasisPluginManager.class);

  private static Instrumentation instrumentation;

  private final Set<Path> loadedLibraries = new HashSet<>();

  public WeasisPluginManager() {
    super();
  }

  public WeasisPluginManager(Path pluginsDir) {
    super(pluginsDir);
  }

  @Override
  protected void initialize() {
    if (!pluginsRoots.isEmpty()) {
      super.initialize();
    }
  }

  @Override
  protected PluginLoader createPluginLoader() {
    return super.createPluginLoader();
  }

  @Override
  protected PluginFactory createPluginFactory() {
    return new WeasisPluginFactory();
  }

  @Override
  protected PluginDescriptorFinder createPluginDescriptorFinder() {
    return new CompoundPluginDescriptorFinder().add(new WeasisManifestFinder());
  }

  @Override
  protected ExtensionFactory createExtensionFactory() {
    return new WeasisExtensionFactory(this);
  }

  /**
   * Called by the Java agent to provide the Instrumentation instance ({@code Premain-Class:
   * org.weasis.plugin.WeasisPluginManager} in the launcher MANIFEST, activated with {@code
   * -javaagent:weasis-launcher.jar}).
   */
  public static void premain(String agentArgs, Instrumentation inst) {
    instrumentation = inst;
  }

  /**
   * Adds the shared library JARs (e.g. weasis-core) from {@link ConfigData#F_LIB_FOLDER} to the
   * system classloader. Must be called after {@code AutoProcessor} has downloaded them and before
   * plugins are loaded and started, otherwise plugins fail with {@code NoClassDefFoundError} on
   * {@code org.weasis.core.*} classes on the first launch with a clean cache. Idempotent: each JAR
   * is added at most once, and jars already present on the application classpath (packaged app:
   * {@code common-lib/} in the app image) are skipped so they stay AOT-cacheable.
   */
  public void loadCommonLibraries() {
    Path pluginsDir = getPluginCachePath();
    if (pluginsDir == null || instrumentation == null) {
      return;
    }

    Path libDir = pluginsDir.resolveSibling(ConfigData.F_LIB_FOLDER);
    if (!Files.exists(libDir)) {
      LOGGER.debug("Common library directory does not exist: {}", libDir);
      return;
    }

    Set<String> classpathJars = classpathJarNames();
    try (Stream<Path> stream = Files.list(libDir)) {
      stream
          .filter(path -> path.toString().endsWith(".jar"))
          .forEach(
              path -> {
                if (classpathJars.contains(path.getFileName().toString())) {
                  LOGGER.debug("Already on the application classpath, skipping: {}", path);
                  return;
                }
                if (!loadedLibraries.add(path.toAbsolutePath())) {
                  return;
                }
                try {
                  addJarToClassLoader(path);
                  LOGGER.info("Added common library: {}", path.getFileName());
                } catch (Exception e) {
                  LOGGER.error("Failed to add library: {}", path, e);
                }
              });
    } catch (IOException e) {
      LOGGER.error("Failed to list common libraries from: {}", libDir, e);
    }
  }

  /** Returns the file names of the jars on the application classpath. */
  public static Set<String> classpathJarNames() {
    return classpathJarNames(System.getProperty("java.class.path", ""));
  }

  static Set<String> classpathJarNames(String classpath) {
    Set<String> names = new HashSet<>();
    for (String entry : classpath.split(File.pathSeparator)) {
      if (entry.endsWith(".jar")) {
        names.add(Path.of(entry).getFileName().toString());
      }
    }
    return names;
  }

  /** Adds a JAR file to the given classloader using reflection. */
  private void addJarToClassLoader(Path jarPath) throws Exception {
    JarFile jarFile = new JarFile(jarPath.toFile());
    instrumentation.appendToSystemClassLoaderSearch(jarFile);
  }

  public void addPluginCachePath(Path path) {
    boolean wasEmpty = this.pluginsRoots.isEmpty();
    try {
      Files.createDirectories(path);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot create plugin cache directory: " + path, e);
    }
    pluginsRoots.add(path);
    if (wasEmpty) {
      initialize();
    }
  }

  public Path getPluginCachePath() {
    return pluginsRoots.isEmpty() ? null : pluginsRoots.getFirst();
  }

  public String getPluginId(Class<?> clazz) {
    PluginWrapper wrapper = whichPlugin(clazz);
    return wrapper != null ? wrapper.getPluginId() : null;
  }
}
