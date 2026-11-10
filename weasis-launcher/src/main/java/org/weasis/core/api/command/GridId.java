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

/** Identifies one grid of panes a view can take. */
@Definition(
    value = "the id of one grid of panes a view can take, from the state the view2d verbs return",
    handle = true)
public record GridId(String value) implements SemanticType {

  private static final String EXPECTED = "a grid id offered by the viewer";

  public GridId {
    value = SemanticType.handle(value, EXPECTED);
  }

  public static GridId parse(String text) {
    return new GridId(text);
  }

  @Override
  public String toString() {
    return value;
  }
}
