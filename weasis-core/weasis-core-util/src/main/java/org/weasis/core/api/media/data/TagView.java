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
                String str = t.getFormattedTagValue(TagUtil.getTagValue(t, tagable), format);
                if (StringUtil.hasText(str)) {
                    return str;
                }
            }
        }
        return StringUtil.EMPTY_STRING;
    }
}
