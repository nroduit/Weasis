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
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import javax.swing.Timer;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.weasis.core.api.gui.model.ViewModel;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.Feature;
import org.weasis.core.api.gui.util.Feature.ComboItemListenerValue;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.model.utils.imp.DefaultViewModel;
import org.weasis.core.util.Pair;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.View2d;
import org.weasis.dicom.viewer2d.mip.MipView;
import org.weasis.dicom.viewer2d.mip.MipView.Type;
import org.weasis.dicom.viewer2d.mpr.MprView.Plane;

public class MprController implements MouseListener, MouseMotionListener, MouseWheelListener {
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
  private final AxesControl axesControl;
  private Timer scrollEndTimer;

  protected MprController() {
    this.axial = new MprAxis(Plane.AXIAL);
    this.coronal = new MprAxis(Plane.CORONAL);
    this.sagittal = new MprAxis(Plane.SAGITTAL);
    this.axesControl = new AxesControl(this);
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
      initScrollEndTimer();
      view.addMouseWheelListener(this);
    } else {
      view.addMouseListener(arcBall);
      view.addMouseMotionListener(arcBall);
    }
  }

  private void initScrollEndTimer() {
    scrollEndTimer =
        new Timer(
            750,
            _ -> {
              adjusting = false;
              if (selectedAxis != null) {
                selectedAxis.updateImage();
              }
              scrollEndTimer.stop();
            });
    scrollEndTimer.setRepeats(false);
  }

  public AxesControl getAxesControl() {
    return axesControl;
  }

  public List<MprView> getMprViews() {
    return List.of(axial.getMprView(), coronal.getMprView(), sagittal.getMprView());
  }

  public Volume<?> getVolume() {
    return volume;
  }

  public void setVolume(Volume<?> volume) {
    this.volume = volume;
  }

  public Quaterniond getRotation(Plane plane) {
    return axesControl.getViewRotation(plane);
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
        updateAllViews();
      }
    };
  }

  public void updateAllViews() {
    if (volume != null) {
      axial.updateImage();
      coronal.updateImage();
      sagittal.updateImage();
    }
  }

  public MprAxis getMprAxis(Plane plane) {
    switch (plane) {
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
    Quaterniond r = new Quaterniond().set(volume.getRotation());
    r.mul(rotation);
    axesControl.setGlobalRotation(r);
  }

  public Vector3d getCenterCoordinate() {
    return getCenterCoordinate(axial);
  }

  public Vector3d getCenterCoordinate(MprAxis axis) {
    if (axis != null && volume != null) {
      return axesControl.getCenterForCanvas(axis.getMprView(), false);
    }
    return null;
  }

  public Vector3d getCrossHairPosition() {
    return getCrossHairPosition(axial);
  }

  public Vector3d getCrossHairPosition(MprAxis axis) {
    Vector3d v = getCenterCoordinate(axis);
    if (v != null) {
      return v.mul(volume.getSliceSize());
    }
    return null;
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

  public Vector3d get3DCoordinates(MouseEvent e, MprView view, Vector3d crossHair) {
    Point2D pt = view.getImageCoordinatesFromMouse(e.getX(), e.getY());
    return new Vector3d(pt.getX(), pt.getY(), crossHair.z);
  }

  @Override
  public void mouseWheelMoved(MouseWheelEvent e) {
    if ((e.getModifiersEx() & MouseWheelEvent.ALT_DOWN_MASK) != 0) {
      int rotation = e.getWheelRotation();
      MprAxis axis = selectedAxis;
      if (axis != null) {
        adjusting = true;
        int oldThickness = axis.getThicknessExtension();
        axis.setThicknessExtension(oldThickness + rotation);

        // restart the timer on each scroll event
        if (scrollEndTimer.isRunning()) {
          scrollEndTimer.restart();
        } else {
          scrollEndTimer.start();
        }

        Pair<MprAxis, MprAxis> pair = getCrossAxis(axis);
        if (pair != null) {
          pair.first().getMprView().repaint();
          pair.second().getMprView().repaint();
        }
      }
      e.consume();
    }
  }

  @Override
  public void mousePressed(MouseEvent e) {
    if (e.getSource() instanceof MprView view
        && !MprView.getViewProperty(view, MprView.HIDE_CROSSLINES)) {
      if (selectedAxis != null) {
        e.consume();
        this.canMoveSelected = true;
        view.setCursor(selectedCursor);
      } else {
        MprAxis axis = view.getMprAxis();
        Vector3d center = getCenterCoordinate(axis);
        if (center == null) {
          return;
        }
        Point2D pt = view.getPlaneCoordinatesFromMouse(e.getX(), e.getY());
        int size = axesControl.getSliceSize();
        if (center.distance(pt.getX(), pt.getY(), center.z) <= 20.0 / size) {
          e.consume();
          adjusting = true;
          updatePosition(view, pt, center);
          view.setCursor(MOVE_CURSOR);
          this.canMove = true;
        }
      }
    }
  }

  @Override
  public void mouseDragged(MouseEvent e) {
    if (e.getSource() instanceof MprView view
        && !MprView.getViewProperty(view, MprView.HIDE_CROSSLINES)) {
      if (canMove) {
        e.consume();
        adjusting = true;
        MprAxis axis = view.getMprAxis();
        Vector3d center = getCenterCoordinate(axis);
        Point2D pt = view.getPlaneCoordinatesFromMouse(e.getX(), e.getY());
        view.setCursor(NO_CURSOR);
        updatePosition(view, pt, center);
      } else if (canMoveSelected) {
        e.consume();
        adjusting = true;
        MprAxis axis = view.getMprAxis();
        MprAxis selAxis = selectedAxis;
        if (selAxis != null) {
          Vector3d center = getCenterCoordinate(axis);
          view.setCursor(selectedCursor);
          boolean vertical = view.isVerticalLine(selAxis);
          Vector3d crossHair = new Vector3d(center).mul(axesControl.getSliceSize());
          List<Point2D> pts = getLinePoints(axis, crossHair, vertical);
          if (pts != null && pts.size() == 2) {
            Line2D line = new Line2D.Double(pts.get(0), pts.get(1));
            addControlPoints(view, line, new Point2D.Double(crossHair.x, crossHair.y));
          }

          Point2D pt = view.getPlaneCoordinatesFromMouse(e.getX(), e.getY());
          if (selectedCursor == ROTATE_CURSOR) {
            Vector3d current = get3DCoordinates(e, view, crossHair);
            updateSelectedRotation(view, current, center);
          } else if (selectedCursor == EXTEND_CURSOR) {
            updateSelectedMIP(view, pt, center);
          } else {
            updateSelectedPosition(view, pt, center);
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
      double axisAngle = -axesControl.getRotationOffset(viewAxis.getPlane());
      Quaterniond rotation = new Quaterniond();
      if (viewAxis.getPlane() == Plane.CORONAL) {
        rotation.rotateY(axisAngle);
        rotation.rotateX(-Math.toRadians(90));
      } else if (viewAxis.getPlane() == Plane.SAGITTAL) {
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
    if (center == null || volume == null) {
      return null;
    }
    Vector3d direction = getPlaneDirection(viewAxis, vertical).mul(volume.getSliceSize() * 0.5);
    Vector3d vStart = new Vector3d(center).add(direction);
    Vector3d vEnd = new Vector3d(center).sub(direction);

    if (vertical && viewAxis.getPlane() == Plane.CORONAL
        || vertical && viewAxis.getPlane() == Plane.AXIAL) {
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
    if (e.getSource() instanceof MprView view
        && !MprView.getViewProperty(view, MprView.HIDE_CROSSLINES)) {
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
    if (e.getSource() instanceof MprView view
        && !MprView.getViewProperty(view, MprView.HIDE_CROSSLINES)) {
      MprAxis axis = view.getMprAxis();
      currentAxis = axis;
      if (axis != null) {
        MprAxis oldSelectedAxis = selectedAxis;
        selectedAxis = null;
        selectedCursor = null;
        Vector3d center = getCrossHairPosition(axis);
        if (center == null) {
          return;
        }
        Vector3d position = get3DCoordinates(e, view, center);
        Cursor cursor = paintCrossline(view, axis, position, center, oldSelectedAxis);
        if (center.distance(position) <= 20) {
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

  public void updateSelectedMIP(MprView view, Point2D pt, Vector3d center) {
    if (view == null || pt == null || center == null) {
      return;
    }
    MprAxis axis = selectedAxis;
    Point2D pt1 = selectedPoint;
    if (axis != null && pt1 != null) {
      int size = axesControl.getSliceSize();
      Line2D line = new Line2D.Double(pt1, new Point2D.Double(center.x * size, center.y * size));
      Point2D point = new Point2D.Double(pt.getX() * size, pt.getY() * size);
      Point2D extPoint = GeomUtil.getPerpendicularPointToLine(line, point);
      axis.setThicknessExtension((int) Math.round(pt.distance(extPoint)));
      axis.updateImage();
    }
  }

  private double calculateRotationAngle(Vector3d current, Point2D center) {
    double deltaX = current.x - center.getX();
    double deltaY = current.y - center.getY();
    return Math.atan2(deltaY, deltaX);
  }

  public void updateSelectedRotation(MprView view, Vector3d current, Vector3d center) {
    if (view == null || current == null || center == null) {
      return;
    }
    MprAxis axis = view.getMprAxis();
    Pair<MprAxis, MprAxis> pair = getCrossAxis(axis);
    if (pair != null) {
      boolean vertical = selectedAxis == pair.second();
      int size = axesControl.getSliceSize();
      Point2D ptCenter = new Point2D.Double(center.x * size, center.y * size);
      Point2D selPt = selectedPoint;
      boolean firstPoint =
          selPt != null
              && (vertical
                  ? selPt.equals(controlPoints.p1Rotate)
                  : selPt.equals(controlPoints.p2Rotate));
      double angle = calculateRotationAngle(current, ptCenter);

      if (vertical) {
        angle -= Math.PI / 2;
      }
      if (firstPoint) {
        angle += Math.PI;
      }
      angle = GeomUtil.normalizeAngle(angle);

      axesControl.rotateAroundAxis(axis.getPlane(), angle);
      pair.first().updateImage();
      pair.second().updateImage();
      center(view);
    }
    view.repaint();
  }

  public void updateSelectedPosition(MprView view, Point2D pt, Vector3d crossHair) {
    if (view == null || pt == null || crossHair == null) {
      return;
    }

    Pair<MprAxis, MprAxis> pair = getCrossAxis(view.getMprAxis());
    MprAxis axis = selectedAxis;
    if (axis != null && pair != null) {
      int size = axesControl.getSliceSize();
      Point2D pta = controlPoints.p1Rotate;
      pta = new Point2D.Double(pta.getX() / size, pta.getY() / size);
      Point2D ptb = controlPoints.p2Rotate;
      ptb = new Point2D.Double(ptb.getX() / size, ptb.getY() / size);
      Point2D extPoint = GeomUtil.getPerpendicularPointToLine(pta, ptb, pt);
      double distance = extPoint.distance(pt);
      double dotProduct =
          (pt.getX() - pta.getX()) * (ptb.getY() - pta.getY())
              - (pt.getY() - pta.getY()) * (ptb.getX() - pta.getX());

      Point2D center = new Point2D.Double(crossHair.x, crossHair.y);
      if (dotProduct < 0) {
        distance = -distance;
      }
      Point2D newCenter = GeomUtil.getPerpendicularPointFromLine(center, pta, center, distance);
      updatePosition(view, newCenter, crossHair);
    }
    view.repaint();
  }

  protected void setNewCenter(MprView view, Vector3d newCenter) {
    if (view == null || newCenter == null) {
      return;
    }
    Vector3d vCenter = view.getVolumeCoordinates(newCenter);
    axesControl.setCenter(vCenter);
  }

  public void updatePosition(MprView view, Point2D pt, Vector3d crossHair) {
    if (view == null || pt == null || crossHair == null) {
      return;
    }
    Pair<MprAxis, MprAxis> pair = getCrossAxis(view.getMprAxis());
    if (pair != null) {
      setNewCenter(view, new Vector3d(pt.getX(), pt.getY(), crossHair.z));
      pair.first().updateImage();
      pair.second().updateImage();
      center(view);
    }
    view.getMprAxis().updateImage();
    view.repaint();
  }

  private void center(MprView view) {
    Pair<MprAxis, MprAxis> pair = getCrossAxis(view.getMprAxis());
    if (pair != null) {
      ImageViewerEventManager<DicomImageElement> eventManager = view.getEventManager();
      int mode = eventManager.getOptions().getIntProperty(View2d.P_CROSSHAIR_MODE, 1);
      recenter(pair.first(), mode);
      recenter(pair.second(), mode);
    }
  }

  protected void recenter(MprAxis axis, int mode) {
    if (axis == null) {
      return;
    }
    MprView view = axis.getMprView();
    if (view == null) {
      return;
    }

    int size = axesControl.getSliceSize();
    Vector3d p = axesControl.getCenterForCanvas(view);
    Point2D pt = new Point2D.Double(p.x * size, p.y * size);
    if (mode == 2 || mode == 1 && view.isAutoCenter(pt)) {
      Rectangle2D dim = view.getViewModel().getModelArea();
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

  public void setArcBall(ArcBallController arcBall) {
    this.arcBall = arcBall;
  }

  public void dispose() {
    axial.dispose();
    coronal.dispose();
    sagittal.dispose();
    if (volume != null && !volume.isSharedVolume()) {
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
    axesControl.reset();
    axial.reset();
    coronal.reset();
    sagittal.reset();
  }

  public double getBestFitViewScale() {
    if (volume == null) {
      return 0.0;
    }

    Vector3d volSize = new Vector3d(volume.getSize()).mul(volume.getVoxelRatio());
    double axialScale = calculateViewScale(volSize.x, volSize.y, axial);
    double coronalScale = calculateViewScale(volSize.x, volSize.z, coronal);
    double sagittalScale = calculateViewScale(volSize.y, volSize.z, sagittal);

    double viewScale = Math.min(axialScale, Math.min(coronalScale, sagittalScale));
    return EventManager.getInstance().getSelectedViewPane().adjustViewScale(viewScale);
  }

  private double calculateViewScale(double width, double height, MprAxis axis) {
    MprView view = axis.getMprView();
    if (view == null) {
      return 0.0;
    }
    ViewModel viewModel = view.getViewModel();
    double viewScale = Math.min(view.getWidth() / width, view.getHeight() / height);
    return DefaultViewModel.cropViewScale(
        viewScale, viewModel.getViewScaleMin(), viewModel.getViewScaleMax());
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
    ImageViewerEventManager<DicomImageElement> eventManager = view.getEventManager();
    int mode = eventManager.getOptions().getIntProperty(View2d.P_CROSSHAIR_MODE, 1);
    centerAll(mode);
  }

  public void centerAll(int mode) {
    recenter(axial, mode);
    recenter(coronal, mode);
    recenter(sagittal, mode);
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
