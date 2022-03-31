/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer.core.bean;

import java.time.LocalDateTime;
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

public class SeriesGroup extends DefaultTaggable implements Comparable<SeriesGroup> {
  public enum Type {
    NONE,
    DATE,
    NAME
  }

  private final Type type;
  private String name;
  private LocalDateTime date;
  private final List<SeriesDataListener> listenerList = new ArrayList<>();
  private boolean needUpdateFromGlobalTags = false;

  public static final SeriesGroup DATE_SERIES = new SeriesGroup(LocalDateTime.now());

  public static final String DEFAULT_SERIES_NAME = Messages.getString("Series.other");

  public SeriesGroup() {
    this(Type.NONE);
  }

  private SeriesGroup(Type type) {
    this.type = Objects.requireNonNull(type);
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

  public boolean isNeedUpdateFromGlobalTags() {
    return needUpdateFromGlobalTags;
  }

  public void setNeedUpdateFromGlobalTags(boolean needUpdateFromGlobalTags) {
    this.needUpdateFromGlobalTags = needUpdateFromGlobalTags;
  }

  private void setIfnotInGlobal(TagW tag, Object value) {
    Object globalValue = AcquireManager.GLOBAL.getTagValue(tag);
    tags.put(tag, globalValue == null ? value : globalValue);
  }

  public void updateDicomTags() {
    // Modality from worklist otherwise XC
    setIfnotInGlobal(TagD.get(Tag.Modality), "XC");
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
    return switch (type) {
      case NAME -> name;
      case DATE -> TagUtil.formatDateTime(date);
      case NONE -> DEFAULT_SERIES_NAME;
    };
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SeriesGroup that = (SeriesGroup) o;
    return type == that.type && Objects.equals(name, that.name) && Objects.equals(date, that.date);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, name, date);
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
    if (this.date != null) {
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
    if (this.name != null) {
      int comp = this.name.compareTo(that.name);
      if (comp != EQUAL) {
        return comp;
      }
    }

    // Check equals
    if (!this.equals(that)) {
      throw new IllegalStateException("compareTo inconsistent with equals.");
    }
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
