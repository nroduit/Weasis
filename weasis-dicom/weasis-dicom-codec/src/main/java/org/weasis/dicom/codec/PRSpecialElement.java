package org.weasis.dicom.codec;

import java.util.HashMap;

import org.weasis.core.api.media.data.TagW;

public class PRSpecialElement extends DicomSpecialElement {

    public PRSpecialElement(DicomMediaIO mediaIO) {
        super(mediaIO);
    }

    public HashMap<TagW, Object> geTags() {
        return tags;
    }

}
