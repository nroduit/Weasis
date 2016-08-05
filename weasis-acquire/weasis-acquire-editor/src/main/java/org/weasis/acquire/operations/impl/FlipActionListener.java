package org.weasis.acquire.operations.impl;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import org.weasis.acquire.AcquireObject;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.core.api.image.FlipOp;

public class FlipActionListener extends AcquireObject implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        AcquireImageInfo imageInfo = getImageInfo();
        imageInfo.getNextValues().toggleFlip();

        setValue(imageInfo.getNextValues().isFlip());
    }
    
    public void setValue(boolean value) {
        AcquireImageInfo imageInfo = getImageInfo();

        imageInfo.getNextValues().setFlip(value);
        
        FlipOp flip = new FlipOp();
        flip.setParam(FlipOp.P_FLIP, imageInfo.getNextValues().isFlip());
        imageInfo.removePreProcessImageOperationAction(FlipOp.class);
        imageInfo.addPreProcessImageOperationAction(flip);

        imageInfo.applyPreProcess(getView());
    }

}
