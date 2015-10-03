/*******************************************************************************
 * Copyright (c) 2011 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.ui.graphic;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.List;

import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import org.weasis.core.api.util.EscapeChars;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.editor.image.DefaultView2d;

public class MeasureDialog extends PropertiesDialog {
    private List<AbstractDragGraphic> graphics;
    private JTextPane textPane = new JTextPane();
    private DefaultView2d view2D;

    public MeasureDialog(DefaultView2d view2d, List<AbstractDragGraphic> selectedGraphic) {
        super(SwingUtilities.getWindowAncestor(view2d), Messages.getString("MeasureDialog.draw_props")); //$NON-NLS-1$
        if (selectedGraphic == null) {
            throw new IllegalArgumentException("Selected Graphics cannot be null!"); //$NON-NLS-1$
        }
        this.view2D = view2d;
        this.graphics = selectedGraphic;
        iniGraphicDialog();
        pack();

    }

    public void iniGraphicDialog() {
        if (graphics.size() > 0) {
            AbstractDragGraphic graphic = graphics.get(0);
            Color color = (Color) graphic.getColorPaint();
            int width = (int) graphic.getLineThickness();
            boolean fill = graphic.isFilled();

            jButtonColor.setBackground(color);
            spinnerLineWidth.setValue(width);
            jCheckBoxFilled.setSelected(fill);

            boolean areaGraphics = false;
            boolean mfill = false;
            boolean mcolor = false;
            boolean mwidth = false;
            for (AbstractDragGraphic g : graphics) {
                if (g instanceof AbstractDragGraphicArea) {
                    areaGraphics = true;
                    if (g.isFilled() != fill) {
                        mfill = true;
                    }
                }
                if (((int) g.getLineThickness()) != width) {
                    mwidth = true;
                }
                if (!color.equals(g.getColorPaint())) {
                    mcolor = true;
                }
            }

            checkBox_color.setVisible(mcolor);
            jLabelLineColor.setEnabled(!mcolor);
            jButtonColor.setEnabled(!mcolor);
            checkBox_width.setVisible(mwidth);
            spinnerLineWidth.setEnabled(!mwidth);
            jLabelLineWidth.setEnabled(!mwidth);

            checkBox_fill.setVisible(mfill);
            jCheckBoxFilled.setEnabled(areaGraphics && !mfill);

            if (graphics.size() == 1 || (!mcolor && !mfill && !mwidth)) {
                lbloverridesmultipleValues.setVisible(false);
            }
            if (view2D != null && graphics.size() == 1 && graphic instanceof AnnotationGraphic) {
                JScrollPane panel = new JScrollPane();

                panel.setBorder(new CompoundBorder(new EmptyBorder(10, 15, 5, 15),
                    new TitledBorder(null, Messages.getString("MeasureDialog.text"), //$NON-NLS-1$
                        TitledBorder.LEADING, TitledBorder.TOP, null, null)));
                panel.setPreferredSize(new Dimension(400, 140));
                StringBuilder buf = new StringBuilder();
                String[] labels = ((AnnotationGraphic) graphic).labelStringArray;
                for (String s : labels) {
                    buf.append(s);
                    buf.append("\n"); //$NON-NLS-1$
                }
                textPane.setText(buf.toString());
                panel.setViewportView(textPane);
                getContentPane().add(panel, BorderLayout.NORTH);
            }
        }

    }

    /**
     * okAction
     */
    @Override
    protected void okAction() {
        for (AbstractDragGraphic graphic : graphics) {
            if (spinnerLineWidth.isEnabled()) {
                graphic.setLineThickness(((Integer) spinnerLineWidth.getValue()).floatValue());
            }
            if (jButtonColor.isEnabled()) {
                graphic.setPaint(jButtonColor.getBackground());
            }
            if (jCheckBoxFilled.isEnabled()) {
                graphic.setFilled(jCheckBoxFilled.isSelected());
            }
        }
        if (graphics.size() == 1 && view2D != null) {
            AbstractDragGraphic graphic = graphics.get(0);
            if (graphic instanceof AnnotationGraphic) {
                graphic.setLabel(EscapeChars.convertToLines(textPane.getText()), view2D);
            }
        }
        dispose();
    }

}
