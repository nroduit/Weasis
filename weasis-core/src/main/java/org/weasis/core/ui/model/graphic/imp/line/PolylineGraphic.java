/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic.imp.line;

import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.swing.Icon;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.graphic.AbstractDragGraphic;
import org.weasis.core.ui.model.graphic.imp.PathConversion;
import org.weasis.core.ui.model.utils.Draggable;
import org.weasis.core.ui.model.utils.bean.AdvancedShape;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.bean.Measurement;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;
import org.weasis.core.ui.model.utils.imp.DefaultDragSequence;
import org.weasis.core.ui.util.MouseEventDouble;

@XmlType(name = "polyline")
@XmlRootElement(name = "polyline")
public class PolylineGraphic extends AbstractDragGraphic {

  public static final Integer POINTS_NUMBER = UNDEFINED;

  public static final Icon ICON = ResourceUtil.getIcon(ActionIcon.DRAW_POLYLINE);

  public static final Measurement LINE_LENGTH =
      new Measurement("length", Messages.getString("measure.length"), 5, true, true, true);

  protected static final List<Measurement> MEASUREMENT_LIST = new ArrayList<>();

  static {
    MEASUREMENT_LIST.add(LINE_LENGTH);
  }

  public PolylineGraphic() {
    super(POINTS_NUMBER);
  }

  public PolylineGraphic(PolylineGraphic graphic) {
    super(graphic);
  }

  @Override
  public Draggable createResizeDrag(Integer i) {
    return isGraphicComplete() ? super.createResizeDrag(i) : new ClosingDragSequence(this, i);
  }

  /** True when the cursor is on the first handle, at the handle size of the view. */
  public boolean isOnFirstPoint(MouseEventDouble mouseEvent) {
    return isNearFirstPoint(mouseEvent, 1.0);
  }

  /** True when the cursor is within {@code factor} handle sizes of the first point. */
  public boolean isNearFirstPoint(MouseEventDouble mouseEvent, double factor) {
    if (mouseEvent == null || pts.isEmpty() || pts.getFirst() == null) {
      return false;
    }
    AffineTransform transform = getAffineTransform(mouseEvent);
    double scale = transform == null ? 1 : GeomUtil.extractScalingFactor(transform);
    return pts.getFirst().distance(mouseEvent.getImageCoordinates())
        <= HANDLE_SIZE * 1.5 * factor / scale;
  }

  /** True while drawing when releasing or clicking now would close the path. */
  protected boolean isClosingPreview(MouseEventDouble mouseEvent) {
    return !isGraphicComplete() && pts.size() >= 4 && isOnFirstPoint(mouseEvent);
  }

  /** Last point of the path proper, before the one following the cursor. */
  protected Point2D previewTail() {
    return pts.get(pts.size() - 2);
  }

  /** The open path, plus a dashed closing segment and a ring on the first point when closing. */
  protected Shape buildPath(MouseEventDouble mouseEvent) {
    Optional<Point2D> first = pts.stream().findFirst();
    if (first.isEmpty() || first.get() == null) {
      return null;
    }
    Path2D path = new Path2D.Double(Path2D.WIND_NON_ZERO, pts.size());
    path.moveTo(first.get().getX(), first.get().getY());
    for (Point2D pt : pts) {
      if (pt == null) {
        break;
      }
      path.lineTo(pt.getX(), pt.getY());
    }
    if (!isClosingPreview(mouseEvent)) {
      return path;
    }
    Point2D start = first.get();
    Point2D tail = previewTail();
    AdvancedShape preview = new AdvancedShape(this, 3);
    preview.addShape(path);
    // The closing segment and the ring are not part of the path yet: they answer "click here and
    // it closes", so they carry the edit hint colour rather than the colour of the graphic
    preview
        .addShape(new Line2D.Double(tail, start), getDashStroke(lineThickness), true)
        .setColorPaint(EDIT_HINT_COLOR);
    double r = HANDLE_SIZE * 1.5;
    preview
        .addScaleInvShape(
            new Ellipse2D.Double(start.getX() - r, start.getY() - r, 2 * r, 2 * r),
            start,
            getStroke(lineThickness),
            false)
        .setColorPaint(EDIT_HINT_COLOR);
    return preview;
  }

  /**
   * Closes the path being drawn into a polygon when the click lands on the first point: the point
   * following the cursor is dropped and the polyline is replaced by a polygon in the view.
   */
  public boolean closeOnFirstPoint(MouseEventDouble mouseEvent) {
    if (pts.size() < 4 || !isOnFirstPoint(mouseEvent)) {
      return false;
    }
    ViewCanvas<?> view = getDefaultView2d(mouseEvent);
    if (view == null) {
      return false;
    }
    pts.removeLast();
    setPointNumber(pts.size());
    setResizeOrMoving(Boolean.FALSE);
    buildShape(mouseEvent);
    try {
      PathConversion.replace(view, this, PathConversion.close(this));
    } catch (InvalidShapeException e) {
      return false;
    }
    mouseEvent.consume();
    return true;
  }

