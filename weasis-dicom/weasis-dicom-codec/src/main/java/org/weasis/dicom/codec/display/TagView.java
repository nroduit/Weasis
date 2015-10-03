package org.weasis.dicom.codec.display;

import org.weasis.core.api.media.data.TagW;

public class TagView {
    private final TagW[] tag;
    private final String format;

    public TagView(TagW... tag) {
        this(null, tag);
    }

    public TagView(String format, TagW... tag) {
        this.tag = tag;
        this.format = format;
    }

    public TagW[] getTag() {
        return tag;
    }

    public String getFormat() {
        return format;
    }

    public boolean containsTag(TagW tag) {
        for (TagW tagW : this.tag) {
            if (tagW.equals(tag)) {
                return true;
            }
        }
        return false;
    }

}
