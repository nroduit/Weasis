/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic.imp.line;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.List;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.test.testers.GraphicTester;

public class LineGraphicTest extends GraphicTester<LineGraphic> {
  private static final String XML_0 = "/graphic/line/line.graphic.0.xml"; // NON-NLS
  private static final String XML_1 = "/graphic/line/line.graphic.1.xml"; // NON-NLS

  public static final String BASIC_TPL =
      "<line fill=\"%s\" showLabel=\"%s\" thickness=\"%s\" uuid=\"%s\">" // NON-NLS
          + "<paint rgb=\"%s\"/>" // NON-NLS
          + "<pts/>" // NON-NLS
          + "</line>"; // NON-NLS

  public static final LineGraphic COMPLETE_OBJECT = new LineGraphic();

  static {
    COMPLETE_OBJECT.setUuid(GRAPHIC_UUID_1);
    COMPLETE_OBJECT.setColorPaint(Color.BLACK);

    List<Point2D> pts =
        Arrays.asList(new Point2D.Double(1028.5, 1110.0), new Point2D.Double(1231.5, 1285.0));
    COMPLETE_OBJECT.setPts(pts);
  }

  @Override
  public String getTemplate() {
    return BASIC_TPL;
  }

  @Override
  public Object[] getParameters() {
    return new Object[] {
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
  public LineGraphic getExpectedDeserializeCompleteGraphic() {
    return COMPLETE_OBJECT;
  }
}
