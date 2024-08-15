/*
 * Copyright (c) 2022 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.vr;

import com.formdev.flatlaf.util.SoftCache;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class ShaderManager {

  private ShaderManager() {}

  public static final String VERTEX_SHADER = "volume.vert";
  public static final String FRAGMENT_SHADER = "volume.frag";
  public static final String COMPUTE_SHADER = "volume.comp";

  public static final String OLD_VERTEX_SHADER = "oldVolume.vert";
  public static final String OLD_FRAGMENT_SHADER = "oldVolume.frag";

  private static final SoftCache<String, String> cache = new SoftCache<>();

  public static String getCode(String name) {
    return cache.computeIfAbsent(
        name,
        s -> {
          StringBuilder result = new StringBuilder();
          try {
            readSource(name, result, 0);
            return result.toString();
          } catch (IOException e) {
            throw new IllegalStateException(e);
          }
        });
  }

  public static int readSource(String name, final StringBuilder result, int lineno)
      throws IOException {
    InputStream stream = ShaderManager.class.getResourceAsStream("/shader/" + name); // NON-NLS
    if (stream == null) {
      throw new IllegalStateException("Cannot load " + name);
    }
    try (final BufferedReader reader =
        new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        lineno++;
        if (line.startsWith("#include ")) { // NON-NLS
          String file = line.substring(9).trim();
          if (file.startsWith("\"") && file.endsWith("\"")) {
            file = file.substring(1, file.length() - 1);
          }
          lineno = readSource(file, result, lineno);
        } else {
          result.append(line).append("\n");
        }
      }
    } catch (Exception e) {
      throw new IOException(e);
    }
    return lineno;
  }
}
