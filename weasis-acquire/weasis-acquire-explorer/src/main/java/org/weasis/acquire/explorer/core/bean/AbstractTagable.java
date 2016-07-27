package org.weasis.acquire.explorer.core.bean;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Tagable;

public abstract class AbstractTagable implements Tagable {
    protected final Map<TagW, Object> tags = new HashMap<>();
    
    @Override
    public boolean containTagKey(TagW tag) {
        return tags.containsKey(tag);
    }

    @Override
    public Object getTagValue(TagW tag) {
        return tag == null ? null : tags.get(tag);
    }

    @Override
    public void setTag(TagW tag, Object value) {
        tags.put(tag, value);
    }

    @Override
    public void setTagNoNull(TagW tag, Object value) {
        if (value != null) {
            setTag(tag, value);
        }

    }

    @Override
    public Iterator<Entry<TagW, Object>> getTagEntrySetIterator() {
        return tags.entrySet().iterator();
    }

    public Set<Entry<TagW, Object>> getTagEntrySet() {
        return tags.entrySet();
    }
}
