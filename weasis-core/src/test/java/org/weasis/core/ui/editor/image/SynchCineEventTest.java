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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.weasis.core.api.media.data.MediaElement;

/**
 * Tests {@link SynchCineEvent} — the cine-specific propagation envelope, carrying the source media,
 * its series index and (optionally) the slice location used for spatial cross-series alignment.
 */
class SynchCineEventTest {

  @Test
  void threeArgConstructor_locationIsNull() {
    MediaElement media = Mockito.mock(MediaElement.class);
    SynchCineEvent event = new SynchCineEvent(Mockito.mock(ViewCanvas.class), media, 5);
    assertSame(media, event.getMedia());
    assertEquals(5, event.getSeriesIndex());
    assertNull(event.getLocation());
  }

  @Test
  void fourArgConstructor_storesLocation() {
    SynchCineEvent event =
        new SynchCineEvent(
            Mockito.mock(ViewCanvas.class), Mockito.mock(MediaElement.class), 0, 12.5);
    assertEquals(12.5, event.getLocation());
  }

  @Test
  void getSeriesIndex_returnsConstructorArg() {
    SynchCineEvent event =
        new SynchCineEvent(Mockito.mock(ViewCanvas.class), Mockito.mock(MediaElement.class), 42);
    assertEquals(42, event.getSeriesIndex());
  }

  @Test
  void getMedia_canBeNull() {
    // SynchCineEvent does not enforce non-null media — defensive cine flows may emit
    // a "no current media" signal during series swaps. Pin the contract here.
    SynchCineEvent event = new SynchCineEvent(Mockito.mock(ViewCanvas.class), null, 0);
    assertNull(event.getMedia());
  }

  @Test
  void setLocation_updatesLocation() {
    SynchCineEvent event =
        new SynchCineEvent(
            Mockito.mock(ViewCanvas.class), Mockito.mock(MediaElement.class), 1, 0.0);
    event.setLocation(17.25);
    assertEquals(17.25, event.getLocation());
  }
}
