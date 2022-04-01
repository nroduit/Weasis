/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.media.data;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Objects;
import org.weasis.core.api.Messages;

public class MediaSeriesGroupNode implements MediaSeriesGroup {

  public static final MediaSeriesGroup rootNode =
      new MediaSeriesGroupNode(TagW.RootElement, "__ROOT__", null); // NON-NLS

  private final TagW tagID;
  private final TagView displayTag;
  private final HashMap<TagW, Object> tags = new HashMap<>();

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
  public boolean matchIdValue(Object valueID) {
    return Objects.equals(tags.get(tagID), valueID);
  }

  @Override
  public String toString() {
    String val = displayTag.getFormattedText(false, this);
    return val == null ? Messages.getString("MediaSeriesGroupNode.no_val") : val;
  }

  @Override
  public int hashCode() {
    return Objects.hash(tags.get(tagID));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    // According to the implementation of MediaSeriesGroupNode, the identifier cannot be null
    return Objects.equals(tags.get(tagID), ((MediaSeriesGroup) o).getTagValue(tagID));
  }

  @Override
  public void setTag(TagW tag, Object value) {
    if (tag != null && !tag.equals(tagID)) {
      tags.put(tag, value);
    }
  }

  public void removeTag(TagW tag) {
    if (tag != null && !tag.equals(tagID)) {
      tags.remove(tag);
    }
  }

  @Override
  public void setTagNoNull(TagW tag, Object value) {
    if (tag != null && value != null && !tag.equals(tagID)) {
      tags.put(tag, value);
    }
  }

  @Override
  public Object getTagValue(TagW tag) {
    return tag == null ? null : tags.get(tag);
  }

  @Override
  public TagW getTagElement(int id) {
    for (TagW e : tags.keySet()) {
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
