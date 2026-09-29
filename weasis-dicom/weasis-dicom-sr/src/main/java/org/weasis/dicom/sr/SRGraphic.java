/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.sr;

import java.awt.Color;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.explorer.pr.PrGraphicUtil;

/**
 * A drawable content item of the report: an SCOORD (PS3.3 C.18.6) or an SCOORD3D (C.18.9) item. The
 * graphic itself is built on demand for a given image, so that the same item can be drawn on
 * several frames or slices and drawn again with a different emphasis.
 *
 * @param nodeId the content item identifier, e.g. "1.4.3"
 * @param item the content item attributes holding Graphic Type and Graphic Data
 * @param threeD true for SCOORD3D, whose points are patient coordinates in a frame of reference
 */
public record SRGraphic(String nodeId, Attributes item, boolean threeD) {
  private static final Logger LOGGER = LoggerFactory.getLogger(SRGraphic.class);

  /** Color of the SR regions drawn over an image. */
  public static final Color COLOR = Color.MAGENTA;

  public static final float LINE_THICKNESS = 1.0f;

  /** Line thickness of the region the user clicked in the report. */
  public static final float HIGHLIGHT_THICKNESS = 3.0f;

  public String getGraphicType() {
    return item.getString(Tag.GraphicType);
  }

  /** The Frame of Reference of an SCOORD3D item, null for an SCOORD item. */
  public String getFrameOfReferenceUID() {
    return threeD ? item.getString(Tag.ReferencedFrameOfReferenceUID) : null;
  }

  /**
   * Builds the graphic to draw over an image.
   *
   * @param image the image, used by SCOORD3D items to project the points on the slice plane
   * @param highlighted true to emphasize the graphic
   * @return the graphic, or null when the item does not apply to this image or cannot be built
   */
  public Graphic build(DicomImageElement image, boolean highlighted) {
    float thickness = highlighted ? HIGHLIGHT_THICKNESS : LINE_THICKNESS;
    if (threeD) {
      return SRScoordProjector.project(
          item, image.getSliceGeometry(), image.getFrameOfReferenceUID(), COLOR, thickness);
    }
    return build2D(item, COLOR, thickness, true);
  }

  static Graphic build2D(Attributes attributes, Color color, float thickness, boolean dcmSR) {
    try {
      Graphic graphic =
          PrGraphicUtil.buildGraphic(attributes, color, false, 1, 1, false, null, dcmSR);
      if (graphic != null) {
        graphic.setLineThickness(thickness);
      }
      return graphic;
    } catch (InvalidShapeException e) {
      LOGGER.error("Cannot build graphic from SR item", e);
    }
    return null;
  }
}
