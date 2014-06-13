package org.weasis.dicom.viewer2d.mpr;

import java.awt.Graphics2D;
import java.beans.PropertyChangeEvent;
import java.util.Map.Entry;

import javax.swing.JProgressBar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.ui.editor.image.AnnotationsLayer;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.SynchData;
import org.weasis.core.ui.editor.image.SynchData.Mode;
import org.weasis.core.ui.editor.image.SynchEvent;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.SortSeriesStack;
import org.weasis.dicom.viewer2d.View2d;

public class MprView extends View2d {
    private static final Logger LOGGER = LoggerFactory.getLogger(MprView.class);

    public enum SliceOrientation {
        AXIAL, CORONAL, SAGITTAL
    };

    private SliceOrientation sliceOrientation;
    private JProgressBar progressBar;

    public MprView(ImageViewerEventManager<DicomImageElement> eventManager) {
        super(eventManager);
        this.sliceOrientation = SliceOrientation.AXIAL;
        infoLayer.setDisplayPreferencesValue(AnnotationsLayer.PRELOADING_BAR, false);

        // Remove PR and KO buttons
        getViewButtons().clear();
    }

    @Override
    protected void initActionWState() {
        super.initActionWState();
        actionsInView.put(DefaultView2d.zoomTypeCmd, ZoomType.CURRENT);
        // Get the radiologist way to see stack (means in axial, the first image is from feet and last image is in the
        // head direction)
        // TODO This option should be fixed
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
    public void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt);
        if (series == null) {
            return;
        }
        final String name = evt.getPropertyName();
        if (name.equals(ActionW.SYNCH.cmd())) {
            SynchEvent synch = (SynchEvent) evt.getNewValue();
            SynchData synchData = (SynchData) actionsInView.get(ActionW.SYNCH_LINK.cmd());
            if (synchData != null && Mode.None.equals(synchData.getMode())) {
                return;
            }
            for (Entry<String, Object> entry : synch.getEvents().entrySet()) {
                final String command = entry.getKey();
                final Object val = entry.getValue();
                if (synchData != null && !synchData.isActionEnable(command)) {
                    continue;
                }

            }
        }
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
