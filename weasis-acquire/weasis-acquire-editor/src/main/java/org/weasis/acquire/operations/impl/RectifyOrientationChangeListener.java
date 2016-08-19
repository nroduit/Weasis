package org.weasis.acquire.operations.impl;

import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.weasis.acquire.AcquireObject;
import org.weasis.acquire.dockable.components.actions.rectify.RectifyAction;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.operations.OpValueChanged;
import org.weasis.acquire.utils.GraphicHelper;
import org.weasis.core.api.image.RotationOp;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.imp.area.RectangleGraphic;

/**
 * 
 * @author Yannick LARVOR
 * @version 2.5.0
 * @since 2.5.0 - 2016-04-12 - ylar - Creation
 */
public class RectifyOrientationChangeListener extends AcquireObject implements ChangeListener, OpValueChanged {

    private final RectifyAction rectifyAction;

    public RectifyOrientationChangeListener(RectifyAction rectifyAction) {
        this.rectifyAction = rectifyAction;
    }

    /**
     * @since 2.5.0
     */
    @Override
    public void stateChanged(ChangeEvent e) {
        JSlider s = (JSlider) e.getSource();
        AcquireImageInfo imageInfo = getImageInfo();
        imageInfo.getNextValues().setOrientation(s.getValue());
        imageInfo.applyPreProcess(getView());
    }

    @Override
    public void applyNextValues() {
        ViewCanvas<ImageElement> view = AcquireObject.getView();
        if (view != null) {
            AcquireImageInfo imageInfo = getImageInfo();
            int angle = imageInfo.getNextValues().getOrientation();
            RectangleGraphic cropGraphic = rectifyAction.getCurrentCropArea();
            if (cropGraphic != null) {
                GraphicModel graphicManager = view.getGraphicManager();
                graphicManager.getModels().removeIf(g -> g.getLayer().getType() == cropGraphic.getLayerType());
            }
            if (angle % 90 != 0) {
                GraphicHelper.newGridLayer(view);
            }
            
            int rotation = (imageInfo.getNextValues().getFullRotation() + 360) % 360;
            getView().getDisplayOpManager().setParamValue(RotationOp.OP_NAME, RotationOp.P_ROTATE, rotation - 360);

            rectifyAction.updateCropDisplay();
        }
    }
}
