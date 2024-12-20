/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr;

import java.awt.Cursor;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.Feature;
import org.weasis.core.api.gui.util.Feature.ComboItemListenerValue;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.util.Pair;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;
import org.weasis.dicom.viewer2d.View2d;
import org.weasis.dicom.viewer2d.mip.MipView;
import org.weasis.dicom.viewer2d.mip.MipView.Type;
import org.weasis.dicom.viewer2d.mpr.MprView.SliceOrientation;

public class MprController implements MouseListener, MouseMotionListener {
  public static final Cursor HAND_CURSOR =
      Feature.getSvgCursor("mpr-hand.svg", "mpr-hand", 0.5f, 0.5f); // NON-NLS
  public static final Cursor EXTEND_CURSOR =
      Feature.getSvgCursor("mpr-extend.svg", "mpr-extend", 0.5f, 0.5f); // NON-NLS
  public static final Cursor MOVE_CURSOR =
      Feature.getSvgCursor("mpr-move.svg", "mpr-move", 0.5f, 0.5f); // NON-NLS
  public static final Cursor ROTATE_CURSOR =
      Feature.getSvgCursor("mpr-rotate.svg", "mpr-rotate", 0.5f, 0.5f); // NON-NLS
  public static final ComboItemListenerValue<MipView.Type> MIP_TYPE =
      new ComboItemListenerValue<>("MIP", "mip.type", 0, 0, null);

  private static final BufferedImage cursorImg =
      new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
  public static final Cursor NO_CURSOR =
      Toolkit.getDefaultToolkit().createCustomCursor(cursorImg, new Point(0, 0), "invisibleCursor");

  private static final int PIX_TOLERANCE = 7;
  private Volume<?> volume;
  private final MprAxis axial;
  private final MprAxis coronal;
  private final MprAxis sagittal;

  private ArcBallController arcBall;
  private boolean canMove;
  private boolean canMoveSelected;
  private boolean adjusting;
  private MprAxis currentAxis;
  private MprAxis selectedAxis;
  private Cursor selectedCursor;
  private final ControlPoints controlPoints;
  private Point2D selectedPoint;
  private final ComboItemListener<MipView.Type> mipTypeOption;
  private final Quaterniond rotation;

  protected MprController() {
    this.axial = new MprAxis(SliceOrientation.AXIAL);
    this.coronal = new MprAxis(SliceOrientation.CORONAL);
    this.sagittal = new MprAxis(SliceOrientation.SAGITTAL);
    this.rotation = new Quaterniond();
    this.controlPoints = new ControlPoints();
    this.canMove = false;
    this.canMoveSelected = false;
    this.adjusting = false;
    this.mipTypeOption = newMipTypeOption();
    mipTypeOption.setSelectedItemWithoutTriggerAction(Type.MAX);
    mipTypeOption.enableAction(true);
  }

  public MprAxis getSagittal() {
    return sagittal;
  }

  public MprAxis getCoronal() {
    return coronal;
  }

  public MprAxis getAxial() {
    return axial;
  }

  protected void initListeners(MprView view) {
    if (arcBall == null) {
      view.addMouseListener(this);
      view.addMouseMotionListener(this);
    } else {
      view.addMouseListener(arcBall);
      view.addMouseMotionListener(arcBall);
    }
  }

  public Volume<?> getVolume() {
    return volume;
  }

  public void setVolume(Volume<?> volume) {
    this.volume = volume;
  }

  public Quaterniond getRotation() {
    return rotation;
  }

  public boolean isAdjusting() {
    return adjusting;
  }

  public void setAdjusting(boolean adjusting) {
    this.adjusting = adjusting;
  }

  public ComboItemListener<Type> getMipTypeOption() {
    return mipTypeOption;
  }

