/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Tests {@link SynchEvent} — the propagation envelope carried by {@link SynchManager} when one view
 * needs other views to mirror an action (zoom, pan, scroll, window/level…). The {@code
 * valueIsAdjusting} flag is the gate that distinguishes interactive drag updates (which must not
 * trigger expensive recomputations on every event) from terminal events.
 */
class SynchEventTest {

  @Test
  void constructor_viewOnly_eventsMapIsEmpty() {
    ViewCanvas<?> view = Mockito.mock(ViewCanvas.class);
    SynchEvent event = new SynchEvent(view);
    assertSame(view, event.getView());
    assertNotNull(event.getEvents());
    assertTrue(event.getEvents().isEmpty());
    assertFalse(event.isValueIsAdjusting());
  }

  @Test
  void constructor_withCommandAndValue_storesEntry() {
    ViewCanvas<?> view = Mockito.mock(ViewCanvas.class);
    SynchEvent event = new SynchEvent(view, "zoom", 1.5);
    assertEquals(1.5, event.getEvents().get("zoom"));
    assertFalse(event.isValueIsAdjusting());
  }

  @Test
  void constructor_withNullCommand_leavesEventsEmpty() {
    SynchEvent event = new SynchEvent(Mockito.mock(ViewCanvas.class), null, "ignored");
    assertTrue(event.getEvents().isEmpty());
  }

  @Test
  void constructor_valueIsAdjustingFlagPropagated() {
    SynchEvent adjusting = new SynchEvent(Mockito.mock(ViewCanvas.class), "pan", 42, true);
    SynchEvent terminal = new SynchEvent(Mockito.mock(ViewCanvas.class), "pan", 42, false);
    assertTrue(adjusting.isValueIsAdjusting());
    assertFalse(terminal.isValueIsAdjusting());
  }

  @Test
  void put_addsEntryToEventsMap() {
    SynchEvent event = new SynchEvent(Mockito.mock(ViewCanvas.class));
    event.put("scrollSeries", 7);
    event.put("level", 128.0);
    assertEquals(7, event.getEvents().get("scrollSeries"));
    assertEquals(128.0, event.getEvents().get("level"));
  }

  @Test
  void put_overwritesExistingKey() {
    SynchEvent event = new SynchEvent(Mockito.mock(ViewCanvas.class), "zoom", 1.0);
    event.put("zoom", 2.0);
    assertEquals(2.0, event.getEvents().get("zoom"));
  }

  @Test
  void put_acceptsNullValue() {
    SynchEvent event = new SynchEvent(Mockito.mock(ViewCanvas.class));
    event.put("zoom", null);
    assertTrue(event.getEvents().containsKey("zoom"));
    assertNull(event.getEvents().get("zoom"));
  }
}
