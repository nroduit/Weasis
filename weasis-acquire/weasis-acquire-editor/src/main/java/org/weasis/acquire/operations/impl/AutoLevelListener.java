/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.acquire.operations.impl;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import org.weasis.acquire.AcquireObject;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.operations.OpValueChanged;
import org.weasis.core.api.image.AutoLevelsOp;
import org.weasis.core.api.image.ImageOpNode;

public class AutoLevelListener extends AcquireObject implements ActionListener, OpValueChanged {

    @Override
    public void actionPerformed(ActionEvent e) {
        AcquireImageInfo imageInfo = getImageInfo();
        imageInfo.getNextValues().toggleAutoLevel();
        applyNextValues();
        imageInfo.applyPreProcess(getView());
    }

    @Override
    public void applyNextValues() {
        AcquireImageInfo imageInfo = getImageInfo();
        ImageOpNode node = imageInfo.getPreProcessOpManager().getNode(AutoLevelsOp.OP_NAME);
        if (node == null) {
            node = new AutoLevelsOp();
            imageInfo.addPreProcessImageOperationAction(node);
        } else {
            node.clearIOCache();
        }
        node.setParam(AutoLevelsOp.P_AUTO_LEVEL, imageInfo.getNextValues().isAutoLevel());
    }

}
