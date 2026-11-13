/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.docking.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

class ToolbarRowHostTest {

  private static JPanel row(int height) {
    JPanel row = new JPanel();
    row.setPreferredSize(new Dimension(400, height));
    return row;
  }

  private static void press(ToolbarRowHost host) {
    host.dispatchEvent(
        new MouseEvent(host, MouseEvent.MOUSE_PRESSED, 0L, 0, 5, 5, 1, false, MouseEvent.BUTTON1));
  }

  @Test
  void aPlaceTheRowLeftKeepsItsHeight() {
    JPanel row = row(34);
    ToolbarRowHost main = new ToolbarRowHost(null, () -> row);
    ToolbarRowHost viewer = new ToolbarRowHost(null, () -> row);
    main.setRow(row);
    assertEquals(34, main.getPreferredSize().height);
    assertEquals(34, viewer.getPreferredSize().height);

    main.setRow(null);
    viewer.setRow(row);
    assertNull(main.getRow());
    assertSame(row, viewer.getRow());
    assertEquals(34, main.getPreferredSize().height);
    assertEquals(34, viewer.getPreferredSize().height);
  }

  @Test
  void aPressOnTheEmptyPlaceActivatesItsWindowOnly() {
    JPanel row = row(30);
    AtomicInteger activations = new AtomicInteger();
    ToolbarRowHost host = new ToolbarRowHost(activations::incrementAndGet, () -> row);

    press(host);
    assertEquals(1, activations.get());

    host.setRow(row);
    press(host);
    assertEquals(1, activations.get());
  }
}
