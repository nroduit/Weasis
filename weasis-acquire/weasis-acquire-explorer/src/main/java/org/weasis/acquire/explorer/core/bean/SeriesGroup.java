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

import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.dcm4che3.data.Tag;
import org.dcm4che3.imageio.codec.mp4.MP4Parser;
import org.dcm4che3.imageio.codec.mpeg.MPEG2Parser;
import org.dcm4che3.util.UIDUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.acquire.explorer.Messages;
import org.weasis.acquire.explorer.gui.central.SeriesDataListener;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.TagUtil;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.TagD;

public class SeriesGroup extends DefaultTaggable implements Comparable<SeriesGroup> {
  private static final Logger LOGGER = LoggerFactory.getLogger(SeriesGroup.class);

  public enum Type {
    IMAGE(Messages.getString("Series.other"), "XC"),
    IMAGE_DATE("Date", "XC"), // NON-NLS
    IMAGE_NAME("Name", "XC"), // NON-NLS
    //    AUDIO("Audio", "AU"),
    VIDEO_MP2(Messages.getString("video.mpeg4"), "XC"),
    VIDEO_MP4(Messages.getString("video.mpeg2"), "XC"),
    PDF(Messages.getString("pdf.document"), "DOC"),
    STL(Messages.getString("stl.3d.model"), "M3D");

    private final String description;
    private final String defaultModality;

    Type(String description, String defaultModality) {
      this.description = description;
      this.defaultModality = defaultModality;
    }

    public String getDescription() {
      return description;
    }

    public String getDefaultModality() {
      return defaultModality;
    }

    @Override
    public String toString() {
      return description;
    }

    public static Type fromMimeType(MediaElement media) {
      if (media == null) {
        return null;
      }
      if (media instanceof ImageElement) {
        return IMAGE;
      }

      // Set specific tags for non-image media
      String mime = media.getMimeType();
      if (mime != null) {
        mime = mime.toLowerCase();
        if (mime.startsWith("video/mp")) { // NON-NLS
          return videoType(media);
          //        } else  if (mime.startsWith("audio/")) { // NON-NLS
          //          return AUDIO;
        } else if (mime.equals("application/pdf")) { // NON-NLS
          return PDF;
        } else if ("application/sla".equals(mime) // NON-NLS
            || "model/stl".equals(mime) // NON-NLS
            || "model/x.stl-binary".equals(mime)) { // NON-NLS
          return STL;
        }
      }
      return null;
    }

    private static Type videoType(MediaElement media) {
      if (isMPEG4(media)) {
        return VIDEO_MP4;
      } else if (isMPEG2(media)) {
        return VIDEO_MP2;
      }
      return null;
    }

    private static boolean isMPEG4(MediaElement media) {
      try (SeekableByteChannel channel = FileChannel.open(media.getFile().toPath())) {
        MP4Parser parser = new MP4Parser(channel);
        return parser.getTransferSyntaxUID() != null;
      } catch (Exception e) {
        LOGGER.trace("Try reading MP4 video file header:", e);
        return false;
      }
    }

    private static boolean isMPEG2(MediaElement media) {
      try (SeekableByteChannel channel = FileChannel.open(media.getFile().toPath())) {
        MPEG2Parser parser = new MPEG2Parser(channel);
        return parser.getTransferSyntaxUID() != null;
      } catch (Exception e) {
        LOGGER.trace("Try reading MPEG2 video file header for:", e);
        return false;
      }
    }
  }

  private final Type type;
  private String name;
  private LocalDateTime date;
  private final List<SeriesDataListener> listenerList = new ArrayList<>();
  private boolean needUpdateFromGlobalTags = false;

  public static final SeriesGroup DATE_SERIES = new SeriesGroup(LocalDateTime.now());

  public SeriesGroup() {
    this(Type.IMAGE);
  }

  public SeriesGroup(SeriesGroup.Type type) {
    this.type = Objects.requireNonNull(type);
    init();
  }

  public SeriesGroup(String name) {
    this.type = Type.IMAGE_NAME;
    this.name = name;
    init();
  }

  public SeriesGroup(LocalDateTime date) {
    this.type = Type.IMAGE_DATE;
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

  private void setIfNotInGlobal(TagW tag, Object value) {
    Object globalValue = AcquireManager.GLOBAL.getTagValue(tag);
    tags.put(tag, globalValue == null ? value : globalValue);
  }

  public void updateDicomTags() {
    // Modality from worklist otherwise default value
    setIfNotInGlobal(TagD.get(Tag.Modality), type.getDefaultModality());
    setIfNotInGlobal(TagD.get(Tag.OperatorsName), null);
    setIfNotInGlobal(TagD.get(Tag.ReferringPhysicianName), null);
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

  public void setSeriesDescription(String description) {
    if (StringUtil.hasText(description)) {
      tags.put(TagD.get(Tag.SeriesDescription), description);
    }
  }

  public String getDisplayName() {
    String desc = TagD.getTagValue(this, Tag.SeriesDescription, String.class);
    if (StringUtil.hasText(desc)) {
      return desc;
    }
    return switch (type) {
      case IMAGE_NAME -> name;
      case IMAGE_DATE -> TagUtil.formatDateTime(date);
      default -> type.getDescription();
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
    return type == that.type && Objects.equals(getDisplayName(), that.getDisplayName());
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
    if (this == that) {
      return 0; // EQUAL
    }
    // Compare Names
    return Objects.compare(
        this.getDisplayName(), that.getDisplayName(), String.CASE_INSENSITIVE_ORDER);
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