  /** The default drawing sequence, plus closing the path on a click on its first point. */
  private static final class ClosingDragSequence extends DefaultDragSequence {

    private final PolylineGraphic polyline;

    ClosingDragSequence(PolylineGraphic polyline, Integer handlePointIndex) {
      super(polyline, handlePointIndex);
      this.polyline = polyline;
    }

    @Override
    public Boolean completeDrag(MouseEventDouble mouseEvent) {
      if (mouseEvent != null
          && mouseEvent.getClickCount() == 1
          && !polyline.isGraphicComplete()
          && polyline.closeOnFirstPoint(mouseEvent)) {
        return Boolean.TRUE;
      }
      return super.completeDrag(mouseEvent);
    }
  }

  @Override
  public PolylineGraphic copy() {
    return new PolylineGraphic(this);
  }

  @Override
  public Icon getIcon() {
    return ICON;
  }

  @Override
  public String getUIName() {
    return Messages.getString("MeasureToolBar.polyline");
  }

  @Override
  protected void prepareShape() throws InvalidShapeException {
    setPointNumber(pts.size());
    buildShape(null);

    if (!isShapeValid()) {
      int lastPointIndex = pts.size() - 1;
      if (lastPointIndex > 0) {
        Point2D checkPoint = pts.get(lastPointIndex);
        /*
         * Must not have two or several points with the same position at the end of the list (two points is the
         * convention to have an uncompleted shape when drawing)
         */
        for (int i = lastPointIndex - 1; i >= 0; i--) {
          if (checkPoint.equals(pts.get(i))) {
            pts.remove(i);
          } else {
            break;
          }
        }
        setPointNumber(pts.size());
      }
      if (!isShapeValid() || pts.size() < 2) {
        throw new IllegalStateException("This Polyline cannot be drawn");
      }
      buildShape(null);
    }
  }

  @Override
  public void buildShape(MouseEventDouble mouseEvent) {
    setShape(buildPath(mouseEvent), mouseEvent);
    updateLabel(mouseEvent, getDefaultView2d(mouseEvent));
  }

  @Override
  public boolean isShapeValid() {
    if (!isGraphicComplete()) {
      return false;
    }

    int lastPointIndex = pts.size() - 1;

    if (lastPointIndex > 0) {
      Point2D checkPoint = pts.get(lastPointIndex);
      return !Objects.equals(checkPoint, pts.get(--lastPointIndex));
    }
    return true;
  }

  @Override
  public List<MeasureItem> computeMeasurements(
      MeasurableLayer layer, boolean releaseEvent, Unit displayUnit) {

    if (layer != null && layer.hasContent() && isShapeValid()) {
      MeasurementsAdapter adapter = layer.getMeasurementAdapter(displayUnit);

      if (adapter != null) {
        ArrayList<MeasureItem> measVal = new ArrayList<>(5);

        double ratio = adapter.calibrationRatio();
        String unitStr = adapter.unit();
        // Get copy to be sure that point value are not modified any more and filter point equal to
        // null.
        List<Point2D> handlePointListcopy = new ArrayList<>(pts.size());
        for (Point2D handlePt : pts) {
          if (handlePt != null) {
            handlePointListcopy.add((Point2D) handlePt.clone());
          }
        }

        if (LINE_LENGTH.getComputed()) {
          Double val =
              (handlePointListcopy.size() > 1) ? getPerimeter(handlePointListcopy) * ratio : null;
          measVal.add(new MeasureItem(LINE_LENGTH, val, unitStr));
        }
        return measVal;
      }
    }
    return Collections.emptyList();
  }

  @Override
  public List<Measurement> getMeasurementList() {
    return MEASUREMENT_LIST;
  }

  protected Double getPerimeter(List<Point2D> handlePointList) {
    if (handlePointList.size() > 1) {
      double perimeter = 0d;
      Point2D pLast = handlePointList.get(0);
      for (Point2D p2 : handlePointList) {
        perimeter += pLast.distance(p2);
        pLast = p2;
      }
      return perimeter;
    }
    return null;
  }

  @Override
  @Deprecated(since = "4.8.0", forRemoval = true)
  public void forceToAddPoints(Integer fromPtIndex) {
    if (getVariablePointsNumber() && fromPtIndex >= 0 && fromPtIndex < pts.size()) {
      if (fromPtIndex < pts.size() - 1) {
        // Add only one point
        pts.add(fromPtIndex, getHandlePoint(fromPtIndex));
        pointNumber++;
      } else {
        // Continue to draw when it is the last point
        setPointNumber(UNDEFINED);
      }
    }
  }
}
