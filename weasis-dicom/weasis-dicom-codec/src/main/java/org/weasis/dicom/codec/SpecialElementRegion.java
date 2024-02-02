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
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.dcm4che3.data.Tag;
import org.weasis.core.ui.model.graphic.imp.seg.SegContour;
import org.weasis.opencv.seg.RegionAttributes;

public interface SpecialElementRegion {
  boolean isVisible();

  void setVisible(boolean visible);

  float getOpacity();

  void setOpacity(float opacity);

  Map<String, Map<String, Set<SegContour>>> getRefMap();

  Map<Integer, ? extends RegionAttributes> getSegAttributes();

  default boolean containsSopInstanceUIDReference(DicomImageElement img) {
    if (img != null) {
      String seriesUID = TagD.getTagValue(img, Tag.SeriesInstanceUID, String.class);
      if (seriesUID != null) {
        String sopInstanceUID = TagD.getTagValue(img, Tag.SOPInstanceUID, String.class);
        Map<String, Set<SegContour>> map = getRefMap().get(seriesUID);
        if (map != null && sopInstanceUID != null) {
          return map.containsKey(sopInstanceUID);
        }
      }
    }
    return false;
  }

  default Collection<SegContour> getContours(DicomImageElement img) {
    String seriesUID = TagD.getTagValue(img, Tag.SeriesInstanceUID, String.class);
    if (seriesUID != null) {
      String sopInstanceUID = TagD.getTagValue(img, Tag.SOPInstanceUID, String.class);
      Map<String, Set<SegContour>> map = getRefMap().get(seriesUID);
      if (map != null && sopInstanceUID != null) {
        Set<SegContour> list = map.get(sopInstanceUID);
        if (list != null) {
          return list;
        }
      }
    }
    return Collections.emptyList();
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