  private ComboItemListener<MipView.Type> newMipTypeOption() {

    return new ComboItemListener<>(MIP_TYPE, MipView.Type.values()) {

      @Override
      public void itemStateChanged(Object object) {
        if (volume != null) {
          axial.updateImage();
          coronal.updateImage();
          sagittal.updateImage();
        }
      }
    };
  }

  public GeometryOfSlice getGeometryOfSlice(MprView view) {
    if (view != null) {
      DicomImageElement image = view.getImage();
      if (image != null) {
        return image.getSliceGeometry();
      }
    }
    return null;
  }

  public MprAxis getMprAxis(SliceOrientation orientation) {
    switch (orientation) {
      case AXIAL -> {
        return axial;
      }
      case CORONAL -> {
        return coronal;
      }
      case SAGITTAL -> {
        return sagittal;
      }
    }
    return null;
  }

  public void initRotation(Quaterniond rotation) {
    if (volume == null) {
      return;
    }
    this.rotation.set(volume.getRotation());
    this.rotation.mul(rotation);
  }

  public Vector3d getVolumePositionZ(MprAxis axis, Point2D pt) {
    Pair<MprAxis, MprAxis> pair = getCrossAxis(axis);
    if (pair != null && volume != null) {
      Point2D p = new Point2D.Double(pt.getX(), pt.getY());
      rectifyPosition(axis, p);
      int sliceSize = volume.getSliceSize();
      double z = axis.getPositionAlongAxis() + sliceSize / 2.0;
      return new Vector3d(p.getX(), p.getY(), z);
    }
    return null;
  }

  public Vector3d getVolumeCrossHair() {
    return getVolumeCrossHair(axial);
  }

  public Vector3d getVolumeCrossHair(MprAxis axis) {
    Pair<MprAxis, MprAxis> pair = getCrossAxis(axis);
    if (pair != null && volume != null) {
      int sliceSize = volume.getSliceSize();
      double x = pair.second().getPositionAlongAxis() + sliceSize / 2.0;
      double y = pair.first().getPositionAlongAxis() + sliceSize / 2.0;
      double z = axis.getPositionAlongAxis() + sliceSize / 2.0;
      return new Vector3d(x, y, z);
    }
    return null;
  }

  public Vector3d getDicomPositionCrossHair(MprAxis axis, GeometryOfSlice sliceGeometry) {
    Pair<MprAxis, MprAxis> pair = getCrossAxis(axis);
    if (pair != null && volume != null) {
      int sliceSize = volume.getSliceSize();
      double x = pair.second().getPositionAlongAxis() + sliceSize / 2.0;
      double y = pair.first().getPositionAlongAxis() + sliceSize / 2.0;
      rectifyPosition(axis, new Point2D.Double(x, y));
      return sliceGeometry.getPosition(new Point2D.Double(x, y));
    }
    return null;
  }

  public void rectifyPosition(MprAxis axis, Point2D pt) {
    Pair<MprAxis, MprAxis> pair = getCrossAxis(axis);
    if (pair != null && volume != null) {
      int sliceSize = volume.getSliceSize();
      double x = pt.getX();
      double y = pt.getY();
      if (pair.first() == coronal) { // FIXME
        pt.setLocation(x, sliceSize - y);
      }
      if (pair.second() == coronal) {
        pt.setLocation(sliceSize - x, y);
      }
    }
  }

  protected Pair<MprAxis, MprAxis> getCrossAxis(MprAxis axis) {
    if (axis == axial) {
      return new Pair<>(coronal, sagittal);
    } else if (axis == coronal) {
      return new Pair<>(axial, sagittal);
    } else if (axis == sagittal) {
      return new Pair<>(axial, coronal);
    }
    return null;
  }

