/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wsi.sr;

import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.SpecialElementOverlay;
import org.weasis.dicom.macro.SOPInstanceReference;
import org.weasis.dicom.sr.SRGraphic;
import org.weasis.dicom.sr.SRImageReference;
import org.weasis.dicom.sr.SRSpecialElement;
import org.weasis.dicom.wsi.model.PyramidLevel;
import org.weasis.dicom.wsi.model.Slide;

/**
 * The regions of a Structured Report drawn over a slide: the SCOORD items referencing an instance
 * of the pyramid, in the pixels of that level, brought to the base level. A VOLUME pixel origin
 * (PS3.3 C.18.6.1.2) is read in the Total Pixel Matrix of the level, a FRAME origin from the tile
 * of the referenced frame.
 */
public final class SlideReportOverlay implements SpecialElementOverlay {

  private final SRSpecialElement report;
  private final Slide slide;

  public SlideReportOverlay(SRSpecialElement report, Slide slide) {
    this.report = report;
    this.slide = slide;
  }

  @Override
  public LayerType getOverlayLayerType() {
    return LayerType.DICOM_SR;
  }

  @Override
  public String getOverlayLayerName() {
    return report.getLabel();
  }

  /** Whether the report references an instance of the slide. */
  @Override
  public boolean appliesTo(DicomImageElement image) {
    for (SRImageReference ref : report.getGraphicReferences()) {
      if (levelOf(slide, ref.getSopInstanceReference()) != null) {
        return true;
      }
    }
    return false;
  }

  @Override
  public List<Graphic> buildOverlayGraphics(DicomImageElement image) {
    return build(report.getGraphicReferences(), slide, report.getHighlightedNodes());
  }

  /**
   * The graphics of the references that name an instance of the slide, in base level pixels, each
   * item once; an item is drawn when its producer asks for it or when it is highlighted.
   */
  public static List<Graphic> build(
      List<SRImageReference> references, Slide slide, Set<String> highlighted) {
    Map<String, Graphic> graphics = new LinkedHashMap<>();
    for (SRImageReference ref : references) {
      SOPInstanceReference sop = ref.getSopInstanceReference();
      PyramidLevel level = levelOf(slide, sop);
      if (level == null) {
        continue;
      }
      AffineTransform toBase =
          AffineTransform.getScaleInstance(level.downsampleX(), level.downsampleY());
      int[] frames = sop.getReferencedFrameNumber();
      Point origin =
          frames == null || frames.length == 0
              ? null
              : level.frameOrigin(sop.getReferencedSOPInstanceUID(), frames[0]).orElse(null);
      for (SRGraphic item : ref.getGraphics()) {
        if (item.threeD() || graphics.containsKey(item.nodeId())) {
          continue;
        }
        boolean emphasized = highlighted.contains(item.nodeId());
        if (!emphasized && !item.isPresentationRequired()) {
          continue;
        }
        Graphic graphic = item.buildOnMatrix(origin, toBase, emphasized);
        if (graphic != null) {
          graphics.put(item.nodeId(), graphic);
        }
      }
    }
    return new ArrayList<>(graphics.values());
  }

  static PyramidLevel levelOf(Slide slide, SOPInstanceReference sop) {
    if (sop == null || sop.getReferencedSOPInstanceUID() == null) {
      return null;
    }
    for (PyramidLevel level : slide.levels()) {
      if (level.instanceUIDs().contains(sop.getReferencedSOPInstanceUID())) {
        return level;
      }
    }
    return null;
  }
}
