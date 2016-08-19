package org.weasis.acquire.operations.impl;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import org.weasis.acquire.AcquireObject;
import org.weasis.acquire.dockable.components.actions.rectify.RectifyAction;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.operations.OpValueChanged;
import org.weasis.core.api.image.FlipOp;

public class FlipActionListener extends AcquireObject implements ActionListener, OpValueChanged {

    private final RectifyAction rectifyAction;

    public FlipActionListener(RectifyAction rectifyAction) {
        this.rectifyAction = rectifyAction;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        AcquireImageInfo imageInfo = getImageInfo();
        imageInfo.getNextValues().toggleFlip();
        applyNextValues();
        imageInfo.applyPreProcess(getView());
    }

    @Override
    public void applyNextValues() {
        AcquireImageInfo imageInfo = getImageInfo();
        getView().getDisplayOpManager().setParamValue(FlipOp.OP_NAME, FlipOp.P_FLIP,
            imageInfo.getNextValues().isFlip());
        rectifyAction.updateCropDisplay();
    }

}
