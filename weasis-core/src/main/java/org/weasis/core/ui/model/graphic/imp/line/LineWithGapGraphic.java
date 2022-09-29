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

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.Optional;
import javax.swing.Icon;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.ui.model.graphic.AbstractDragGraphic;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;
import org.weasis.core.ui.serialize.PointAdapter;
import org.weasis.core.ui.util.MouseEventDouble;

@XmlType(name = "lineWithGap")
@XmlRootElement(name = "lineWithGap")
public class LineWithGapGraphic extends AbstractDragGraphic {

  public static final Integer POINTS_NUMBER = 2;
  public static final Integer DEFAULT_GAP_SIZE = 0;

  protected Integer gapSize = DEFAULT_GAP_SIZE;

  // Let AB be a simple a line segment
  protected Point2D ptA;
  protected Point2D ptB;
  protected Point2D centerGap;

  // estimate if line segment is valid or not
  protected Boolean lineABvalid;

  public LineWithGapGraphic() {
    super(POINTS_NUMBER);
  }

  public LineWithGapGraphic(LineWithGapGraphic graphic) {
    super(graphic);
  }

  @Override
  public LineWithGapGraphic copy() {
    return new LineWithGapGraphic(this);
  }

  @Override
  protected void initCopy(Graphic graphic) {
    super.initCopy(graphic);
    this.gapSize = ((LineWithGapGraphic) graphic).gapSize;
  }

  @Override
  protected void prepareShape() throws InvalidShapeException {
    if (!isShapeValid()) {
      throw new InvalidShapeException("This shape cannot be drawn");
    }
    buildShape(null);
  }

  @Override
  public boolean isShapeValid() {
    updateTool();
    return super.isShapeValid();
  }

  @XmlElement(name = "centerGap")
  @XmlJavaTypeAdapter(PointAdapter.Point2DAdapter.class)
  public Point2D getCenterGap() {
    return centerGap;
  }

  public void setCenterGap(Point2D centerGap) {
    this.centerGap = centerGap;
  }

  @XmlAttribute(name = "gapSize")
  public Integer getGapSize() {
    return gapSize;
  }

  public void setGapSize(Integer gapSize) {
    this.gapSize = Optional.ofNullable(gapSize).orElse(DEFAULT_GAP_SIZE);
  }

  protected void setHandlePointList(Point2D ptStart, Point2D ptEnd) {
    setHandlePoint(0, ptStart == null ? null : (Point2D) ptStart.clone());
    setHandlePoint(1, ptEnd == null ? null : (Point2D) ptEnd.clone());
    buildShape(null);
  }

  @Override
  public Icon getIcon() {
    return LineGraphic.ICON;
  }

  @Override
  public String getUIName() {
    return "";
  }

  @Override
  public void buildShape(MouseEventDouble mouseEvent) {
    updateTool();
    Shape newShape = null;

    if (lineABvalid) {
      if (centerGap == null) {
        centerGap = GeomUtil.getColinearPointWithRatio(ptA, ptB, 0.5);
      }
      double dist = ptA.distance(ptB);
      double distCenterGap = ptA.distance(centerGap);
      double distCenterB = ptB.distance(centerGap);

      Point2D ptap = null;
      Point2D ptbp = null;
      if (distCenterGap < dist && distCenterB < dist) {
        double distGap = 0.5 * gapSize / dist;
        ptap = GeomUtil.getColinearPointWithRatio(ptA, ptB, distCenterGap / dist - distGap);
        ptbp = GeomUtil.getColinearPointWithRatio(ptA, ptB, distCenterGap / dist + distGap);
      }

      Path2D path = new Path2D.Double(Path2D.WIND_NON_ZERO, 4);
      path.moveTo(ptA.getX(), ptA.getY());
      if (ptap != null) {
        path.lineTo(ptap.getX(), ptap.getY());
        path.moveTo(ptbp.getX(), ptbp.getY());
      }
      path.lineTo(ptB.getX(), ptB.getY());

      newShape = path;
    }

    setShape(newShape, mouseEvent);
    updateLabel(mouseEvent, getDefaultView2d(mouseEvent));
  }

  protected void updateTool() {
    ptA = getHandlePoint(0);
    ptB = getHandlePoint(1);

    lineABvalid = ptA != null && ptB != null && !ptB.equals(ptA);
  }

  public Point2D getStartPoint() {
    updateTool();
    return ptA == null ? null : (Point2D) ptA.clone();
  }

  public Point2D getEndPoint() {
    updateTool();
    return ptB == null ? null : (Point2D) ptB.clone();
  }
}
