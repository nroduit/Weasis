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

import java.util.jar.Manifest;
import org.pf4j.ManifestPluginDescriptorFinder;
import org.pf4j.PluginDescriptor;

public class WeasisManifestFinder extends ManifestPluginDescriptorFinder {

  public static final String PLUGIN_NATIVE_FOLDER = "Plugin-Native-Folder";
  public static final String PLUGIN_NATIVE_FILES = "Plugin-Native-Files";

  protected PluginDescriptor createPluginDescriptor(Manifest manifest) {
    PluginDescriptor base = super.createPluginDescriptor(manifest);
    ManifestPluginDescriptor descriptor = new ManifestPluginDescriptor(base);
    descriptor.setNativeFolder(manifest.getMainAttributes().getValue(PLUGIN_NATIVE_FOLDER));
    descriptor.setNativeFiles(manifest.getMainAttributes().getValue(PLUGIN_NATIVE_FILES));
    return descriptor;
  }
}
