/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image.cv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** Loads the OpenCV native library built by the weasis-opencv modules, for tests that need it. */
public final class OpenCvTestLoader {
  private static Boolean loaded;

  private OpenCvTestLoader() {}

  /** Returns false when no library of this platform has been built: the caller skips its tests. */
  public static synchronized boolean tryLoad() {
    if (loaded == null) {
      loaded = load();
    }
    return loaded;
  }

  private static boolean load() {
    String os = System.getProperty("os.name", "").toLowerCase();
    String libFile =
        os.contains("win")
            ? "opencv_java.dll"
            : os.contains("mac") ? "libopencv_java.dylib" : "libopencv_java.so";
    Path opencvModules =
        Path.of(System.getProperty("user.dir")).getParent().resolve("weasis-opencv");
    if (!Files.isDirectory(opencvModules)) {
      return false;
    }
    try (Stream<Path> dirs = Files.list(opencvModules)) {
      List<Path> candidates =
          dirs.filter(Files::isDirectory)
              .map(d -> d.resolve("target").resolve("classes").resolve(libFile))
              .filter(Files::isRegularFile)
              .toList();
      for (Path lib : candidates) {
        try {
          System.load(lib.toAbsolutePath().toString());
          return true;
        } catch (Throwable ignore) {
          // wrong architecture or incompatible binary: try the next candidate
        }
      }
    } catch (IOException e) {
      return false;
    }
    return false;
  }
}
