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

/** Position of an image in the series a pane shows, counted from 1. */
@Definition(value = "the position of an image in the series a pane shows, counted from 1")
public record ImageIndex(int value) implements SemanticType {

  private static final String EXPECTED = "an image index from 1";

  public ImageIndex {
    if (value < 1) {
      throw new IllegalArgumentException(EXPECTED);
    }
  }

  public static ImageIndex parse(String text) {
    return new ImageIndex(SemanticType.index(text, EXPECTED));
  }

  /** The 0-based position the code uses. */
  public int toOffset() {
    return value - 1;
  }

  @Override
  public String toString() {
    return Integer.toString(value);
  }
}
