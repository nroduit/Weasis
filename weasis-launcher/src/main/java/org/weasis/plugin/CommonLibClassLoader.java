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

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Holds the shared libraries (weasis-core, …) downloaded at runtime into {@link
 * org.weasis.pref.ConfigData#F_LIB_FOLDER} and is the parent of every {@link
 * WeasisPluginClassLoader}, so plugins resolve those classes through the normal parent-last lookup.
 *
 * <p>Delegation to the application class loader is parent-first: a library already on the classpath
 * of the packaged application wins over a copy downloaded from a remote codebase.
 */
class CommonLibClassLoader extends URLClassLoader {

  static {
    ClassLoader.registerAsParallelCapable();
  }

  private final Set<Path> files = new HashSet<>();

  CommonLibClassLoader(ClassLoader parent) {
    super("weasis-common-lib", new URL[0], parent); // NON-NLS
  }

  /** Returns false when the jar was already added, so the caller does not log it twice. */
  synchronized boolean addFile(Path path) throws MalformedURLException {
    Path file = path.toAbsolutePath();
    if (!files.add(file)) {
      return false;
    }
    addURL(file.toUri().toURL());
    return true;
  }
}
