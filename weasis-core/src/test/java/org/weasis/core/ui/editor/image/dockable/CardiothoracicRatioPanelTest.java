/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image.dockable;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Point2D;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.Messages;
import org.weasis.core.ui.model.graphic.GraphicToolContext;
import org.weasis.core.ui.model.graphic.imp.line.CardiothoracicRatioGraphic;

@DisplayNameGeneration(ReplaceUnderscores.class)
class CardiothoracicRatioPanelTest {

  private static CardiothoracicRatioGraphic withPoints(int count) {
    CardiothoracicRatioGraphic g = new CardiothoracicRatioGraphic();
    for (int i = 0; i < count; i++) {
      g.getPts().add(new Point2D.Double(10.0 * i, 5.0 * i));
    }
    return g;
  }

  @Test
  void the_message_follows_the_drawing_step() {
    String heart = Messages.getString("CardiothoracicRatioPanel.step_heart");
    String thorax = Messages.getString("CardiothoracicRatioPanel.step_thorax");
    String adjust = Messages.getString("CardiothoracicRatioPanel.adjust");
    assertAll(
        () -> assertEquals(heart, CardiothoracicRatioPanel.message(null, false)),
        () -> assertEquals(heart, CardiothoracicRatioPanel.message(withPoints(2), false)),
        () -> assertEquals(thorax, CardiothoracicRatioPanel.message(withPoints(3), false)),
        () -> assertEquals(thorax, CardiothoracicRatioPanel.message(withPoints(4), false)),
        () -> assertEquals(adjust, CardiothoracicRatioPanel.message(withPoints(6), false)));
  }

  @Test
  void the_text_wraps_to_the_width_of_the_tool_and_gets_the_height_it_needs() {
    CardiothoracicRatioPanel panel = new CardiothoracicRatioPanel();
    panel.update(new GraphicToolContext(null, null, withPoints(6)));
    panel.setSize(400, 10);
    int wide = panel.getPreferredSize().height;
    panel.setSize(120, 10);
    int narrow = panel.getPreferredSize().height;
    assertTrue(narrow > wide, "narrow " + narrow + " wide " + wide);
  }

  @Test
  void an_ap_view_adds_the_warning() {
    String message = CardiothoracicRatioPanel.message(withPoints(6), true);
    assertTrue(message.endsWith(Messages.getString("CardiothoracicRatioPanel.ap")));
  }
}
