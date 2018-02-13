/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
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
