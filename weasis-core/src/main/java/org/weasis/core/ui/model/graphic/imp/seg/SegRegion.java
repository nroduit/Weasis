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

  public SegRegion(int id, String label, Color color) {
    super(id, label, color);
    this.selected = true;
    resetPixelCount();
  }

  public SegRegion(SegRegion<E> region) {
    super(region.getId(), region.getLabel(), new Color(region.getColor().getRGB()));
    this.setDescription(region.getDescription());
    this.setType(region.getType());
    this.setFilled(region.isFilled());
    this.setLineThickness(region.getLineThickness());
    this.setVisible(region.isVisible());
    this.setInteriorOpacity(region.getInteriorOpacity());
    this.algorithmName = region.algorithmName;
    this.anatomicRegionCodes = region.anatomicRegionCodes;
    this.categories = region.categories;

    this.numberOfPixels = region.numberOfPixels;
    this.selected = region.selected;
    this.measurableLayer = region.measurableLayer;
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
    this.anatomicRegionCodes = anatomicRegions;
  }

  public List<String> getAnatomicRegionCodes() {
    return anatomicRegionCodes;
  }

  public void setCategories(List<String> categories) {
    this.categories = categories;
  }

  public List<String> getCategories() {
    return categories;
  }
}
