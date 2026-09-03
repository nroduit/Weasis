/*
 * Copyright (c) 2026 Weasis Team and other contributors.
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
import org.opencv.core.Point3;

/** Binds an OpenCV {@link Point3} to a {@code <pt x y z/>} element. */
public class Point3Adapter extends XmlAdapter<Point3Adapter.Pt3, Point3> {

  public static class Pt3 {
    @XmlAttribute(required = true)
    double x;

    @XmlAttribute(required = true)
    double y;

    @XmlAttribute(required = true)
    double z;
  }

  @Override
  public Pt3 marshal(Point3 v) {
    if (v == null) {
      return null;
    }
    Pt3 p = new Pt3();
    p.x = v.x;
    p.y = v.y;
    p.z = v.z;
    return p;
  }

  @Override
  public Point3 unmarshal(Pt3 v) {
    return new Point3(v.x, v.y, v.z);
  }
}
