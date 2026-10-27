/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.subtraction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.weasis.opencv.natives.NativeLibrary;

/**
 * Loads the OpenCV library for the tests that touch pixels: from the library path Maven sets, else
 * from the native module built in this repository; false when neither has it, so the test skips.
 */
final class OpenCv {

  private static Boolean loaded;

  private OpenCv() {}

  static synchronized boolean tryLoad() {
    if (loaded == null) {
      loaded = load();
    }
    return loaded;
  }

  private static boolean load() {
    try {
      NativeLibrary.loadLibraryFromLibraryName();
      return true;
    } catch (Throwable e) {
      return loadFromRepository();
    }
  }

  private static boolean loadFromRepository() {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT); // NON-NLS
    String libFile =
        os.contains("win") // NON-NLS
            ? "opencv_java.dll" // NON-NLS
            : os.contains("mac") ? "libopencv_java.dylib" : "libopencv_java.so"; // NON-NLS
    Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath(); // NON-NLS
    while (dir != null && !Files.isDirectory(dir.resolve("weasis-opencv"))) { // NON-NLS
      dir = dir.getParent();
    }
    if (dir == null) {
      return false;
    }
    try (Stream<Path> modules = Files.list(dir.resolve("weasis-opencv"))) { // NON-NLS
      List<Path> candidates =
          modules
              .filter(Files::isDirectory)
              .map(m -> m.resolve("target").resolve("classes")) // NON-NLS
              .flatMap(c -> Stream.of(c.resolve(libFile), c.resolve("natives").resolve(libFile)))
              .filter(Files::isRegularFile)
              .toList();
      for (Path lib : candidates) {
        try {
          System.load(lib.toString());
          return true;
        } catch (Throwable ignore) {
          // another architecture: try the next one
        }
      }
    } catch (IOException e) {
      return false;
    }
    return false;
  }
}
