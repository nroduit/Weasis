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

/** Center of the window applied to the pixel values, in their own unit. */
@Definition(value = "the window center applied to the pixel values, in their own unit")
public record WindowCenter(double value) implements SemanticType {

  private static final String EXPECTED = "a window center in the unit of the pixels";

  public WindowCenter {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(EXPECTED);
    }
  }

  public static WindowCenter parse(String text) {
    return new WindowCenter(SemanticType.decimal(text, EXPECTED));
  }

  @Override
  public String toString() {
    return Double.toString(value);
  }
}
