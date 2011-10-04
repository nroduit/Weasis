package org.weasis.dicom.codec;

import java.awt.Rectangle;
import java.util.HashMap;

import org.dcm4che2.data.DicomElement;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.iod.module.pr.DisplayedAreaModule;
import org.dcm4che2.iod.module.pr.SpatialTransformationModule;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.TagW;

public class PresentationStateReader {
    private final MediaElement dicom;
    private final DicomObject dcmobj;
    private final HashMap<String, Object> tags = new HashMap<String, Object>();

    public PresentationStateReader(MediaElement dicom) {
        if (dicom == null) {
            throw new IllegalArgumentException("Dicom parameter cannot be null");
        }
        this.dicom = dicom;
        if (dicom.getMediaReader() instanceof DicomMediaIO) {
            DicomMediaIO dicomImageLoader = (DicomMediaIO) dicom.getMediaReader();
            dcmobj = dicomImageLoader.getDicomObject();
        } else {
            dcmobj = null;
        }
    }

    public void readDisplayArea(int frame) {

        if (dcmobj != null) {
            DicomElement sq = dcmobj.get(Tag.DisplayedAreaSelectionSequence);
            if (sq == null || !sq.hasItems() || frame >= sq.countItems()) {
                return;
            }
            // Rotation and then Flip
            SpatialTransformationModule spat = new SpatialTransformationModule(dcmobj);
            tags.put(ActionW.ROTATION.cmd(), spat.getRotation());
            tags.put(ActionW.FLIP.cmd(), spat.isHorizontalFlip());

            DisplayedAreaModule dam = new DisplayedAreaModule(sq.getDicomObject(frame));
            if (dam != null) {
                double[] pixelsize = null;
                float[] spacing = dam.getPresentationPixelSpacing();
                if (spacing != null && spacing.length == 2) {
                    pixelsize = new double[] { spacing[1], spacing[0] };
                }
                if (spacing == null) {
                    int[] aspects = dam.getPresentationPixelAspectRatio();
                    if (aspects != null && aspects.length == 2 && aspects[0] != aspects[1]) {
                        // set the aspects to the pixel size of the image to stretch the image rendering (square pixel)
                        if (aspects[1] < aspects[0]) {
                            pixelsize = new double[] { 1.0, (double) aspects[0] / (double) aspects[1] };
                        } else {
                            pixelsize = new double[] { (double) aspects[1] / (double) aspects[0], 1.0 };
                        }
                    }
                }
                if (pixelsize != null) {
                    tags.put(TagW.PixelSpacing.getName(), pixelsize);
                }

                String presentationMode = dam.getPresentationSizeMode();
                int[] tlhc = dam.getDisplayedAreaTopLeftHandCorner();
                int[] brhc = dam.getDisplayedAreaBottomRightHandCorner();

                if (tlhc != null && tlhc.length == 2 && brhc != null && brhc.length == 2) {
                    // Lots of systems encode topLeft as 1,1, even when they mean 0,0
                    if (tlhc[0] == 1) {
                        tlhc[0] = 0;
                    }
                    if (tlhc[1] == 1) {
                        tlhc[1] = 0;

                    }
                    tags.put(ActionW.CROP.cmd(), new Rectangle(tlhc[0], tlhc[1], brhc[0] - tlhc[0], brhc[1] - tlhc[1]));
                }

                if ("SCALE TO FIT".equalsIgnoreCase(presentationMode)) {
                    tags.put(ActionW.ZOOM.cmd(), 0.0);
                } else if ("MAGNIFY".equalsIgnoreCase(presentationMode)) {
                    tags.put(ActionW.ZOOM.cmd(), dam.getPresentationPixelMagnificationRatio());
                } else if ("TRUE SIZE".equalsIgnoreCase(presentationMode)) {
                    // TODO required to calibrate the screen (Measure physically two lines displayed on screen, must be
                    // square pixel)
                    // tags.put(ActionW.ZOOM.cmd(), 0.0);
                }
            }
        }
    }

    public MediaElement getDicom() {
        return dicom;
    }

    public DicomObject getDcmobj() {
        return dcmobj;
    }

    public HashMap<String, Object> getTags() {
        return tags;
    }

    public Object getTagValue(String key, Object defaultValue) {
        if (key == null) {
            return defaultValue;
        }
        Object object = tags.get(key);
        return object == null ? defaultValue : object;
    }
}
