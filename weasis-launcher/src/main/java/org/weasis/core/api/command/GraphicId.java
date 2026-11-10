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

/** Identifies one graphic of an image. Produced by the verbs that draw and by {@code graphics}. */
@Definition(
    value = "the id of one graphic of an image, from the verbs that draw and from view2d:graphics",
    handle = true)
public record GraphicId(String value) implements SemanticType {

  private static final String EXPECTED = "a graphic id from 'graphics'"; // NON-NLS

  public GraphicId {
    value = SemanticType.handle(value, EXPECTED);
  }

  public static GraphicId parse(String text) {
    return new GraphicId(text);
  }

  @Override
  public String toString() {
    return value;
  }
}
