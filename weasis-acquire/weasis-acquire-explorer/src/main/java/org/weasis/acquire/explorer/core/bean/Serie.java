/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.acquire.explorer.core.bean;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

import org.dcm4che3.data.Tag;
import org.dcm4che3.util.UIDUtils;
import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.core.api.media.data.TagUtil;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.TagD;

public class Serie extends AbstractTagable implements Comparable<Serie> {
    public enum Type {
        NONE, DATE, NAME;
    }

    private final Type type;
    private String name;
    private LocalDateTime date;

    public static final Serie DEFAULT_SERIE = new Serie();
    public static final Serie DATE_SERIE = new Serie(LocalDateTime.now());
    public static final String DEFAULT_SERIE_NAME = "Other";

    public Serie() {
        this(Type.NONE);
    }

    private Serie(Type type) {
        this.type = type;
        init();
    }

    public Serie(String name) {
        this.type = Type.NAME;
        this.name = name;
        init();
    }

    public Serie(LocalDateTime date) {
        Objects.requireNonNull(date);
        this.type = Type.DATE;        
        this.date = date;
        init();
    }

    private void init() {
        // Default Modality if not overridden
        tags.put(TagD.get(Tag.Modality), "XC");
        tags.put(TagD.get(Tag.SeriesInstanceUID), UIDUtils.createUID());
        TagW operator = TagD.get(Tag.OperatorsName);
        tags.put(operator, AcquireManager.GLOBAL.getTagValue(operator));
    }

    public Type getType() {
        return type;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public void setDate(LocalDateTime date) {
        this.date = Objects.requireNonNull(date);
    }

    public String getDisplayName() {
        switch (type) {
            case NAME:
                return name;
            case DATE:
                return TagUtil.formatDateTime(date);
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
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Serie other = (Serie) obj;
        if (date == null) {
            if (other.date != null)
                return false;
        } else {
            if (other.date == null) {
                return false;
            } else {
                if (!date.atZone(ZoneId.systemDefault()).equals(other.date.atZone(ZoneId.systemDefault())))
                    return false;
            }
        }

        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (type != other.type)
            return false;
        return true;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public int compareTo(Serie that) {
        final int BEFORE = -1;
        final int EQUAL = 0;
        final int AFTER = 1;

        // this optimization is usually worthwhile, and can
        // always be added
        if (this == that)
            return EQUAL;

        // Check Type
        if (this.type.equals(Type.NONE) && !that.type.equals(Type.NONE))
            return BEFORE;
        if (this.type.equals(Type.DATE) && that.type.equals(Type.NONE))
            return AFTER;
        if (this.type.equals(Type.DATE) && that.type.equals(Type.NAME))
            return BEFORE;

        // Check Dates
        if (this.date != null && that.date == null)
            return BEFORE;
        if (this.date == null && that.date != null)
            return AFTER;
        if (this.date != null && that.date != null) {
            int comp = this.date.compareTo(that.date);
            if (comp != EQUAL)
                return comp;
        }

        // Check Names
        if (this.name != null && that.name == null)
            return BEFORE;
        if (this.name == null && that.name != null)
            return AFTER;
        if (this.name != null && that.name != null) {
            int comp = this.name.compareTo(that.name);
            if (comp != EQUAL)
                return comp;
        }

        // Check equals
        assert this.equals(that) : "compareTo inconsistent with equals.";

        return EQUAL;
    }

}
