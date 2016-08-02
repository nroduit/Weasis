package org.weasis.acquire.operations.impl;

import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.weasis.acquire.AcquireObject;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.utils.GraphicHelper;
import org.weasis.core.api.image.RotationOp;
import org.weasis.core.api.image.util.ImageLayer;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.model.layer.LayerType;

/**
 * 
 * @author Yannick LARVOR
 * @version 2.5.0
 * @since 2.5.0 - 2016-04-12 - ylar - Creation
 */
public class RectifyOrientationChangeListener extends AcquireObject implements ChangeListener {
    /**
     * @since 2.5.0
     */
    @Override
    public void stateChanged(ChangeEvent e) {
        JSlider s = (JSlider) e.getSource();

        ImageLayer<ImageElement> layer = getImageLayer();

        if (layer != null) {
            AcquireImageInfo imageInfo = getImageInfo();
            int angle = s.getValue();

            getView().getGraphicManager().deleteByLayerType(LayerType.ACQUIRE);
            if (angle % 90 != 0) {
                GraphicHelper.newGridLayer(getView()); // Add a grid to the layer
            }

            imageInfo.getNextValues().setOrientation(angle);

            RotationOp rotation = new RotationOp();
            rotation.setParam(RotationOp.P_ROTATE, imageInfo.getNextValues().getFullRotation());

            imageInfo.removePreProcessImageOperationAction(RotationOp.class);
            imageInfo.addPreProcessImageOperationAction(rotation);

            imageInfo.applyPreProcess(getView());
        }
    }
}
