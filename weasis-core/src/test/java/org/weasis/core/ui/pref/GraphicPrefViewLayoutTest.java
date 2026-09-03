/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.pref;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.Messages;

/** The elements of the closed shape block start on the same left edge. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class GraphicPrefViewLayoutTest {

  private static <T extends Component> List<T> all(Container root, Class<T> type) {
    List<T> found = new ArrayList<>();
    for (Component c : root.getComponents()) {
      if (type.isInstance(c)) {
        found.add(type.cast(c));
      }
      if (c instanceof Container container) {
        found.addAll(all(container, type));
      }
    }
    return found;
  }

  private static void layoutTree(Component component) {
    component.doLayout();
    if (component instanceof Container container) {
      for (Component child : container.getComponents()) {
        layoutTree(child);
      }
    }
  }

  private static int leftEdge(Component c, Component root) {
    return SwingUtilities.convertPoint(c.getParent(), c.getLocation(), root).x;
  }

  @Test
  void closed_shape_elements_are_aligned_on_the_left() {
    GraphicPrefView page = new GraphicPrefView();
    page.setSize(520, 400);
    layoutTree(page);

    JCheckBox fill =
        all(page, JCheckBox.class).stream()
            .filter(b -> Messages.getString("PropertiesDialog.fill_shape").equals(b.getText()))
            .findFirst()
            .orElseThrow();
    JCheckBox upright =
        all(page, JCheckBox.class).stream()
            .filter(b -> Messages.getString("GraphicPrefView.upright").equals(b.getText()))
            .findFirst()
            .orElseThrow();
    JSlider opacity = all(page, JSlider.class).getFirst();
    JLabel radius =
        all(page, JLabel.class).stream()
            .filter(
                l -> l.getText().startsWith(Messages.getString("GraphicPrefView.circle_radius")))
            .findFirst()
            .orElseThrow();

    int edge = leftEdge(fill, page);
    Container closedShape = fill.getParent();
    Container line = all(page, JSpinner.class).getFirst().getParent();
    assertAll(
        () -> assertEquals(edge, leftEdge(opacity, page)),
        () -> assertEquals(edge, leftEdge(upright, page)),
        () -> assertEquals(edge, leftEdge(radius, page)),
        () ->
            assertTrue(
                opacity.getWidth() > 300, "the slider uses the width: " + opacity.getWidth()),
        () -> assertEquals(line.getWidth(), closedShape.getWidth(), "as wide as the line block"),
        () -> assertTrue(closedShape.getWidth() > 480, "whole width: " + closedShape.getWidth()),
        () -> assertNotNull(upright.getToolTipText()));
  }
}
