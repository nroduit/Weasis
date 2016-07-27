package org.weasis.acquire.operations.impl;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import org.weasis.acquire.AcquireObject;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.core.api.image.AutoLevelsOp;

public class AutoLevelListener extends AcquireObject implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        AcquireImageInfo imageInfo = getImageInfo();
        imageInfo.getNextValues().toggleAutoLevel();

        AutoLevelsOp autoLevelOp = new AutoLevelsOp();
        autoLevelOp.setParam(AutoLevelsOp.P_AUTO_LEVEL, imageInfo.getNextValues().isAutoLevel());

        imageInfo.getPreProcessOpManager().removeAllImageOperationAction();
        imageInfo.addPreProcessImageOperationAction(autoLevelOp);

        imageInfo.applyPreProcess(getView());
    }

}
