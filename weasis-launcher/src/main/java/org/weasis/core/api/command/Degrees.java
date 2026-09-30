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

/** An angle in degrees, clockwise, reduced to 0-359. */
public record Degrees(int value) implements SemanticType {

  private static final String EXPECTED = "an angle in degrees, clockwise";

  public Degrees {
    value = ((value % 360) + 360) % 360;
  }

  public static Degrees parse(String text) {
    return new Degrees(SemanticType.index(text, EXPECTED));
  }

  @Override
  public String toString() {
    return Integer.toString(value);
  }
}
