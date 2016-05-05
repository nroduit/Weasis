package org.weasis.core.api.media.data;

import org.weasis.core.api.util.StringUtil;

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

    public String getFormattedText(boolean anonymize, TagReadable... tagable) {
        for (TagW t : this.tag) {
            if (!anonymize || t.getAnonymizationType() != 1) {
                Object value = TagUtil.getTagValue(t, tagable);
                if (value != null) {
                    String str = t.getFormattedText(value, format);
                    if (StringUtil.hasText(str)) {
                        return str;
                    }
                }
            }
        }
        return "";
    }
}
