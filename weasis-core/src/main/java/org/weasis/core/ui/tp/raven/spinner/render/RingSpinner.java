/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.tp.raven.spinner.render;

import com.formdev.flatlaf.util.UIScale;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Point2D;

/**
 * RingSpinner is a SpinnerRender that renders a ring spinner.
 *
 * @author Raven Laing
 * @see <a href="https://github.com/DJ-Raven/spinner-progress">spinner-progress</a>
 */
public class RingSpinner implements SpinnerRender {

  private final int size;

  public RingSpinner(int size) {
    this.size = size;
  }

  @Override
  public boolean isDisplayStringAble() {
    return true;
  }

  @Override
  public boolean isPaintComplete() {
    return true;
  }

  @Override
  public void paintCompleteIndeterminate(
      Graphics2D g2, Component component, Rectangle rec, float last, float f, float p) {
    g2.setColor(component.getBackground());
    g2.fill(createShape(rec, 0, 360));
    Point2D lastPoint = getPoint(last);
    double target = p * 360;
    double targetStart = 360 - lastPoint.getX();
    double targetEnd = 360 - lastPoint.getY() + target;
    Shape shape =
        createShape(rec, lastPoint.getX() + targetStart * f, lastPoint.getY() + targetEnd * f);
    g2.setColor(component.getForeground());
    g2.fill(shape);
  }

  @Override
  public void paintIndeterminate(Graphics2D g2, Component component, Rectangle rec, float f) {
    Point2D p = getPoint(f);
    g2.setColor(component.getBackground());
    g2.fill(createShape(rec, 0, 360));
    Shape shape = createShape(rec, p.getX(), p.getY());
    g2.setColor(component.getForeground());
    g2.fill(shape);
  }

  @Override
  public void paintDeterminate(Graphics2D g2, Component component, Rectangle rec, float p) {
    g2.setColor(component.getBackground());
    g2.fill(createShape(rec, 0, 360));
    g2.setColor(component.getForeground());
    g2.fill(createShape(rec, 0, (p * 360)));
  }

  @Override
  public int getInsets() {
    return UIScale.scale(size + 5);
  }

  private Shape createShape(Rectangle rec, double start, double end) {
    start *= -1;
    end *= -1;
    start += 90;
    end += 90;
    double add = end - start;
    Area area =
        new Area(new Arc2D.Double(rec.x, rec.y, rec.width, rec.height, start, add, Arc2D.PIE));
    float lineWidth = UIScale.scale(size);
    float x = rec.x + lineWidth;
    float y = rec.y + lineWidth;
    float width = rec.width - lineWidth * 2;
    float height = rec.height - lineWidth * 2;
    area.subtract(new Area(new Arc2D.Double(x, y, width, height, 0, 360, Arc2D.PIE)));
    return area;
  }

  private Point2D getPoint(float f) {
    double start;
    double end;
    double a = 50;
    double b = 360 - a;
    if (f > 1f) {
      f = f - 1f;
      float ease = easeInOutQuad(f);
      end = b + (f * a);
      start = a + (ease * b);
    } else {
      float ease = easeInOutQuad(f);
      end = (ease * b);
      start = (f * a);
    }
    return new Point2D.Double(start, end);
  }

  public static float easeInOutQuad(float x) {
    double v = x < 0.5 ? 2 * x * x : 1 - Math.pow(-2 * x + 2, 2) / 2;
    return (float) v;
  }
}
