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
import org.weasis.acquire.dockable.components.actions.rectify.RectifyAction;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.operations.OpValueChanged;
import org.weasis.core.api.gui.util.ActionW;

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
        rectifyAction.updateCropDisplay();
        imageInfo.applyPreProcess(getView());
    }

    @Override
    public void applyNextValues() {
        AcquireImageInfo imageInfo = getImageInfo();
        getView().setActionsInView(ActionW.FLIP.cmd(), imageInfo.getNextValues().isFlip());
    }
}
