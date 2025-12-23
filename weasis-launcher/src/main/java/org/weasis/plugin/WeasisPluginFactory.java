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

import java.lang.reflect.Constructor;
import org.pf4j.DefaultPluginFactory;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WeasisPluginFactory extends DefaultPluginFactory {
  private static final Logger LOGGER = LoggerFactory.getLogger(WeasisPluginFactory.class);

  @Override
  protected Plugin createInstance(Class<?> pluginClass, PluginWrapper pluginWrapper) {
    PluginContext context = new PluginContext(pluginWrapper);
    try {
      Constructor<?> constructor = pluginClass.getConstructor(PluginContext.class);
      return (Plugin) constructor.newInstance(context);
    } catch (NoSuchMethodException e) {
      return createUsingNoParametersConstructor(pluginClass);
    } catch (Exception e) {
      LOGGER.error(e.getMessage(), e);
    }
    return null;
  }
}
