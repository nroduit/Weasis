package org.weasis.dicom.viewer2d.rendering;

import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;

import javax.swing.JPopupMenu;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.ui.editor.image.AnnotationsLayer;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.SortSeriesStack;
import org.weasis.dicom.viewer2d.View2d;

public class RenderingService extends View2d {
    private static final Logger LOGGER = LoggerFactory.getLogger(RenderingService.class);

    public RenderingService(ImageViewerEventManager<DicomImageElement> eventManager) {
        super(eventManager);
        infoLayer.setDisplayPreferencesValue(AnnotationsLayer.PRELOADING_BAR, false);

        // Remove PR and KO buttons
        getViewButtons().clear();
    }

    @Override
    protected void initActionWState() {
        super.initActionWState();
        actionsInView.put(ViewCanvas.zoomTypeCmd, ZoomType.CURRENT);
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

}
