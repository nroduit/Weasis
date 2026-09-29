/*
 * Copyright (c) 2026 Weasis Team and other contributors.
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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Component;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.imp.angle.AngleToolGraphic;
import org.weasis.core.ui.model.graphic.imp.area.CircleGraphic;
import org.weasis.core.ui.model.graphic.imp.line.LineGraphic;

/** A profile changes the tools of a palette: the panel of the tool shows the new ones. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class PalettePanelRefreshTest {

  private static List<String> tooltips(JPanel palette) {
    return Arrays.stream(palette.getComponents())
        .map(c -> ((JToggleButton) c).getToolTipText())
        .toList();
  }

  @Test
  void the_panel_takes_the_buttons_of_the_new_tool_list() throws Exception {
    Graphic line = new LineGraphic();
    Graphic angle = new AngleToolGraphic();
    Graphic circle = new CircleGraphic();
    ComboItemListener<Graphic> palette =
        new ComboItemListener<>(ActionW.DRAW_MEASURE, new Graphic[] {line, angle}) {
          @Override
          public void itemStateChanged(Object object) {
            // Nothing to draw here
          }
        };
    ImageViewerEventManager<?> eventManager = mock(ImageViewerEventManager.class);
    when(eventManager.getAction(ActionW.DRAW_MEASURE)).thenReturn(Optional.of(palette));

    JPanel root = new JPanel();
    MeasureTool.buildIconPanel(root, eventManager, ActionW.MEASURE, ActionW.DRAW_MEASURE, 5);
    JPanel icons = (JPanel) root.getComponent(0);
    Component firstButton = icons.getComponent(0);
    List<String> before = tooltips(icons);

    // What applying a profile does
    palette.setDataListWithoutTriggerAction(new Graphic[] {circle, line, angle});
    SwingUtilities.invokeAndWait(() -> {});

    assertAll(
        () -> assertEquals(List.of(line.toString(), angle.toString()), before),
        () ->
            assertEquals(
                List.of(circle.toString(), line.toString(), angle.toString()), tooltips(icons)),
        () -> assertNotSame(firstButton, icons.getComponent(1), "the group built new buttons"));
  }
}
