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

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
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

  public WeasisPluginManager() {
    super();
  }

  public WeasisPluginManager(Path pluginsDir) {
    super(pluginsDir);
  }

  @Override
  protected void initialize() {
    if (!pluginsRoots.isEmpty()) {
      loadCommonLibraries();
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
   * This method is called by the Java agent to provide the Instrumentation instance. Add this to
   * your MANIFEST.MF: Premain-Class: org.weasis.launcher.WeasisPluginManager
   */
  public static void premain(String agentArgs, Instrumentation inst) {
    instrumentation = inst;
  }

  /** Loads JAR files from F_LIB_FOLDER into the current classloader. */
  private void loadCommonLibraries() {
    Path pluginsDir = getPluginCachePath();
    if (pluginsDir == null || instrumentation == null) {
      return;
    }

    Path libDir = pluginsDir.resolveSibling(ConfigData.F_LIB_FOLDER);
    if (!Files.exists(libDir)) {
      LOGGER.debug("Common library directory does not exist: {}", libDir);
      return;
    }

    try (Stream<Path> stream = Files.list(libDir)) {
      stream
          .filter(path -> path.toString().endsWith(".jar"))
          .forEach(
              path -> {
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