  @Override
  public void mousePressed(MouseEvent e) {
    if (e.getSource() instanceof MprView view) {
      Point2D pt = view.getImageCoordinatesFromMouse(e.getX(), e.getY());
      Vector3d current = getVolumePositionZ(view.getMprAxis(), pt);
      MprAxis axis = view.getMprAxis();
      if (selectedAxis != null) {
        e.consume();
        this.canMoveSelected = true;
        view.setCursor(selectedCursor);
      } else {
        Vector3d crossHair = getVolumeCrossHair(axis);
        if (crossHair != null && crossHair.distance(current) <= 20) {
          e.consume();
          updatePosition(view, current);
          view.setCursor(MOVE_CURSOR);
          this.canMove = true;
        }
      }
    }
  }

  @Override
  public void mouseDragged(MouseEvent e) {
    if (e.getSource() instanceof MprView view) {
      if (canMove) {
        e.consume();
        adjusting = true;
        Point p = e.getPoint();
        Point2D pt = view.getImageCoordinatesFromMouse(p.x, p.y);
        Vector3d current = getVolumePositionZ(view.getMprAxis(), pt);
        view.setCursor(NO_CURSOR);
        updatePosition(view, current);
      } else if (canMoveSelected) {
        e.consume();
        adjusting = true;
        MprAxis axis = view.getMprAxis();
        MprAxis selAxis = selectedAxis;
        if (selAxis != null) {
          Point p = e.getPoint();
          Point2D pt = view.getImageCoordinatesFromMouse(p.x, p.y);
          Vector3d current = getVolumePositionZ(axis, pt);
          Vector3d crossHair = getVolumeCrossHair(axis);
          view.setCursor(selectedCursor);
          boolean vertical = view.isVerticalLine(selAxis);
          List<Point2D> pts = getLinePoints(axis, crossHair, vertical);
          if (pts != null && pts.size() == 2) {
            Line2D line = new Line2D.Double(pts.get(0), pts.get(1));
            addControlPoints(view, line, new Point2D.Double(crossHair.x, crossHair.y));
          }

          if (selectedCursor == ROTATE_CURSOR) {
            updateSelectedRotation(view, pt, crossHair);
          } else if (selectedCursor == EXTEND_CURSOR) {
            updateSelectedMIP(view, current, crossHair);
          } else {
            updateSelectedPosition(view, current, crossHair);
          }
        }
      }
    }
  }

  protected Vector3d getPlaneDirection(MprAxis viewAxis, boolean vertical) {
    AxisDirection dir = viewAxis.getAxisDirection();
    Vector3d direction = new Vector3d(vertical ? dir.getAxisY() : dir.getAxisX());

    Pair<MprAxis, MprAxis> pair = getCrossAxis(viewAxis);
    if (pair != null) {
      double axisAngle = viewAxis.getAxisAngle();
      Quaterniond rotation = new Quaterniond();
      if (viewAxis.getViewOrientation() == SliceOrientation.CORONAL) {
        rotation.rotateY(axisAngle);
        rotation.rotateX(-Math.toRadians(90));
      } else if (viewAxis.getViewOrientation() == SliceOrientation.SAGITTAL) {
        rotation.rotateX(axisAngle);
        rotation.rotateY(Math.toRadians(90)).rotateZ(Math.toRadians(90));
      } else {
        rotation.rotateZ(axisAngle);
      }
      rotation.invert().transform(direction);
    }
    return direction;
  }

  protected List<Point2D> getLinePoints(MprAxis viewAxis, Vector3d center, boolean vertical) {
    if (center == null) {
      return null;
    }
    Vector3d direction = getPlaneDirection(viewAxis, vertical).mul(volume.getSliceSize() * 0.5);
    Vector3d vStart = new Vector3d(center).add(direction);
    Vector3d vEnd = new Vector3d(center).sub(direction);

    if (vertical && viewAxis.getViewOrientation() == SliceOrientation.CORONAL
        || !vertical && viewAxis.getViewOrientation() == SliceOrientation.SAGITTAL) {
      Vector3d tmp = vStart;
      vStart = vEnd;
      vEnd = tmp;
    }
    return List.of(new Point2D.Double(vStart.x, vStart.y), new Point2D.Double(vEnd.x, vEnd.y));
  }

