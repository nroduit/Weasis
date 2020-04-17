/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.acquire.explorer.core.bean;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.dcm4che3.data.Tag;
import org.dcm4che3.util.UIDUtils;
import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.acquire.explorer.Messages;
import org.weasis.acquire.explorer.gui.central.SeriesDataListener;
import org.weasis.core.api.media.data.TagUtil;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.TagD;

public class SeriesGroup extends DefaultTagable implements Comparable<SeriesGroup> {
    public enum Type {
        NONE, DATE, NAME;
    }

    private final Type type;
    private String name;
    private LocalDateTime date;
    private final List<SeriesDataListener> listenerList = new ArrayList<>();
    private boolean needUpateFromGlobaTags = false;

    public static final SeriesGroup DATE_SERIE = new SeriesGroup(LocalDateTime.now());

    public static final String DEFAULT_SERIE_NAME = Messages.getString("Serie.other"); //$NON-NLS-1$

    public SeriesGroup() {
        this(Type.NONE);
    }

    private SeriesGroup(Type type) {
        this.type = type;
        init();
    }

    public SeriesGroup(String name) {
        this.type = Type.NAME;
        this.name = name;
        init();
    }

    public SeriesGroup(LocalDateTime date) {
        this.type = Type.DATE;
        this.date = Objects.requireNonNull(date);
        init();
    }

    private void init() {
        tags.put(TagD.get(Tag.SeriesInstanceUID), UIDUtils.createUID());
        tags.put(TagD.get(Tag.SeriesDescription), getDisplayName());
        updateDicomTags();
    }

    public boolean isNeedUpateFromGlobaTags() {
        return needUpateFromGlobaTags;
    }

    public void setNeedUpateFromGlobaTags(boolean needUpateFromGlobaTags) {
        this.needUpateFromGlobaTags = needUpateFromGlobaTags;
    }

    private void setIfnotInGlobal(TagW tag, Object value) {
        Object globalValue = AcquireManager.GLOBAL.getTagValue(tag);
        tags.put(tag, globalValue == null ? value : globalValue);
    }

    public void updateDicomTags() {
        // Modality from worklist otherwise XC
        setIfnotInGlobal(TagD.get(Tag.Modality), "XC"); //$NON-NLS-1$
        setIfnotInGlobal(TagD.get(Tag.OperatorsName), null);
        setIfnotInGlobal(TagD.get(Tag.ReferringPhysicianName), null);
    }

    public Type getType() {
        return type;
    }

    public String getUID() {
        return TagD.getTagValue(this, Tag.SeriesInstanceUID, String.class);
    }

    public LocalDateTime getDate() {
        return date;
    }

    public void setDate(LocalDateTime date) {
        this.date = Objects.requireNonNull(date);
    }

    public String getDisplayName() {
        String desc = TagD.getTagValue(this, Tag.SeriesDescription, String.class);
        if (StringUtil.hasText(desc)) {
            return desc;
        }
        switch (type) {
            case NAME:
                return name;
            case DATE:
                return TagUtil.formatDateTime(date);
            case NONE:
            default:
                return DEFAULT_SERIE_NAME;
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((date == null) ? 0 : date.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
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
        if (getClass() != obj.getClass()) {
            return false;
        }
        SeriesGroup other = (SeriesGroup) obj;
        if (date == null) {
            if (other.date != null) {
                return false;
            }
        } else {
            if (other.date == null) {
                return false;
            } else {
                if (!date.atZone(ZoneId.systemDefault()).equals(other.date.atZone(ZoneId.systemDefault()))) {
                    return false;
                }
            }
        }

        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        if (type != other.type) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public int compareTo(SeriesGroup that) {
        final int BEFORE = -1;
        final int EQUAL = 0;
        final int AFTER = 1;

        // this optimization is usually worthwhile, and can
        // always be added
        if (this == that) {
            return EQUAL;
        }

        // Check Type
        if (this.type.equals(Type.NONE) && !that.type.equals(Type.NONE)) {
            return BEFORE;
        }
        if (this.type.equals(Type.DATE) && that.type.equals(Type.NONE)) {
            return AFTER;
        }
        if (this.type.equals(Type.DATE) && that.type.equals(Type.NAME)) {
            return BEFORE;
        }

        // Check Dates
        if (this.date != null && that.date == null) {
            return BEFORE;
        }
        if (this.date == null && that.date != null) {
            return AFTER;
        }
        if (this.date != null && that.date != null) {
            int comp = this.date.compareTo(that.date);
            if (comp != EQUAL) {
                return comp;
            }
        }

        // Check Names
        if (this.name != null && that.name == null) {
            return BEFORE;
        }
        if (this.name == null && that.name != null) {
            return AFTER;
        }
        if (this.name != null && that.name != null) {
            int comp = this.name.compareTo(that.name);
            if (comp != EQUAL) {
                return comp;
            }
        }

        // Check equals
        assert this.equals(that) : "compareTo inconsistent with equals."; //$NON-NLS-1$

        return EQUAL;
    }

    public void addLayerChangeListener(SeriesDataListener listener) {
        if (listener != null && !listenerList.contains(listener)) {
            listenerList.add(listener);
        }
    }

    public void removeLayerChangeListener(SeriesDataListener listener) {
        if (listener != null) {
            listenerList.remove(listener);
        }
    }

    public void fireDataChanged() {
        for (SeriesDataListener l : listenerList) {
            l.handleSeriesChanged();
        }
    }

}
