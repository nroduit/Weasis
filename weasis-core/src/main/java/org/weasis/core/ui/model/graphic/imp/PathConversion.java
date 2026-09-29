/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic.imp;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.List;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.AbstractGraphicModel;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.SpatialAnchor;
import org.weasis.core.ui.model.graphic.imp.area.PolygonGraphic;
import org.weasis.core.ui.model.graphic.imp.line.PolylineGraphic;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;

/** Turns an open path into a closed one and back, keeping identity, style and position. */
public final class PathConversion {

  private PathConversion() {}

  public static PolygonGraphic close(PolylineGraphic polyline) throws InvalidShapeException {
    PolygonGraphic polygon = new PolygonGraphic();
    copy(polyline, polygon);
    return polygon;
  }

  public static PolylineGraphic open(PolygonGraphic polygon) throws InvalidShapeException {
    PolylineGraphic polyline = new PolylineGraphic();
    copy(polygon, polyline);
    return polyline;
  }

  private static void copy(Graphic from, Graphic to) throws InvalidShapeException {
    to.setUuid(from.getUuid());
    to.setLayerType(from.getLayerType());
    if (from.getColorPaint() instanceof Color color) {
      to.setPaint(color);
    }
    to.setLineThickness(from.getLineThickness());
    to.setFilled(from.getFilled());
    to.setFillOpacity(from.getFillOpacity());
    to.setLabelVisible(from.getLabelVisible());
    SpatialAnchor anchor = from.getAnchor();
    to.setAnchor(anchor == null ? null : anchor.copy());
    List<Point2D> pts =
        from.getPts().stream().map(p -> (Point2D) new Point2D.Double(p.getX(), p.getY())).toList();
    to.buildGraphic(pts);
  }

  /** Replaces a graphic of the view by its converted form and selects the result. */
  public static void replace(ViewCanvas<?> view, Graphic old, Graphic replacement) {
    old.fireRemoveAction();
    AbstractGraphicModel.addGraphicToModel(view, replacement);
    view.getGraphicManager().setSelectedGraphic(List.of(replacement));
    view.getJComponent().repaint();
  }
}
