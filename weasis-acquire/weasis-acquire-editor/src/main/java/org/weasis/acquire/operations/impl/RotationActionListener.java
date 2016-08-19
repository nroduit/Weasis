package org.weasis.acquire.operations.impl;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import org.weasis.acquire.AcquireObject;
import org.weasis.acquire.dockable.components.actions.rectify.RectifyAction;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.operations.OpValueChanged;
import org.weasis.acquire.utils.GraphicHelper;
import org.weasis.core.api.image.RotationOp;

public class RotationActionListener extends AcquireObject implements ActionListener, OpValueChanged {
    private int angle;
    private final RectifyAction rectifyAction;

    public RotationActionListener(int angle, RectifyAction rectifyAction) {
        this.angle = angle;
        this.rectifyAction = rectifyAction;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        AcquireImageInfo imageInfo = getImageInfo();

        int rotation = (imageInfo.getNextValues().getRotation() + 720 + angle) % 360;
        imageInfo.getNextValues().setRotation(rotation);
        applyNextValues();
        imageInfo.applyPreProcess(getView());
    }

    @Override
    public void applyNextValues() {
        AcquireImageInfo imageInfo = getImageInfo();

        int rotation = (imageInfo.getNextValues().getFullRotation() + 360) % 360;
        if (rotation % 90 != 0) {
            GraphicHelper.newGridLayer(getView());
        }

        getView().getDisplayOpManager().setParamValue(RotationOp.OP_NAME, RotationOp.P_ROTATE,
            rotation % 90 == 0 ? rotation : rotation - 360);

        rectifyAction.updateCropDisplay();
    }
}
