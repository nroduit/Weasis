/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.Point2D;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.ui.model.layer.LayerAnnotation.Position;
import org.weasis.core.util.LangUtil;
import org.weasis.dicom.codec.geometry.PatientOrientation.Biped;
import org.weasis.dicom.viewer2d.Messages;
import org.weasis.dicom.viewer2d.mpr.MprView.Plane;

public class AxisDirection {
  private final Color color;
  private final String name;
  private final Vector3d axisX;
  private final Vector3d axisY;
  private final Vector3d axisZ;
  private final Color xColor;
  private final Color yColor;
  private final Color zColor;
  private final boolean invertedDirection;

  public AxisDirection(Plane plane) {
    this.color =
        switch (plane) {
          case AXIAL -> Biped.H.getColor();
          case CORONAL -> Biped.A.getColor();
          case SAGITTAL -> Biped.R.getColor();
        };
    this.name =
        switch (plane) {
          case AXIAL -> Messages.getString("axial");
          case CORONAL -> Messages.getString("coronal");
          case SAGITTAL -> Messages.getString("sagittal");
        };

    switch (plane) {
      case AXIAL -> {
        this.invertedDirection = true;
        xColor = Biped.R.getColor();
        yColor = Biped.A.getColor();
        zColor = Biped.H.getColor();
        axisX = new Vector3d(1, 0, 0);
        axisY = new Vector3d(0, 1, 0);
        axisZ = new Vector3d(0, 0, -1);
      }
      case CORONAL -> {
        this.invertedDirection = false;
        xColor = Biped.R.getColor();
        yColor = Biped.H.getColor();
        zColor = Biped.A.getColor();
        axisX = new Vector3d(1, 0, 0);
        axisY = new Vector3d(0, 0, -1);
        axisZ = new Vector3d(0, 1, 0);
      }
      case SAGITTAL -> {
        this.invertedDirection = false;
        xColor = Biped.A.getColor();
        yColor = Biped.H.getColor();
        zColor = Biped.R.getColor();
        axisX = new Vector3d(0, 1, 0);
        axisY = new Vector3d(0, 0, -1);
        axisZ = new Vector3d(1, 0, 0);
      }
      default -> throw new IllegalStateException("Unexpected value: " + plane);
    }
  }

  public Color getDirectionColor(Vector3d direction) {
    Color x = Biped.R.getColor();
    Color y = Biped.A.getColor();
    Color z = Biped.H.getColor();

    Vector3d dir = new Vector3d(direction);
    dir.normalize();
    double weightX = Math.abs(dir.x);
    double weightY = Math.abs(dir.y);
    double weightZ = Math.abs(dir.z);
    int r = (int) (weightX * x.getRed() + weightY * y.getRed() + weightZ * z.getRed());
    int g = (int) (weightX * x.getGreen() + weightY * y.getGreen() + weightZ * z.getGreen());
    int b = (int) (weightX * x.getBlue() + weightY * y.getBlue() + weightZ * z.getBlue());

    r = Math.clamp(r, 0, 255);
    g = Math.clamp(g, 0, 255);
    b = Math.clamp(b, 0, 255);
    float[] hsb = Color.RGBtoHSB(r, g, b, null);
    return Color.getHSBColor(hsb[0], 1.0f, hsb[2]);
  }

  public Color getColor() {
    return color;
  }

  public String getName() {
    return name;
  }

  public Vector3d getAxisX() {
    return axisX;
  }

  public Vector3d getAxisY() {
    return axisY;
  }

  public Vector3d getAxisZ() {
    return axisZ;
  }

  public boolean isInvertedDirection() {
    return invertedDirection;
  }

  public void drawAxes(Graphics2D g2d, MprView mprView) {
    g2d.setStroke(new BasicStroke(2));
    int axisLength = GuiUtils.getScaleLength(30);
    MprAxis axis = mprView.getMprAxis();

    Vector3d arrow1 = getArrowDirection(axis, new Vector3d(axisX), axisLength);
    Vector3d arrow2 = getArrowDirection(axis, new Vector3d(axisY), axisLength);
    Vector3d arrow3 = getArrowDirection(axis, new Vector3d(axisZ), axisLength);

    Point2D pt = mprView.getInfoLayer().getPosition(Position.TopLeft);
    Point offset = computeOrigin(axisLength, new Vector3d[] {arrow1, arrow2, arrow3}, pt);

    g2d.setColor(xColor);
    drawAxisLine(g2d, arrow1, offset);

    g2d.setColor(yColor);
    drawAxisLine(g2d, arrow2, offset);

    g2d.setColor(zColor);
    drawAxisLine(g2d, arrow3, offset);
  }

