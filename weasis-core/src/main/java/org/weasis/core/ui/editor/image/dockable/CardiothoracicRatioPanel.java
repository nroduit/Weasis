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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.GraphicToolContext;
import org.weasis.core.ui.model.graphic.ToolPanel;
import org.weasis.core.ui.model.graphic.imp.line.CardiothoracicRatioGraphic;

/** Guides the two strokes of the cardiothoracic ratio and warns when the view is not PA. */
public class CardiothoracicRatioPanel extends JPanel implements ToolPanel {

  private static final int DEFAULT_WIDTH = 200;

  private final JTextArea text = new JTextArea();

  public CardiothoracicRatioPanel() {
    super(new BorderLayout());
    text.setLineWrap(true);
    text.setWrapStyleWord(true);
    text.setEditable(false);
    text.setOpaque(false);
    text.setFocusable(false);
    add(text, BorderLayout.CENTER);
    // The height of a wrapped text depends on its width: ask again when the width changes
    addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentResized(ComponentEvent e) {
            revalidate();
          }
        });
  }

  /** Height of the text wrapped to the width the panel has; the width is the one of the tool. */
  @Override
  public Dimension getPreferredSize() {
    int width = getWidth() > 0 ? getWidth() : GuiUtils.getScaleLength(DEFAULT_WIDTH);
    text.setSize(width, Short.MAX_VALUE);
    return new Dimension(width, text.getPreferredSize().height);
  }

  @Override
  public void update(GraphicToolContext context) {
    text.setText(message(context.graphic(), isAnteroPosterior(context.view())));
    revalidate();
    repaint();
  }

  static String message(Graphic graphic, boolean anteroPosterior) {
    int points = graphic instanceof CardiothoracicRatioGraphic ctr ? ctr.getPts().size() : 0;
    String step;
    if (points <= 2) {
      step = Messages.getString("CardiothoracicRatioPanel.step_heart");
    } else if (points < CardiothoracicRatioGraphic.POINTS_NUMBER) {
      step = Messages.getString("CardiothoracicRatioPanel.step_thorax");
    } else {
      step = Messages.getString("CardiothoracicRatioPanel.adjust");
    }
    return anteroPosterior
        ? step + "\n\n" + Messages.getString("CardiothoracicRatioPanel.ap")
        : step;
  }

  /** View Position (0018,5101) of the displayed image is AP. */
  static boolean isAnteroPosterior(ViewCanvas<?> view) {
    ImageElement image = view == null ? null : view.getImage();
    TagW tag = TagW.get("ViewPosition"); // NON-NLS
    Object position = image == null || tag == null ? null : image.getTagValue(tag);
    return position instanceof String value && "AP".equalsIgnoreCase(value.trim()); // NON-NLS
  }
}
