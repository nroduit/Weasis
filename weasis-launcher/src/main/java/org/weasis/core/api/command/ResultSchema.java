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
import java.util.List;
import org.weasis.core.api.event.EventType;

/**
 * What a verb answers: the top-level keys of its JSON body, typed with the words of the vocabulary
 * (the semantic types, {@code string}, {@code integer}, {@code number}, {@code boolean}, {@code
 * array}, {@code object}), so a caller can read a result without running the verb first. A verb
 * declares it by implementing {@link Returns}; {@code help --json} and the reference print it.
 *
 * @param fields the keys of the body, in the order the verb writes them
 */
public record ResultSchema(List<EventType.Field> fields) {

  public ResultSchema {
    fields = fields == null ? List.of() : List.copyOf(fields);
  }

  public static Builder of() {
    return new Builder();
  }

  public void write(JsonWriter out) {
    out.beginObject().name("fields").beginArray(); // NON-NLS
    for (EventType.Field field : fields) {
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
    private final List<EventType.Field> fields = new ArrayList<>();

    private Builder() {}

    /** A key whose type is a semantic type of the vocabulary ({@code ViewId} → view-id). */
    public Builder field(String name, Class<?> type, String what) {
      String typeName =
          SemanticType.class.isAssignableFrom(type)
              ? SemanticType.nameOf(type)
              : SemanticType.primitiveOf(type);
      fields.add(new EventType.Field(name, typeName, what));
      return this;
    }

    /** A key typed by a vocabulary word that is not a class: {@code array}, {@code object}. */
    public Builder field(String name, String typeName, String what) {
      fields.add(new EventType.Field(name, typeName, what));
      return this;
    }

    public ResultSchema build() {
      return new ResultSchema(fields);
    }
  }
}