  protected void updateAdjusting() {
    if (adjusting) {
      adjusting = false;
      if (axial.getThicknessExtension() > 0) {
        axial.updateImage();
      }
      if (coronal.getThicknessExtension() > 0) {
        coronal.updateImage();
      }
      if (sagittal.getThicknessExtension() > 0) {
        sagittal.updateImage();
      }
    }
  }

  @Override
  public void mouseReleased(MouseEvent e) {
    if (e.getSource() instanceof MprView view) {
      if (canMove) {
        this.canMove = false;
        e.consume();
        view.setCursor(MOVE_CURSOR);
        updateAdjusting();
        centerAll(view);
      } else if (canMoveSelected) {
        this.canMoveSelected = false;
        e.consume();
        view.setCursor(HAND_CURSOR);
        updateAdjusting();
        centerAll(view);

        selectedAxis = null;
        selectedCursor = null;
        controlPoints.clear();
      }
    }
  }

  @Override
  public void mouseMoved(MouseEvent e) {
    if (e.getSource() instanceof MprView view) {
      MprAxis axis = view.getMprAxis();
      currentAxis = axis;
      if (axis != null) {
        MprAxis oldSelectedAxis = selectedAxis;
        selectedAxis = null;
        selectedCursor = null;
        Point2D pt = view.getImageCoordinatesFromMouse(e.getX(), e.getY());

        Vector3d position = getVolumePositionZ(axis, pt);
        Vector3d crossHair = getVolumeCrossHair(axis);
        Cursor cursor = paintCrossline(view, axis, position, crossHair, oldSelectedAxis);

        if (crossHair != null && crossHair.distance(position) <= 20) {
          cursor = MOVE_CURSOR;
          selectedAxis = null;
          selectedCursor = null;
          controlPoints.clear();
          selectedPoint = null;
        }
        view.setCursor(cursor);
        if (cursor != DefaultView2d.DEFAULT_CURSOR) {
          e.consume();
        }

        if (oldSelectedAxis != selectedAxis) {
          view.repaint();
        }
      }
    }
  }

  private Cursor paintCrossline(
      MprView view, MprAxis axis, Vector3d position, Vector3d crossHair, MprAxis oldSelectedAxis) {
    Cursor cursor = DefaultView2d.DEFAULT_CURSOR;
    Pair<MprAxis, MprAxis> pair = getCrossAxis(axis);
    if (pair != null) {
      cursor =
          processImageElement(
              view, position, crossHair, pair.first(), false, oldSelectedAxis, cursor);
      cursor =
          processImageElement(
              view, position, crossHair, pair.second(), true, oldSelectedAxis, cursor);
    }
    return cursor;
  }

  private Cursor processImageElement(
      MprView view,
      Vector3d position,
      Vector3d crossHair,
      MprAxis currentAxis,
      boolean vertical,
      MprAxis oldSelectedAxis,
      Cursor cursor) {
    List<Point2D> pts = getLinePoints(view.getMprAxis(), crossHair, vertical);
    if (pts != null && pts.size() == 2) {
      if (processLine(view, pts, position, crossHair, currentAxis, oldSelectedAxis)) {
        this.selectedPoint = controlPoints.getSelectedPoint(view, position);
        cursor = controlPoints.getCursor(selectedPoint);
        selectedCursor = cursor;
      }
    }
    return cursor;
  }

  private boolean processLine(
      MprView view,
      List<Point2D> pts,
      Vector3d position,
      Vector3d crossHair,
      MprAxis currentAxis,
      MprAxis oldSelectedAxis) {
    Line2D line = new Line2D.Double(pts.get(0), pts.get(1));
    boolean intersects = intersectsLine(line, position);
    if (intersects) {
      selectedAxis = currentAxis;
      if (oldSelectedAxis != currentAxis) {
        addControlPoints(view, line, new Point2D.Double(crossHair.x, crossHair.y));
        view.repaint();
      }
    }
    return intersects;
  }

