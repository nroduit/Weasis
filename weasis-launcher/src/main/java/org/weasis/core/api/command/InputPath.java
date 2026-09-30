/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.command;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/** A local file a verb reads. The restriction policies decide what a remote caller may read. */
public record InputPath(Path value) implements SemanticType {

  private static final String EXPECTED = "a local file path to read";

  public InputPath {
    if (value == null) {
      throw new IllegalArgumentException(EXPECTED);
    }
  }

  public static InputPath parse(String text) {
    return new InputPath(toPath(text, EXPECTED));
  }

  static Path toPath(String text, String expected) {
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException(expected);
    }
    try {
      return Path.of(text.trim());
    } catch (InvalidPathException e) {
      throw new IllegalArgumentException(expected);
    }
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
