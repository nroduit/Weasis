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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;
import org.weasis.core.api.image.lut.ColorMapJson;
import org.weasis.core.api.image.op.ByteLutCollection;
import org.weasis.core.util.FileUtil;
import org.weasis.opencv.op.lut.colormap.ColorMap;

/** The formats every editor host gets: the JSON source form, and the legacy table for import. */
public final class ColorMapFormats {

  public static final ColorMapFormat JSON =
      new ColorMapFormat() {
        @Override
        public String description() {
          return "JSON color map"; // NON-NLS
        }

        @Override
        public List<String> extensions() {
          return List.of("json"); // NON-NLS
        }

        @Override
        public List<ColorMap> read(Path file) throws IOException {
          return ColorMapJson.readAll(file);
        }

        @Override
        public boolean canWrite() {
          return true;
        }

        @Override
        public void write(Path file, ColorMap map) throws IOException {
          ColorMapJson.write(file, List.of(map));
        }
      };

  /**
   * Import only: the flat table has no room for alpha, domain or metadata, so it is not written.
   */
  public static final ColorMapFormat TXT =
      new ColorMapFormat() {
        @Override
        public String description() {
          return "Legacy color table (256 lines R G B)"; // NON-NLS
        }

        @Override
        public List<String> extensions() {
          return List.of("txt"); // NON-NLS
        }

        @Override
        public List<ColorMap> read(Path file) throws IOException {
          try (Scanner scanner = new Scanner(file, StandardCharsets.UTF_8)) {
            String name = FileUtil.nameWithoutExtension(file.getFileName().toString());
            return List.of(ColorMap.fromBgrTable(name, ByteLutCollection.readLutFile(scanner)));
          }
        }
      };

  private ColorMapFormats() {}
}
