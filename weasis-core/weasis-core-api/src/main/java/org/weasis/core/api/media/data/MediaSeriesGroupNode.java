/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.api.media.data;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import org.weasis.core.api.Messages;

public class MediaSeriesGroupNode implements MediaSeriesGroup {

    public static final MediaSeriesGroup rootNode = new MediaSeriesGroupNode(TagW.RootElement, "__ROOT__", null); //$NON-NLS-1$

    private final TagW tagID;
    private final TagView displayTag;
    private final HashMap<TagW, Object> tags;
    private Comparator<TagW> comparator;

    public MediaSeriesGroupNode(TagW tagID, Object identifier, TagView displayTag) {
        if (tagID == null || identifier == null) {
            throw new IllegalArgumentException("tagID or identifier cannot be null"); //$NON-NLS-1$
        }
        this.displayTag = displayTag == null ? new TagView(tagID) : displayTag;
        this.tags = new HashMap<>();
        this.tagID = tagID;
        tags.put(tagID, identifier);
    }

    @Override
    public TagW getTagID() {
        return tagID;
    }

    @Override
    public boolean containTagKey(TagW tag) {
        return tags.containsKey(tag);
    }

    @Override
    public String toString() {
        String val = displayTag.getFormattedText(false, this);
        return val == null ? Messages.getString("MediaSeriesGroupNode.no_val"): val; //$NON-NLS-1$
    }

    @Override
    public boolean equals(Object obj) {
        Object value1 = tags.get(tagID);
        if (value1 == obj) {
            return true;
        }
        if (value1 == null) {
            return false;
        }
        if (obj instanceof MediaSeriesGroupNode) {
            Object value2 = ((MediaSeriesGroupNode) obj).tags.get(tagID);
            return value1.equals(value2);
        }
        return value1.equals(obj);
    }

    @Override
    public int hashCode() {
        Object val = tags.get(tagID);
        if (val instanceof Integer) {
            return (Integer) val;
        }
        // Should never happens, but it does very rarely ?
        if (val == null) {
            return tags.hashCode();
        }
        return val.hashCode();
    }

    @Override
    public void setTag(TagW tag, Object value) {
        if (tag != null) {
            tags.put(tag, value);
        }
    }

    public void removeTag(TagW tag) {
        if (tag != null) {
            tags.remove(tag);
        }
    }

    @Override
    public void setTagNoNull(TagW tag, Object value) {
        if (tag != null && value != null) {
            tags.put(tag, value);
        }
    }

    @Override
    public Object getTagValue(TagW tag) {
        return tag == null ? null : tags.get(tag);
    }

    @Override
    public TagW getTagElement(int id) {
        Iterator<TagW> enumVal = tags.keySet().iterator();
        while (enumVal.hasNext()) {
            TagW e = enumVal.next();
            if (e.getId() == id) {
                return e;
            }
        }
        return null;
    }

    @Override
    public Iterator<Entry<TagW, Object>> getTagEntrySetIterator() {
        return tags.entrySet().iterator();
    }

    @Override
    public void dispose() {
        // Nothing to dispose
    }

    // can be null
    @Override
    public Comparator<TagW> getComparator() {
        return comparator;
    }

    @Override
    public void setComparator(Comparator<TagW> comparator) {
        this.comparator = comparator;

    }

}
