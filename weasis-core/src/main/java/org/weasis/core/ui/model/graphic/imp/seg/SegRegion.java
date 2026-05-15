/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic.imp.seg;

import java.awt.Color;
import java.util.List;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.util.Copyable;
import org.weasis.opencv.seg.RegionAttributes;

public class SegRegion<E extends ImageElement> extends RegionAttributes
    implements Copyable<SegRegion<E>> {

  private SegMeasurableLayer<E> measurableLayer;
  private boolean selected;
  private String algorithmName;
  private List<String> anatomicRegionCodes;
  private List<String> categories;
  private String fractionalType;

  public SegRegion(int id, String label, Color color) {
    super(id, label, color);
    this.selected = true;
    resetPixelCount();
  }

  public SegRegion(SegRegion<E> region) {
    super(region.getId(), region.getLabel(), new Color(region.getColor().getRGB()));
    copyAttributesFrom(region);
  }

  private void copyAttributesFrom(SegRegion<E> other) {
    setDescription(other.getDescription());
    setType(other.getType());
    setFilled(other.isFilled());
    setLineThickness(other.getLineThickness());
    setVisible(other.isVisible());
    setInteriorOpacity(other.getInteriorOpacity());
    this.algorithmName = other.algorithmName;
    this.anatomicRegionCodes = other.anatomicRegionCodes;
    this.categories = other.categories;
    this.fractionalType = other.fractionalType;
    this.numberOfPixels = other.numberOfPixels;
    this.selected = other.selected;
    this.measurableLayer = other.measurableLayer;
  }

  public SegMeasurableLayer<E> getMeasurableLayer() {
    return measurableLayer;
  }

  public void setMeasurableLayer(SegMeasurableLayer<E> measurableLayer) {
    this.measurableLayer = measurableLayer;
  }

  public boolean isSelected() {
    return selected;
  }

  public void setSelected(boolean selected) {
    this.selected = selected;
  }

  @Override
  public SegRegion<E> copy() {
    return new SegRegion<>(this);
  }

  public String getAlgorithmName() {
    return algorithmName;
  }

  public void setAlgorithmName(String algorithmName) {
    this.algorithmName = algorithmName;
  }

  public void setAnatomicRegionCodes(List<String> anatomicRegions) {
    this.anatomicRegionCodes = anatomicRegions == null ? null : List.copyOf(anatomicRegions);
  }

  public List<String> getAnatomicRegionCodes() {
    return anatomicRegionCodes;
  }

  public void setCategories(List<String> categories) {
    this.categories = categories == null ? null : List.copyOf(categories);
  }

  public List<String> getCategories() {
    return categories;
  }

  /** Returns the fractional type (PROBABILITY or OCCUPANCY) or null for BINARY segmentations. */
  public String getFractionalType() {
    return fractionalType;
  }

  public void setFractionalType(String fractionalType) {
    this.fractionalType = fractionalType;
  }

  /** Returns true if this region is from a FRACTIONAL segmentation. */
  public boolean isFractional() {
    return fractionalType != null;
  }
}
