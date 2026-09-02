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

import java.util.function.Function;
import java.util.stream.Stream;
import org.weasis.core.util.StringUtil;

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

  /**
   * Formats the first tag of this view that yields text, applying the active {@link IdentityMask}.
   */
  public String getFormattedText(TagReadable... taggable) {
    TagReadable readable =
        Stream.of(taggable).filter(t -> t.containTagKey(TagW.Timezone)).findFirst().orElse(null);
    return resolveText(t -> TagUtil.getTagValue(t, taggable), readable);
  }

  /**
   * Shared resolution used by every annotation layer: walks the candidate tags, applies the active
   * mask, and returns the first non-empty rendering.
   *
   * @param valueLookup supplies the raw value of a tag from the caller's own context
   * @param timeZoneSource source of {@link TagW#Timezone} for time formatting, may be null
   */
  public String resolveText(Function<TagW, Object> valueLookup, TagReadable timeZoneSource) {
    IdentityMask mask = IdentityMask.active().orElse(null);
    for (TagW t : this.tag) {
      AnonymizationAction action = mask == null ? AnonymizationAction.KEEP : mask.actionFor(t);
      if (action == AnonymizationAction.REMOVE) {
        continue;
      }
      if (action == AnonymizationAction.CLEAR) {
        return StringUtil.EMPTY_STRING;
      }
      Object value = valueLookup.apply(t);
      if (value == null) {
        continue;
      }
      String str =
          t.getFormattedTagValue(
              mask == null ? value : mask.apply(t, value), t.addGMTOffset(format, timeZoneSource));
      if (StringUtil.hasText(str)) {
        return str;
      }
    }
    return StringUtil.EMPTY_STRING;
  }
}
