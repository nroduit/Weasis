package org.weasis.acquire.graphics;

import java.awt.Color;
import java.util.Collections;
import java.util.List;

import javax.swing.Icon;
import javax.swing.JOptionPane;

import org.weasis.acquire.AcquireObject;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.CalibrationView;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.graphic.imp.line.LineGraphic;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.bean.Measurement;
import org.weasis.core.ui.util.MouseEventDouble;

public class CalibrationGraphic extends LineGraphic {
    private static final long serialVersionUID = -6996238746877983645L;

    public CalibrationGraphic() {
        super();
        setColorPaint(Color.RED);
    }
    
    public CalibrationGraphic(CalibrationGraphic calibrationGraphic) {
        super(calibrationGraphic);
    }

    @Override
    public void buildShape(MouseEventDouble mouseevent) {
        super.buildShape(mouseevent);
        if (!getResizingOrMoving()) {
            ViewCanvas<ImageElement> view = AcquireObject.getView();

            CalibrationView calibrationDialog = new CalibrationView(this, view);
            int res = JOptionPane.showConfirmDialog(view.getJComponent(), calibrationDialog, "Calibration",
                JOptionPane.OK_CANCEL_OPTION);
            if (res == JOptionPane.OK_OPTION) {
                calibrationDialog.applyNewCalibration();
            }

        }
    }

    @Override
    public Icon getIcon() {
        return LineGraphic.ICON;
    }

    @Override
    public LayerType getLayerType() {
        return LayerType.ACQUIRE;
    }

    @Override
    public String getUIName() {
        return "Calibration line";
    }

    @Override
    public List<MeasureItem> computeMeasurements(MeasurableLayer layer, boolean releaseEvent, Unit displayUnit) {
        return Collections.emptyList();
    }

    @Override
    public List<Measurement> getMeasurementList() {
        return Collections.emptyList();
    }

    @Override
    public CalibrationGraphic copy() {
        return new CalibrationGraphic(this);
    }
}
