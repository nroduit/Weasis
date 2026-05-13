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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.weasis.core.ui.model.graphic.imp.seg.SegRegion;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.opencv.seg.RegionAttributes;

public class StructRegion extends SegRegion<DicomImageElement> {
  private int observationNumber;
  private String rtRoiInterpretedType;
  private String roiObservationLabel;
  private double thickness;
  private double volume; // unit cm^3
  private DataSource volumeSource;

  private Dvh dvh;
  private Map<KeyDouble, List<StructContour>> planes;

  public StructRegion(int id, String label, Color color) {
    super(id, label, color);
    this.volume = -1.0;
  }

  public int getObservationNumber() {
    return observationNumber;
  }

  public void setObservationNumber(int observationNumber) {
    this.observationNumber = observationNumber;
  }

  public String getRtRoiInterpretedType() {
    return rtRoiInterpretedType;
  }

  public void setRtRoiInterpretedType(String value) {
    this.rtRoiInterpretedType = value;
    setFilled(!"EXTERNAL".equals(value));
  }

  public String getRoiObservationLabel() {
    return roiObservationLabel;
  }

  public void setRoiObservationLabel(String roiObservationLabel) {
    this.roiObservationLabel = roiObservationLabel;
  }

  public double getThickness() {
    return thickness;
  }

  public void setThickness(double value) {
    this.thickness = value;
  }

  public Map<KeyDouble, List<StructContour>> getPlanes() {
    return planes;
  }

  public void setPlanes(Map<KeyDouble, List<StructContour>> contours) {
    this.planes = contours;
  }

  public Dvh getDvh() {
    return dvh;
  }

  public void setDvh(Dvh dvh) {
    this.dvh = dvh;
  }

  public DataSource getVolumeSource() {
    return volumeSource;
  }

  public double getVolume() {
    if (volume < 0) {
      volume = calculateVolume();
      volumeSource = DataSource.CALCULATED;
    }
    return volume;
  }

  public void setVolume(double value) {
    this.volume = value;
    this.volumeSource = DataSource.PROVIDED;
  }

  /** Returns the index and area of the largest contour in the given plane. */
  public LargestContour calculateLargestContour(List<StructContour> planeContours) {
    double maxContourArea = 0.0;
    int maxContourIndex = 0;
    for (int i = 0; i < planeContours.size(); i++) {
      double area = planeContours.get(i).getArea();
      if (area > maxContourArea) {
        maxContourArea = area;
        maxContourIndex = i;
      }
    }
    return new LargestContour(maxContourIndex, maxContourArea);
  }

  public String getSortLabel() {
    return StringUtil.hasText(rtRoiInterpretedType)
        ? rtRoiInterpretedType + getLabel()
        : getLabel();
  }

  public static List<List<StructRegion>> sort(Collection<List<StructRegion>> regions) {
    List<List<StructRegion>> sortedRegions = new ArrayList<>(regions);
    sortedRegions.sort(
        Comparator.comparing(
            (List<StructRegion> list) -> list.isEmpty() ? null : list.getFirst(),
            Comparator.nullsLast(Comparator.naturalOrder())));
    return sortedRegions;
  }

  private double calculateVolume() {
    if (planes == null || planes.isEmpty()) {
      return 0.0;
    }
    double structureVolume = 0.0;
    int n = 0;
    int last = planes.size() - 1;
    for (List<StructContour> planeContours : planes.values()) {
      double weight = (n == 0 || n == last) ? 0.5 : 1.0;
      structureVolume += planeArea(planeContours) * thickness * weight;
      n++;
    }
    // DICOM uses millimeters -> convert from mm^3 to cm^3
    return structureVolume / 1000;
  }

  private static double planeArea(List<StructContour> planeContours) {
    double area = 0.0;
    for (StructContour c : planeContours) {
      area += c.getArea();
    }
    return area;
  }

  @Override
  public int compareTo(RegionAttributes o) {
    if (o instanceof StructRegion other) {
      return StringUtil.collator.compare(getSortLabel(), other.getSortLabel());
    }
    return super.compareTo(o);
  }
}
