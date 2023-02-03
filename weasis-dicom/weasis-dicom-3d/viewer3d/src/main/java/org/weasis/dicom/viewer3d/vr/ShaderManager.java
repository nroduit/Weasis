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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class ShaderManager {
  public static final String VERTEX_SHADER = "volume.vert";
  public static final String FRAGMENT_SHADER = "volume.frag";
  public static final String COMPUTE_SHADER = "volume.comp";

  private static final SoftCache<String, String> cache = new SoftCache<>();

  public static String getCode(String name) {
    return cache.computeIfAbsent(
        name,
        s -> {
          InputStream stream = ShaderManager.class.getResourceAsStream("/shader/" + name);
          if (stream == null) {
            throw new IllegalStateException("Cannot load " + name);
          }
          return new Scanner(stream, StandardCharsets.UTF_8).useDelimiter("\\A").next();
        });
  }
}
