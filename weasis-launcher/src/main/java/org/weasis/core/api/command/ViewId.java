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

/**
 * Identifies one view of the workspace, for the life of that view. Produced by {@code
 * workspace:list} and by the verbs that open a series.
 */
@Definition(value = "the id of a view, from workspace:list", handle = true)
public record ViewId(String value) implements SemanticType {

  private static final String EXPECTED = "a view id from 'workspace:list'"; // NON-NLS

  public ViewId {
    value = SemanticType.handle(value, EXPECTED);
  }

  public static ViewId parse(String text) {
    return new ViewId(text);
  }

  @Override
  public String toString() {
    return value;
  }
}
