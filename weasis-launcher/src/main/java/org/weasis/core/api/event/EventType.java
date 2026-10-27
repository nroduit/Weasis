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
import java.util.List;
import java.util.Objects;
import org.weasis.core.api.command.JsonWriter;
import org.weasis.core.api.command.SemanticType;

/**
 * What the catalog says about one event: its name, the version that introduced it, what it tells,
 * whether a slow subscriber may see only the last one of a run, and the fields of its payload with
 * their vocabulary types. A plugin declares its events where it declares its verbs ({@code
 * CommandExtension.getEventTypes()}); an event, once published in a release, keeps its name and its
 * fields within a major.
 */
public record EventType(
    String name, String since, String description, boolean coalesced, List<Field> fields) {

  /** One payload field: its key, its type in the vocabulary and what it holds. */
  public record Field(String name, String type, String description) {}

  public EventType {
    Objects.requireNonNull(name);
    if (name.indexOf(':') <= 0) {
      throw new IllegalArgumentException("An event is named namespace:subject.change: " + name);
    }
    fields = fields == null ? List.of() : List.copyOf(fields);
  }

  public static Builder of(String name, String since, String description) {
    return new Builder(name, since, description);
  }

  public String namespace() {
    return name.substring(0, name.indexOf(':'));
  }

  /** The name without its namespace. */
  public String localName() {
    return name.substring(name.indexOf(':') + 1);
  }

  /** The JSON descriptor of this event, as {@code events:list --json} and the help print it. */
  public void write(JsonWriter out) {
    out.beginObject().name("name").value(name); // NON-NLS
    out.optional("since", since); // NON-NLS
    out.optional("description", description); // NON-NLS
    out.name("coalesced").value(coalesced); // NON-NLS
    out.name("fields").beginArray(); // NON-NLS
    for (Field field : fields) {
      out.beginObject()
          .name("name") // NON-NLS
          .value(field.name())
          .name("type") // NON-NLS
          .value(field.type())
          .name("description") // NON-NLS
          .value(field.description())
          .endObject();
    }
    out.endArray().endObject();
  }

  public static final class Builder {
    private final String name;
    private final String since;
    private final String description;
    private boolean coalesced;
    private final List<Field> fields = new ArrayList<>();

    private Builder(String name, String since, String description) {
      this.name = name;
      this.since = since;
      this.description = description;
    }

    public Builder coalesced() {
      this.coalesced = true;
      return this;
    }

    /** A field whose type is a semantic type of the vocabulary ({@code ViewId} → view-id). */
    public Builder field(String fieldName, Class<?> type, String what) {
      String typeName =
          SemanticType.class.isAssignableFrom(type)
              ? SemanticType.nameOf(type)
              : SemanticType.primitiveOf(type);
      fields.add(new Field(fieldName, typeName, what));
      return this;
    }

    /** A field typed by a vocabulary word that is not a class: {@code array}, {@code object}. */
    public Builder field(String fieldName, String typeName, String what) {
      fields.add(new Field(fieldName, typeName, what));
      return this;
    }

    public EventType build() {
      return new EventType(name, since, description, coalesced, fields);
    }
  }
}
