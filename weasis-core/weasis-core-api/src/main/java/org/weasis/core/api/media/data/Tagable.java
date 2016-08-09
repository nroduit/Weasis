package org.weasis.core.api.media.data;

public interface Tagable extends TagReadable {

    void setTag(TagW tag, Object value);

    void setTagNoNull(TagW tag, Object value);

}