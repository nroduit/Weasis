/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.acquire.graphics;

import java.awt.Color;
import java.util.Collections;
import java.util.List;

import javax.swing.Icon;

import org.weasis.acquire.AcquireObject;
import org.weasis.acquire.Messages;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.core.api.image.ImageOpNode;
import org.weasis.core.api.image.MaskOp;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.graphic.imp.area.RectangleGraphic;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.bean.Measurement;
import org.weasis.core.ui.util.MouseEventDouble;

/**
 *
 * @author Yannick LARVOR
 * @version 2.5.0
 * @since v2.5.0 - 2016-04-08 - ylar - creation
 *
 */
public class CropRectangleGraphic extends RectangleGraphic {
    private static final long serialVersionUID = -933393713355235688L;

    public CropRectangleGraphic() {
        super();
        setColorPaint(Color.RED);
    }

    public CropRectangleGraphic(CropRectangleGraphic rectangleGraphic) {
        super(rectangleGraphic);
    }

    @Override
    public LayerType getLayerType() {
        return LayerType.ACQUIRE;
    }

    @Override
    public void buildShape(MouseEventDouble mouseevent) {
        super.buildShape(mouseevent);
        if (!getResizingOrMoving()) {
            AcquireImageInfo info = AcquireManager.getCurrentAcquireImageInfo();
            if (info != null) {
                ViewCanvas<ImageElement> view = AcquireObject.getView();
                info.getNextValues().setCropZone(this.getShape().getBounds());
                updateCropDisplay(info);

                if (view != null) {
                    view.getImageLayer().setImage(view.getImage(), info.getPreProcessOpManager());
                }
            }
        }
    }

    public void updateCropDisplay(AcquireImageInfo imageInfo) {
        ImageOpNode node = imageInfo.getPreProcessOpManager().getNode(MaskOp.OP_NAME);
        if (node == null) {
            node = new MaskOp();
            imageInfo.addPreProcessImageOperationAction(node);
        } else {
            node.clearIOCache();
        }
        node.setParam(MaskOp.P_SHOW, true);
        node.setParam(MaskOp.P_SHAPE, imageInfo.getNextValues().getCropZone());
        node.setParam(MaskOp.P_ALPHA, 0.7);
    }

    @Override
    public Icon getIcon() {
        return RectangleGraphic.ICON;
    }

    @Override
    public String getUIName() {
        return Messages.getString("CropRectangleGraphic.crop_img"); //$NON-NLS-1$
    }

    @Override
    public List<MeasureItem> computeMeasurements(MeasurableLayer layer, boolean releaseEvent, Unit displayUnit) {
        return Collections.emptyList();
    }

    @Override
    public List<Measurement> getMeasurementList() {
        return Collections.emptyList();
    }

    @Override
    public CropRectangleGraphic copy() {
        if (!pts.isEmpty()) {
            // Do not allow to copy it elsewhere
            return null;
        }
        return new CropRectangleGraphic(this);
    }
}
