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

/** Share of the space given to the first side of a split, strictly between 0 and 1. */
public record SplitRatio(double value) implements SemanticType {

  private static final String EXPECTED = "a share between 0 and 1, both excluded";

  public SplitRatio {
    if (!(value > 0) || !(value < 1)) {
      throw new IllegalArgumentException(EXPECTED);
    }
  }

  public static SplitRatio parse(String text) {
    try {
      return new SplitRatio(Double.parseDouble(text.trim()));
    } catch (NumberFormatException | NullPointerException e) {
      throw new IllegalArgumentException(EXPECTED);
    }
  }

  @Override
  public String toString() {
    return Double.toString(value);
  }
}
