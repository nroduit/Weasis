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
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.swing.Icon;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.model.graphic.AbstractDragGraphic;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.bean.Measurement;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;
import org.weasis.core.ui.util.MouseEventDouble;
import org.weasis.core.util.MathUtil;

@XmlType(name = "parallelLine")
@XmlRootElement(name = "parallelLine")
public class ParallelLineGraphic extends AbstractDragGraphic {

  public static final Integer POINTS_NUMBER = 6;

  public static final Icon ICON = ResourceUtil.getIcon(ActionIcon.DRAW_PARALLEL);

  public static final Measurement DISTANCE =
      new Measurement(Messages.getString("measure.distance"), 1, true, true, true);
  public static final Measurement ORIENTATION =
      new Measurement(Messages.getString("measure.orientation"), 2, true, true, false);
  public static final Measurement AZIMUTH =
      new Measurement(Messages.getString("measure.azimuth"), 3, true, true, false);

  protected static final List<Measurement> MEASUREMENT_LIST = new ArrayList<>();

  static {
    MEASUREMENT_LIST.add(DISTANCE);
    MEASUREMENT_LIST.add(ORIENTATION);
    MEASUREMENT_LIST.add(AZIMUTH);
  }

  // Let AB & CD two parallel line segments
  protected Point2D ptA;
  protected Point2D ptB;
  protected Point2D ptC;
  protected Point2D ptD;

  // Let E,F middle points of AB & CD
  protected Point2D ptE;
  protected Point2D ptF;

  // estimate if line segments are valid or not
  protected Boolean lineABvalid;
  protected Boolean lineCDvalid;

  public ParallelLineGraphic() {
    super(POINTS_NUMBER);
  }

  public ParallelLineGraphic(ParallelLineGraphic graphic) {
    super(graphic);
  }

  @Override
  public ParallelLineGraphic copy() {
    return new ParallelLineGraphic(this);
  }

  @Override
  public Icon getIcon() {
    return ICON;
  }

  @Override
  public String getUIName() {
    return Messages.getString("measure.parallel");
  }

  @Override
  protected void prepareShape() throws InvalidShapeException {
    if (!isShapeValid()) {
      throw new InvalidShapeException("This shape cannot be drawn");
    }
    buildShape(null);
  }

  @Override
  public Integer moveAndResizeOnDrawing(
      Integer handlePointIndex, Double deltaX, Double deltaY, MouseEventDouble mouseEvent) {

    handlePointIndex = super.moveAndResizeOnDrawing(handlePointIndex, deltaX, deltaY, mouseEvent);

    if (handlePointIndex >= 0 && handlePointIndex < getHandlePointListSize()) {
      updateTool();

      if (lineABvalid && lineCDvalid) {

        if (handlePointIndex == 0 || handlePointIndex == 1) {
          // drag point is A or B

          Point2D anchor = (handlePointIndex == 0) ? ptB : ptA;
          double theta =
              GeomUtil.getSmallestAngleRad(
                  GeomUtil.getAngleRad(ptC, ptD) - GeomUtil.getAngleRad(ptA, ptB));

          // rotation angle around anchor point
          AffineTransform rotate =
              AffineTransform.getRotateInstance(theta, anchor.getX(), anchor.getY());

          rotate.transform(ptC, ptC);
          rotate.transform(ptD, ptD);

          setHandlePoint(2, ptC);
          setHandlePoint(3, ptD);

        } else if (handlePointIndex == 2 || handlePointIndex == 3) {
          // drag point is C or D

          Point2D pt1 = (handlePointIndex == 2) ? ptC : ptD;
          Point2D pt2 = (handlePointIndex == 2) ? ptD : ptC;
          int hIndex = (handlePointIndex == 2) ? 3 : 2;

          Point2D ptI = GeomUtil.getPerpendicularPointToLine(ptA, ptB, pt1);
          Point2D ptJ = GeomUtil.getPerpendicularPointToLine(ptA, ptB, pt2);

          double transX = (pt1.getX() - ptI.getX()) - (pt2.getX() - ptJ.getX());
          double transY = (pt1.getY() - ptI.getY()) - (pt2.getY() - ptJ.getY());

          AffineTransform translate = AffineTransform.getTranslateInstance(transX, transY);
          translate.transform(pt2, pt2);

          setHandlePoint(hIndex, pt2);

        } else if (handlePointIndex == 4 || handlePointIndex == 5) {
          // drag point is E middle of AB or F middle of CD
          Point2D pt0 = (handlePointIndex == 4) ? ptE : ptF;
          Point2D pt1 = (handlePointIndex == 4) ? ptA : ptC;
          Point2D pt2 = (handlePointIndex == 4) ? ptB : ptD;
          int hIndex1 = (handlePointIndex == 4) ? 0 : 2;
          int hIndex2 = (handlePointIndex == 4) ? 1 : 3;

          if (Objects.nonNull(pt0)) {
            Point2D ptI = GeomUtil.getPerpendicularPointToLine(pt1, pt2, pt0);

            AffineTransform translate =
                AffineTransform.getTranslateInstance(
                    pt0.getX() - ptI.getX(), pt0.getY() - ptI.getY());
            translate.transform(pt1, pt1);
            translate.transform(pt2, pt2);

            setHandlePoint(hIndex1, pt1);
            setHandlePoint(hIndex2, pt2);
          }
        }

        setHandlePoint(4, GeomUtil.getMidPoint(ptA, ptB));
        setHandlePoint(5, GeomUtil.getMidPoint(ptC, ptD));
      }
    }
    return handlePointIndex;
  }

