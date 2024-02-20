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
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
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
    return this.observationNumber;
  }

  public void setObservationNumber(int observationNumber) {
    this.observationNumber = observationNumber;
  }

  public String getRtRoiInterpretedType() {
    return this.rtRoiInterpretedType;
  }

  public void setRtRoiInterpretedType(String value) {
    this.rtRoiInterpretedType = value;
    setFilled(!"EXTERNAL".equals(value));
  }

  public String getRoiObservationLabel() {
    return this.roiObservationLabel;
  }

  public void setRoiObservationLabel(String roiObservationLabel) {
    this.roiObservationLabel = roiObservationLabel;
  }

  public double getThickness() {
    return this.thickness;
  }

  public void setThickness(double value) {
    this.thickness = value;
  }

  public Map<KeyDouble, List<StructContour>> getPlanes() {
    return this.planes;
  }

  public void setPlanes(Map<KeyDouble, List<StructContour>> contours) {
    this.planes = contours;
  }

  public Dvh getDvh() {
    return this.dvh;
  }

  public void setDvh(Dvh dvh) {
    this.dvh = dvh;
  }

  public DataSource getVolumeSource() {
    return this.volumeSource;
  }

  public double getVolume() {
    // If volume was not initialised from DVH (e.g. DVH does not exist) recalculate it
    if (this.volume < 0) {
      this.volume = this.calculateVolume();
      this.volumeSource = DataSource.CALCULATED;
    }

    return this.volume;
  }

  public void setVolume(double value) {
    this.volume = value;
    this.volumeSource = DataSource.PROVIDED;
  }

  public AbstractMap.SimpleImmutableEntry<Integer, Double> calculateLargestContour(
      List<StructContour> planeContours) {
    double maxContourArea = 0.0;
    int maxContourIndex = 0;

    // Calculate the area for each contour of this structure in provided plane
    for (int i = 0; i < planeContours.size(); i++) {
      StructContour polygon = planeContours.get(i);

      // Find the largest polygon of contour
      if (polygon.getArea() > maxContourArea) {
        maxContourArea = polygon.getArea();
        maxContourIndex = i;
      }
    }

    return new AbstractMap.SimpleImmutableEntry<>(maxContourIndex, maxContourArea);
  }

  public String getSortLabel() {
    if (StringUtil.hasText(rtRoiInterpretedType)) {
      return rtRoiInterpretedType + getLabel();
    }
    return this.getLabel();
  }

  public static List<List<StructRegion>> sort(Collection<List<StructRegion>> regions) {
    List<List<StructRegion>> sortedRegions = new ArrayList<>(regions);
    sortedRegions.sort(
        (List<StructRegion> a, List<StructRegion> b) -> {
          if (a.isEmpty() && b.isEmpty()) {
            return 0;
          }
          if (a.isEmpty()) {
            return 1;
          }
          if (b.isEmpty()) {
            return -1;
          }
          return a.getFirst().compareTo(b.getFirst());
        });
    return sortedRegions;
  }

  private double calculateVolume() {
    double structureVolume = 0.0;

    // Iterate over structure planes (z)
    int n = 0;
    for (List<StructContour> structurePlaneContours : this.planes.values()) {

      // Calculate the area for each contour in the current plane
      AbstractMap.SimpleImmutableEntry<Integer, Double> maxContour =
          this.calculateLargestContour(structurePlaneContours);
      int maxContourIndex = maxContour.getKey();
      double maxContourArea = maxContour.getValue();

      for (int i = 0; i < structurePlaneContours.size(); i++) {
        StructContour polygon = structurePlaneContours.get(i);

        // Find the largest polygon of contour
        if (polygon.getArea() > maxContourArea) {
          maxContourArea = polygon.getArea();
          maxContourIndex = i;
        }
      }

      // Sum the area of contours in the current plane
      StructContour largestPolygon = structurePlaneContours.get(maxContourIndex);
      double area = largestPolygon.getArea();
      for (int i = 0; i < structurePlaneContours.size(); i++) {
        StructContour polygon = structurePlaneContours.get(i);
        if (i != maxContourIndex) {
          area += polygon.getArea();
        }
      }

      // For first and last plane calculate with half of thickness
      if ((n == 0) || (n == this.planes.size() - 1)) {
        structureVolume += area * this.thickness * 0.5;
      } else {
        // For rest use the full slice thickness
        structureVolume += area * this.thickness;
      }

      n++;
    }

    // DICOM uses millimeters -> convert from mm^3 to cm^3
    return structureVolume / 1000;
  }

  @Override
  public int compareTo(RegionAttributes o) {
    if (o instanceof StructRegion) {
      return StringUtil.collator.compare(getSortLabel(), ((StructRegion) o).getSortLabel());
    }
    return super.compareTo(o);
  }
}