  public void updateSelectedMIP(MprView view, Vector3d current, Vector3d crossHair) {
    if (view == null || current == null || crossHair == null) {
      return;
    }
    MprAxis axis = selectedAxis;
    Point2D pt1 = selectedPoint;
    if (axis != null && pt1 != null) {
      Line2D line = new Line2D.Double(pt1, new Point2D.Double(crossHair.x, crossHair.y));
      Point2D pt = new Point2D.Double(current.x, current.y);
      Point2D extPoint = GeomUtil.getPerpendicularPointToLine(line, pt);
      axis.setThicknessExtension((int) Math.round(pt.distance(extPoint)));
      axis.updateImage();
    }
  }

  private double calculateRotationAngle(Point2D current, Point2D center) {
    double deltaX = current.getX() - center.getX();
    double deltaY = current.getY() - center.getY();
    return Math.atan2(deltaY, deltaX);
  }

  public void updateSelectedRotation(MprView view, Point2D originalPos, Vector3d crossHair) {
    if (view == null || originalPos == null || crossHair == null) {
      return;
    }
    MprAxis axis = view.getMprAxis();
    Pair<MprAxis, MprAxis> pair = getCrossAxis(axis);
    if (pair != null) {
      boolean isCoronal = view.getSliceOrientation() == SliceOrientation.CORONAL;
      boolean vertical = selectedAxis == pair.second();
      Point2D center = new Point2D.Double(crossHair.x, crossHair.y);
      rectifyPosition(axis, center);
      Point2D selPt = selectedPoint;
      boolean firstPoint =
          selPt != null
              && (vertical
                  ? selPt.equals(controlPoints.p1Rotate)
                  : selPt.equals(controlPoints.p2Rotate));
      double angle = calculateRotationAngle(originalPos, center);

      if (vertical) {
        angle -= Math.PI / 2;
      }
      if (firstPoint) {
        angle += Math.PI;
      }
      angle = GeomUtil.normalizeAngle(angle);
      if (isCoronal) {
        angle = -angle;
      }

      axis.setAxisAngle(angle);
      axisRotation(angle, view.getSliceOrientation());
      pair.first().updateImage();
      pair.second().updateImage();
      center(view, crossHair);
    }
    view.repaint();
  }

  private void axisRotation(double angle, SliceOrientation orientation) {
    Quaterniond newRotation = new Quaterniond();
    switch (orientation) {
      case AXIAL -> newRotation.rotateZ(angle);
      case CORONAL -> newRotation.rotateY(angle);
      case SAGITTAL -> newRotation.rotateX(angle);
    }

    Quaterniond relativeRotation = new Quaterniond(rotation).invert().mul(newRotation);
    switch (orientation) {
      case AXIAL -> {
        relativeRotation.x = 0;
        relativeRotation.y = 0;
        relativeRotation.normalize();
      }
      case CORONAL -> {
        relativeRotation.x = 0;
        relativeRotation.z = 0;
        relativeRotation.normalize();
      }
      case SAGITTAL -> {
        relativeRotation.y = 0;
        relativeRotation.z = 0;
        relativeRotation.normalize();
      }
    }
    rotation.mul(relativeRotation);
  }

