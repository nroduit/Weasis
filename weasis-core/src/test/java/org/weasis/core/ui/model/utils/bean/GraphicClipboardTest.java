/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.utils.bean;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.imp.line.PolylineGraphic;

@DisplayNameGeneration(ReplaceUnderscores.class)
class GraphicClipboardTest {

  private static PolylineGraphic line() throws Exception {
    PolylineGraphic graphic = new PolylineGraphic();
    graphic.buildGraphic(
        List.of(new Point2D.Double(0, 0), new Point2D.Double(10, 0), new Point2D.Double(10, 10)));
    return graphic;
  }

  @Test
  void a_copy_is_detached_from_the_graphic_it_was_taken_from() throws Exception {
    GraphicClipboard clipboard = new GraphicClipboard();
    PolylineGraphic original = line();
    clipboard.copy(List.of(original));

    // The original is edited after the copy: what will be pasted must not follow
    original.getPts().getFirst().setLocation(500, 500);

    Graphic stored = clipboard.getGraphics().getFirst();
    assertAll(
        () -> assertNotSame(original, stored),
        () -> assertEquals(new Point2D.Double(0, 0), stored.getPts().getFirst()),
        () -> assertEquals(3, stored.getPts().size()),
        () -> assertNotEquals(original.getUuid(), stored.getUuid()));
  }

  @Test
  void the_content_is_handed_out_as_a_list_that_cannot_be_edited() throws Exception {
    GraphicClipboard clipboard = new GraphicClipboard();
    clipboard.copy(List.of(line()));

    assertAll(
        () -> assertTrue(clipboard.hasContent()),
        () -> assertEquals(clipboard.getGraphics(), clipboard.getGraphics()),
        () -> assertEquals(1, clipboard.getGraphics().size()));
  }

  @Test
  void copying_nothing_empties_the_clipboard() throws Exception {
    GraphicClipboard clipboard = new GraphicClipboard();
    clipboard.copy(List.of(line()));
    clipboard.copy(List.of());

    assertFalse(clipboard.hasContent());
  }

  @Test
  void duplicating_leaves_the_copied_content_alone() throws Exception {
    GraphicClipboard clipboard = new GraphicClipboard();
    clipboard.copy(List.of(line()));

    PolylineGraphic other = line();
    other.getPts().getFirst().setLocation(500, 500);
    // Without a view nothing is pasted, but the clipboard must not have been replaced either
    clipboard.duplicate(List.of(other), null);

    assertEquals(new Point2D.Double(0, 0), clipboard.getGraphics().getFirst().getPts().getFirst());
  }

  @Test
  void clearing_and_copying_notify_the_listeners() throws Exception {
    GraphicClipboard clipboard = new GraphicClipboard();
    AtomicInteger changes = new AtomicInteger();
    Runnable listener = changes::incrementAndGet;
    clipboard.addListener(listener);

    clipboard.copy(List.of(line()));
    clipboard.clear();
    clipboard.removeListener(listener);
    clipboard.copy(List.of(line()));

    assertEquals(2, changes.get());
  }
}
