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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.pf4j.Plugin;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;

public abstract class WeasisPlugin extends Plugin {

  protected final PluginContext context;

  protected WeasisPlugin(PluginContext context) {
    this.context = context;
  }

  protected PluginWrapper getPluginWrapper() {
    return context.getPluginWrapper();
  }

  public PluginContext getContext() {
    return context;
  }

  public String getPluginId() {
    return getPluginWrapper().getPluginId();
  }

  public PluginState getPluginState() {
    return getPluginWrapper().getPluginState();
  }

  protected void extractAndLoadNatives(Consumer<Path> libraryLoader) throws IOException {
    // Create a folder for natives
    Path root = getPluginWrapper().getPluginPath().getParent().getParent();
    Path nativeLibDir = root.resolve("natives");
    Files.createDirectories(nativeLibDir);

    // Get all native library resources from the natives directory in the JAR
    List<Path> nativeLibs = getNativeLibraryPaths();

    for (Path libName : nativeLibs) {
      extractAndLoadLibrary(libName, nativeLibDir, libraryLoader);
    }
  }

  private List<Path> getNativeLibraryPaths() {
    List<Path> nativeLibs = new ArrayList<>();
    PluginDescriptor descriptor = getPluginWrapper().getDescriptor();
    if (descriptor instanceof ManifestPluginDescriptor manifest) {
      String folder = manifest.getNativeFolder();
      if (!folder.startsWith("/")) {
        folder = "/" + folder;
      }
      String[] files = manifest.getNativeFiles().split(",");
      for (String file : files) {
        nativeLibs.add(Path.of(folder, file.trim()));
      }
    }
    return nativeLibs;
  }

  private void extractAndLoadLibrary(Path libName, Path nativeLibDir, Consumer<Path> libraryLoader)
      throws IOException {
    try (InputStream is = getClass().getResourceAsStream(libName.toString())) {
      if (is != null) {
        Path targetPath = nativeLibDir.resolve(libName.getFileName());
        Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
        libraryLoader.accept(targetPath);
      }
    }
  }
}
