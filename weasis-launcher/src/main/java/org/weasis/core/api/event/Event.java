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

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.weasis.core.api.command.CommandContext;
import org.weasis.core.api.command.JsonWriter;

/**
 * One change of state, as the bus carries it and as the socket streams it: a name in the command
 * vocabulary ({@code namespace:subject.change}), the global sequence number, the time it was
 * published, where it came from and a payload of handles and small values — never a model object or
 * a Swing type, so a remote caller and a UI part read the same thing.
 *
 * @param seq the global sequence number, 0 until the bus dispatched it
 * @param origin {@code app} for the application itself, else the lower-case command origin of the
 *     call that caused the change
 * @param coalesceKey when two pending events share it, a slow subscriber gets the last one only
 * @param identity the payload keys that carry identity, with the kind of identity each one carries,
 *     so a remote subscriber sees them through the masking profile of the instance
 */
public record Event(
    String name,
    long seq,
    Instant time,
    String origin,
    Map<String, Object> payload,
    String coalesceKey,
    Map<String, String> identity) {

  /** Origin of an event the application publishes outside any command. */
  public static final String APP_ORIGIN = "app"; // NON-NLS

  /** Payload keys the envelope reserves; a payload cannot use them. */
  public static final Set<String> RESERVED = Set.of("name", "seq", "time", "origin"); // NON-NLS

  /** Kinds of identity a payload field may carry; the masker of the instance knows each one. */
  public static final String PATIENT_NAME = "patient-name"; // NON-NLS

  public static final String PATIENT_ID = "patient-id"; // NON-NLS
  public static final String BIRTH_DATE = "birth-date"; // NON-NLS
  public static final String ACCESSION_NUMBER = "accession-number"; // NON-NLS
  public static final String DESCRIPTION = "description"; // NON-NLS
  public static final String DATE = "date"; // NON-NLS
  public static final String FREE_TEXT = "free-text"; // NON-NLS

  public Event {
    Objects.requireNonNull(name, "name");
    if (name.indexOf(':') <= 0) {
      throw new IllegalArgumentException("An event is named namespace:subject.change: " + name);
    }
    time = time == null ? Instant.now() : time;
    origin = origin == null ? APP_ORIGIN : origin;
    payload = payload == null ? Map.of() : copy(payload);
    identity = identity == null ? Map.of() : Map.copyOf(identity);
  }

  private static Map<String, Object> copy(Map<String, Object> values) {
    Map<String, Object> copy = new LinkedHashMap<>();
    values.forEach(
        (key, value) -> {
          if (RESERVED.contains(key)) {
            throw new IllegalArgumentException("Reserved payload key: " + key);
          }
          if (value != null) {
            copy.put(key, value);
          }
        });
    return java.util.Collections.unmodifiableMap(copy);
  }

  /** Starts an event of that name, with the origin of the running command when there is one. */
  public static Builder of(String name) {
    return new Builder(name);
  }

  /** The namespace part of the name. */
  public String namespace() {
    return name.substring(0, name.indexOf(':'));
  }

  /** The same event with its place in the global order. */
  public Event numbered(long sequence) {
    return new Event(name, sequence, time, origin, payload, coalesceKey, identity);
  }

  /** The same event with another payload, which is how a mask rewrites it. */
  public Event withPayload(Map<String, Object> values, Map<String, String> identityKinds) {
    return new Event(name, seq, time, origin, values, coalesceKey, identityKinds);
  }

  /** A payload value, or {@code null}. */
  public Object get(String key) {
    return payload.get(key);
  }

  /** The envelope and the payload as one JSON object, on one line for a stream. */
  public String toJson() {
    return toJson(false);
  }

  public String toJson(boolean indent) {
    JsonWriter out = new JsonWriter(indent);
    write(out);
    return out.toString();
  }

  /** Writes this event as a JSON object into {@code out}. */
  public void write(JsonWriter out) {
    out.beginObject()
        .name("seq") // NON-NLS
        .value(seq)
        .name("name") // NON-NLS
        .value(name)
        .name("time") // NON-NLS
        .value(time.toString())
        .name("origin") // NON-NLS
        .value(origin);
    payload.forEach((key, value) -> value(out.name(key), value));
    out.endObject();
  }

  private static void value(JsonWriter out, Object value) {
    switch (value) {
      case null -> out.value((String) null);
      case Boolean b -> out.value(b);
      case Number n -> out.value(n);
      case Collection<?> values -> {
        out.beginArray();
        values.forEach(v -> value(out, v));
        out.endArray();
      }
      case Map<?, ?> map -> {
        out.beginObject();
        map.forEach((k, v) -> value(out.name(String.valueOf(k)), v));
        out.endObject();
      }
      default -> out.value(String.valueOf(value));
    }
  }

  /** Builds the payload; a value that is {@code null} is left out, a reserved key refused. */
  public static final class Builder {
    private final String name;
    private final Map<String, Object> payload = new LinkedHashMap<>();
    private final Map<String, String> identity = new LinkedHashMap<>();
    private String coalesceKey;
    private String origin;

    private Builder(String name) {
      this.name = name;
    }

    public Builder put(String key, Object value) {
      if (value != null) {
        payload.put(key, normalize(value));
      }
      return this;
    }

    public Builder put(String key, List<?> values) {
      if (values != null && !values.isEmpty()) {
        payload.put(key, List.copyOf(values));
      }
      return this;
    }

    /** A value that identifies a person: masked for a remote subscriber, by its kind. */
    public Builder identity(String key, Object value, String kind) {
      if (value != null) {
        put(key, value);
        identity.put(key, Objects.requireNonNull(kind));
      }
      return this;
    }

    /** Only the last pending event with this key reaches a subscriber that fell behind. */
    public Builder coalesce(String key) {
      this.coalesceKey = key;
      return this;
    }

    /** The command origin that caused the change, when the builder runs outside its context. */
    public Builder origin(org.weasis.core.api.command.CommandOrigin commandOrigin) {
      this.origin = commandOrigin == null ? null : originName(commandOrigin);
      return this;
    }

    public Event build() {
      return new Event(
          name,
          0,
          Instant.now(),
          origin != null ? origin : currentOrigin(),
          payload,
          coalesceKey,
          identity);
    }

    private static String originName(org.weasis.core.api.command.CommandOrigin commandOrigin) {
      return commandOrigin.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static Object normalize(Object value) {
      if (value instanceof Enum<?> constant) {
        return constant.name().toLowerCase(Locale.ROOT).replace('_', '-');
      }
      if (value instanceof Boolean
          || value instanceof Number
          || value instanceof String
          || value instanceof java.time.temporal.Temporal
          || value instanceof Collection<?>
          || value instanceof Map<?, ?>) {
        return value; // a date stays a date, so a mask can shift it
      }
      return String.valueOf(value);
    }

    private static String currentOrigin() {
      return CommandContext.isBound() ? originName(CommandContext.current().origin()) : APP_ORIGIN;
    }
  }
}
