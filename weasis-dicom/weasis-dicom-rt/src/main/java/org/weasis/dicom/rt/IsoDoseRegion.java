/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.rt;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import org.weasis.core.ui.model.graphic.imp.seg.SegRegion;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.opencv.seg.SegmentAttributes;
import org.weasis.opencv.seg.SegmentCategory;

public class IsoDoseRegion extends SegRegion<DicomImageElement> {
  private final int level;
  private final double absoluteDose;
  private double thickness;

  private Map<KeyDouble, List<StructContour>> planes;

  public IsoDoseRegion(int level, Color color, String name, double planDose) {
    super(String.valueOf(level));
    this.level = level;
    setAttributes(new SegmentAttributes(color, true, 1.0f));
    getAttributes().setInteriorOpacity(0.2f);
    this.absoluteDose = ((this.level) * planDose) / 100.0;
    String result =
        STR."\{this.level} % / \{String.format("%.6g", this.absoluteDose)} cGy"; // NON-NLS
    if (StringUtil.hasText(name)) {
      result += STR." [\{name}]";
    }
    setCategory(new SegmentCategory(level, result, null, null));
  }

  public int getLevel() {
    return level;
  }

  public double getAbsoluteDose() {
    return absoluteDose;
  }

  public double getThickness() {
    return thickness;
  }

  public void setThickness(double thickness) {
    this.thickness = thickness;
  }

  public Map<KeyDouble, List<StructContour>> getPlanes() {
    return planes;
  }

  public void setPlanes(Map<KeyDouble, List<StructContour>> planes) {
    this.planes = planes;
  }
}
