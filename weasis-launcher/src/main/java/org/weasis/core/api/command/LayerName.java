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

/** Names a layer of graphics in a view. */
@Definition(value = "the name of a layer of graphics in a view")
public record LayerName(String value) implements SemanticType {

  private static final String EXPECTED = "a layer name from 'graphics'"; // NON-NLS

  public LayerName {
    value = SemanticType.handle(value, EXPECTED);
  }

  public static LayerName parse(String text) {
    return new LayerName(text);
  }

  @Override
  public String toString() {
    return value;
  }
}
