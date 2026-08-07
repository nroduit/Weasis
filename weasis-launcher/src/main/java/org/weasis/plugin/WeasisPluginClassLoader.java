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
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.pf4j.ClassLoadingStrategy;
import org.pf4j.ClassLoadingStrategy.Source;
import org.pf4j.IndexedExtensionFinder;
import org.pf4j.PluginDependency;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parallel-capable replacement for {@link org.pf4j.PluginClassLoader}, with the same parent-last
 * (plugin → dependencies → application) lookup order.
 *
 * <p>pf4j's loader never registers as parallel capable, so {@link #getClassLoadingLock(String)}
 * returns the loader itself: every class load of a plugin serializes on a single monitor, and the
 * {@code DEPENDENCIES} lookup calls another plugin's loader while still holding it. Two threads
 * walking the dependency graph in opposite directions then deadlock. Subclassing {@code
 * PluginClassLoader} cannot fix this — {@link ClassLoader#registerAsParallelCapable()} only
 * succeeds when the direct superclass is registered, and overriding the lock is not enough because
 * the VM takes the loader monitor again in {@code defineClass} for a non-parallel-capable loader.
 * Hence this reimplementation on top of {@link URLClassLoader}, which is registered.
 *
 * <p>The per-class {@code trace} logging of the pf4j loader is deliberately not reproduced: it
 * emits several lines per class load through a synchronized appender, which dominates startup when
 * trace logging is enabled.
 */
public class WeasisPluginClassLoader extends URLClassLoader {
  private static final Logger LOGGER = LoggerFactory.getLogger(WeasisPluginClassLoader.class);

  private static final String JAVA_PACKAGE_PREFIX = "java."; // NON-NLS
  private static final String PF4J_PACKAGE_PREFIX = "org.pf4j."; // NON-NLS
  private static final String PF4J_TEST_PACKAGE_PREFIX = "org.pf4j.test"; // NON-NLS

  static {
    ClassLoader.registerAsParallelCapable();
  }

  private final PluginManager pluginManager;
  private final PluginDescriptor pluginDescriptor;
  private final ClassLoadingStrategy classLoadingStrategy;

  public WeasisPluginClassLoader(
      PluginManager pluginManager, PluginDescriptor pluginDescriptor, ClassLoader parent) {
    this(pluginManager, pluginDescriptor, parent, ClassLoadingStrategy.PDA);
  }

  public WeasisPluginClassLoader(
      PluginManager pluginManager,
      PluginDescriptor pluginDescriptor,
      ClassLoader parent,
      ClassLoadingStrategy classLoadingStrategy) {
    super(pluginDescriptor.getPluginId(), new URL[0], parent);
    this.pluginManager = pluginManager;
    this.pluginDescriptor = pluginDescriptor;
    this.classLoadingStrategy = classLoadingStrategy;
  }

  /** Adds a jar file or a classes directory to the search path of this plugin. */
  public void addFile(File file) {
    try {
      addURL(file.getCanonicalFile().toURI().toURL());
    } catch (IOException e) {
      LOGGER.error("Cannot add {} to the classpath of {}", file, getName(), e);
    }
  }

  @Override
  protected Class<?> loadClass(String className, boolean resolve) throws ClassNotFoundException {
    synchronized (getClassLoadingLock(className)) {
      Class<?> clazz = doLoadClass(className);
      if (resolve) {
        resolveClass(clazz);
      }
      return clazz;
    }
  }

  private Class<?> doLoadClass(String className) throws ClassNotFoundException {
    if (className.startsWith(JAVA_PACKAGE_PREFIX)) {
      return findSystemClass(className);
    }
    if (shouldDelegateToParent(className)) {
      return getParent().loadClass(className);
    }
    Class<?> loadedClass = findLoadedClass(className);
    if (loadedClass != null) {
      return loadedClass;
    }
    for (Source source : classLoadingStrategy.getSources()) {
      Class<?> clazz = loadFromSource(source, className);
      if (clazz != null) {
        return clazz;
      }
    }
    throw new ClassNotFoundException(className);
  }

  private Class<?> loadFromSource(Source source, String className) {
    try {
      return switch (source) {
        case PLUGIN -> findClass(className);
        case DEPENDENCIES -> loadClassFromDependencies(className);
        case APPLICATION -> getParent() == null ? null : getParent().loadClass(className);
      };
    } catch (ClassNotFoundException e) {
      return null;
    }
  }

  /**
   * Loads a class from the plugins this one depends on. A dependency without a class loader is
   * skipped whether or not it is declared optional, so an unresolved dependency degrades to a
   * {@link ClassNotFoundException} instead of a {@link NullPointerException}.
   */
  private Class<?> loadClassFromDependencies(String className) {
    for (PluginDependency dependency : pluginDescriptor.getDependencies()) {
      ClassLoader classLoader = pluginManager.getPluginClassLoader(dependency.getPluginId());
      if (classLoader == null) {
        continue;
      }
      try {
        return classLoader.loadClass(className);
      } catch (ClassNotFoundException e) {
        // Try the next dependency.
      }
    }
    return null;
  }

  /** Classes of the plugin framework itself always come from the application class loader. */
  protected boolean shouldDelegateToParent(String className) {
    return className.startsWith(PF4J_PACKAGE_PREFIX)
        && !className.startsWith(PF4J_TEST_PACKAGE_PREFIX);
  }

  @Override
  public URL getResource(String name) {
    for (Source source : resourceStrategy(name).getSources()) {
      URL url =
          switch (source) {
            case PLUGIN -> findResource(name);
            case DEPENDENCIES -> findResourceFromDependencies(name);
            case APPLICATION -> super.getResource(name);
          };
      if (url != null) {
        return url;
      }
    }
    return null;
  }

  @Override
  public Enumeration<URL> getResources(String name) throws IOException {
    List<URL> resources = new ArrayList<>();
    for (Source source : resourceStrategy(name).getSources()) {
      switch (source) {
        case PLUGIN -> resources.addAll(Collections.list(findResources(name)));
        case DEPENDENCIES -> resources.addAll(findResourcesFromDependencies(name));
        case APPLICATION -> {
          if (getParent() != null) {
            resources.addAll(Collections.list(getParent().getResources(name)));
          }
        }
      }
    }
    return Collections.enumeration(resources);
  }

  /**
   * The extension index must be read from the plugin itself before its dependencies, otherwise a
   * plugin without extensions inherits the index of the plugins it depends on.
   */
  private ClassLoadingStrategy resourceStrategy(String name) {
    return IndexedExtensionFinder.EXTENSIONS_RESOURCE.equals(name)
        ? ClassLoadingStrategy.PAD
        : classLoadingStrategy;
  }

  private URL findResourceFromDependencies(String name) {
    for (PluginDependency dependency : pluginDescriptor.getDependencies()) {
      ClassLoader classLoader = pluginManager.getPluginClassLoader(dependency.getPluginId());
      URL url =
          classLoader instanceof WeasisPluginClassLoader plugin
              ? plugin.findResource(name)
              : resourceOf(classLoader, name);
      if (url != null) {
        return url;
      }
    }
    return null;
  }

  private List<URL> findResourcesFromDependencies(String name) throws IOException {
    List<URL> resources = new ArrayList<>();
    for (PluginDependency dependency : pluginDescriptor.getDependencies()) {
      ClassLoader classLoader = pluginManager.getPluginClassLoader(dependency.getPluginId());
      if (classLoader instanceof WeasisPluginClassLoader plugin) {
        resources.addAll(Collections.list(plugin.findResources(name)));
      } else if (classLoader != null) {
        resources.addAll(Collections.list(classLoader.getResources(name)));
      }
    }
    return resources;
  }

  private static URL resourceOf(ClassLoader classLoader, String name) {
    return classLoader == null ? null : classLoader.getResource(name);
  }

  /** Visible for testing: distinct locks per class name prove the loader is parallel capable. */
  Object classLoadingLock(String className) {
    return getClassLoadingLock(className);
  }
}
