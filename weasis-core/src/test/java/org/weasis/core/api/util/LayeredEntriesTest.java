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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.util.LayeredEntries.Entry;
import org.weasis.core.api.util.LayeredEntries.Layer;
import org.weasis.core.api.util.LayeredEntries.Merged;
import org.weasis.core.api.util.LayeredEntries.Origin;

@DisplayNameGeneration(ReplaceUnderscores.class)
class LayeredEntriesTest {

  private record Item(String id, String name, boolean hidden, boolean locked) implements Entry {
    static Item of(String id, String name) {
      return new Item(id, name, false, false);
    }
  }

  @Test
  void a_later_layer_replaces_the_entry_and_keeps_its_position() {
    List<Item> bundled = List.of(Item.of("a", "A"), Item.of("b", "B"), Item.of("c", "C"));
    List<Item> site = List.of(Item.of("c", "Site C"), Item.of("s", "S"));
    List<Item> user = List.of(Item.of("a", "My A"), Item.of("u", "U"));

    Merged<Item> merged =
        LayeredEntries.merge(
            List.of(
                Layer.of(Origin.BUILT_IN, bundled),
                Layer.of(Origin.SITE, site),
                Layer.of(Origin.USER, user)),
            "item");

    assertAll(
        () ->
            assertEquals(
                List.of("a", "b", "c", "s", "u"), merged.entries().stream().map(Item::id).toList()),
        () -> assertEquals("My A", merged.entries().getFirst().name()),
        () -> assertEquals("Site C", merged.entries().get(2).name()),
        () -> assertEquals(Origin.USER, merged.origin("a")),
        () -> assertEquals(Origin.SITE, merged.origin("c")),
        () -> assertEquals(Origin.BUILT_IN, merged.origin("b")),
        () -> assertEquals(Origin.BUILT_IN, merged.origin("unknown")),
        () -> assertEquals(Origin.USER.ordinal(), merged.layerOf("a")),
        () -> assertTrue(merged.locked().isEmpty()));
  }

  @Test
  void below_names_the_definition_a_user_entry_hides() {
    List<Item> bundled = List.of(Item.of("a", "A"), Item.of("b", "B"));
    List<Item> plugin = List.of(Item.of("a", "Plugin A"));
    List<Item> site = List.of(Item.of("b", "Site B"));
    List<Item> user = List.of(Item.of("a", "My A"), Item.of("b", "My B"), Item.of("u", "U"));

    Merged<Item> merged =
        LayeredEntries.merge(
            List.of(
                Layer.of(Origin.BUILT_IN, bundled),
                Layer.of(Origin.CONTRIBUTED, plugin),
                Layer.of(Origin.SITE, site),
                Layer.of(Origin.USER, user)),
            "item");

    assertAll(
        () -> assertEquals(Optional.of(Origin.CONTRIBUTED), merged.below("a")),
        () -> assertEquals(Optional.of(Origin.SITE), merged.below("b")),
        () -> assertTrue(merged.below("u").isEmpty(), "nothing under the user entry"),
        () -> assertTrue(merged.below("x").isEmpty(), "unknown id"),
        () -> assertTrue(merged.below(null).isEmpty()),
        () -> assertEquals(Optional.of(Origin.BUILT_IN), merged.below("a", Origin.CONTRIBUTED)),
        () -> assertTrue(merged.below("b", Origin.CONTRIBUTED).isEmpty(), "plugin defines no b"));
  }

  @Test
  void a_locked_site_entry_ignores_the_user_definition_and_only_the_site_can_lock() {
    List<Item> bundled = List.of(new Item("a", "A", false, true), Item.of("b", "B"));
    List<Item> site = List.of(new Item("b", "Site B", false, true));
    List<Item> user =
        List.of(Item.of("a", "My A"), new Item("b", "My B", true, false), Item.of("u", "U"));

    Merged<Item> merged =
        LayeredEntries.merge(
            List.of(
                Layer.of(Origin.BUILT_IN, bundled),
                Layer.of(Origin.SITE, site),
                Layer.of(Origin.USER, user)),
            "item");

    assertAll(
        () -> assertEquals("My A", merged.entries().getFirst().name(), "bundled cannot lock"),
        () -> assertEquals("Site B", merged.entries().get(1).name()),
        () -> assertFalse(merged.entries().get(1).hidden(), "the user could not hide it"),
        () -> assertEquals(Origin.SITE, merged.origin("b")),
        () -> assertTrue(merged.isLocked("b")),
        () -> assertFalse(merged.isLocked("a")),
        () -> assertEquals(3, merged.entries().size()));
  }

  @Test
  void entries_without_the_interface_merge_by_an_id_function_and_a_lock_set() {
    record Plain(String key, String name) {}
    List<Plain> bundled = List.of(new Plain("a", "A"), new Plain("b", "B"));
    List<Plain> site = List.of(new Plain("a", "Site A"));
    List<Plain> user = List.of(new Plain("a", "My A"), new Plain("b", "My B"));

    Merged<Plain> merged =
        LayeredEntries.merge(
            List.of(
                Layer.of(Origin.BUILT_IN, bundled, Set.of()),
                Layer.of(Origin.SITE, site, Set.of("a")),
                Layer.of(Origin.USER, user, Set.of())),
            Plain::key,
            "plain");

    assertAll(
        () -> assertEquals("Site A", merged.entries().getFirst().name(), "locked by the site"),
        () -> assertEquals("My B", merged.entries().get(1).name()),
        () -> assertTrue(merged.isLocked("a")),
        () -> assertEquals(Optional.of(Origin.SITE), merged.below("a")),
        () -> assertEquals(Optional.of(Origin.BUILT_IN), merged.below("b")));
  }

  @Test
  void a_hidden_entry_stays_in_the_merge_to_replace_the_lower_layers() {
    List<Item> bundled = List.of(Item.of("a", "A"));
    List<Item> site = List.of(new Item("a", "A", true, false));

    Merged<Item> merged =
        LayeredEntries.merge(
            List.of(Layer.of(Origin.BUILT_IN, bundled), Layer.of(Origin.SITE, site)), "item");

    assertAll(
        () -> assertEquals(1, merged.entries().size()),
        () -> assertTrue(merged.entries().getFirst().hidden()));
  }

  @Test
  void pick_prefers_the_most_specific_layer_then_the_first_in_order() {
    List<Item> candidates =
        List.of(Item.of("b1", "B1"), Item.of("s1", "S1"), Item.of("b2", "B2"), Item.of("s2", "S2"));

    Optional<Item> winner =
        LayeredEntries.pick(
            candidates, item -> item.id().startsWith("s") ? 1 : 0, Item::id, "key", "CT");

    assertAll(
        () -> assertEquals("s1", winner.orElseThrow().id()),
        () ->
            assertTrue(
                LayeredEntries.pick(List.of(), i -> 0, Object::toString, "k", "x").isEmpty()),
        () ->
            assertEquals(
                "b1",
                LayeredEntries.pick(candidates.subList(0, 1), i -> 0, Item::id, "k", "x")
                    .orElseThrow()
                    .id()));
  }
}
