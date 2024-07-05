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
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.model.graphic.imp.seg.SegRegion;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;

public class IsoDoseRegion extends SegRegion<DicomImageElement> {
  private final int level;
  private final double absoluteDose;
  private double thickness;

  public IsoDoseRegion(int level, Color color, String name, double planDose) {
    super(level, TagW.NO_VALUE, color);
    this.level = level;
    setInteriorOpacity(0.2f);
    this.absoluteDose = ((this.level) * planDose) / 100.0;
    String result =
        this.level + " % / " + String.format("%.6g", this.absoluteDose) + " cGy"; // NON-NLS
    if (StringUtil.hasText(name)) {
      result += " [" + name + "]";
    }
    setLabel(result);
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
}
