/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image.lut;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.weasis.opencv.op.lut.colormap.ColorMap;

/** A file format the editor can import maps from and, when {@link #canWrite()}, export to. */
public interface ColorMapFormat {

  /** Short label shown in the file chooser. */
  String description();

  /** Lower-case extensions without the dot. */
  List<String> extensions();

  List<ColorMap> read(Path file) throws IOException;

  default boolean canWrite() {
    return false;
  }

  default void write(Path file, ColorMap map) throws IOException {
    throw new UnsupportedOperationException(description());
  }

  default boolean matches(Path file) {
    String name = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
    return extensions().stream().anyMatch(ext -> name.endsWith("." + ext));
  }

  /** {@code name} with this format's first extension, replacing any existing one. */
  default String fileName(String name) {
    int dot = name.lastIndexOf('.');
    String base = dot > 0 ? name.substring(0, dot) : name;
    return base + "." + extensions().getFirst();
  }
}
