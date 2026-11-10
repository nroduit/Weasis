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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import picocli.CommandLine.Model.CommandSpec;

/**
 * The semantic types the registered namespaces use, with what each one is ({@link Definition}), the
 * verbs that consume it as an option or a parameter and the verbs that produce it ({@link
 * Produces}). It is the handle graph of the command surface: {@code help types} prints it, the
 * descriptor carries it, and the vocabulary check that every consumed handle has a producer reads
 * it.
 */
public final class TypeCatalog {

  /**
   * One type: vocabulary name, definition, whether it is a handle, who produces and consumes it.
   */
  public record TypeInfo(
      String name,
      String definition,
      boolean handle,
      List<String> producedBy,
      List<String> consumedBy) {}

  private final Map<String, Entry> entries = new TreeMap<>();

  private static final class Entry {
    final Class<?> type;
    final Set<String> producers = new TreeSet<>();
    final Set<String> consumers = new TreeSet<>();

    Entry(Class<?> type) {
      this.type = type;
    }
  }

  private TypeCatalog() {}

  /** The catalog of the namespaces given, keyed by namespace. */
  public static TypeCatalog of(Map<String, ? extends CommandExtension> namespaces) {
    TypeCatalog catalog = new TypeCatalog();
    for (Map.Entry<String, ? extends CommandExtension> e : namespaces.entrySet()) {
      Object model = e.getValue().getCommandModel();
      if (model != null) {
        catalog.add(e.getKey(), model);
      }
    }
    return catalog;
  }

  /** A catalog of one namespace, for its vocabulary test. */
  public static TypeCatalog of(String namespace, Object model) {
    TypeCatalog catalog = new TypeCatalog();
    catalog.add(namespace, model);
    return catalog;
  }

  private void add(String namespace, Object model) {
    for (CommandSpec spec : CommandModel.verbSpecs(model)) {
      String verb = namespace + ":" + spec.name();
      for (Class<?> type : CommandModel.consumedTypes(spec)) {
        entry(type).consumers.add(verb);
      }
      for (Class<?> type : CommandModel.producedTypes(spec)) {
        entry(type).producers.add(verb);
      }
    }
  }

  private Entry entry(Class<?> type) {
    return entries.computeIfAbsent(SemanticType.nameOf(type), n -> new Entry(type));
  }

  public List<TypeInfo> types() {
    List<TypeInfo> types = new ArrayList<>();
    for (Map.Entry<String, Entry> e : entries.entrySet()) {
      types.add(info(e.getKey(), e.getValue()));
    }
    return types;
  }

  public Optional<TypeInfo> find(String name) {
    Entry entry = entries.get(name.strip());
    return entry == null ? Optional.empty() : Optional.of(info(name.strip(), entry));
  }

  private static TypeInfo info(String name, Entry entry) {
    Definition definition = entry.type.getAnnotation(Definition.class);
    return new TypeInfo(
        name,
        definition == null ? "" : definition.value(),
        definition != null && definition.handle(),
        List.copyOf(entry.producers),
        List.copyOf(entry.consumers));
  }

  /** The verbs that hand out {@code type}, for an unknown-handle error or a help line. */
  public List<String> producersOf(Class<?> type) {
    Entry entry = entries.get(SemanticType.nameOf(type));
    return entry == null ? List.of() : List.copyOf(entry.producers);
  }

  /** The handle types some verb consumes and no verb produces: a hole in the handle graph. */
  public List<String> handlesWithoutProducer() {
    List<String> missing = new ArrayList<>();
    for (TypeInfo type : types()) {
      if (type.handle() && type.producedBy().isEmpty() && !type.consumedBy().isEmpty()) {
        missing.add(type.name());
      }
    }
    return missing;
  }

  /** The types without a {@link Definition}: every semantic type explains itself. */
  public List<String> undefined() {
    List<String> undefined = new ArrayList<>();
    for (TypeInfo type : types()) {
      if (type.definition().isBlank()) {
        undefined.add(type.name());
      }
    }
    return undefined;
  }

  /** One line per type, for {@code help types}. */
  public String text() {
    List<CommandHelp.Entry> rows =
        types().stream().map(t -> new CommandHelp.Entry(t.name(), summary(t))).toList();
    return CommandHelp.table("Types of the command surface", rows, false);
  }

  /** The index form of {@code help types --index}. */
  public String index() {
    List<CommandHelp.Entry> rows =
        types().stream().map(t -> new CommandHelp.Entry(t.name(), t.definition())).toList();
    return CommandHelp.index("Types", rows);
  }

  /** The detail of one type, for {@code help types <type>}. */
  public static String text(TypeInfo type) {
    StringBuilder sb = new StringBuilder(type.name()).append(": ").append(type.definition());
    if (type.handle()) {
      sb.append(" (a handle)");
    }
    if (!type.producedBy().isEmpty()) {
      sb.append('\n').append("From: ").append(String.join(", ", type.producedBy()));
    }
    if (!type.consumedBy().isEmpty()) {
      sb.append('\n').append("Used by: ").append(String.join(", ", type.consumedBy()));
    }
    return sb.append('\n').toString();
  }

  private static String summary(TypeInfo type) {
    return type.producedBy().isEmpty()
        ? type.definition()
        : type.definition() + " (from " + String.join(", ", type.producedBy()) + ")";
  }

  /** The Markdown table of the types, for the reference. */
  public String markdown() {
    StringBuilder sb = new StringBuilder("## Types\n\n");
    sb.append("| Type | Definition | From | Used by |\n|---|---|---|---|\n");
    for (TypeInfo type : types()) {
      sb.append("| ")
          .append(type.name())
          .append(" | ")
          .append(type.definition().replace("|", "\\|"))
          .append(type.handle() ? " (handle)" : "")
          .append(" | ")
          .append(String.join(", ", type.producedBy()))
          .append(" | ")
          .append(String.join(", ", type.consumedBy()))
          .append(" |\n");
    }
    return sb.append('\n').toString();
  }

  /** The {@code types} array of the descriptor. */
  public void write(JsonWriter out) {
    out.beginArray();
    for (TypeInfo type : types()) {
      out.beginObject()
          .name("name") // NON-NLS
          .value(type.name())
          .name("label") // NON-NLS
          .value("<" + type.name() + ">")
          .name("definition") // NON-NLS
          .value(type.definition())
          .name("handle") // NON-NLS
          .value(type.handle());
      writeList(out, "producedBy", type.producedBy()); // NON-NLS
      writeList(out, "consumedBy", type.consumedBy()); // NON-NLS
      out.endObject();
    }
    out.endArray();
  }

  private static void writeList(JsonWriter out, String name, Collection<String> values) {
    out.name(name).beginArray();
    values.forEach(out::value);
    out.endArray();
  }
}
