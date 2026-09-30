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

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Minimal streaming JSON writer for the launcher, which embeds no JSON library. The output is
 * indented, as the descriptor of {@code help --json} is read and reviewed as a file; an empty
 * object or array stays on its line.
 */
public final class JsonWriter {

  private static final String INDENT = "  ";

  private final StringBuilder sb = new StringBuilder();

  /** One flag per open object or array: whether the next element needs a comma. */
  private final Deque<boolean[]> scopes = new ArrayDeque<>();

  private boolean afterName;

  private final boolean indent;

  /** A writer that indents, for a document a person reads. */
  public JsonWriter() {
    this(true);
  }

  /**
   * @param indent false for one line, which is what a line-oriented answer needs
   */
  public JsonWriter(boolean indent) {
    this.indent = indent;
  }

  public JsonWriter beginObject() {
    separate();
    sb.append('{');
    scopes.push(new boolean[1]);
    return this;
  }

  public JsonWriter endObject() {
    close('}');
    return this;
  }

  public JsonWriter beginArray() {
    separate();
    sb.append('[');
    scopes.push(new boolean[1]);
    return this;
  }

  public JsonWriter endArray() {
    close(']');
    return this;
  }

  public JsonWriter name(String name) {
    separate();
    sb.append('"').append(escape(name)).append("\":");
    afterName = true;
    return this;
  }

  public JsonWriter value(String value) {
    separate();
    if (value == null) {
      sb.append("null"); // NON-NLS
    } else {
      sb.append('"').append(escape(value)).append('"');
    }
    return this;
  }

  public JsonWriter value(Number value) {
    separate();
    sb.append(value == null ? "null" : value.toString()); // NON-NLS
    return this;
  }

  public JsonWriter value(boolean value) {
    separate();
    sb.append(value);
    return this;
  }

  /** Writes an already serialized JSON value, such as the body a verb produced. */
  public JsonWriter raw(String json) {
    separate();
    sb.append(json == null || json.isBlank() ? "null" : json.strip()); // NON-NLS
    return this;
  }

  /** Writes {@code name: value} only when the value is not null. */
  public JsonWriter optional(String name, String value) {
    if (value != null) {
      name(name).value(value);
    }
    return this;
  }

  private void separate() {
    if (afterName) {
      afterName = false;
      if (indent) {
        sb.append(' ');
      }
      return;
    }
    boolean[] scope = scopes.peek();
    if (scope != null) {
      if (scope[0]) {
        sb.append(',');
      }
      scope[0] = true;
      newLine(scopes.size());
    }
  }

  /** Closes the innermost scope, on its own line when it holds something. */
  private void close(char bracket) {
    boolean[] scope = scopes.pop();
    if (scope[0]) {
      newLine(scopes.size());
    }
    sb.append(bracket);
  }

  private void newLine(int depth) {
    if (indent) {
      sb.append('\n').append(INDENT.repeat(depth));
    }
  }

  @Override
  public String toString() {
    return sb.toString();
  }

  public static String escape(String s) {
    if (s == null) {
      return "";
    }
    StringBuilder out = new StringBuilder(s.length());
    for (char c : s.toCharArray()) {
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (c < 0x20) {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
        }
      }
    }
    return out.toString();
  }
}
