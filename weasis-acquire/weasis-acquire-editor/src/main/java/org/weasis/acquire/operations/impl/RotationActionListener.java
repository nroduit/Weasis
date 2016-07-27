package org.weasis.acquire.operations.impl;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import org.weasis.acquire.AcquireObject;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.utils.GraphicHelper;
import org.weasis.core.api.image.RotationOp;

public class RotationActionListener extends AcquireObject implements ActionListener {
    private int angle;

    public RotationActionListener(int angle) {
        this.angle = angle;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        AcquireImageInfo imageInfo = getImageInfo();

        int change = (imageInfo.getNextValues().getFullRotation() + angle >= 0)
            ? imageInfo.getNextValues().getRotation() + angle : imageInfo.getNextValues().getRotation() + 360 + angle;
        imageInfo.getNextValues().setRotation(change);

        if (imageInfo.getNextValues().getFullRotation() % 90 != 0) {
            GraphicHelper.newGridLayer(getView());
        }

        RotationOp rotation = new RotationOp();
        rotation.setParam(RotationOp.P_ROTATE, imageInfo.getNextValues().getFullRotation());

        imageInfo.removePreProcessImageOperationAction(RotationOp.class);
        imageInfo.addPreProcessImageOperationAction(rotation);

        imageInfo.applyPreProcess(getView());
    }
}
