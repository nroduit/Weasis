package org.weasis.dicom.codec;

import java.util.Date;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Tagable;
import org.weasis.core.api.media.data.TagW.TagType;
import org.weasis.dicom.codec.utils.DicomMediaUtils;

public class TagSeq extends TagD {

    public TagSeq(int tagID, String keyword, String displayedName, String privateCreatorID, VR vr, int vmMin, int vmMax,
        Object defaultValue, boolean retired) {
        super(tagID, keyword, displayedName, privateCreatorID, vr, vmMin, vmMax, defaultValue, retired);

    }

    @Override
    public void readValue(Object data, Tagable tagabale) {
        if (data instanceof MacroSeqData) {
            MacroSeqData macro = (MacroSeqData) data;
            Object val = getValue(macro.getAttributes());
            if (val instanceof Sequence) {
                Sequence seq = (Sequence) val;
                if (!seq.isEmpty()) {
                    val = seq.get(0);
                }
            }

            if (val instanceof Attributes) {
                Attributes dataset = (Attributes) val;
                for (TagW tag : macro.getTags()) {
                    if (tag != null) {
                        tag.readValue(dataset, tagabale);
                    }
                }

            }
        }
    };

    public static class MacroSeqData {
        private final Attributes attributes;
        private final TagW[] tags;

        public MacroSeqData(Attributes attributes, TagW[] tags) {
            this.attributes = attributes;
            this.tags = tags;
        }

        public Attributes getAttributes() {
            return attributes;
        }

        public TagW[] getTags() {
            return tags;
        }
    }
}
