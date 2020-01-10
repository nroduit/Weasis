/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.codec;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import org.dcm4che3.data.Attributes;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Tagable;
import org.weasis.dicom.codec.TagD.Level;

public class TagManager {
    private final EnumMap<Level, List<TagW>> levelMap;

    public TagManager() {
        this.levelMap = new EnumMap<>(Level.class);
        this.levelMap.put(Level.PATIENT, new ArrayList<TagW>());
        this.levelMap.put(Level.STUDY, new ArrayList<TagW>());
        this.levelMap.put(Level.SERIES, new ArrayList<TagW>());
        this.levelMap.put(Level.INSTANCE, new ArrayList<TagW>());
        this.levelMap.put(Level.FRAME, new ArrayList<TagW>());
    }

    public void addTag(int tagID, Level level) {
        addTag(TagD.get(tagID), level);
    }

    public void addTag(TagW tag, Level level) {
        if (tag == null || level == null) {
            return;
        }

        List<TagW> list = levelMap.get(level);
        if (list != null && !list.contains(tag)) {
            list.add(tag);
        }
    }

    public boolean contains(TagW tag, Level level) {
        if (tag == null || level == null) {
            return false;
        }

        List<TagW> list = levelMap.get(level);
        return list != null && list.contains(tag);
    }

    public void readTags(Level level, Attributes header, Tagable tags) {
        if (level == null || header == null || tags == null) {
            return;
        }

        List<TagW> list = levelMap.get(level);
        if (list != null) {
            for (TagW tagW : list) {
                tagW.readValue(header, tags);
            }
        }
    }
}
