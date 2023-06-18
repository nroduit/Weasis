/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer.gui.central.meta.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.swing.table.AbstractTableModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.acquire.explorer.Messages;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Taggable;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.TagD;

public abstract class AcquireMetadataTableModel extends AbstractTableModel {
  private static final Logger LOGGER = LoggerFactory.getLogger(AcquireMetadataTableModel.class);

  protected String[] headers = {
    Messages.getString("AcquireMetadataTableModel.tag"),
    Messages.getString("AcquireMetadataTableModel.val")
  };
  protected Optional<Taggable> taggable;
  private final TagW[] tagsToDisplay;
  private final TagW[] tagsEditable;
  private final TagW[] tagsToPublish;

  protected AcquireMetadataTableModel(
      Taggable taggable, TagW[] tagsToDisplay, TagW[] tagsEditable, TagW[] tagsToPublish) {
    this.taggable = Optional.ofNullable(taggable);
    this.tagsToPublish = tagsToPublish == null ? new TagW[0] : tagsToPublish;

    List<TagW> addTags = new ArrayList<>();
    for (TagW tag : this.tagsToPublish) {
      if (taggable == null || taggable.getTagValue(tag) == null) {
        if (tagsToDisplay == null || Arrays.stream(tagsToDisplay).noneMatch(t -> t.equals(tag))) {
          addTags.add(tag);
        }
      }
    }
    this.tagsToDisplay = getMoreTags(tagsToDisplay, addTags);
    this.tagsEditable = getMoreTags(tagsEditable, addTags);
  }

  private static TagW[] getMoreTags(TagW[] tags, List<TagW> addTags) {
    if (addTags.isEmpty()) {
      return tags == null ? new TagW[0] : tags;
    }
    addTags.addAll(Arrays.asList(tags));
    return addTags.toArray(new TagW[0]);
  }

  public static boolean hasNonNullValues(TagW[] tags, TagReadable tagMaps) {
    if (tags != null) {
      for (TagW t : tags) {
        Object val = tagMaps.getTagValue(t);
        if (val == null) {
          return false;
        }
        if (val instanceof String str && !StringUtil.hasText(str)) {
          return false;
        }
      }
    }
    return true;
  }

  protected TagW[] tagsToDisplay() {
    return tagsToDisplay;
  }

  protected TagW[] tagsEditable() {
    return AcquireManager.GLOBAL.isAllowFullEdition() ? tagsToDisplay() : tagsEditable;
  }

  protected TagW[] tagsToPublish() {
    return tagsToPublish;
  }

  @Override
  public int getRowCount() {
    return tagsToDisplay().length;
  }

  @Override
  public int getColumnCount() {
    return headers.length;
  }

  @Override
  public String getColumnName(int column) {
    return headers[column];
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    TagW tag = tagsToDisplay()[rowIndex];
    return switch (columnIndex) {
      case 0 -> tag;
      case 1 -> taggable.map(value -> value.getTagValue(tag)).orElse(null);
      default -> null;
    };
  }

  public boolean isValueRequired(int rowIndex) {
    TagW tag = tagsToDisplay()[rowIndex];
    return Arrays.asList(tagsToPublish()).contains(tag);
  }

  @Override
  public boolean isCellEditable(int rowIndex, int columnIndex) {
    TagW tag = tagsToDisplay()[rowIndex];
    if (columnIndex == 1) {
      return Arrays.asList(tagsEditable()).contains(tag);
    }
    return false;
  }

  @Override
  public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
    if (columnIndex == 1) {
      TagW tag = tagsToDisplay()[rowIndex];
      taggable.ifPresent(
          t -> {
            t.setTag(tag, aValue);
            fireTableCellUpdated(rowIndex, columnIndex);
          });
    }
  }

  public static TagW[] getTags(String property, String defaultValues) {
    String values = BundleTools.SYSTEM_PREFERENCES.getProperty(property, defaultValues);
    if (values == null) {
      return new TagW[0];
    }
    String[] val = values.split(",");
    List<TagW> list = new ArrayList<>(val.length);
    for (String s : val) {
      TagW tag = TagD.get(s.trim());
      if (tag != null) {
        list.add(tag);
      } else if (StringUtil.hasText(s)) {
        LOGGER.warn("Cannot find the tag named {}", s);
      }
    }
    return list.toArray(new TagW[0]);
  }
}
