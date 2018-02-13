/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.acquire.operations.impl;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

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
        rectifyAction.updateCropDisplay();
        imageInfo.applyPreProcess(getView());
    }

    @Override
    public void applyNextValues() {

        ViewCanvas<ImageElement> view = AcquireObject.getView();
        if (view != null) {
            AcquireImageInfo imageInfo = getImageInfo();
            int rotation = (imageInfo.getNextValues().getFullRotation() + 360) % 360;
            RectangleGraphic cropGraphic = rectifyAction.getCurrentCropArea();
            if (cropGraphic != null) {
                GraphicModel graphicManager = view.getGraphicManager();
                graphicManager.getModels().removeIf(g -> g.getLayer().getType() == cropGraphic.getLayerType());
            }
            if (rotation % 90 != 0) {
                GraphicHelper.newGridLayer(getView());
            }

            getView().getDisplayOpManager().setParamValue(RotationOp.OP_NAME, RotationOp.P_ROTATE,
                rotation % 90 == 0 ? rotation : rotation - 360);
        }
    }
}
