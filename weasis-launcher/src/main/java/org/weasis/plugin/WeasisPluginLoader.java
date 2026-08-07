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
import java.nio.file.Files;
import java.nio.file.Path;
import org.pf4j.DefaultPluginClasspath;
import org.pf4j.PluginClasspath;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginLoader;
import org.pf4j.PluginManager;
import org.pf4j.util.FileUtils;

/**
 * Replaces the pf4j {@code JarPluginLoader} and {@code DefaultPluginLoader} so that plugins get a
 * {@link WeasisPluginClassLoader}. Both loaders are collapsed here because they only differ in
 * whether the plugin is a jar file or an exploded directory.
 */
public class WeasisPluginLoader implements PluginLoader {

  private final PluginManager pluginManager;
  private final PluginClasspath pluginClasspath = new DefaultPluginClasspath();

  public WeasisPluginLoader(PluginManager pluginManager) {
    this.pluginManager = pluginManager;
  }

  @Override
  public boolean isApplicable(Path pluginPath) {
    return Files.isDirectory(pluginPath) || FileUtils.isJarFile(pluginPath);
  }

  @Override
  public ClassLoader loadPlugin(Path pluginPath, PluginDescriptor pluginDescriptor) {
    WeasisPluginClassLoader classLoader =
        new WeasisPluginClassLoader(pluginManager, pluginDescriptor, parentClassLoader());
    if (Files.isDirectory(pluginPath)) {
      loadDirectory(pluginPath, classLoader);
    } else {
      classLoader.addFile(pluginPath.toFile());
    }
    return classLoader;
  }

  /**
   * Plugins resolve the shared libraries through the common library class loader, which itself
   * delegates to the application class loader for the jars already on the classpath.
   */
  private ClassLoader parentClassLoader() {
    return pluginManager instanceof WeasisPluginManager manager
        ? manager.getCommonLibClassLoader()
        : getClass().getClassLoader();
  }

  /** Adds the classes directories then the jars directories of an exploded plugin. */
  private void loadDirectory(Path pluginPath, WeasisPluginClassLoader classLoader) {
    for (String directory : pluginClasspath.getClassesDirectories()) {
      File classes = pluginPath.resolve(directory).toFile();
      if (classes.isDirectory()) {
        classLoader.addFile(classes);
      }
    }
    for (String jarsDirectory : pluginClasspath.getJarsDirectories()) {
      FileUtils.getJars(pluginPath.resolve(jarsDirectory)).forEach(classLoader::addFile);
    }
  }
}
