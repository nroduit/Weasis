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

import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import org.weasis.core.api.Messages;

public class MediaSeriesGroupNode implements MediaSeriesGroup {

    private final TagElement tagID;
    private final TagElement displayTag;
    private final HashMap<TagElement, Object> tags;
    private Comparator<TagElement> comparator;

    public MediaSeriesGroupNode(TagElement tagID, Object identifier) {
        this(tagID, identifier, null);
    }

    public MediaSeriesGroupNode(TagElement tagID, Object identifier, TagElement displayTag) {
        if (tagID == null || identifier == null) {
            throw new IllegalArgumentException("tagID or identifier cannot be null"); //$NON-NLS-1$
        }
        this.displayTag = displayTag == null ? tagID : displayTag;
        this.tags = new HashMap<TagElement, Object>();
        this.tagID = tagID;
        tags.put(tagID, identifier);
    }

    public TagElement getTagID() {
        return tagID;
    }

    public boolean containTagKey(TagElement tag) {
        return tags.containsKey(tag);
    }

    @Override
    public String toString() {
        Object val = tags.get(displayTag);
        // TODO handle date format in Local settings
        if (val instanceof Date) {
            val = new SimpleDateFormat("dd/MM/yyyy").format(val); //$NON-NLS-1$
        }
        return val == null ? Messages.getString("MediaSeriesGroupNode.no_val") + displayTag.getName() : val.toString(); //$NON-NLS-1$
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
        // Should never happens, but it does ?
        if (val == null) {
            return this.hashCode();
        }
        return val.hashCode();
    }

    public void setTag(TagElement tag, Object value) {
        if (tag != null) {
            tags.put(tag, value);
        }
    }

    public Object getTagValue(TagElement tag) {
        return tags.get(tag);
    }

    public TagElement getTagElement(int id) {
        Iterator<TagElement> enumVal = tags.keySet().iterator();
        while (enumVal.hasNext()) {
            TagElement e = enumVal.next();
            if (e.getId() == id) {
                return e;
            }
        }
        return null;
    }

    public Iterator<Entry<TagElement, Object>> getTagEntrySetIterator() {
        return tags.entrySet().iterator();
    }

    public void dispose() {

    }

    // can be null
    @Override
    public Comparator<TagElement> getComparator() {
        return comparator;
    }

    @Override
    public void setComparator(Comparator<TagElement> comparator) {
        this.comparator = comparator;

    }

}
