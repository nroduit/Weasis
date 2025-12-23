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

import java.util.HashMap;
import java.util.Map;
import org.pf4j.DefaultExtensionFactory;
import org.pf4j.PluginManager;

public class WeasisExtensionFactory extends DefaultExtensionFactory {

  private final Map<ClassLoader, Map<String, Object>> cache;

  public WeasisExtensionFactory(PluginManager pluginManager) {

    cache = new HashMap<>();

    pluginManager.addPluginStateListener(
        event -> {
          if (!event.getPluginState().isStarted()) {
            cache.remove(event.getPlugin().getPluginClassLoader());
          }
        });
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T create(Class<T> extensionClass) {
    String extensionClassName = extensionClass.getName();
    ClassLoader extensionClassLoader = extensionClass.getClassLoader();

    if (!cache.containsKey(extensionClassLoader)) {
      cache.put(extensionClassLoader, new HashMap<>());
    }

    Map<String, Object> classLoaderBucket = cache.get(extensionClassLoader);

    if (classLoaderBucket.containsKey(extensionClassName)) {
      return (T) classLoaderBucket.get(extensionClassName);
    }

    T extension = super.create(extensionClass);
    if (isSingletonExtension(extensionClass)) {
      classLoaderBucket.put(extensionClassName, extension);
    }

    return extension;
  }

  private boolean isSingletonExtension(Class<?> extensionClass) {
    return extensionClass.isAnnotationPresent(SingletonExtension.class);
  }
}
