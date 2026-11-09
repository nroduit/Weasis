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
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.pf4j.CompoundPluginDescriptorFinder;
import org.pf4j.DefaultPluginManager;
import org.pf4j.ExtensionFactory;
import org.pf4j.ExtensionFinder;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginDescriptorFinder;
import org.pf4j.PluginFactory;
import org.pf4j.PluginLoader;
import org.pf4j.PluginRuntimeException;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.pf4j.VersionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.launcher.BootstrapContract;
import org.weasis.plugin.manager.LoadedPlugin;
import org.weasis.plugin.manager.LoadedPlugins;
import org.weasis.plugin.manager.PluginGate;
import org.weasis.plugin.manager.PluginPolicy;
import org.weasis.pref.ConfigData;

public class WeasisPluginManager extends DefaultPluginManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(WeasisPluginManager.class);

  private final CommonLibClassLoader commonLibClassLoader =
      new CommonLibClassLoader(WeasisPluginManager.class.getClassLoader());

  // Created by createExtensionFinder(), which pf4j calls lazily from the superclass.
  private CommonLibExtensionFinder commonLibExtensionFinder;

  private PluginGate gate;
  private final Map<Path, LoadedPlugin> decisions = new LinkedHashMap<>();
  private Path userRoot;

  public WeasisPluginManager() {
    super();
  }

  /** The gate every jar passes before its classes load; without one everything is loaded. */
  public void setGate(PluginGate gate) {
    this.gate = gate;
  }

  public PluginGate getGate() {
    return gate;
  }

  /** Adds the drop-in folder of the user as a second root, its jars judged by the gate. */
  public void addUserRoot(Path root) {
    this.userRoot = root;
    if (root != null && Files.isDirectory(root)) {
      pluginsRoots.add(root);
    }
  }

  public Path getUserRoot() {
    return userRoot;
  }

  /** The descriptor of a jar that is not loaded, for an import dialog or a verb. */
  public PluginDescriptor describe(Path jar) {
    return getPluginDescriptorFinder().find(jar);
  }

  @Override
  protected PluginWrapper loadPluginFromPath(Path pluginPath) {
    if (gate == null) {
      return super.loadPluginFromPath(pluginPath);
    }
    PluginDescriptor descriptor = getPluginDescriptorFinder().find(pluginPath);
    PluginGate.Decision decision = gate.decide(pluginPath, descriptor);
    decisions.put(pluginPath, decision.plugin());
    if (!decision.load()) {
      throw new PluginRuntimeException(
          "Plugin {} not loaded: {}", descriptor.getPluginId(), decision.plugin().reason());
    }
    return super.loadPluginFromPath(pluginPath);
  }

  /**
   * The manifest of this start: every jar the gate saw with its state after the start, and the jars
   * the roots held that pf4j never offered (a candidate of the drop-in folder).
   */
  public LoadedPlugins manifest(Path cacheDir) {
    List<LoadedPlugin> lines = new ArrayList<>();
    for (Map.Entry<Path, LoadedPlugin> e : decisions.entrySet()) {
      LoadedPlugin line = e.getValue();
      if (LoadedPlugin.STARTED.equals(line.state())) {
        PluginWrapper wrapper = getPlugin(line.id());
        if (wrapper == null) {
          line = line.withState(LoadedPlugin.FAILED, "not loaded");
        } else if (wrapper.getPluginState() != PluginState.STARTED) {
          Throwable failure = wrapper.getFailedException();
          line =
              line.withState(
                  wrapper.getPluginState() == PluginState.DISABLED
                      ? LoadedPlugin.DISABLED
                      : LoadedPlugin.FAILED,
                  failure == null ? wrapper.getPluginState().toString() : failure.getMessage());
        }
      }
      lines.add(line);
    }
    PluginPolicy policy = gate == null ? null : gate.policy();
    return new LoadedPlugins(
        lines,
        policy == null ? null : policy.policy(),
        policy != null && policy.lock(),
        cacheDir == null ? null : cacheDir.resolve(LoadedPlugins.FILE));
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

  /** Classpath extensions are looked up through the common-library loader, see the finder. */
  @Override
  protected ExtensionFinder createExtensionFinder() {
    commonLibExtensionFinder = new CommonLibExtensionFinder(this, this::getCommonLibClassLoader);
    addPluginStateListener(commonLibExtensionFinder);
    return commonLibExtensionFinder;
  }

  @Override
  protected VersionManager createVersionManager() {
    return new WeasisVersionManager();
  }

  /**
   * Publishes the shared library JARs (e.g. weasis-core) of {@link ConfigData#F_LIB_FOLDER}, and
   * the translation packages of {@link ConfigData#F_TRANSLATION_FOLDER}, through {@link
   * #getCommonLibClassLoader()}, the parent of every plugin class loader. Must be called after
   * {@code AutoProcessor} has downloaded them and before plugins are loaded and started, otherwise
   * plugins fail with {@code NoClassDefFoundError} on {@code org.weasis.core.*} classes on the
   * first launch with a clean cache. Idempotent: each JAR is added at most once, and jars already
   * present on the application classpath (packaged app: {@code common-lib/} in the app image) are
   * skipped so they stay AOT-cacheable.
   */
  public void loadCommonLibraries() {
    // Threads started from here on inherit a context class loader that sees the shared libraries.
    Thread.currentThread().setContextClassLoader(commonLibClassLoader);

    Path pluginsDir = getPluginCachePath();
    if (pluginsDir == null) {
      return;
    }

    Set<String> classpathJars = classpathJarNames();
    addCommonLibraries(pluginsDir.resolveSibling(ConfigData.F_LIB_FOLDER), classpathJars);
    // The translation packages: the messages_<lang>.properties of a plugin or of a shared library
    // are found by its own Messages through the parent delegation of its class loader
    addCommonLibraries(pluginsDir.resolveSibling(ConfigData.F_TRANSLATION_FOLDER), classpathJars);
    if (commonLibExtensionFinder != null) {
      commonLibExtensionFinder.invalidateIndex();
    }
  }

  private void addCommonLibraries(Path dir, Set<String> classpathJars) {
    if (!Files.isDirectory(dir)) {
      LOGGER.debug("Common library directory does not exist: {}", dir);
      return;
    }
    try (Stream<Path> stream = Files.list(dir)) {
      stream
          .filter(path -> path.toString().endsWith(".jar"))
          .sorted()
          .forEach(path -> addCommonLibrary(path, classpathJars));
    } catch (IOException e) {
      LOGGER.error("Failed to list common libraries from: {}", dir, e);
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

  /**
   * The file names of the jars the launcher runs from: the application class path, plus the jars of
   * the launcher's own class loader when the bootstrap loaded the set in-process (macOS). The
   * embedded suffix of the installer is removed, so a library of the set is recognised by its name.
   */
  public static Set<String> classpathJarNames() {
    Set<String> names = classpathJarNames(System.getProperty("java.class.path", ""));
    if (WeasisPluginManager.class.getClassLoader() instanceof URLClassLoader loader) {
      for (URL url : loader.getURLs()) {
        String name = url.getPath();
        name = name.substring(name.lastIndexOf('/') + 1);
        if (!name.isEmpty()) {
          names.add(BootstrapContract.jarName(name));
        }
      }
    }
    return names;
  }

  static Set<String> classpathJarNames(String classpath) {
    Set<String> names = new HashSet<>();
    for (String entry : classpath.split(File.pathSeparator)) {
      String name = BootstrapContract.jarName(entry);
      if (name.endsWith(".jar")) {
        names.add(Path.of(name).getFileName().toString());
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
