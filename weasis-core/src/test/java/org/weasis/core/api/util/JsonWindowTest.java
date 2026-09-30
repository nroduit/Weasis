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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.command.ListOptions;

/** A caller with a small context reads a listing in windows and is told where the next one is. */
class JsonWindowTest {

  private static final List<JsonObject> ENTRIES =
      IntStream.rangeClosed(1, 5)
          .mapToObj(i -> Json.createObjectBuilder().add("n", i).build())
          .map(JsonObject.class::cast)
          .toList();

  private static JsonObject window(Integer limit, Integer after) {
    ListOptions bounds = new ListOptions();
    if (limit != null) {
      set(bounds, "limit", limit);
    }
    if (after != null) {
      set(bounds, "after", after);
    }
    return JsonUtil.window(Json.createObjectBuilder(), "entries", ENTRIES, bounds).build();
  }

  private static void set(ListOptions bounds, String field, int value) {
    try {
      var f = ListOptions.class.getDeclaredField(field);
      f.setAccessible(true); // the options are set by the parser, not by hand
      f.set(bounds, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void theWholeListingIsTheDefault() {
    JsonObject body = window(null, null);
    assertAll(
        () -> assertEquals(5, body.getJsonArray("entries").size()),
        () -> assertFalse(body.containsKey("total"), "nothing to say when nothing is left out"),
        () -> assertFalse(body.containsKey("truncated"), body.toString()));
  }

  @Test
  void aWindowSaysWhatItLeftOutAndWhereToGoOn() {
    JsonObject first = window(2, null);
    JsonObject next = window(2, 2);
    JsonObject last = window(2, 4);
    assertAll(
        () -> assertEquals(2, first.getJsonArray("entries").size()),
        () -> assertEquals(1, first.getJsonArray("entries").getJsonObject(0).getInt("n")),
        () -> assertEquals(5, first.getInt("total")),
        () -> assertTrue(first.getBoolean("truncated")),
        () -> assertEquals(2, first.getInt("next")),
        () -> assertEquals(3, next.getJsonArray("entries").getJsonObject(0).getInt("n")),
        () -> assertEquals(1, last.getJsonArray("entries").size()),
        () -> assertFalse(last.getBoolean("truncated"), last.toString()),
        () -> assertFalse(last.containsKey("next"), last.toString()));
  }
}
