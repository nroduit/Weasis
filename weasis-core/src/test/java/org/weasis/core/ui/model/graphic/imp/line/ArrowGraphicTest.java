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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.utils.bean.AdvancedShape;
import org.weasis.core.ui.test.testers.GraphicTester;

public class ArrowGraphicTest extends GraphicTester<ArrowGraphic> {
  private static final String XML_0 = "/graphic/arrow/arrow.graphic.0.xml"; // NON-NLS
  private static final String XML_1 = "/graphic/arrow/arrow.graphic.1.xml"; // NON-NLS

  public static final String BASIC_TPL =
      "<arrow fillOpacity=\"%s\" fill=\"%s\" showLabel=\"%s\" thickness=\"%s\" uuid=\"%s\">" // NON-NLS
          + "<paint rgb=\"%s\"/>" // NON-NLS
          + "<pts/>" // NON-NLS
          + "</arrow>"; // NON-NLS

  public static final ArrowGraphic COMPLETE_OBJECT = new ArrowGraphic();

  static {
    COMPLETE_OBJECT.setUuid(GRAPHIC_UUID_1);
    COMPLETE_OBJECT.setColorPaint(Color.BLACK);
    COMPLETE_OBJECT.setPts(
        List.of(new Point2D.Double(10.0, 10.0), new Point2D.Double(110.0, 60.0)));
  }

  @Test
  void the_head_is_drawn_at_the_first_point() throws Exception {
    Point2D tip = new Point2D.Double(10, 10);
    Point2D tail = new Point2D.Double(110, 60);
    ArrowGraphic arrow = new ArrowGraphic();
    arrow.buildGraphic(List.of(tip, tail));
    Rectangle2D head =
        ((AdvancedShape) arrow.getShape()).getShapeList().get(1).getShape().getBounds2D();
    Rectangle2D around =
        new Rectangle2D.Double(
            head.getX() - 1, head.getY() - 1, head.getWidth() + 2, head.getHeight() + 2);
    assertAll(() -> assertTrue(around.contains(tip)), () -> assertFalse(around.contains(tail)));
  }

  @Override
  public String getTemplate() {
    return BASIC_TPL;
  }

  @Override
  public Object[] getParameters() {
    return new Object[] {
      Graphic.DEFAULT_FILL_OPACITY,
      Graphic.DEFAULT_FILLED,
      Graphic.DEFAULT_LABEL_VISIBLE,
      Graphic.DEFAULT_LINE_THICKNESS,
      getGraphicUuid(),
      WProperties.color2Hexadecimal(Graphic.DEFAULT_COLOR, true)
    };
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
  public ArrowGraphic getExpectedDeserializeCompleteGraphic() {
    return COMPLETE_OBJECT;
  }
}
