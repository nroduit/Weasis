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

/** How much an image is magnified in a pane: 1 is one image pixel per canvas pixel. */
public record Magnification(double value) implements SemanticType {

  private static final String EXPECTED =
      "a magnification greater than 0, 1 being one image pixel per canvas pixel; a fit of the image"
          + " in the pane is not a magnification and has its own option";

  public Magnification {
    if (!(value > 0) || !Double.isFinite(value)) {
      throw new IllegalArgumentException(EXPECTED);
    }
  }

  public static Magnification parse(String text) {
    return new Magnification(SemanticType.decimal(text, EXPECTED));
  }

  @Override
  public String toString() {
    return Double.toString(value);
  }
}
