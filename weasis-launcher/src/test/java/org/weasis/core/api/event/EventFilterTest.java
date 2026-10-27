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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class EventFilterTest {

  @Test
  void globsMatchNamespacesPrefixesAndEverything() {
    assertAll(
        () -> assertTrue(EventFilter.parse("dicom:*").matchesName("dicom:study.added")),
        () -> assertFalse(EventFilter.parse("dicom:*").matchesName("view2d:image.changed")),
        () ->
            assertTrue(
                EventFilter.parse("view2d:windowLevel.*")
                    .matchesName("view2d:windowLevel.changed")),
        () -> assertTrue(EventFilter.parse("*").matchesName("anything:at.all")),
        () -> assertTrue(EventFilter.parse("").matchesName("anything:at.all")),
        () -> assertTrue(EventFilter.parse("a:b, c:*").matchesName("c:d")),
        () -> assertFalse(EventFilter.parse("a:b").matchesName("a:bc")),
        () -> assertTrue(EventFilter.parse("*.closed").matchesName("workspace:view.closed")));
  }

  @Test
  void conditionsReadThePayloadAndDescribeThemselves() {
    EventFilter filter = EventFilter.parse("workspace:*", List.of("view=v3"));
    Event match = Event.of("workspace:view.selected").put("view", "v3").build();
    Event other = Event.of("workspace:view.selected").put("view", "v4").build();
    assertAll(
        () -> assertTrue(filter.matches(match)),
        () -> assertFalse(filter.matches(other)),
        () -> assertEquals("workspace:* view=v3", filter.describe()),
        () -> assertEquals("*", EventFilter.ALL.describe()),
        () ->
            assertThrows(
                IllegalArgumentException.class,
                () -> EventFilter.parse("a:*", List.of("novalue"))));
  }
}
