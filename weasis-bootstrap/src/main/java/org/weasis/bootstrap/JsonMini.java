/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.bootstrap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader (RFC 8259) for the configuration files stage 1 inspects: objects become
 * {@code Map<String, Object>}, arrays {@code List<Object>}, strings {@code String}, numbers {@code
 * Double}, and the literals {@code Boolean} or {@code null}. The bootstrap has no library, so the
 * launcher's JSON reader cannot be shared.
 */
final class JsonMini {

  private final String text;
  private int pos;

  private JsonMini(String text) {
    this.text = text;
  }

  static Object parse(String text) {
    JsonMini reader = new JsonMini(text);
    Object value = reader.readValue();
    reader.skipWhitespace();
    if (reader.pos != text.length()) {
      throw reader.error("trailing characters");
    }
    return value;
  }

  private Object readValue() {
    skipWhitespace();
    if (pos >= text.length()) {
      throw error("unexpected end");
    }
    char c = text.charAt(pos);
    return switch (c) {
      case '{' -> readObject();
      case '[' -> readArray();
      case '"' -> readString();
      case 't' -> readLiteral("true", Boolean.TRUE);
      case 'f' -> readLiteral("false", Boolean.FALSE);
      case 'n' -> readLiteral("null", null);
      default -> readNumber();
    };
  }

  private Map<String, Object> readObject() {
    Map<String, Object> map = new LinkedHashMap<>();
    pos++;
    skipWhitespace();
    if (peek() == '}') {
      pos++;
      return map;
    }
    while (true) {
      skipWhitespace();
      if (peek() != '"') {
        throw error("expected a key");
      }
      String key = readString();
      skipWhitespace();
      expect(':');
      map.put(key, readValue());
      skipWhitespace();
      char c = next();
      if (c == '}') {
        return map;
      }
      if (c != ',') {
        throw error("expected ',' or '}'");
      }
    }
  }

  private List<Object> readArray() {
    List<Object> list = new ArrayList<>();
    pos++;
    skipWhitespace();
    if (peek() == ']') {
      pos++;
      return list;
    }
    while (true) {
      list.add(readValue());
      skipWhitespace();
      char c = next();
      if (c == ']') {
        return list;
      }
      if (c != ',') {
        throw error("expected ',' or ']'");
      }
    }
  }

  private String readString() {
    pos++;
    StringBuilder sb = new StringBuilder();
    while (true) {
      char c = next();
      if (c == '"') {
        return sb.toString();
      }
      if (c != '\\') {
        sb.append(c);
        continue;
      }
      char e = next();
      switch (e) {
        case '"', '\\', '/' -> sb.append(e);
        case 'b' -> sb.append('\b');
        case 'f' -> sb.append('\f');
        case 'n' -> sb.append('\n');
        case 'r' -> sb.append('\r');
        case 't' -> sb.append('\t');
        case 'u' -> {
          if (pos + 4 > text.length()) {
            throw error("bad unicode escape");
          }
          sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
          pos += 4;
        }
        default -> throw error("bad escape");
      }
    }
  }

  private Object readLiteral(String literal, Object value) {
    if (!text.startsWith(literal, pos)) {
      throw error("unexpected literal");
    }
    pos += literal.length();
    return value;
  }

  private Double readNumber() {
    int start = pos;
    while (pos < text.length() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) {
      pos++;
    }
    if (start == pos) {
      throw error("unexpected character");
    }
    try {
      return Double.valueOf(text.substring(start, pos));
    } catch (NumberFormatException e) {
      throw error("bad number");
    }
  }

  private void expect(char c) {
    if (next() != c) {
      throw error("expected '" + c + "'");
    }
  }

  private char peek() {
    if (pos >= text.length()) {
      throw error("unexpected end");
    }
    return text.charAt(pos);
  }

  private char next() {
    char c = peek();
    pos++;
    return c;
  }

  private void skipWhitespace() {
    while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
      pos++;
    }
  }

  private IllegalArgumentException error(String message) {
    return new IllegalArgumentException("Invalid JSON at " + pos + ": " + message);
  }
}
