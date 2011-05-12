package org.weasis.core.ui.graphic;

import java.awt.Color;
import java.awt.Window;

public class MeasureDialog extends PropertiesDialog {
    private AbstractDragGraphic graphic;

    public MeasureDialog(Window parent, AbstractDragGraphic graphic) {
        super(parent, "Drawing Properties");
        this.graphic = graphic;
        iniGraphicDialog();
        pack();
    }

    @Override
    protected boolean hasChanged() {
        boolean hasChanged = false;
        if (jCheckBoxFilled.isSelected() != graphic.isFilled()
            || !graphic.getPaint().equals(jPVButtonColor.getBackground())
            || ((Integer) jPVSpinLineWidth.getValue()).floatValue() != graphic.getLineThickness()) {
            hasChanged = true;
        }
        return hasChanged;
    }

    public void iniGraphicDialog() {
        if (graphic == null)
            return;
        // if (graphic instanceof LineGraphic || graphic instanceof PointGraphic || graphic instanceof
        // FreeHandLineGraphic) {
        if (graphic instanceof LineGraphic) {
            jLabelLineColor1.setText("Line color :");
            jCheckBoxFilled.setEnabled(false);
        } else {
            jCheckBoxFilled.setSelected(graphic.isFilled());
        }
        jPVButtonColor.setBackground((Color) graphic.getPaint());
        jPVSpinLineWidth.setValue((int) graphic.getLineThickness());
    }

    /**
     * okAction
     */
    @Override
    protected void okAction() {
        graphic.setLineThickness(((Integer) jPVSpinLineWidth.getValue()).floatValue());
        graphic.setPaint(jPVButtonColor.getBackground());
        if (jCheckBoxFilled.isEnabled()) {
            graphic.setFilled(jCheckBoxFilled.isSelected());
        }
        // graphic.pcs.firePropertyChange("updatePane", null, null);
        dispose();
    }

}
