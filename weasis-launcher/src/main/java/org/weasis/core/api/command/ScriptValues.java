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

import com.formdev.flatlaf.json.Json;
import com.formdev.flatlaf.json.ParseException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a script knows while it runs: the parameters it was given and the body of each step kept
 * under a name. A reference {@code ${name.path[0].key}} reads inside a body — handles such as a
 * view id exist only at run time, so a script names them rather than writing them down. An array is
 * indexed by position, or by the value of one of its fields ({@code [key=length]}), which is how a
 * measurement is picked out of a list.
 */
public final class ScriptValues {

  private static final Pattern REFERENCE = Pattern.compile("\\$\\{([^}]+)}");
  private static final Pattern STEP = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)|\\[([^]]+)]");

  private final Map<String, Object> parameters = new LinkedHashMap<>();
  private final Map<String, Object> bodies = new LinkedHashMap<>();

  public void setParameter(String name, String value) {
    parameters.put(name, value);
  }

  /** Keeps the body of a step under {@code name}; a body that is not JSON is kept as text. */
  public void setBody(String name, String body) {
    bodies.put(name, parse(body));
  }

  /** The value a reference points at, or {@code null} when nothing does. */
  public Object value(String reference) {
    Matcher matcher = STEP.matcher(reference);
    Object current = null;
    boolean first = true;
    while (matcher.find()) {
      String name = matcher.group(1);
      String index = matcher.group(2);
      if (first) {
        if (name == null) {
          return null;
        }
        current = bodies.containsKey(name) ? bodies.get(name) : parameters.get(name);
        first = false;
        continue;
      }
      current = name != null ? field(current, name) : element(current, index);
    }
    return first ? null : current;
  }

  /** {@code text} with every reference replaced by what it points at. */
  public String substitute(String text) {
    Matcher matcher = REFERENCE.matcher(text);
    StringBuilder out = new StringBuilder();
    while (matcher.find()) {
      Object value = value(matcher.group(1).strip());
      if (value == null) {
        throw new IllegalArgumentException("Nothing named '" + matcher.group(1).strip() + "'");
      }
      matcher.appendReplacement(out, Matcher.quoteReplacement(asText(value)));
    }
    matcher.appendTail(out);
    return out.toString();
  }

  /** The text of a value: a whole number keeps no decimal point, so it reads back as an index. */
  public static String asText(Object value) {
    if (value instanceof Double number && number == Math.floor(number) && !number.isInfinite()) {
      return String.valueOf(number.longValue());
    }
    return String.valueOf(value);
  }

  @SuppressWarnings("unchecked")
  private static Object field(Object value, String name) {
    return value instanceof Map<?, ?> map ? ((Map<String, Object>) map).get(name) : null;
  }

  private static Object element(Object value, String index) {
    if (!(value instanceof List<?> list)) {
      return null;
    }
    int equals = index.indexOf('=');
    if (equals < 0) {
      try {
        int position = Integer.parseInt(index.strip());
        return position >= 0 && position < list.size() ? list.get(position) : null;
      } catch (NumberFormatException e) {
        return null;
      }
    }
    String field = index.substring(0, equals).strip();
    String expected = index.substring(equals + 1).strip();
    return list.stream()
        .filter(item -> expected.equals(asText(field(item, field))))
        .findFirst()
        .orElse(null);
  }

  /** The body as a structure, or as text when it is not JSON. */
  private static Object parse(String body) {
    String text = body == null ? "" : body.strip();
    if (!text.startsWith("{") && !text.startsWith("[")) {
      return text;
    }
    try (StringReader reader = new StringReader(text)) {
      return Json.parse(reader);
    } catch (ParseException | java.io.IOException e) {
      return text;
    }
  }

  /** The names a script knows, for an error that says what it could have meant. */
  public List<String> names() {
    List<String> names = new ArrayList<>(bodies.keySet());
    names.addAll(parameters.keySet());
    return names;
  }
}
