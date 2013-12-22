package org.weasis.dicom.codec;

import java.util.HashMap;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.weasis.core.api.media.data.TagW;

public class PRSpecialElement extends DicomSpecialElement {

    public PRSpecialElement(DicomMediaIO mediaIO) {
        super(mediaIO);
    }

    public HashMap<TagW, Object> geTags() {
        return tags;
    }

    @Override
    protected void iniLabel() {

        Attributes dicom = ((DicomMediaIO) mediaIO).getDicomObject();
        /*
         * DICOM PS 3.3 - 2011 - CONTENT IDENTIFICATION MACRO. Used in Presentation State Identification C.11.10
         * 
         * ContentLabel (mandatory): a label that is used to identify this SOP Instance.
         * 
         * ContentDescription: a description of the content of the SOP Instance.
         */

        String clabel = dicom.getString(Tag.ContentLabel);
        if (clabel == null) {
            clabel = dicom.getString(Tag.ContentDescription);
        }

        if (clabel == null) {
            super.iniLabel();
        } else {
            StringBuilder buf = new StringBuilder(getLabelPrefix());
            buf.append(clabel);
            label = buf.toString();
        }
    }
}