  /**
   * Computes the screen-space origin point that centres the three arrow tips inside a square of
   * side {@code 2 × axisLength}, optionally offset to the top-left info-layer position.
   *
   * <p>Used by both {@link #drawAxes} (2D MPR) and {@code View3d.drawLpsOrientation} (3D volume).
   *
   * @param axisLength desired length of each arrow in pixels
   * @param arrows the three projected, scaled arrow direction vectors (tips relative to origin)
   * @param topLeft top-left info-layer position, or {@code null}
   * @return the common origin point from which all arrows should be drawn
   */
  public static Point computeOrigin(int axisLength, Vector3d[] arrows, Point2D topLeft) {
    double minX = 0, minY = 0, maxX = 0, maxY = 0;
    for (Vector3d arr : arrows) {
      if (arr.x < minX) minX = arr.x;
      if (arr.y < minY) minY = arr.y;
      if (arr.x > maxX) maxX = arr.x;
      if (arr.y > maxY) maxY = arr.y;
    }
    double dimX = axisLength * 2.0;
    double dimY = axisLength * 2.0;
    if (topLeft != null) {
      dimX += topLeft.getX();
      dimY += topLeft.getY() * 2.0;
    }
    return new Point(
        (int) ((dimX - (maxX - minX)) / 2.0 - minX), (int) ((dimY - (maxY - minY)) / 2.0 - minY));
  }

  private Vector3d getArrowDirection(MprAxis axis, Vector3d direction, int length) {
    Vector3d end = new Vector3d(direction);
    new Matrix4d(axis.getTransformation()).invert().transformDirection(end);
    MprView view = axis.getMprView();
    if (view != null) {
      Integer r = (Integer) view.getActionValue(ActionW.ROTATION.cmd());
      if (r != null && r != 0) {
        Vector3d dir = Plane.AXIAL.getDirection();
        Quaterniond q = new Quaterniond().fromAxisAngleRad(dir, Math.toRadians(r));
        q.transform(end);
      }
      if (LangUtil.nullToFalse((Boolean) view.getActionValue((ActionW.FLIP.cmd())))) {
        end.x = -end.x;
      }
    }
    end.mul(length);
    return end;
  }

  /**
   * Draws a single labelled arrow from {@code offset} (the common origin) to {@code offset +
   * arrow}. Skipped when the resulting segment is shorter than 10 px.
   */
  public static void drawAxisLine(Graphics2D g2d, Vector3d arrow, Point offset) {
    int x1 = offset.x;
    int y1 = offset.y;
    int x2 = offset.x + (int) arrow.x;
    int y2 = offset.y + (int) arrow.y;
    double distance = Math.hypot((double) x2 - x1, (double) y2 - y1);
    if (distance >= 10.0) {
      g2d.drawLine(x1, y1, x2, y2);
      drawArrowHead(g2d, x2, y2, x1, y1);
    }
  }

  private static void drawArrowHead(Graphics2D g2d, int xTip, int yTip, int xBase, int yBase) {
    final int arrowHeadSize = 10;
    double angle = Math.atan2((double) yTip - yBase, (double) xTip - xBase);

    int x1 = xTip - (int) (arrowHeadSize * Math.cos(angle - Math.PI / 6));
    int y1 = yTip - (int) (arrowHeadSize * Math.sin(angle - Math.PI / 6));
    int x2 = xTip - (int) (arrowHeadSize * Math.cos(angle + Math.PI / 6));
    int y2 = yTip - (int) (arrowHeadSize * Math.sin(angle + Math.PI / 6));

    g2d.drawLine(xTip, yTip, x1, y1);
    g2d.drawLine(xTip, yTip, x2, y2);
  }
}
