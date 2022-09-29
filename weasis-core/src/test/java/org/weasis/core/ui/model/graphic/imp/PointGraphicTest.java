/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic.imp;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.Collections;
import java.util.List;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.test.testers.GraphicTester;

public class PointGraphicTest extends GraphicTester<PointGraphic> {
  private static final String XML_0 = "/graphic/point/point.graphic.0.xml"; // NON-NLS
  private static final String XML_1 = "/graphic/point/point.graphic.1.xml"; // NON-NLS

  static final String BASIC_TPL =
      "<point pointSize=\"%s\" fill=\"%s\" showLabel=\"%s\" thickness=\"%s\" uuid=\"%s\">" // NON-NLS
          + "<paint rgb=\"%s\"/>" // NON-NLS
          + "<pts/>" // NON-NLS
          + "</point>"; // NON-NLS

  public static final PointGraphic COMPLETE_OBJECT = new PointGraphic();

  static {
    COMPLETE_OBJECT.setUuid(GRAPHIC_UUID_1);
    COMPLETE_OBJECT.setLineThickness(3.0f);
    COMPLETE_OBJECT.setColorPaint(Color.RED);

    List<Point2D> pts = Collections.singletonList(new Point2D.Double(1665.5, 987.0));
    COMPLETE_OBJECT.setPts(pts);
  }

  @Override
  public String getTemplate() {
    return BASIC_TPL;
  }

  @Override
  public Object[] getParameters() {
    return new Object[] {
      PointGraphic.DEFAULT_POINT_SIZE,
      Graphic.DEFAULT_FILLED,
      Graphic.DEFAULT_LABEL_VISIBLE,
      Graphic.DEFAULT_LINE_THICKNESS,
      getGraphicUuid(),
      WProperties.color2Hexadecimal(Graphic.DEFAULT_COLOR, true)
    };
  }

  @Override
  public void additionalTestsForDeserializeBasicGraphic(
      PointGraphic result, PointGraphic expected) {
    assertThat(result.getPointSize()).isEqualTo(PointGraphic.DEFAULT_POINT_SIZE);
  }

  @Override
  public String getXmlFilePathCase0() {
    return XML_0;
  }

  @Override
  public String getXmlFilePathCase1() {
    return XML_1;
  }

  @Override
  public PointGraphic getExpectedDeserializeCompleteGraphic() {
    return COMPLETE_OBJECT;
  }

  @Override
  public void additionalTestsForDeserializeCompleteGraphic(
      PointGraphic result, PointGraphic expected) {
    assertThat(result.getPointSize()).isEqualTo(expected.getPointSize());
  }
}
