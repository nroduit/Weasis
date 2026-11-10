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

import java.nio.file.Path;

/** A local file a verb writes. The restriction policies decide where a remote caller may write. */
@Definition(
    value = "a local file the verb writes; the policy decides where a remote caller may write")
public record OutputPath(Path value) implements SemanticType {

  private static final String EXPECTED = "a local file path to write";

  public OutputPath {
    if (value == null) {
      throw new IllegalArgumentException(EXPECTED);
    }
  }

  public static OutputPath parse(String text) {
    return new OutputPath(InputPath.toPath(text, EXPECTED));
  }

  /** The absolute path, which is what a result echoes. */
  public Path toAbsolutePath() {
    return value.toAbsolutePath();
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
