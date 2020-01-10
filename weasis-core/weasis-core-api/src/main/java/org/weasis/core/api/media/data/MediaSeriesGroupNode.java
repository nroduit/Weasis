/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.api.media.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;

import org.weasis.core.api.Messages;

public class MediaSeriesGroupNode implements MediaSeriesGroup {

    public static final MediaSeriesGroup rootNode = new MediaSeriesGroupNode(TagW.RootElement, "__ROOT__", null); //$NON-NLS-1$

    private final TagW tagID;
    private final TagView displayTag;
    private final HashMap<TagW, Object> tags = new HashMap<>();
    private final List<Object> oldIds = new ArrayList<>();

    public MediaSeriesGroupNode(TagW tagID, Object identifier, TagView displayTag) {
        this.tagID = Objects.requireNonNull(tagID);
        tags.put(tagID, Objects.requireNonNull(identifier));
        this.displayTag = displayTag == null ? new TagView(tagID) : displayTag;
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
    public void addMergeIdValue(Object valueID) {
        if (!oldIds.contains(valueID)) {
            oldIds.add(valueID);
        }
    }

    @Override
    public boolean matchIdValue(Object valueID) {
        Object v = tags.get(tagID);

        if (Objects.equals(v, valueID)) {
            return true;
        }
        for (Object id : oldIds) {
            if (Objects.equals(id, valueID)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        String val = displayTag.getFormattedText(false, this);
        return val == null ? Messages.getString("MediaSeriesGroupNode.no_val") : val; //$NON-NLS-1$
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        Object val = tags.get(tagID);
        result = prime * result + ((val == null) ? tags.hashCode() : val.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof MediaSeriesGroup)) {
            return false;
        }
        // According to the implementation of MediaSeriesGroupNode, the identifier cannot be null
        return Objects.equals(tags.get(tagID), ((MediaSeriesGroup) obj).getTagValue(tagID));
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

}
