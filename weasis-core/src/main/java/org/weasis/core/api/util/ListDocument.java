/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.util;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonStructure;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * The envelope shared by the list documents of the configuration (DICOM nodes, printers, calling
 * nodes, DICOMweb nodes, authentication methods, search templates): {@code {"schema": 1, "updated":
 * "<instant>", "<entries>": [...]}}. A bare array is accepted when reading. The {@code updated}
 * instant is written on every save, so that a later merge with a remote copy has something to
 * compare.
 */
public final class ListDocument {

  public static final int SCHEMA_VERSION = 1;
  public static final String SCHEMA = "schema"; // NON-NLS
  public static final String UPDATED = "updated"; // NON-NLS

  /**
   * A document read: its entries in order and, when the envelope carried it, its update instant.
   */
  public record Content(int schema, Instant updated, List<JsonObject> entries) {
    public static final Content EMPTY = new Content(SCHEMA_VERSION, null, List.of());
  }

  private ListDocument() {}

  /**
   * @param file the document
   * @param entries the name of the array of entries in the envelope
   * @throws IOException when the file cannot be read, is not JSON or has a newer schema
   */
  public static Content read(Path file, String entries) throws IOException {
    try (InputStream in = Files.newInputStream(file)) {
      return read(in, entries);
    } catch (JsonException | IllegalArgumentException e) {
      throw new IOException("Invalid document " + file, e);
    }
  }

  /**
   * @throws IllegalArgumentException when the schema is newer than {@link #SCHEMA_VERSION}
   * @throws JsonException when the content is not JSON
   */
  public static Content read(InputStream in, String entries) {
    try (JsonReader reader = Json.createReader(in)) {
      JsonStructure structure = reader.read();
      return switch (structure) {
        case JsonArray array -> new Content(SCHEMA_VERSION, null, JsonUtil.objects(array));
        case JsonObject envelope -> {
          int schema = JsonUtil.getInt(envelope, SCHEMA, SCHEMA_VERSION);
          if (schema > SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                "Schema %d of %s is newer than the supported %d"
                    .formatted(schema, entries, SCHEMA_VERSION));
          }
          JsonArray array = envelope.get(entries) instanceof JsonArray a ? a : null;
          yield new Content(
              schema, JsonUtil.getInstant(envelope, UPDATED), JsonUtil.objects(array));
        }
        default -> Content.EMPTY;
      };
    }
  }

  /** The envelope of the entries, at the current schema, stamped now. */
  public static JsonObject envelope(String entries, Collection<JsonObject> objects) {
    JsonArrayBuilder array = Json.createArrayBuilder();
    objects.forEach(array::add);
    JsonObjectBuilder b =
        Json.createObjectBuilder()
            .add(SCHEMA, SCHEMA_VERSION)
            .add(UPDATED, Instant.now().toString())
            .add(entries, array);
    return b.build();
  }

  /**
   * Writes the entries in an envelope, through a temporary file so that a crash never leaves a
   * truncated document; the parent folder is created when needed.
   */
  public static void write(Path file, String entries, Collection<JsonObject> objects)
      throws IOException {
    Path parent = file.toAbsolutePath().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Path temp = Files.createTempFile(parent, file.getFileName().toString(), ".tmp"); // NON-NLS
    try {
      JsonUtil.write(temp, envelope(entries, objects));
      Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      Files.deleteIfExists(temp);
      throw e;
    }
  }
}
