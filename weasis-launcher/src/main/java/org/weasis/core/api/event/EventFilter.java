/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.event;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Which events a subscriber wants: globs on the name ({@code dicom:*}, {@code
 * view2d:windowLevel.*}, {@code *}) and, optionally, payload values that must match ({@code
 * view=v3}). An empty filter matches everything.
 *
 * @param globs the name patterns, any of which may match
 * @param where payload key to expected text, all of which must match
 */
public record EventFilter(List<String> globs, Map<String, String> where) {

  /** Matches every event. */
  public static final EventFilter ALL = new EventFilter(List.of(), Map.of());

  public EventFilter {
    globs = globs == null ? List.of() : List.copyOf(globs);
    where = where == null ? Map.of() : Map.copyOf(where);
  }

  /** Globs separated by commas or whitespace. */
  public static EventFilter parse(String text) {
    return new EventFilter(globList(text), Map.of());
  }

  /** The globs of {@code text} with the {@code key=value} conditions of {@code conditions}. */
  public static EventFilter parse(String text, List<String> conditions) {
    Map<String, String> where = new LinkedHashMap<>();
    if (conditions != null) {
      for (String condition : conditions) {
        int equals = condition.indexOf('=');
        if (equals <= 0) {
          throw new IllegalArgumentException("A condition is key=value: " + condition);
        }
        where.put(condition.substring(0, equals).strip(), condition.substring(equals + 1).strip());
      }
    }
    return new EventFilter(globList(text), where);
  }

  private static List<String> globList(String text) {
    List<String> globs = new ArrayList<>();
    if (text != null) {
      for (String glob : text.split("[,\\s]+")) {
        if (!glob.isBlank() && !"*".equals(glob)) {
          globs.add(glob.strip());
        }
      }
    }
    return globs;
  }

  public EventFilter withGlobs(List<String> names) {
    return new EventFilter(names, where);
  }

  public boolean matches(Event event) {
    return matchesName(event.name()) && matchesPayload(event);
  }

  public boolean matchesName(String name) {
    if (globs.isEmpty()) {
      return true;
    }
    for (String glob : globs) {
      if (globMatches(glob, name)) {
        return true;
      }
    }
    return false;
  }

  private boolean matchesPayload(Event event) {
    for (Map.Entry<String, String> condition : where.entrySet()) {
      Object value = event.get(condition.getKey());
      if (value == null || !String.valueOf(value).equals(condition.getValue())) {
        return false;
      }
    }
    return true;
  }

  /** {@code *} matches any run of characters, including none; the rest is literal. */
  static boolean globMatches(String glob, String name) {
    if (glob.indexOf('*') < 0) {
      return glob.equals(name);
    }
    StringBuilder regex = new StringBuilder();
    for (String part : glob.split("\\*", -1)) {
      if (!regex.isEmpty()) {
        regex.append(".*");
      }
      regex.append(Pattern.quote(part));
    }
    return Pattern.matches(regex.toString(), name);
  }

  /** The filter as the caller wrote it, for the audit and the listing of subscribers. */
  public String describe() {
    String names = globs.isEmpty() ? "*" : String.join(",", globs);
    if (where.isEmpty()) {
      return names;
    }
    StringBuilder sb = new StringBuilder(names);
    where.forEach((key, value) -> sb.append(' ').append(key).append('=').append(value));
    return sb.toString();
  }
}
