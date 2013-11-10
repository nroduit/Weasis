package org.weasis.dicom.viewer2d.mpr;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.RenderedImage;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import javax.swing.JProgressBar;
import javax.vecmath.Point3d;
import javax.vecmath.Tuple3d;
import javax.vecmath.Vector3d;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.MouseActionAdapter;
import org.weasis.core.ui.editor.image.AnnotationsLayer;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.SynchData;
import org.weasis.core.ui.editor.image.SynchData.Mode;
import org.weasis.core.ui.editor.image.SynchEvent;
import org.weasis.core.ui.graphic.Graphic;
import org.weasis.core.ui.graphic.InvalidShapeException;
import org.weasis.core.ui.graphic.LineWithGapGraphic;
import org.weasis.core.ui.graphic.PolygonGraphic;
import org.weasis.core.ui.graphic.RectangleGraphic;
import org.weasis.core.ui.graphic.model.AbstractLayer;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.SortSeriesStack;
import org.weasis.dicom.codec.display.PresetWindowLevel;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;
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
        actionsInView.put(ActionW.DEFAULT_PRESET.cmd(), false);
    }

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
