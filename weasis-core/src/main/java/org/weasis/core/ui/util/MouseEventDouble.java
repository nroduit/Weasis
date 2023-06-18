/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.util;

import java.awt.Component;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;

public class MouseEventDouble extends MouseEvent {

  final Point2D.Double point2d;

  public MouseEventDouble(MouseEvent e, int x, int y) {
    this(
        (Component) e.getSource(),
        e.getID(),
        e.getWhen(),
        e.getModifiers(),
        x,
        y,
        e.getXOnScreen(),
        e.getYOnScreen(),
        e.getClickCount(),
        e.isPopupTrigger(),
        e.getButton());
  }

  public MouseEventDouble(MouseEvent e) {
    this(
        (Component) e.getSource(),
        e.getID(),
        e.getWhen(),
        e.getModifiers(),
        e.getX(),
        e.getY(),
        e.getXOnScreen(),
        e.getYOnScreen(),
        e.getClickCount(),
        e.isPopupTrigger(),
        e.getButton());
  }

  public MouseEventDouble(
      Component source,
      int id,
      long when,
      int modifiers,
      int x,
      int y,
      int xAbs,
      int yAbs,
      int clickCount,
      boolean popupTrigger,
      int button) {
    super(source, id, when, modifiers, x, y, xAbs, yAbs, clickCount, popupTrigger, button);
    this.point2d = new Point2D.Double(x, y);
  }

  public void setImageCoordinates(Point2D point) {
    point2d.setLocation(point);
  }

  public void setImageCoordinates(double x, double y) {
    point2d.setLocation(x, y);
  }

  public Point2D.Double getImageCoordinates() {
    return (Point2D.Double) point2d.clone();
  }

  public double getImageX() {
    return point2d.getX();
  }

  public double getImageY() {
    return point2d.getY();
  }
}
