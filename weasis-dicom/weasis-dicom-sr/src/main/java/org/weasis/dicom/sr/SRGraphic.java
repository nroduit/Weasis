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
import java.awt.Point;
import java.awt.geom.Rectangle2D;
import java.util.Objects;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.util.DicomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.pr.PrGraphicUtil;
import org.weasis.dicom.macro.Code;

/**
 * A drawable content item of the report: an SCOORD (PS3.3 C.18.6) or an SCOORD3D (C.18.9) item. The
 * graphic itself is built on demand for a given image, so that the same item can be drawn on
 * several frames or slices and drawn again with a different emphasis.
 *
 * @param nodeId the content item identifier, e.g. "1.4.3"
 * @param item the content item attributes holding Graphic Type and Graphic Data
 * @param threeD true for SCOORD3D, whose points are patient coordinates in a frame of reference
 * @param intent how the producer expects the item to be presented (CAD reports, CID 6034)
 * @param label text shown next to the item when it is highlighted (tracking identifier, finding or
 *     concept name), may be null
 */
public record SRGraphic(
    String nodeId, Attributes item, boolean threeD, RenderingIntent intent, String label) {
  private static final Logger LOGGER = LoggerFactory.getLogger(SRGraphic.class);

  /** Color of the SR regions drawn over an image. */
  public static final Color COLOR = Color.MAGENTA;

  public static final float LINE_THICKNESS = 1.0f;

  /** Line thickness of the region the user clicked in the report. */
  public static final float HIGHLIGHT_THICKNESS = 3.0f;

  /**
   * Graphic Data relative to the Total Pixel Matrix of a tiled image (Pixel Origin Interpretation).
   */
  static final String VOLUME = "VOLUME"; // NON-NLS

  /**
   * Rendering Intent (111056, DCM) of a CAD finding, CID 6034. It applies to the finding and its
   * descendants; an item without any is presented.
   */
  public enum RenderingIntent {
    PRESENTATION_REQUIRED("111150"),
    PRESENTATION_OPTIONAL("111151"),
    NOT_FOR_PRESENTATION("111152");

    private final String codeValue;

    RenderingIntent(String codeValue) {
      this.codeValue = codeValue;
    }

    /** The intent for a code of CID 6034, or null for any other code. */
    public static RenderingIntent fromCode(Code code) {
      if (code == null || !"DCM".equals(code.getCodingSchemeDesignator())) { // NON-NLS
        return null;
      }
      for (RenderingIntent intent : values()) {
        if (intent.codeValue.equals(code.getCodeValue())) {
          return intent;
        }
      }
      return null;
    }
  }

  public SRGraphic {
    Objects.requireNonNull(item);
    if (intent == null) {
      intent = RenderingIntent.PRESENTATION_REQUIRED;
    }
  }

  public SRGraphic(String nodeId, Attributes item, boolean threeD) {
    this(nodeId, item, threeD, RenderingIntent.PRESENTATION_REQUIRED, null);
  }

  public SRGraphic(String nodeId, Attributes item, boolean threeD, RenderingIntent intent) {
    this(nodeId, item, threeD, intent, null);
  }

  public String getGraphicType() {
    return item.getString(Tag.GraphicType);
  }

  /** The Frame of Reference of an SCOORD3D item, null for an SCOORD item. */
  public String getFrameOfReferenceUID() {
    return threeD ? item.getString(Tag.ReferencedFrameOfReferenceUID) : null;
  }

  /** Whether the producer expects the item to be shown without being asked for. */
  public boolean isPresentationRequired() {
    return intent == RenderingIntent.PRESENTATION_REQUIRED;
  }

  /** True when the 2D coordinates are relative to the Total Pixel Matrix of a tiled image. */
  public boolean isVolumeOrigin() {
    return !threeD && VOLUME.equalsIgnoreCase(item.getString(Tag.PixelOriginInterpretation));
  }

  /**
   * Builds the graphic to draw over an image.
   *
   * @param image the image; a frame of a tiled image for VOLUME coordinates, a slice for SCOORD3D
   * @param highlighted true to emphasize the graphic
   * @return the graphic, or null when the item does not apply to this image or cannot be built
   */
  public Graphic build(DicomImageElement image, boolean highlighted) {
    float thickness = highlighted ? HIGHLIGHT_THICKNESS : LINE_THICKNESS;
    Graphic graphic;
    if (threeD) {
      graphic =
          SRScoordProjector.project(
              item, image.getSliceGeometry(), image.getFrameOfReferenceUID(), COLOR, thickness);
    } else if (isVolumeOrigin()) {
      graphic = buildFromTotalPixelMatrix(image, thickness);
    } else {
      graphic = build2D(item, COLOR, thickness, true);
    }
    if (graphic != null && highlighted && label != null && !label.isBlank()) {
      // The label is measured again with the view's font when the model is attached to a view
      graphic.setLabelVisible(Boolean.TRUE);
      graphic.setLabel(new String[] {label}, null);
    }
    return graphic;
  }

  /**
   * Translates Total Pixel Matrix coordinates into the frame's own pixel coordinates and keeps the
   * graphic only when it overlaps the frame.
   */
  private Graphic buildFromTotalPixelMatrix(DicomImageElement image, float thickness) {
    Point tile = tileOrigin(image);
    if (tile == null) {
      // Not a tiled image after all: the coordinates can only be frame-relative
      return build2D(item, COLOR, thickness, true);
    }
    float[] data = DicomUtils.getFloatArrayFromDicomElement(item, Tag.GraphicData, null);
    if (data == null || data.length < 2) {
      return null;
    }
    float[] shifted = new float[data.length];
    for (int i = 0; i + 1 < data.length; i += 2) {
      shifted[i] = data[i] - tile.x;
      shifted[i + 1] = data[i + 1] - tile.y;
    }
    Attributes local = new Attributes(item);
    local.setFloat(Tag.GraphicData, VR.FL, shifted);
    Graphic graphic = build2D(local, COLOR, thickness, true);
    if (graphic == null) {
      return null;
    }
    Integer columns = TagD.getTagValue(image, Tag.Columns, Integer.class);
    Integer rows = TagD.getTagValue(image, Tag.Rows, Integer.class);
    if (columns != null && rows != null && !overlaps(graphic, columns, rows)) {
      return null;
    }
    return graphic;
  }

  /** Overlap test that also accepts degenerate bounds (a point, a horizontal or vertical line). */
  private static boolean overlaps(Graphic graphic, int columns, int rows) {
    Rectangle2D b = graphic.getShape().getBounds2D();
    return b.getMaxX() >= 0 && b.getMinX() <= columns && b.getMaxY() >= 0 && b.getMinY() <= rows;
  }

  /**
   * The position of a frame in the Total Pixel Matrix of a tiled image, as a 0-based (column, row)
   * pixel offset.
   *
   * <p>The position comes from the Plane Position (Slide) Sequence of the frame's functional group
   * (PS3.3 C.8.12.6.1), or is computed from the frame index for TILED_FULL organizations (PS3.3
   * C.7.6.17.3), where tiles run left to right then top to bottom.
   *
   * @return the offset, or null when the image is not tiled or the position cannot be determined
   */
  static Point tileOrigin(DicomImageElement image) {
    if (image == null || image.getMediaReader() == null) {
      return null;
    }
    Attributes dcm = image.getMediaReader().getDicomObject();
    if (dcm == null || !dcm.containsValue(Tag.TotalPixelMatrixColumns)) {
      return null;
    }
    int frame = image.getKey() instanceof Integer index ? index : 0;
    Attributes group = dcm.getNestedDataset(Tag.PerFrameFunctionalGroupsSequence, frame);
    Attributes position =
        group == null ? null : group.getNestedDataset(Tag.PlanePositionSlideSequence);
    if (position != null && position.containsValue(Tag.ColumnPositionInTotalImagePixelMatrix)) {
      return new Point(
          position.getInt(Tag.ColumnPositionInTotalImagePixelMatrix, 1) - 1,
          position.getInt(Tag.RowPositionInTotalImagePixelMatrix, 1) - 1);
    }
    if ("TILED_FULL".equals(dcm.getString(Tag.DimensionOrganizationType))) { // NON-NLS
      int columns = dcm.getInt(Tag.Columns, 0);
      int rows = dcm.getInt(Tag.Rows, 0);
      int total = dcm.getInt(Tag.TotalPixelMatrixColumns, 0);
      if (columns > 0 && rows > 0 && total > 0) {
        int tilesPerRow = (total + columns - 1) / columns;
        return new Point((frame % tilesPerRow) * columns, (frame / tilesPerRow) * rows);
      }
    }
    LOGGER.debug("Cannot locate frame {} in the Total Pixel Matrix", frame);
    return null;
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
