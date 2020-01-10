/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.codec;

import java.util.function.Predicate;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.VR;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Tagable;

public class TagSeq extends TagD {

    private static final long serialVersionUID = -7782852222112967568L;

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
                Predicate<? super Attributes> predicate = macro.getApplicable();
                if (predicate == null || predicate.test(dataset)) {
                    for (TagW tag : macro.getTags()) {
                        if (tag != null) {
                            tag.readValue(dataset, tagabale);
                        }
                    }
                }
            }
        }
    }

    public static class MacroSeqData {
        private final Attributes attributes;
        private final TagW[] tags;
        private final Predicate<? super Attributes> applicable;

        public MacroSeqData(Attributes attributes, TagW[] tags) {
            this(attributes, tags, null);
        }

        public MacroSeqData(Attributes attributes, TagW[] tags, Predicate<? super Attributes> applicable) {
            this.attributes = attributes;
            this.tags = tags;
            this.applicable = applicable;
        }

        public Attributes getAttributes() {
            return attributes;
        }

        public TagW[] getTags() {
            return tags;
        }

        public Predicate<? super Attributes> getApplicable() {
            return applicable;
        }
    }
}
