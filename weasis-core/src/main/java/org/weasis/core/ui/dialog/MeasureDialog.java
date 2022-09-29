/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.dialog;

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.List;
import java.util.Objects;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.graphic.DragGraphic;
import org.weasis.core.ui.model.graphic.GraphicArea;
import org.weasis.core.ui.model.graphic.imp.AnnotationGraphic;
import org.weasis.core.ui.model.graphic.imp.PixelInfoGraphic;
import org.weasis.core.util.EscapeChars;

public class MeasureDialog extends PropertiesDialog {
  private final List<DragGraphic> graphics;
  private final JTextPane textPane = new JTextPane();
  private final ViewCanvas<?> view2D;

  public MeasureDialog(ViewCanvas<?> view2d, List<DragGraphic> selectedGraphic) {
    super(
        SwingUtilities.getWindowAncestor(view2d.getJComponent()),
        Messages.getString("MeasureDialog.draw_props"));
    this.view2D = view2d;
    this.graphics = Objects.requireNonNull(selectedGraphic);
    iniGraphicDialog();
    pack();
  }

  public void iniGraphicDialog() {
    if (!graphics.isEmpty()) {
      DragGraphic graphic = graphics.get(0);
      this.color = (Color) graphic.getColorPaint();
      int width = graphic.getLineThickness().intValue();
      boolean fill = graphic.getFilled();

      spinnerLineWidth.setValue(width);
      jCheckBoxFilled.setSelected(fill);

      boolean areaGraphics = false;
      boolean mfill = false;
      boolean mcolor = false;
      boolean mwidth = false;
      for (DragGraphic g : graphics) {
        if (g instanceof GraphicArea) {
          areaGraphics = true;
          if (g.getFilled() != fill) {
            mfill = true;
          }
        }
        if ((g.getLineThickness().intValue()) != width) {
          mwidth = true;
        }
        if (!color.equals(g.getColorPaint())) {
          mcolor = true;
        }
      }

      checkBoxColor.setEnabled(mcolor);
      jLabelLineColor.setEnabled(!mcolor);
      jButtonColor.setEnabled(!mcolor);
      checkBoxWidth.setEnabled(mwidth);
      spinnerLineWidth.setEnabled(!mwidth);
      jLabelLineWidth.setEnabled(!mwidth);

      checkBoxFill.setEnabled(mfill);
      jCheckBoxFilled.setEnabled(areaGraphics && !mfill);

      if (graphics.size() == 1 || (!mcolor && !mfill && !mwidth)) {
        overrideMultipleValues.setVisible(false);
      } else {
        int size = overrideMultipleValues.getPreferredSize().width / 2;
        panelColor.add(GuiUtils.boxHorizontalStrut(size));
        panelColor.add(checkBoxColor);
        panelColor.add(GuiUtils.boxHorizontalStrut(size));
        panelLine.add(GuiUtils.boxHorizontalStrut(size));
        panelLine.add(checkBoxWidth);
        panelLine.add(GuiUtils.boxHorizontalStrut(size));
        panelFilled.add(GuiUtils.boxHorizontalStrut(size));
        panelFilled.add(checkBoxFill);
        panelFilled.add(GuiUtils.boxHorizontalStrut(size));
      }
      if (view2D != null
          && graphics.size() == 1
          && graphic instanceof AnnotationGraphic annotationGraphic) {
        JScrollPane panel = new JScrollPane();
        panel.setBorder(
            new CompoundBorder(
                GuiUtils.getEmptyBorder(10, 15, 5, 15),
                GuiUtils.getTitledBorder(Messages.getString("MeasureDialog.text"))));
        panel.setPreferredSize(GuiUtils.getDimension(400, 140));
        StringBuilder buf = new StringBuilder();
        String[] labels = annotationGraphic.getLabels();
        for (String s : labels) {
          buf.append(s);
          buf.append("\n");
        }
        textPane.setEnabled(!(graphic instanceof PixelInfoGraphic));
        textPane.setText(buf.toString());
        panel.setViewportView(textPane);
        getContentPane().add(panel, BorderLayout.NORTH);
      }
    }
  }

  /** okAction */
  @Override
  protected void okAction() {
    for (DragGraphic graphic : graphics) {
      if (spinnerLineWidth.isEnabled()) {
        graphic.setLineThickness(((Integer) spinnerLineWidth.getValue()).floatValue());
      }
      if (jButtonColor.isEnabled()) {
        graphic.setPaint(color);
      }
      if (jCheckBoxFilled.isEnabled()) {
        graphic.setFilled(jCheckBoxFilled.isSelected());
      }
    }
    if (graphics.size() == 1 && view2D != null) {
      DragGraphic graphic = graphics.get(0);
      if (graphic instanceof AnnotationGraphic) {
        graphic.setLabel(EscapeChars.convertToLines(textPane.getText()), view2D);
      }
    }
    dispose();
  }
}