  @Override
  public void buildShape(MouseEventDouble mouseEvent) {

    updateTool();

    Shape newShape = null;
    Path2D path = new Path2D.Double(Path2D.WIND_NON_ZERO, 2);

    if (lineABvalid) {
      path.append(new Line2D.Double(ptA, ptB), false);
    }

    if (lineCDvalid) {
      path.append(new Line2D.Double(ptC, ptD), false);
    }

    if (path.getCurrentPoint() != null) {
      newShape = path;
    }

    setShape(newShape, mouseEvent);
    updateLabel(mouseEvent, getDefaultView2d(mouseEvent));
  }

  @Override
  public List<MeasureItem> computeMeasurements(
      MeasurableLayer layer, boolean releaseEvent, Unit displayUnit) {

    if (layer != null && layer.hasContent() && isShapeValid()) {
      MeasurementsAdapter adapter = layer.getMeasurementAdapter(displayUnit);

      if (adapter != null) {
        ArrayList<MeasureItem> measVal = new ArrayList<>(3);

        if (DISTANCE.getComputed()) {
          Double val =
              ptC.distance(GeomUtil.getPerpendicularPointToLine(ptA, ptB, ptC))
                  * adapter.getCalibRatio();
          measVal.add(new MeasureItem(DISTANCE, val, adapter.getUnit()));
        }
        if (ORIENTATION.getComputed()) {
          measVal.add(
              new MeasureItem(
                  ORIENTATION,
                  MathUtil.getOrientation(ptA, ptB),
                  Messages.getString("measure.deg")));
        }
        if (AZIMUTH.getComputed()) {
          measVal.add(
              new MeasureItem(
                  AZIMUTH, MathUtil.getAzimuth(ptA, ptB), Messages.getString("measure.deg")));
        }
        return measVal;
      }
    }
    return Collections.emptyList();
  }

  @Override
  public boolean isShapeValid() {
    updateTool();
    return lineABvalid && lineCDvalid;
  }

  // /////////////////////////////////////////////////////////////////////////////////////////////////////

  protected void updateTool() {
    ptA = getHandlePoint(0);
    ptB = getHandlePoint(1);
    ptC = getHandlePoint(2);
    ptD = getHandlePoint(3);
    ptE = getHandlePoint(4);
    ptF = getHandlePoint(5);

    lineABvalid = ptA != null && ptB != null && !ptB.equals(ptA);
    lineCDvalid = ptC != null && ptD != null && !ptC.equals(ptD);
  }

  @Override
  public List<Measurement> getMeasurementList() {
    return MEASUREMENT_LIST;
  }
}
