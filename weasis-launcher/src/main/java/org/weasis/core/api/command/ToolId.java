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

/** Identifies a tool of the shell: a plain lowercase token such as {@code display}. */
@Definition(value = "the id of a tool of the shell, from workspace:panel", handle = true)
public record ToolId(String value) implements SemanticType {
  private static final String EXPECTED = "a tool id from 'workspace:panel'"; // NON-NLS

  public ToolId {
    value = SemanticType.handle(value, EXPECTED).toLowerCase(java.util.Locale.ROOT);
  }

  public static ToolId parse(String text) {
    return new ToolId(text);
  }

  @Override
  public String toString() {
    return value;
  }
}
