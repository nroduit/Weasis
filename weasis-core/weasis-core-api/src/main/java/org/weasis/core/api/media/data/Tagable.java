package org.weasis.core.api.media.data;

import java.util.Iterator;
import java.util.Map.Entry;

public interface Tagable extends TagReadable {

    void setTag(TagW tag, Object value);

    void setTagNoNull(TagW tag, Object value);

    Iterator<Entry<TagW, Object>> getTagEntrySetIterator();

}