  public void updateSelectedPosition(MprView view, Vector3d current, Vector3d crossHair) {
    if (view == null || current == null || crossHair == null) {
      return;
    }

    Pair<MprAxis, MprAxis> pair = getCrossAxis(view.getMprAxis());
    MprAxis axis = selectedAxis;
    if (axis != null && pair != null) {
      boolean isFirst = axis == pair.first();
      int sliceSize = volume.getSliceSize();
      Point2D pta = controlPoints.p1Rotate;
      Point2D ptb = controlPoints.p2Rotate;
      Point2D ptc = new Point2D.Double(current.x, current.y);
      Point2D extPoint = GeomUtil.getPerpendicularPointToLine(pta, ptb, ptc);
      double distance = extPoint.distance(current.x, current.y);
      double dotProduct =
          (ptc.getX() - pta.getX()) * (ptb.getY() - pta.getY())
              - (ptc.getY() - pta.getY()) * (ptb.getX() - pta.getX());

      Point2D center = new Point2D.Double(crossHair.x, crossHair.y);
      if (dotProduct < 0) {
        distance = -distance;
      }
      Point2D newCenter = GeomUtil.getPerpendicularPointFromLine(center, pta, center, distance);
      double offset = isFirst ? newCenter.getY() : newCenter.getX();
      axis.setPositionAlongAxis(offset - sliceSize / 2.0);
      if (isFirst) {
        pair.second().setPositionAlongAxis(newCenter.getX() - sliceSize / 2.0);
      } else {
        pair.first().setPositionAlongAxis(newCenter.getY() - sliceSize / 2.0);
      }
      axis.updateImage();
      center(view, getVolumeCrossHair(view.getMprAxis()));
    }
    view.repaint();
  }

  public void updatePosition(MprView view, Vector3d crossHair) {
    if (view == null) {
      return;
    }

    if (crossHair == null) {
      crossHair = getVolumeCrossHair(view.getMprAxis());
    }
    if (crossHair == null) {
      return;
    }

    Pair<MprAxis, MprAxis> pair = getCrossAxis(view.getMprAxis());
    if (pair != null) {
      int sliceSize = volume.getSliceSize();
      pair.first().setPositionAlongAxis(crossHair.y - sliceSize / 2.0);
      pair.second().setPositionAlongAxis(crossHair.x - sliceSize / 2.0);
      pair.first().updateImage();
      pair.second().updateImage();

      center(view, getVolumeCrossHair(view.getMprAxis()));
    }
    view.repaint();
  }

  private void center(MprView view, Vector3d crossHair) {
    Pair<MprAxis, MprAxis> pair = getCrossAxis(view.getMprAxis());
    if (pair != null) {
      ImageViewerEventManager<DicomImageElement> eventManager = view.getEventManager();
      int centerGap = eventManager.getOptions().getIntProperty(View2d.P_CROSSHAIR_CENTER_GAP, 40);
      int mode = eventManager.getOptions().getIntProperty(View2d.P_CROSSHAIR_MODE, 1);
      recenter(pair.first(), crossHair, mode, centerGap);
      recenter(pair.second(), crossHair, mode, centerGap);
    }
  }

  protected void recenter(MprAxis axis, Vector3d current, int mode, int centerGap) {
    if (axis == null || current == null) {
      return;
    }
    MprView view = axis.getMprView();
    if (view == null) {
      return;
    }

    current = getVolumeCrossHair(axis);
    Point2D pt = new Point2D.Double(current.x, current.y);
    rectifyPosition(axis, pt);

    Rectangle2D dim = view.getViewModel().getModelArea();
    if (mode == 2 || mode == 1 && view.isAutoCenter(pt)) {
      double mx = pt.getX() - dim.getWidth() * 0.5;
      double my = pt.getY() - dim.getHeight() * 0.5;
      view.setCenter(mx, my);
    } else {
      view.repaint();
    }
  }

  @Override
  public void mouseClicked(MouseEvent e) {}

  @Override
  public void mouseEntered(MouseEvent e) {}

  @Override
  public void mouseExited(MouseEvent e) {}

  public MprAxis getSelectedAxis() {
    return selectedAxis;
  }

  public MprAxis getCurrentAxis() {
    return currentAxis;
  }

  public ControlPoints getControlPoints() {
    return controlPoints;
  }

  public boolean isOblique() {
    return volume != null;
  }

  public void setArcBall(ArcBallController arcBall) {
    this.arcBall = arcBall;
  }

