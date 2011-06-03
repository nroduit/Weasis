package org.weasis.core.ui.graphic;

import java.awt.Color;
import java.awt.Window;
import java.util.List;

public class MeasureDialog extends PropertiesDialog {
    private List<AbstractDragGraphic> graphics;
    private boolean hasChanged = false;

    public MeasureDialog(Window parent, List<AbstractDragGraphic> selectedGraphic) {
        super(parent, "Drawing Properties");
        if (selectedGraphic == null)
            throw new IllegalArgumentException("Selected Graphics cannot be null!");
        this.graphics = selectedGraphic;
        iniGraphicDialog();
        pack();
    }

    @Override
    protected boolean hasChanged() {
        return hasChanged;
    }

    public void iniGraphicDialog() {
        boolean areaGraphics = false;
        for (AbstractDragGraphic graphic : graphics) {
            if (graphic instanceof AbstractDragGraphicArea) {
                areaGraphics = true;
                break;
            }
        }
        jCheckBoxFilled.setEnabled(areaGraphics);
        if (graphics.size() > 0) {
            AbstractDragGraphic graphic = graphics.get(0);
            jPVButtonColor.setBackground((Color) graphic.getColorPaint());
            jPVSpinLineWidth.setValue((int) graphic.getLineThickness());
        } else {
            jPVButtonColor.setEnabled(false);
            jPVSpinLineWidth.setEnabled(false);
        }
    }

    /**
     * okAction
     */
    @Override
    protected void okAction() {
        for (AbstractDragGraphic graphic : graphics) {
            graphic.setLineThickness(((Integer) jPVSpinLineWidth.getValue()).floatValue());
            graphic.setPaint(jPVButtonColor.getBackground());
            if (jCheckBoxFilled.isEnabled()) {
                graphic.setFilled(jCheckBoxFilled.isSelected());
            }
        }
        dispose();
    }

}
