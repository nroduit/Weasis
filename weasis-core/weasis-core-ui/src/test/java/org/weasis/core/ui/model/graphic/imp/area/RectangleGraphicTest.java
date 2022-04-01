/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic.imp.area;

import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.List;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.test.testers.GraphicTester;

public class RectangleGraphicTest extends GraphicTester<ObliqueRectangleGraphic> {
  private static final String XML_0 = "/graphic/rectangle/rectangle.graphic.0.xml"; // NON-NLS
  private static final String XML_1 = "/graphic/rectangle/rectangle.graphic.1.xml"; // NON-NLS

  public static final String BASIC_TPL =
      "<rectangle fill=\"%s\" showLabel=\"%s\" thickness=\"%s\" uuid=\"%s\">" // NON-NLS
          + "<paint rgb=\"%s\"/>" // NON-NLS
          + "<pts/>" // NON-NLS
          + "</rectangle>"; // NON-NLS

  public static final ObliqueRectangleGraphic COMPLETE_OBJECT = new ObliqueRectangleGraphic();

  static {
    COMPLETE_OBJECT.setUuid(GRAPHIC_UUID_1);
    COMPLETE_OBJECT.setFilled(Boolean.TRUE);

    List<Point2D> pts =
        Arrays.asList(
            new Point2D.Double(252.13800313643495, 91.52639832723474),
            new Point2D.Double(311.46889702038686, 71.13957135389444),
            new Point2D.Double(281.8034500784109, 81.33298484056459),
            new Point2D.Double(294.2900410111563, 117.67216614483638));
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
  public ObliqueRectangleGraphic getExpectedDeserializeCompleteGraphic() {
    return COMPLETE_OBJECT;
  }
}
