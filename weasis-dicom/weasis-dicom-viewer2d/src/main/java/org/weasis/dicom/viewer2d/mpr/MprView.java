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
package org.weasis.dicom.viewer2d.mpr;

import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;

import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;

import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.layer.LayerAnnotation;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.SortSeriesStack;
import org.weasis.dicom.viewer2d.View2d;

public class MprView extends View2d {
    
    public enum SliceOrientation {
        AXIAL, CORONAL, SAGITTAL
    }

    private SliceOrientation sliceOrientation;
    private JProgressBar progressBar;

    public MprView(ImageViewerEventManager<DicomImageElement> eventManager) {
        super(eventManager);
        this.sliceOrientation = SliceOrientation.AXIAL;
        infoLayer.setDisplayPreferencesValue(LayerAnnotation.PRELOADING_BAR, false);

        // Remove PR and KO buttons
        getViewButtons().clear();
    }

    @Override
    protected void initActionWState() {
        super.initActionWState();
        actionsInView.put(ViewCanvas.ZOOM_TYPE_CMD, ZoomType.CURRENT);
        /*
         * Get the radiologist way to see stack (means in axial, the first image is from feet and last image is in the
         * head direction) This option may not be changed. Sorting stack must be disabled from menu in UI.
         */
        actionsInView.put(ActionW.SORTSTACK.cmd(), SortSeriesStack.slicePosition);

        // Propagate the preset
        OpManager disOp = getDisplayOpManager();
        disOp.setParamValue(WindowOp.OP_NAME, ActionW.DEFAULT_PRESET.cmd(), false);
        // disOp.setParamValue(WindowOp.OP_NAME, ActionW.PRESET.cmd(), null);
    }

    @Override
    public SliceOrientation getSliceOrientation() {
        return sliceOrientation;
    }

    public void setType(SliceOrientation sliceOrientation) {
        this.sliceOrientation = sliceOrientation == null ? SliceOrientation.AXIAL : sliceOrientation;
    }

    @Override
    protected void setImage(DicomImageElement img) {
        super.setImage(img);
    }

    @Override
    protected JPopupMenu buildContexMenu(final MouseEvent evt) {
        ActionState action = eventManager.getAction(ActionW.SORTSTACK);
        if (action != null && action.isActionEnabled()) {
            // Force to disable sort stack menu
            action.enableAction(false);
            JPopupMenu ctx = super.buildContexMenu(evt);
            action.enableAction(true);
            return ctx;
        }

        return super.buildContexMenu(evt);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt);
        // if (series == null) {
        // return;
        // }
        // final String name = evt.getPropertyName();
        // if (name.equals(ActionW.SYNCH.cmd())) {
        // SynchEvent synch = (SynchEvent) evt.getNewValue();
        // SynchData synchData = (SynchData) actionsInView.get(ActionW.SYNCH_LINK.cmd());
        // if (synchData != null && Mode.None.equals(synchData.getMode())) {
        // return;
        // }
        // for (Entry<String, Object> entry : synch.getEvents().entrySet()) {
        // final String command = entry.getKey();
        // final Object val = entry.getValue();
        // if (synchData != null && !synchData.isActionEnable(command)) {
        // continue;
        // }
        //
        // }
        // }
    }

    @Override
    protected void drawOnTop(Graphics2D g2d) {
        super.drawOnTop(g2d);
        final JProgressBar bar = progressBar;
        if (bar != null && bar.isVisible()) {
            int shiftx = getWidth() / 2 - progressBar.getWidth() / 2;
            int shifty = getHeight() / 2 - progressBar.getHeight() / 2;
            g2d.translate(shiftx, shifty);
            progressBar.paint(g2d);
            g2d.translate(-shiftx, -shifty);
        }
    }

    public void setProgressBar(JProgressBar bar) {
        this.progressBar = bar;
    }

    public JProgressBar getProgressBar() {
        return progressBar;
    }

}
