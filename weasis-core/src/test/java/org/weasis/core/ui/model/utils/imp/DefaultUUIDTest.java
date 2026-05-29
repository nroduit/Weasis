/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.utils.imp;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link DefaultUUID} — the UUID-keyed identity base used by {@code ReferencedSeries}, {@code
 * ReferencedImage}, and persisted graphics. Two instances are equal iff their UUIDs match AND they
 * are the same concrete class; class-narrowing prevents a {@code ReferencedSeries} with UUID "X"
 * from comparing equal to a {@code ReferencedImage} with the same UUID — that would silently merge
 * two distinct entities in the persisted graphic model.
 */
class DefaultUUIDTest {

  // -- Constructors --------------------------------------------------------

  @Test
  void defaultConstructor_generatesUuid() {
    DefaultUUID u = new DefaultUUID();

    assertAll(
        () -> assertNotNull(u.getUuid(), "UUID must be generated"),
        () -> assertEquals(36, u.getUuid().length(), "UUID is the canonical 36-char form"),
        () -> assertTrue(u.getUuid().contains("-"), "UUID contains separators"));
  }

  @Test
  void defaultConstructor_generatesUniqueUuids() {
    DefaultUUID a = new DefaultUUID();
    DefaultUUID b = new DefaultUUID();

    assertNotEquals(a.getUuid(), b.getUuid(), "each instance gets a unique UUID");
  }

  @Test
  void stringConstructor_acceptsExplicitUuid() {
    String uuid = "550e8400-e29b-41d4-a716-446655440000";

    DefaultUUID u = new DefaultUUID(uuid);

    assertEquals(uuid, u.getUuid());
  }

  @Test
  void stringConstructor_nullFallsBackToRandomUuid() {
    DefaultUUID u = new DefaultUUID((String) null);

    assertAll(
        () -> assertNotNull(u.getUuid()),
        () ->
            assertEquals(36, u.getUuid().length(), "fallback is a real UUID, not an empty string"));
  }

  @Test
  void setUuid_replacesValue() {
    DefaultUUID u = new DefaultUUID();
    String newUuid = UUID.randomUUID().toString();

    u.setUuid(newUuid);

    assertEquals(newUuid, u.getUuid());
  }

  @Test
  void setUuid_nullFallsBackToRandom() {
    DefaultUUID u = new DefaultUUID("550e8400-e29b-41d4-a716-446655440000");

    u.setUuid(null);

    assertAll(
        () -> assertNotNull(u.getUuid()),
        () ->
            assertNotEquals(
                "550e8400-e29b-41d4-a716-446655440000",
                u.getUuid(),
                "null replaces, not preserves"));
  }

  // -- equals / hashCode (UUID-based, class-narrowed) ----------------------

  @Test
  void equals_sameUuidSameClassAreEqual() {
    String uuid = UUID.randomUUID().toString();
    DefaultUUID a = new DefaultUUID(uuid);
    DefaultUUID b = new DefaultUUID(uuid);

    assertAll(
        () -> assertEquals(a, b),
        () -> assertEquals(a.hashCode(), b.hashCode(), "equal objects must share hashCode"));
  }

  @Test
  void equals_differentUuidAreNotEqual() {
    DefaultUUID a = new DefaultUUID();
    DefaultUUID b = new DefaultUUID();

    assertNotEquals(a, b, "distinct UUIDs -> not equal");
  }

  @Test
  void equals_reflexive() {
    DefaultUUID a = new DefaultUUID();

    assertEquals(a, a);
  }

  @Test
  void equals_nullIsNotEqual() {
    assertNotEquals(new DefaultUUID(), null);
  }

  @Test
  void equals_crossSubclassWithSameUuidIsNotEqual() {
    // Class-narrowed equality: a ReferencedSeries and a ReferencedImage that happen to share a
    // UUID must NOT collapse into a single entity in the persisted graphic model.
    String uuid = UUID.randomUUID().toString();
    DefaultUUID a = new DefaultUUID(uuid);
    DefaultUUID b = new DefaultUUID(uuid) {
          /* anonymous subclass for class-narrowed equality test */
        };

    assertNotEquals(a, b, "different runtime classes -> not equal, even with same UUID");
  }

  // -- toString -----------------------------------------------------------

  @Test
  void toString_includesSimpleClassNameAndUuid() {
    DefaultUUID u = new DefaultUUID("550e8400-e29b-41d4-a716-446655440000");

    String s = u.toString();

    assertAll(
        () -> assertTrue(s.contains("DefaultUUID"), s),
        () -> assertTrue(s.contains("550e8400-e29b-41d4-a716-446655440000"), s));
  }
}
