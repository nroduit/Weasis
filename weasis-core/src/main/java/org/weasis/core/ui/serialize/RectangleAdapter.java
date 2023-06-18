/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.serialize;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.adapters.XmlAdapter;
import java.awt.geom.Rectangle2D;
import java.util.Objects;

public class RectangleAdapter {

  private RectangleAdapter() {}

  static class RectanglePt {
    @XmlAttribute(required = true)
    double x;

    @XmlAttribute(required = true)
    double y;

    @XmlAttribute(required = true)
    double width;

    @XmlAttribute public double height;
  }

  public static class Rectangle2DAdapter extends XmlAdapter<RectanglePt, Rectangle2D> {

    @Override
    public RectanglePt marshal(Rectangle2D v) throws Exception {
      if (Objects.isNull(v)) {
        return null;
      }
      RectanglePt p = new RectanglePt();
      p.x = v.getX();
      p.y = v.getY();
      p.width = v.getWidth();
      p.height = v.getHeight();
      return p;
    }

    @Override
    public Rectangle2D unmarshal(RectanglePt v) throws Exception {
      return new Rectangle2D.Double(v.x, v.y, v.width, v.height);
    }
  }
}
