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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.event.KeyEvent;
import java.util.List;
import javax.swing.DefaultComboBoxModel;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.RadioMenuItem;
import org.weasis.core.ui.model.graphic.Graphic;

/** The measure menu shows the tool shortcuts, also after its list is replaced (issue #976). */
class MeasureMenuShortcutTest {

  private static Graphic tool(int keyCode) {
    Graphic graphic = mock(Graphic.class);
    when(graphic.getKeyCode()).thenReturn(keyCode);
    when(graphic.getModifier()).thenReturn(0);
    return graphic;
  }

  @Test
  void shortcutsSurviveAListChange() {
    Graphic line = tool(KeyEvent.VK_D);
    Graphic rectangle = tool(0);
    Graphic circle = tool(KeyEvent.VK_O);

    MeasureToolBar.MeasureGroupMenu menu =
        new MeasureToolBar.MeasureGroupMenu(ActionW.DRAW_MEASURE);
    menu.setModel(new DefaultComboBoxModel<>(new Graphic[] {line, rectangle, circle}));
    // A measurement profile replaces the list: the menu builds new items
    menu.setModel(new DefaultComboBoxModel<>(new Graphic[] {circle, rectangle}));

    List<RadioMenuItem> items = menu.getRadioMenuItemListCopy();
    assertEquals(2, items.size());
    assertEquals(KeyStroke.getKeyStroke(KeyEvent.VK_O, 0), items.get(0).getAccelerator());
    assertNull(items.get(1).getAccelerator());
  }
}
