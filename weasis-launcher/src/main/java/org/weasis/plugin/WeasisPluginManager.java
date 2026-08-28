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
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.pf4j.CompoundPluginDescriptorFinder;
import org.pf4j.DefaultPluginManager;
import org.pf4j.ExtensionFactory;
import org.pf4j.PluginDescriptorFinder;
import org.pf4j.PluginFactory;
import org.pf4j.PluginLoader;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.pf4j.VersionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.pref.ConfigData;

public class WeasisPluginManager extends DefaultPluginManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(WeasisPluginManager.class);

  private final CommonLibClassLoader commonLibClassLoader =
      new CommonLibClassLoader(WeasisPluginManager.class.getClassLoader());

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

  /**
   * Uses {@link WeasisPluginClassLoader} instead of the pf4j one, which is not parallel capable and
   * deadlocks when two threads load classes across plugins at the same time.
   */
  @Override
  protected PluginLoader createPluginLoader() {
    return new WeasisPluginLoader(this);
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

  @Override
  protected VersionManager createVersionManager() {
    return new WeasisVersionManager();
  }

  /**
   * Publishes the shared library JARs (e.g. weasis-core) of {@link ConfigData#F_LIB_FOLDER} through
   * {@link #getCommonLibClassLoader()}, the parent of every plugin class loader. Must be called
   * after {@code AutoProcessor} has downloaded them and before plugins are loaded and started,
   * otherwise plugins fail with {@code NoClassDefFoundError} on {@code org.weasis.core.*} classes
   * on the first launch with a clean cache. Idempotent: each JAR is added at most once, and jars
   * already present on the application classpath (packaged app: {@code common-lib/} in the app
   * image) are skipped so they stay AOT-cacheable.
   */
  public void loadCommonLibraries() {
    // Threads started from here on inherit a context class loader that sees the shared libraries.
    Thread.currentThread().setContextClassLoader(commonLibClassLoader);

    Path pluginsDir = getPluginCachePath();
    if (pluginsDir == null) {
      return;
    }

    Path libDir = pluginsDir.resolveSibling(ConfigData.F_LIB_FOLDER);
    if (!Files.isDirectory(libDir)) {
      LOGGER.debug("Common library directory does not exist: {}", libDir);
      return;
    }

    Set<String> classpathJars = classpathJarNames();
    try (Stream<Path> stream = Files.list(libDir)) {
      stream
          .filter(path -> path.toString().endsWith(".jar"))
          .forEach(path -> addCommonLibrary(path, classpathJars));
    } catch (IOException e) {
      LOGGER.error("Failed to list common libraries from: {}", libDir, e);
    }
  }

  private void addCommonLibrary(Path path, Set<String> classpathJars) {
    if (classpathJars.contains(path.getFileName().toString())) {
      LOGGER.debug("Already on the application classpath, skipping: {}", path);
      return;
    }
    try {
      if (commonLibClassLoader.addFile(path)) {
        LOGGER.info("Added common library: {}", path.getFileName());
      }
    } catch (MalformedURLException e) {
      LOGGER.error("Failed to add library: {}", path, e);
    }
  }

  /** Parent of the plugin class loaders, delegating to the application class loader. */
  public ClassLoader getCommonLibClassLoader() {
    return commonLibClassLoader;
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

  /**
   * Starts the resolved plugins one by one in dependency order, logging each plugin's start time so
   * the slow activators can be ranked ({@code *PERF* Plugin start, id:<id> time:<ms>}). Equivalent
   * to {@link #startPlugins()} but instrumented; dependencies are already started when their
   * dependents are reached, so each measurement reflects only that plugin's own {@code start()}.
   */
  public void startPluginsTimed() {
    long total = System.currentTimeMillis();
    for (PluginWrapper wrapper : getResolvedPlugins()) {
      startPluginTimed(wrapper);
    }
    LOGGER.info("*PERF* Plugins start total, time:{}", System.currentTimeMillis() - total);
  }

  private void startPluginTimed(PluginWrapper wrapper) {
    if (wrapper.getPluginState() == PluginState.STARTED) {
      return;
    }
    String id = wrapper.getPluginId();
    long start = System.currentTimeMillis();
    try {
      startPlugin(id);
    } finally {
      LOGGER.info("*PERF* Plugin start, id:{} time:{}", id, System.currentTimeMillis() - start);
    }
  }

  /**
   * Stops the started plugins in reverse start order, so a plugin is always stopped before its
   * dependencies. Overrides {@link org.pf4j.AbstractPluginManager#stopPlugins()}, which iterates
   * its live {@code startedPlugins} list while removing from it: that throws {@link
   * java.util.ConcurrentModificationException} on shutdown and leaves every plugin after the first
   * one running. Iterating a snapshot avoids the comodification.
   */
  @Override
  public void stopPlugins() {
    for (PluginWrapper wrapper : List.copyOf(getStartedPlugins()).reversed()) {
      if (wrapper.getPluginState().isStarted()) {
        stopPlugin(wrapper.getPluginId(), false);
      }
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
