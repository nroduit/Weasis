/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.plugin;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.pf4j.Extension;
import org.pf4j.ExtensionDescriptor;
import org.pf4j.ExtensionWrapper;
import org.pf4j.IndexedExtensionFinder;
import org.pf4j.PluginManager;
import org.pf4j.processor.ExtensionStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds the extensions of the application classpath through the common-library class loader, so
 * that a shared library downloaded at runtime (weasis-core in a source build) contributes its
 * extensions like one shipped on the classpath. pf4j reads the classpath index and loads its
 * classes through its own class loader, which does not see the {@link CommonLibClassLoader}.
 */
final class CommonLibExtensionFinder extends IndexedExtensionFinder {
  private static final Logger LOGGER = LoggerFactory.getLogger(CommonLibExtensionFinder.class);

  // Resolved lazily: the manager creates its finder before its own fields are initialized.
  private final Supplier<ClassLoader> commonLibClassLoader;

  CommonLibExtensionFinder(
      PluginManager pluginManager, Supplier<ClassLoader> commonLibClassLoader) {
    super(pluginManager);
    this.commonLibClassLoader = commonLibClassLoader;
  }

  /**
   * Drops the cached index. pf4j reads it once and only invalidates it when a plugin changes state,
   * so a library published after the first lookup would never contribute its extensions.
   */
  void invalidateIndex() {
    entries = null;
  }

  /** The classpath bucket ({@code null} plugin id) read through the common-library loader. */
  @Override
  public Map<String, Set<String>> readClasspathStorages() {
    Set<String> bucket = new HashSet<>();
    try {
      Enumeration<URL> urls = commonLibClassLoader.get().getResources(EXTENSIONS_RESOURCE);
      while (urls.hasMoreElements()) {
        URL url = urls.nextElement();
        try (Reader reader = new InputStreamReader(url.openStream(), StandardCharsets.UTF_8)) {
          ExtensionStorage.read(reader, bucket);
        }
      }
    } catch (IOException e) {
      LOGGER.error("Cannot read the extension indexes of the common libraries", e);
    }
    Map<String, Set<String>> result = new LinkedHashMap<>();
    result.put(null, bucket);
    return result;
  }

  @Override
  public <T> List<ExtensionWrapper<T>> find(Class<T> type, String pluginId) {
    if (pluginId != null) {
      return super.find(type, pluginId);
    }
    List<ExtensionWrapper<T>> result = new ArrayList<>();
    for (Class<?> extensionClass : classpathExtensions()) {
      if (type.isAssignableFrom(extensionClass)) {
        result.add(wrap(extensionClass));
      }
    }
    Collections.sort(result);
    return result;
  }

  @Override
  @SuppressWarnings("rawtypes")
  public List<ExtensionWrapper> find(String pluginId) {
    if (pluginId != null) {
      return super.find(pluginId);
    }
    List<ExtensionWrapper> result = new ArrayList<>();
    for (Class<?> extensionClass : classpathExtensions()) {
      result.add(wrap(extensionClass));
    }
    Collections.sort(result);
    return result;
  }

  private List<Class<?>> classpathExtensions() {
    List<Class<?>> classes = new ArrayList<>();
    for (String className : findClassNames(null)) {
      try {
        classes.add(commonLibClassLoader.get().loadClass(className));
      } catch (ClassNotFoundException | NoClassDefFoundError e) {
        LOGGER.error("Cannot load extension {}", className, e);
      }
    }
    return classes;
  }

  private <T> ExtensionWrapper<T> wrap(Class<?> extensionClass) {
    Extension annotation = extensionClass.getAnnotation(Extension.class);
    int ordinal = annotation == null ? 0 : annotation.ordinal();
    return new ExtensionWrapper<>(
        new ExtensionDescriptor(ordinal, extensionClass), pluginManager.getExtensionFactory());
  }
}
