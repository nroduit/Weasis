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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import org.weasis.core.api.command.JsonWriter;

/**
 * Every event this instance may publish, by name. The bus declares its own, the command manager
 * adds what each loaded namespace declares, and core services declare the events of a family they
 * bridge. {@code events:list} prints it and the help of a namespace lists its events next to its
 * verbs.
 */
public final class EventCatalog {

  private final ConcurrentMap<String, EventType> types = new ConcurrentSkipListMap<>();

  public void declare(EventType... declared) {
    for (EventType type : declared) {
      types.put(type.name(), type);
    }
  }

  public void declare(Collection<EventType> declared) {
    declared.forEach(type -> types.put(type.name(), type));
  }

  /** Forgets the events of a namespace, when its plugin is unloaded. */
  public void forget(String namespace) {
    types.keySet().removeIf(name -> name.startsWith(namespace + ":"));
  }

  public Optional<EventType> find(String name) {
    return Optional.ofNullable(types.get(name));
  }

  /** Every declared event, in name order. */
  public List<EventType> types() {
    return List.copyOf(types.values());
  }

  /** The events of one namespace, in name order. */
  public List<EventType> types(String namespace) {
    return types.values().stream().filter(t -> t.namespace().equals(namespace)).toList();
  }

  /** The namespaces that declare at least one event, in order. */
  public List<String> namespaces() {
    return types.values().stream().map(EventType::namespace).distinct().toList();
  }

  /** Writes the events of a namespace as a JSON array. */
  public void write(JsonWriter out, Collection<EventType> events) {
    out.beginArray();
    events.forEach(type -> type.write(out));
    out.endArray();
  }

  /** The whole catalog as one JSON object, namespaces as keys. */
  public String toJson() {
    JsonWriter out = new JsonWriter();
    out.beginObject().name("events").beginObject(); // NON-NLS
    Map<String, List<EventType>> byNamespace = new TreeMap<>();
    types
        .values()
        .forEach(
            t ->
                byNamespace
                    .computeIfAbsent(t.namespace(), k -> new java.util.ArrayList<>())
                    .add(t));
    byNamespace.forEach(
        (namespace, events) -> {
          out.name(namespace);
          write(out, events);
        });
    return out.endObject().endObject().toString();
  }

  /** A Markdown table of the given events: name, since, coalesced, fields, description. */
  public static String markdownTable(Collection<EventType> events) {
    StringBuilder sb =
        new StringBuilder("| Event | Since | Payload | Description |\n|---|---|---|---|\n");
    for (EventType type : events) {
      sb.append("| `")
          .append(type.name())
          .append(type.coalesced() ? "` (coalesced)" : "`")
          .append(" | ")
          .append(type.since() == null ? "" : type.since())
          .append(" | ")
          .append(fields(type))
          .append(" | ")
          .append(cell(type.description()))
          .append(" |\n");
    }
    return sb.toString();
  }

  /** A plain-text listing of the given events, one per line with its payload keys. */
  public static String text(Collection<EventType> events) {
    StringBuilder sb = new StringBuilder();
    for (EventType type : events) {
      sb.append("  ").append(type.name());
      if (type.coalesced()) {
        sb.append(" (coalesced)");
      }
      if (!type.fields().isEmpty()) {
        sb.append("  {").append(fields(type)).append('}');
      }
      sb.append('\n');
      if (type.description() != null && !type.description().isBlank()) {
        sb.append("      ").append(type.description()).append('\n');
      }
    }
    return sb.toString();
  }

  private static String fields(EventType type) {
    StringBuilder sb = new StringBuilder();
    for (EventType.Field field : type.fields()) {
      if (!sb.isEmpty()) {
        sb.append(", ");
      }
      sb.append(field.name()).append(": ").append(field.type());
    }
    return sb.toString();
  }

  private static String cell(String text) {
    return text == null ? "" : text.replace("|", "\\|").replace('\n', ' ').trim();
  }
}
