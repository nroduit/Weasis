package org.weasis.dicom.viewer2d;

import java.util.Iterator;

import javax.media.jai.operator.TransposeDescriptor;
import javax.media.jai.operator.TransposeType;
import javax.swing.JComponent;

import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeries.MEDIA_POSITION;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.display.PresetWindowLevel;
import org.weasis.dicom.codec.geometry.ImageOrientation;

public class MprView extends View2d {

    private final GridBagLayoutModel mprModel;
    private double[] stackOrientation;

    public MprView(ImageViewerEventManager<DicomImageElement> eventManager, GridBagLayoutModel layoutModel) {
        super(eventManager);
        this.mprModel = layoutModel;
    }

    public int getViewIndex() {
        Iterator<JComponent> enumVal = mprModel.getConstraints().values().iterator();
        int index = 0;
        while (enumVal.hasNext()) {
            if (enumVal.next() == this)
                return index;
            index++;
        }
        return -1;
    }

    @Override
    public void setSeries(MediaSeries<DicomImageElement> series, int defaultIndex) {
        int index = getViewIndex();
        if (index == 0) {
            super.setSeries(series, defaultIndex);
        } else {
            MediaSeries<DicomImageElement> oldsequence = this.series;
            if (oldsequence != null) {
                closingSeries(oldsequence);
            }

            if (series == null) {
                imageLayer.setImage(null);
                getLayerModel().deleteAllGraphics();
                closeLens();
            } else {
                this.series = new DicomSeries((String) series.getTagValue(series.getTagID()));

                DicomImageElement dcm = series.getMedia(MEDIA_POSITION.MIDDLE);
                if (dcm != null) {
                    double[] val = (double[]) dcm.getTagValue(TagW.ImageOrientationPatient);
                    stackOrientation = ImageOrientation.computeNormalVectorOfPlan(val);
                    if (index == 1) {

                    }
                }

                defaultIndex = defaultIndex < 0 || defaultIndex >= series.size() ? 0 : defaultIndex;
                frameIndex = defaultIndex + tileOffset;
                actionsInView.put(ActionW.PRESET.cmd(), PresetWindowLevel.DEFAULT);

                setImage(series.getMedia(frameIndex), true);
                Double val = (Double) actionsInView.get(ActionW.ZOOM.cmd());
                zoom(val == null ? 1.0 : val);
                center();
            }
            eventManager.updateComponentsListener(this);

            // Set the sequence to the state OPEN
            if (series != null && oldsequence != series) {
                series.setOpen(true);
            }
        }
    }

    @Override
    protected void setImage(DicomImageElement img, boolean bestFit) {
        int index = getViewIndex();
        if (index == 0) {
            super.setImage(img, bestFit);
        } else {
            DicomImageElement oldImage = imageLayer.getSourceImage();
            if (img != null && !img.equals(oldImage)) {

            }
        }
    }

    public void anonymizeVolumeNotAxial() {
        int size = this.series.size();
        DicomImageElement midSeries = this.series.getMedia(MediaSeries.MEDIA_POSITION.MIDDLE);
        boolean readVert = false;

        double[] v = (double[]) midSeries.getTagValue(TagW.ImageOrientationPatient);
        String rowAxis = ImageOrientation.getMajorAxisFromPatientRelativeDirectionCosine(v[0], v[1], v[2]);
        String colAxis = ImageOrientation.getMajorAxisFromPatientRelativeDirectionCosine(v[3], v[4], v[5]);
        TransposeType rotate = null;
        // Coronal
        if (((rowAxis.equals("L") && colAxis.equals("F")) || (rowAxis.equals("R")) && colAxis.equals("H"))) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            rotate = TransposeDescriptor.ROTATE_180;
        } else if ((rowAxis.equals("H") || rowAxis.equals("F")) && (colAxis.equals("R") || colAxis.equals("L"))) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            readVert = true;
            if ((rowAxis.equals("H") && colAxis.equals("L")) || (rowAxis.equals("F") && colAxis.equals("R"))) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                rotate = TransposeDescriptor.ROTATE_180;
            }
        }

        // Sagittal
        else if (rowAxis.equals("A") && (colAxis.equals("F") || colAxis.equals("H"))) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            rotate = TransposeDescriptor.ROTATE_270;
        } else if (rowAxis.equals("P") && (colAxis.equals("F") || colAxis.equals("H"))) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            rotate = TransposeDescriptor.ROTATE_90;
        } else if (colAxis.equals("P") && (rowAxis.equals("H") || rowAxis.equals("F"))) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            readVert = true;
            rotate = TransposeDescriptor.ROTATE_90;
        } else if (colAxis.equals("A") && (rowAxis.equals("H") || rowAxis.equals("F"))) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            readVert = true;
            rotate = TransposeDescriptor.ROTATE_270;
        }

    }
}
