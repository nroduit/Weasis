package org.weasis.core.ui.graphic;

import java.awt.Color;
import java.awt.Window;
import java.util.List;

public class MeasureDialog extends PropertiesDialog {
    private List<AbstractDragGraphic> graphics;

    public MeasureDialog(Window parent, List<AbstractDragGraphic> selectedGraphic) {
        super(parent, "Drawing Properties");
        if (selectedGraphic == null)
            throw new IllegalArgumentException("Selected Graphics cannot be null!");
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
        dispose();
    }

}
