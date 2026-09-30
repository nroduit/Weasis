/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.docking.ws.drag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.Point;
import java.awt.Rectangle;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.docking.ws.DockSide;

class DropGeometryTest {

  private final Rectangle bounds = new Rectangle(100, 100, 400, 200);

  @Test
  void centerBandIsStackZone() {
    assertNull(DropGeometry.sideAt(bounds, new Point(300, 200)));
    assertNull(DropGeometry.sideAt(bounds, new Point(220, 160)));
  }

  @Test
  void pointOutsideBoundsHasNoSide() {
    assertNull(DropGeometry.sideAt(bounds, new Point(50, 50)));
    assertNull(DropGeometry.sideAt(bounds, new Point(600, 200)));
  }

  @Test
  void edgeBandsResolveToNearestSide() {
    assertEquals(DockSide.LEFT, DropGeometry.sideAt(bounds, new Point(110, 200)));
    assertEquals(DockSide.RIGHT, DropGeometry.sideAt(bounds, new Point(490, 200)));
    assertEquals(DockSide.TOP, DropGeometry.sideAt(bounds, new Point(300, 105)));
    assertEquals(DockSide.BOTTOM, DropGeometry.sideAt(bounds, new Point(300, 295)));
  }

  @Test
  void cornerResolvesToClosestEdge() {
    assertEquals(DockSide.LEFT, DropGeometry.sideAt(bounds, new Point(102, 130)));
    assertEquals(DockSide.TOP, DropGeometry.sideAt(bounds, new Point(150, 101)));
  }

  @Test
  void highlightRectCoversHalfOnSplitAndAllOnStack() {
    assertEquals(bounds, DropGeometry.highlightRect(bounds, null));
    assertEquals(
        new Rectangle(100, 100, 200, 200), DropGeometry.highlightRect(bounds, DockSide.LEFT));
    assertEquals(
        new Rectangle(300, 100, 200, 200), DropGeometry.highlightRect(bounds, DockSide.RIGHT));
    assertEquals(
        new Rectangle(100, 100, 400, 100), DropGeometry.highlightRect(bounds, DockSide.TOP));
    assertEquals(
        new Rectangle(100, 200, 400, 100), DropGeometry.highlightRect(bounds, DockSide.BOTTOM));
  }

  @Test
  void tabInsertionIndexUsesTabMiddles() {
    Rectangle[] tabs = {
      new Rectangle(0, 0, 100, 24), new Rectangle(100, 0, 100, 24), new Rectangle(200, 0, 100, 24)
    };
    assertEquals(0, DropGeometry.tabInsertionIndex(tabs, 10));
    assertEquals(1, DropGeometry.tabInsertionIndex(tabs, 60));
    assertEquals(1, DropGeometry.tabInsertionIndex(tabs, 120));
    assertEquals(3, DropGeometry.tabInsertionIndex(tabs, 260));
    assertEquals(3, DropGeometry.tabInsertionIndex(tabs, 500));
  }
}
