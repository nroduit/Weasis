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
import org.weasis.core.api.gui.util.DecFormatter;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.util.Copyable;
import org.weasis.core.ui.util.StructToolTipTreeNode;
import org.weasis.core.util.StringUtil;
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

  public void setNumberOfPixels(long numberOfPixels) {
    this.numberOfPixels = numberOfPixels;
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

  /**
   * Builds the HTML description of this region, shared by the region tree tooltips and the hover
   * popup displayed over the image.
   */
  public String getToolTipHtml() {
    StringBuilder buf = new StringBuilder();
    buf.append(GuiUtils.HTML_START).append("<b>").append(getLabel()).append("</b>");
    buf.append(GuiUtils.HTML_BR);
    appendLine(buf, "Algorithm type", getType());
    appendLine(buf, "Algorithm name", algorithmName);
    appendList(buf, "Categories", categories);
    appendList(buf, "Anatomic regions", anatomicRegionCodes);
    appendVoxelCount(buf);
    appendVolume(buf);
    if (isFractional()) {
      appendFractionalLut(buf);
    }
    buf.append(GuiUtils.HTML_END);
    return buf.toString();
  }

  protected static void appendLine(StringBuilder buf, String label, String value) {
    if (StringUtil.hasText(value)) {
      buf.append(label).append(StringUtil.COLON_AND_SPACE).append(value).append(GuiUtils.HTML_BR);
    }
  }

  protected static void appendList(StringBuilder buf, String label, List<String> values) {
    if (values != null && !values.isEmpty()) {
      buf.append(label)
          .append(StringUtil.COLON_AND_SPACE)
          .append(String.join(", ", values))
          .append(GuiUtils.HTML_BR);
    }
  }

  private void appendVoxelCount(StringBuilder buf) {
    buf.append("Voxel count");
    if (isFractional()) {
      buf.append(" (weighted");
      if (StringUtil.hasText(fractionalType)) {
        buf.append(", ").append(fractionalType.toLowerCase());
      }
      buf.append(")");
    }
    buf.append(StringUtil.COLON_AND_SPACE)
        .append(DecFormatter.allNumber(getNumberOfPixels()))
        .append(GuiUtils.HTML_BR);
  }

  private void appendVolume(StringBuilder buf) {
    if (measurableLayer == null) {
      return;
    }
    MeasurementsAdapter adapter =
        measurableLayer.getMeasurementAdapter(
            measurableLayer.getSourceImage().getPixelSpacingUnit());
    double ratio = adapter.calibrationRatio();
    buf.append("Volume (%s3)".formatted(adapter.unit()))
        .append(StringUtil.COLON_AND_SPACE)
        .append(
            DecFormatter.twoDecimal(
                getNumberOfPixels() * ratio * ratio * measurableLayer.getThickness()))
        .append(GuiUtils.HTML_BR);
  }

  private void appendFractionalLut(StringBuilder buf) {
    boolean occupancy = "OCCUPANCY".equalsIgnoreCase(fractionalType);
    String unit = occupancy ? " %" : "";
    String minLabel = "0" + unit;
    String maxLabel = (occupancy ? "100" : "1") + unit;
    buf.append("LUT")
        .append(StringUtil.COLON_AND_SPACE)
        .append(GuiUtils.HTML_BR)
        .append(StructToolTipTreeNode.buildHorizontalLutBar(getColor(), 24, minLabel, maxLabel));
  }
}