  public void dispose() {
    axial.dispose();
    coronal.dispose();
    sagittal.dispose();
    if (volume != null) {
      volume.removeData();
    }
  }

  public void reset() {
    if (volume != null) {
      volume.resetTranslation();
      volume.resetRotation();
    }
    selectedPoint = null;
    canMove = false;
    canMoveSelected = false;
    selectedAxis = null;
    selectedCursor = null;
    axial.reset();
    coronal.reset();
    sagittal.reset();
    rotation.identity();
  }

  public static boolean pointsMatch(Point2D p1, Point2D p2) {
    return p1.distance(p2) <= PIX_TOLERANCE;
  }

  public static boolean intersectsLine(Line2D line, Vector3d p) {
    return line.ptLineDist(p.x, p.y) <= PIX_TOLERANCE;
  }

  public void addControlPoints(MprView view, Line2D line, Point2D center) {
    ControlPoints c = view.getControlPoints(line, center);

    if (selectedPoint != null) {
      if (selectedPoint == controlPoints.p1Rotate) {
        this.selectedPoint = c.p1Rotate;
      } else if (selectedPoint == controlPoints.p2Rotate) {
        this.selectedPoint = c.p2Rotate;
        //      } else if(selectedPoint == controlPoints.p1Extend) {
        //        this.selectedPoint = c.p1Extend;
        //      } else if(selectedPoint == controlPoints.p2Extend) {
        //        this.selectedPoint = c.p2Extend;;
      }
    }

    controlPoints.p1 = c.p1;
    controlPoints.p2 = c.p2;
    controlPoints.p1Rotate = c.p1Rotate;
    controlPoints.p2Rotate = c.p2Rotate;
    //    controlPoints.p1Extend = c.p1Extend;
    //    controlPoints.p2Extend = c.p2Extend;
  }

  public void centerAll(MprView view) {
    Vector3d current = getVolumeCrossHair(view.getMprAxis());
    ImageViewerEventManager<DicomImageElement> eventManager = view.getEventManager();
    int centerGap = eventManager.getOptions().getIntProperty(View2d.P_CROSSHAIR_CENTER_GAP, 40);
    int mode = eventManager.getOptions().getIntProperty(View2d.P_CROSSHAIR_MODE, 1);
    centerAll(current, mode, centerGap);
  }

  public void centerAll(Vector3d current, int mode, int centerGap) {
    recenter(axial, current, mode, centerGap);
    recenter(coronal, current, mode, centerGap);
    recenter(sagittal, current, mode, centerGap);
  }

  public static class ControlPoints {
    Point2D p1;
    Point2D p2;
    Point2D p1Rotate;
    Point2D p2Rotate;
    Point2D p1Extend;
    Point2D p2Extend;

    public void clear() {
      p1 = null;
      p2 = null;
      p1Rotate = null;
      p2Rotate = null;
      p1Extend = null;
      p2Extend = null;
    }

    public List<Point2D> getPointList() {
      return Stream.of(p1Rotate, p2Rotate, p1Extend, p2Extend).filter(Objects::nonNull).toList();
    }

    public Line2D getLine() {
      return new Line2D.Double(p1, p2);
    }

    public Point2D getSelectedPoint(MprView view, Vector3d position) {
      Point2D current = new Point2D.Double(position.x, position.y);
      Point2D selectedPoint = null;
      double smallest = 15;
      for (Point2D p : getPointList()) {
        double dist = view.modelToViewLength(current.distance(p));
        if (dist < smallest) {
          smallest = dist;
          selectedPoint = p;
        }
      }
      return selectedPoint;
    }

    public Cursor getCursor(Point2D selectedPoint) {
      if (selectedPoint == null) {
        return HAND_CURSOR;
      }

      if (selectedPoint == p1Rotate || selectedPoint == p2Rotate) {
        return ROTATE_CURSOR;
      } else {
        return EXTEND_CURSOR;
      }
    }
  }
}
