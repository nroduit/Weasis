package org.weasis.acquire.graphics;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.util.Collections;
import java.util.List;

import javax.swing.Icon;

import org.weasis.acquire.AcquireObject;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.core.api.image.MaskOp;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.imp.area.RectangleGraphic;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.bean.Measurement;
import org.weasis.core.ui.util.MouseEventDouble;

/**
 * 
 * @author Yannick LARVOR
 * @version 2.5.0
 * @since v2.5.0 - 2016-04-08 - ylar - creation
 * 
 */
public class CropRectangleGraphic extends RectangleGraphic {
    private static final long serialVersionUID = -933393713355235688L;

    public CropRectangleGraphic() {
        super();
        setColorPaint(Color.RED);
    }

    public CropRectangleGraphic(CropRectangleGraphic rectangleGraphic) {
        super(rectangleGraphic);
    }

    @Override
    public LayerType getLayerType() {
        return LayerType.ACQUIRE;
    }

    @Override
    public void buildShape(MouseEventDouble mouseevent) {
        super.buildShape(mouseevent);
        if (!getResizingOrMoving()) {
            AcquireImageInfo info = AcquireManager.getCurrentAcquireImageInfo();
            if (info != null) {
                GraphicModel graphicManager = AcquireObject.getView().getGraphicManager();

                graphicManager.getModels()
                    .removeIf(model -> model.getLayer().getType() == getLayerType() && model != this);

                info.clearPreProcess();

                MaskOp mask = new MaskOp();
                mask.setParam(MaskOp.P_SHOW, true);
                mask.setParam(MaskOp.P_SHAPE, new Area(this.getBounds((AffineTransform) null)));
                mask.setParam(MaskOp.P_GRAY_TRANSPARENCY, 255);
                info.addPreProcessImageOperationAction(mask);
                info.applyPreProcess(AcquireObject.getView());
            }
        }
    }

    @Override
    public Icon getIcon() {
        return RectangleGraphic.ICON;
    }

    @Override
    public String getUIName() {
        return "Crop image";
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
    public CropRectangleGraphic copy() {
        return new CropRectangleGraphic(this);
    }
}
