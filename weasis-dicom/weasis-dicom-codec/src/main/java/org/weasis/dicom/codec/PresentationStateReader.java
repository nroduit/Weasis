package org.weasis.dicom.codec;

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

    public double[] applyDisplayAreaModule(int frame, HashMap<String, Object> actionsInView) {
        double[] pixelsize = null;
        if (dcmobj != null) {
            DicomElement sq = dcmobj.get(Tag.DisplayedAreaSelectionSequence);
            if (sq == null || !sq.hasItems() || frame >= sq.countItems()) {
                return null;
            }
            // Rotation and Flip
            SpatialTransformationModule spat = new SpatialTransformationModule(dcmobj);
            actionsInView.put(ActionW.ROTATION.cmd(), spat.getRotation());
            actionsInView.put(ActionW.FLIP.cmd(), spat.isHorizontalFlip());

            DisplayedAreaModule dam = new DisplayedAreaModule(sq.getDicomObject(frame));
            if (dam != null) {
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

                String presentationMode = dam.getPresentationSizeMode();
                int[] tlhc = dam.getDisplayedAreaTopLeftHandCorner();
                int[] brhc = dam.getDisplayedAreaBottomRightHandCorner();
                // Lots of systems encode topLeft as 1,1, even when they mean 0,0
                if (tlhc[0] == 1) {
                    tlhc[0] = 0;
                }
                if (tlhc[1] == 1) {
                    tlhc[1] = 0;
                    // left = tlhc[0] / imgCols;
                    // top = tlhc[1] / imgRows;
                    // right = brhc[0] / imgCols;
                    // bottom = brhc[1] / imgRows;
                    // if( left<0 ) left = 0;
                    // if( top<0 ) top = 0;
                    // if( right>1 ) right = 1;
                    // if( bottom>1 ) bottom = 1;
                    // cols = (int) (imgCols * (right - left));
                    // rows = (int) (imgRows * (bottom - top) * aspect);
                }

                if ("SCALE TO FIT".equalsIgnoreCase(presentationMode)) {
                    actionsInView.put(ActionW.ZOOM.cmd(), 0.0);
                }
            }
        }
        return pixelsize;
    }

    public void applyShutter(int frame, HashMap<String, Object> actionsInView) {
        actionsInView.put(TagW.ShutterFinalShape.getName(), dicom.getTagValue(TagW.ShutterFinalShape));
        actionsInView.put(TagW.ShutterPSValue.getName(), dicom.getTagValue(TagW.ShutterPSValue));
        actionsInView.put(TagW.ShutterRGBColor.getName(), dicom.getTagValue(TagW.ShutterRGBColor));
    }
}
