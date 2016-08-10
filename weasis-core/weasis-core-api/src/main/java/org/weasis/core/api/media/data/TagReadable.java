package org.weasis.core.api.media.data;

import java.util.Iterator;
import java.util.Map.Entry;

public interface TagReadable {

    boolean containTagKey(TagW tag);

    Object getTagValue(TagW tag);

    Iterator<Entry<TagW, Object>> getTagEntrySetIterator();
}