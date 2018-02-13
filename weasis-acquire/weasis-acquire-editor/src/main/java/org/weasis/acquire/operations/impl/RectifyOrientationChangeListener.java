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

import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.weasis.acquire.AcquireObject;
import org.weasis.acquire.dockable.components.actions.rectify.RectifyAction;
import org.weasis.acquire.dockable.components.util.AbstractComponent;
import org.weasis.acquire.dockable.components.util.AbstractSliderComponent;
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
        JPanel panel = (JPanel) s.getParent();
        if (panel instanceof AbstractSliderComponent) {
            ((AbstractComponent) panel).updatePanelTitle();
        }
        AcquireImageInfo imageInfo = getImageInfo();
        imageInfo.getNextValues().setOrientation(s.getValue());
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
                GraphicHelper.newGridLayer(view);
            }

            getView().getDisplayOpManager().setParamValue(RotationOp.OP_NAME, RotationOp.P_ROTATE,
                rotation % 90 == 0 ? rotation : rotation - 360);
        }
    }
}
