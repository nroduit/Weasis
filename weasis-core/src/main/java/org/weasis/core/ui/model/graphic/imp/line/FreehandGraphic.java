/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic.imp.line;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.List;
import javax.swing.Icon;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.editor.image.EdgeSnapper;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.graphic.imp.PathConversion;
import org.weasis.core.ui.model.utils.Draggable;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;
import org.weasis.core.ui.util.MouseEventDouble;

/**
 * A path traced in one drag. Released on its start point it closes into a polygon, elsewhere it
 * stays an open line; the trace is simplified on release so the handles stay editable. With Shift
 * held the trace is magnetic: between anchors dropped as the cursor advances, it follows the
 * strongest edge of the image instead of the cursor.
 */
@XmlType(name = "freehand")
@XmlRootElement(name = "freehand")
@XmlAccessorType(XmlAccessType.NONE)
public class FreehandGraphic extends PolylineGraphic {

  public static final Icon ICON = ResourceUtil.getIcon(ActionIcon.DRAW_FREEHAND);

  /** Image pixels: closer sample points are merged on release. */
  public static final double SIMPLIFY_TOLERANCE = 1.0;

  /** Handle sizes around the start point within which a release closes the trace. */
  static final double CLOSE_FACTOR = 2.0;

  /** Screen pixels the cursor travels from the last anchor before the magnetic path is frozen. */
  static final double ANCHOR_STEP = 24.0;

  public FreehandGraphic() {
    super();
  }

  public FreehandGraphic(FreehandGraphic graphic) {
    super(graphic);
  }

  @Override
  public FreehandGraphic copy() {
    return new FreehandGraphic(this);
  }

  @Override
  public Icon getIcon() {
    return ICON;
  }

  @Override
  public String getUIName() {
    return Messages.getString("measure.freehand");
  }

  @Override
  public Draggable createResizeDrag(Integer i) {
    return isGraphicComplete() ? super.createResizeDrag(i) : new TraceSequence();
  }

  @Override
  protected boolean isClosingPreview(MouseEventDouble mouseEvent) {
    return !isGraphicComplete() && pts.size() >= 3 && isNearFirstPoint(mouseEvent, CLOSE_FACTOR);
  }

  @Override
  protected Point2D previewTail() {
    return pts.getLast();
  }

  /** Ends the trace: closed into a polygon on the start point, open otherwise. */
  public boolean finishTrace(MouseEventDouble evt) {
    boolean close = isClosingPreview(evt);
    List<Point2D> simplified = GeomUtil.simplify(pts, SIMPLIFY_TOLERANCE);
    setResizeOrMoving(Boolean.FALSE);
    if (simplified.size() < 2 || (close && simplified.size() < 3)) {
      // Still an incomplete graphic at this point: only the draft removal drops it
      fireRemoveDraftAction();
      return false;
    }
    setPts(simplified);
    setPointNumber(simplified.size());
    buildShape(evt);
    ViewCanvas<?> view = getDefaultView2d(evt);
    if (close && view != null) {
      try {
        PathConversion.replace(view, this, PathConversion.close(this));
        return true;
      } catch (InvalidShapeException e) {
        // Stays an open trace
      }
    }
    return false;
  }

  /**
   * Appends a point per mouse move while tracing, or the edge path from the last anchor when Shift
   * is held, then finishes the trace on release.
   */
  private final class TraceSequence implements Draggable {

    private EdgeSnapper snapper;

    /** Points before this index are final; the ones after are the live magnetic segment. */
    private int frozen;

    @Override
    public void startDrag(MouseEventDouble evt) {
      setResizeOrMoving(Boolean.TRUE);
      if (pts.isEmpty()) {
        pts.add(evt.getImageCoordinates());
      }
      frozen = pts.size();
    }

    @Override
    public void drag(MouseEventDouble evt) {
      if (evt.isShiftDown() && snapper(evt) != null) {
        magnetic(evt);
        return;
      }
      // Makes a live magnetic segment final, e.g. when Shift is released mid-trace. The snapper
      // is re-anchored lazily by magnetic(), not on every free-hand move.
      frozen = pts.size();
      Point2D p = evt.getImageCoordinates();
      Point2D last = pts.isEmpty() ? null : pts.getLast();
      if (last == null || last.distance(p) >= 1.0) {
        pts.add(p);
        frozen = pts.size();
        buildShape(evt);
      }
    }

    private EdgeSnapper snapper(MouseEventDouble evt) {
      if (snapper == null) {
        ViewCanvas<?> view = getDefaultView2d(evt);
        if (view != null) {
          EdgeSnapper candidate = EdgeSnapper.forView(view);
          snapper = candidate.isReady() ? candidate : null;
        }
      }
      return snapper;
    }

    /** Replaces the live segment by the edge path from the last anchor to the cursor. */
    private void magnetic(MouseEventDouble evt) {
      Point2D cursor = evt.getImageCoordinates();
      Point2D anchorPoint = pts.get(frozen - 1);
      if (snapper.getAnchor() == null || !snapper.getAnchor().equals(anchorPoint)) {
        snapper.anchorAt(anchorPoint);
      }
      List<Point2D> live = snapper.pathTo(cursor);
      while (pts.size() > frozen) {
        pts.removeLast();
      }
      pts.addAll(live.subList(1, live.size()));
      double scale = scaleOf(evt);
      if (cursor.distance(anchorPoint) * scale >= ANCHOR_STEP) {
        frozen = pts.size();
      }
      buildShape(evt);
    }

    private double scaleOf(MouseEventDouble evt) {
      AffineTransform transform = getAffineTransform(evt);
      return transform == null ? 1 : GeomUtil.extractScalingFactor(transform);
    }

    @Override
    public Boolean completeDrag(MouseEventDouble evt) {
      finishTrace(evt);
      return Boolean.TRUE;
    }
  }
}
