package org.weasis.dicom.codec;

import java.util.HashMap;

import org.dcm4che2.data.DicomElement;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.iod.module.pr.DisplayedAreaModule;
import org.dcm4che2.iod.module.pr.SpatialTransformationModule;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.media.data.MediaElement;

public class PresentationStateReader {
    private final DicomSpecialElement dicom;
    private final DicomObject dcmobj;

    public PresentationStateReader(DicomSpecialElement dicom) {
        if (dicom == null) {
            throw new IllegalArgumentException("Dicom parameter cannot be null");
        }
        this.dicom = dicom;
        if (((MediaElement) dicom).getMediaReader() instanceof DicomMediaIO) {
            DicomMediaIO dicomImageLoader = (DicomMediaIO) ((MediaElement) dicom).getMediaReader();
            dcmobj = dicomImageLoader.getDicomObject();
        } else {
            dcmobj = null;
        }
    }

    public void applyDisplayAreaModule(int index, HashMap<String, Object> actionsInView) {
        if (dcmobj != null) {
            DicomElement sq = dcmobj.get(Tag.DisplayedAreaSelectionSequence);
            if (sq == null || !sq.hasItems() || index >= sq.countItems()) {
                return;
            }

            // Rotation and Flip
            SpatialTransformationModule spat = new SpatialTransformationModule(dcmobj);
            actionsInView.put(ActionW.ROTATION.cmd(), spat.getRotation());
            actionsInView.put(ActionW.FLIP.cmd(), spat.isHorizontalFlip());

            DisplayedAreaModule dam = new DisplayedAreaModule(sq.getDicomObject(index));
            if (dam != null) {

                String presentationMode = dam.getPresentationSizeMode();
                int[] tlhc = dam.getDisplayedAreaTopLeftHandCorner();
                int[] brhc = dam.getDisplayedAreaBottomRightHandCorner();
                float[] spacing = dam.getPresentationPixelSpacing();
                int[] aspectPair = dam.getPresentationPixelAspectRatio();

            }
        }

    }

}
