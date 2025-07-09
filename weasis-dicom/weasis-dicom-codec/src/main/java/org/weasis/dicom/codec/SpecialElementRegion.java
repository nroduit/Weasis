/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import java.awt.*;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.dcm4che3.data.Tag;
import org.joml.Vector3d;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.opencv.seg.RegionAttributes;

public interface SpecialElementRegion {
  boolean isVisible();

  void setVisible(boolean visible);

  float getOpacity();

  void setOpacity(float opacity);

  Map<String, Map<String, Set<LazyContourLoader>>> getRefMap();

  Map<String, Set<LazyContourLoader>> getPositionMap();

  Map<Integer, ? extends RegionAttributes> getSegAttributes();

  default boolean containsSopInstanceUIDReference(DicomImageElement img) {
    if (img != null) {
      String seriesUID = TagD.getTagValue(img, Tag.SeriesInstanceUID, String.class);
      if (seriesUID != null) {
        String sopInstanceUID = TagD.getTagValue(img, Tag.SOPInstanceUID, String.class);
        Map<String, Set<LazyContourLoader>> map = getRefMap().get(seriesUID);
        Map<String, Set<LazyContourLoader>> positionMap = getPositionMap();
        if (!positionMap.isEmpty()) {
          Series<?> series = img.getMediaReader().getMediaSeries();
          String frameOfRef = TagD.getTagValue(series, Tag.FrameOfReferenceUID, String.class);
          if (frameOfRef != null && this instanceof DicomElement group) {
            String frameOfRef2 =
                TagD.getTagValue(
                    group.getMediaReader().getMediaSeries(), Tag.FrameOfReferenceUID, String.class);
            return Objects.equals(frameOfRef, frameOfRef2);
          }
        } else if (map != null && sopInstanceUID != null) {
          return map.containsKey(sopInstanceUID);
        }
      }
    }
    return false;
  }

  default Set<LazyContourLoader> getContours(DicomImageElement img) {
    String seriesUID = TagD.getTagValue(img, Tag.SeriesInstanceUID, String.class);
    if (seriesUID != null) {
      String sopInstanceUID = TagD.getTagValue(img, Tag.SOPInstanceUID, String.class);
      Map<String, Set<LazyContourLoader>> map = getRefMap().get(seriesUID);
      Map<String, Set<LazyContourLoader>> positionMap = getPositionMap();
      if (!positionMap.isEmpty()) {
        double[] loc = (double[]) img.getTagValue(TagW.SlicePosition);
        if (loc != null) {
          String position =
              new Vector3d(loc).toString(SegSpecialElement.roundFloat).replace("-0 ", "0 ");
          return positionMap.get(position);
        }
      } else if (map != null && sopInstanceUID != null) {
        Set<LazyContourLoader> loader;
        int frames = img.getMediaReader().getMediaElementNumber();
        if (frames > 1 && img.getKey() instanceof Integer intVal) {
          loader = map.get(sopInstanceUID + "_" + intVal);
        } else {
          loader = map.get(sopInstanceUID);
        }
        return loader;
      }
    }
    return null;
  }

  default void updateOpacityInSegAttributes(float opacity) {
    int opacityValue = (int) (opacity * 255f);
    getSegAttributes()
        .values()
        .forEach(
            c -> {
              Color color = c.getColor();
              color = new Color(color.getRed(), color.getGreen(), color.getBlue(), opacityValue);
              c.setColor(color);
            });
  }
}
